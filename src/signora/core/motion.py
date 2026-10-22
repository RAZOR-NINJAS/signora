"""Hand Motion Tracking — ultra-optimised for real-time ISL inference at 30+ fps.

Implementation notes:
  - Pre-allocated numpy ring buffers (O(1) append, cache-friendly memory layout)
  - Single _get_sorted() materialisation per frame — dirty flag invalidates on append
  - Vectorised weighted least-squares velocity over all 3 dims in one solve
  - NumPy-only detect_pattern: single masked slice, no Python loops over data
  - Stale-data expiry inside append(), not on every query
  - FPS-independent dual-hand lookback via np.searchsorted (not fixed frame count)
  - np.convolve trail smoothing — no Python loop over pixel coordinates
"""

import math
import time
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np

# ---------------------------------------------------------------------------
# Tunable constants
# ---------------------------------------------------------------------------
MOTION_MIN_SPEED = 0.08
SWIPE_MIN_SPEED = 0.30
SWIPE_MIN_DISPLACEMENT = 0.08
WAVE_MIN_AMPLITUDE = 0.05
WAVE_MIN_REVERSALS = 3
CIRCLE_MIN_RADIUS = 0.03
TRAJECTORY_STALE_SEC = 1.5   # clear buffer when hand disappears > this long

# ---------------------------------------------------------------------------
# Pattern / dual-hand label constants
# ---------------------------------------------------------------------------
PATTERN_STATIC = "STATIC"
PATTERN_SWIPE_LEFT = "SWIPE_LEFT"
PATTERN_SWIPE_RIGHT = "SWIPE_RIGHT"
PATTERN_SWIPE_UP = "SWIPE_UP"
PATTERN_SWIPE_DOWN = "SWIPE_DOWN"
PATTERN_WAVE = "WAVE"
PATTERN_CIRCLE_CW = "CIRCLE_CW"
PATTERN_CIRCLE_CCW = "CIRCLE_CCW"
PATTERN_NOD = "NOD"
PATTERN_PUSH = "PUSH"
PATTERN_PULL = "PULL"

DUAL_STATIC = "STATIC"
DUAL_CONVERGING = "CONVERGING"
DUAL_DIVERGING = "DIVERGING"
DUAL_PARALLEL = "PARALLEL"
DUAL_ALTERNATING = "ALTERNATING"

SIGN_MOTION_MAP: Dict[str, str] = {
    "HELLO": PATTERN_WAVE,
    "BYE": PATTERN_WAVE,
    "THANK_YOU": PATTERN_SWIPE_DOWN,
    "PLEASE": PATTERN_CIRCLE_CW,
    "HELP": PATTERN_SWIPE_UP,
    "GOOD": PATTERN_SWIPE_UP,
    "BAD": PATTERN_SWIPE_DOWN,
    "NAMASTE": DUAL_CONVERGING,
    "PEACE": PATTERN_STATIC,
    "STOP": PATTERN_STATIC,
    "LOVE": DUAL_CONVERGING,
    "DOCTOR": PATTERN_NOD,
    "EAT": PATTERN_NOD,
    "DRINK": PATTERN_SWIPE_UP,
}

# Pre-built smoothing kernel (avoid re-creating on every draw call)
_SMOOTH_KERNEL = np.array([0.25, 0.5, 0.25], dtype=np.float32)


# ---------------------------------------------------------------------------
# Low-level helpers
# ---------------------------------------------------------------------------

def _get_coords(pt: Any) -> Tuple[float, float, float]:
    """Extract (x, y, z) safely from tuple, list, or MediaPipe landmark."""
    if hasattr(pt, "x"):
        return float(pt.x), float(pt.y), float(getattr(pt, "z", 0.0))
    if isinstance(pt, (list, tuple, np.ndarray)) and len(pt) >= 2:
        z = float(pt[2]) if len(pt) > 2 else 0.0
        return float(pt[0]), float(pt[1]), z
    return 0.0, 0.0, 0.0


def _wls_velocity_3d(times: np.ndarray, positions: np.ndarray) -> np.ndarray:
    """Weighted least-squares linear velocity for all 3 spatial dims in one solve.

    Args:
        times:     (N,)   monotonic timestamps (float64)
        positions: (N, 3) xyz positions (float32)

    Returns:
        (3,) velocity vector [vx, vy, vz] (units/sec).

    This is ~3× faster than calling a scalar LS fit per dimension.
    """
    n = len(times)
    if n < 2:
        return np.zeros(3, dtype=np.float32)

    # Centre time for numerical stability
    t0 = times[0]
    ts = (times - t0).astype(np.float32)          # (N,)
    dt_span = ts[-1] - ts[0]
    if dt_span < 1e-5:
        return np.zeros(3, dtype=np.float32)

    # Linearly increasing weights: recent frames count more
    w = np.linspace(0.5, 1.0, n, dtype=np.float32)  # (N,)
    w_sum = w.sum()

    t_mean = (w @ ts) / w_sum                        # scalar
    p_mean = (w @ positions) / w_sum                 # (3,)

    dt_c = (ts - t_mean) * w                         # (N,) weighted centred time
    dp_c = positions - p_mean                         # (N, 3) centred positions

    num = dt_c @ dp_c                                 # (3,) — numerator per dim
    den = (w * (ts - t_mean) ** 2).sum()              # scalar denominator

    if den < 1e-10:
        return np.zeros(3, dtype=np.float32)

    return (num / den).astype(np.float32)             # (3,)


# ---------------------------------------------------------------------------
# HandTrajectory — ring-buffer-backed, numpy-native
# ---------------------------------------------------------------------------

class HandTrajectory:
    """Rolling temporal buffer backed by pre-allocated numpy ring buffers.

    Public API is identical to the deque-based version; internal representation
    is a fixed-size numpy array pair (timestamps + landmark coords) with a
    write-head pointer.  A dirty flag gates a single materialisation step per
    frame: the first call to _get_sorted() after an append is O(N); all
    subsequent calls in the same frame are O(1) cache hits.
    """

    # point indices stored in axis-1 of _data
    _WRIST = 0
    _TIP   = 1
    _PALM  = 2

    def __init__(self, maxlen: int = 45):
        self._maxlen = maxlen
        # Ring buffers — pre-allocated once
        self._times = np.empty(maxlen, dtype=np.float64)     # (maxlen,)
        self._data  = np.empty((maxlen, 3, 3), dtype=np.float32)  # (maxlen, point, xyz)
        self._head  = 0    # next write index
        self._count = 0    # valid entries (≤ maxlen)
        self._last_t = 0.0

        # Per-frame sorted-array cache
        self._cache_t: Optional[np.ndarray] = None    # (N,)
        self._cache_p: Optional[np.ndarray] = None    # (N, 3, 3)
        self._dirty = True

    # ------------------------------------------------------------------
    # Core write path

    def append(self, hand_landmarks: Any, timestamp: Optional[float] = None) -> None:
        """O(1) ring-buffer write.  Clears history on stale re-entry."""
        t = timestamp if timestamp is not None else time.monotonic()
        if not hand_landmarks or len(hand_landmarks) < 9:
            return

        # Stale expiry: if hand disappeared and came back, reset so old history
        # doesn't contaminate velocity / pattern calculations.
        if self._last_t > 0.0 and (t - self._last_t) > TRAJECTORY_STALE_SEC:
            self.clear()

        wrist = _get_coords(hand_landmarks[0])
        tip   = _get_coords(hand_landmarks[8])
        mcp   = _get_coords(hand_landmarks[9]) if len(hand_landmarks) > 9 else wrist
        palm  = (
            (wrist[0] + mcp[0]) * 0.5,
            (wrist[1] + mcp[1]) * 0.5,
            (wrist[2] + mcp[2]) * 0.5,
        )

        i = self._head
        self._times[i]    = t
        self._data[i, 0]  = wrist
        self._data[i, 1]  = tip
        self._data[i, 2]  = palm

        self._head  = (i + 1) % self._maxlen
        self._count = min(self._count + 1, self._maxlen)
        self._last_t = t
        self._dirty = True

    def clear(self) -> None:
        self._head  = 0
        self._count = 0
        self._last_t = 0.0
        self._dirty = True
        self._cache_t = None
        self._cache_p = None

    # ------------------------------------------------------------------
    # Core read path — single materialisation per frame

    def _get_sorted(self) -> Tuple[np.ndarray, np.ndarray]:
        """Return (times, pts) numpy arrays sorted oldest→newest.

        Result is cached until next append() — O(N) first call, O(1) after.
        times : (N,)      float64
        pts   : (N, 3, 3) float32  [frame, point_idx, xyz]
        """
        if not self._dirty and self._cache_t is not None:
            return self._cache_t, self._cache_p

        n = self._count
        if n == 0:
            self._cache_t = np.empty(0, dtype=np.float64)
            self._cache_p = np.empty((0, 3, 3), dtype=np.float32)
            self._dirty = False
            return self._cache_t, self._cache_p

        if n < self._maxlen:
            # Buffer not yet wrapped — data is contiguous 0..n-1
            self._cache_t = self._times[:n].copy()
            self._cache_p = self._data[:n].copy()
        else:
            # Wrapped ring: oldest entry is at self._head
            idx = (np.arange(self._maxlen, dtype=np.int32) + self._head) % self._maxlen
            self._cache_t = self._times[idx]
            self._cache_p = self._data[idx]

        self._dirty = False
        return self._cache_t, self._cache_p

    # ------------------------------------------------------------------
    # Public query methods — all use the cached sorted arrays

    def get_positions(self, point_idx: int = 0) -> List[Tuple[float, float, float]]:
        """Return list of (x, y, z) for the specified point (compat shim)."""
        _, pts = self._get_sorted()
        if len(pts) == 0:
            return []
        col = pts[:, point_idx, :]     # (N, 3)
        return [tuple(row) for row in col]

    def get_velocity(
        self, dt_window: float = 0.20, point_idx: int = 0
    ) -> Tuple[float, float, float]:
        """Weighted least-squares velocity over recent time window.

        All 3 spatial dimensions solved in one vectorised pass.
        """
        t_all, p_all = self._get_sorted()
        n = len(t_all)
        if n < 2:
            return 0.0, 0.0, 0.0

        cutoff = t_all[-1] - dt_window
        mask = t_all >= cutoff
        if mask.sum() < 2:
            mask = np.zeros(n, dtype=bool)
            mask[-2:] = True

        t_w = t_all[mask]
        p_w = p_all[mask, point_idx, :]   # (M, 3)

        vel = _wls_velocity_3d(t_w, p_w)
        return float(vel[0]), float(vel[1]), float(vel[2])

    def get_speed(self, dt_window: float = 0.20, point_idx: int = 0) -> float:
        vx, vy, vz = self.get_velocity(dt_window=dt_window, point_idx=point_idx)
        return math.sqrt(vx * vx + vy * vy + vz * vz)

    def get_net_displacement(
        self, window_sec: float = 0.50, point_idx: int = 0
    ) -> Tuple[float, float, float]:
        t_all, p_all = self._get_sorted()
        if len(t_all) < 2:
            return 0.0, 0.0, 0.0

        mask = t_all >= (t_all[-1] - window_sec)
        if mask.sum() < 2:
            mask = np.ones(len(t_all), dtype=bool)

        p_w = p_all[mask, point_idx, :]
        d = p_w[-1] - p_w[0]
        return float(d[0]), float(d[1]), float(d[2])

    def get_path_length(self, window_sec: float = 0.50, point_idx: int = 0) -> float:
        t_all, p_all = self._get_sorted()
        if len(t_all) < 2:
            return 0.0

        mask = t_all >= (t_all[-1] - window_sec)
        p_w = p_all[mask, point_idx, :]
        if len(p_w) < 2:
            return 0.0

        # Vectorised arc-length: sum of Euclidean distances between consecutive pts
        diffs = np.diff(p_w, axis=0)           # (N-1, 3)
        return float(np.sqrt((diffs * diffs).sum(axis=1)).sum())

    def detect_pattern(self, window_sec: float = 0.65) -> str:
        """Classify pattern using a single masked slice of the cached buffer."""
        t_all, p_all = self._get_sorted()
        if len(t_all) < 5:
            return PATTERN_STATIC

        # Adaptive window: use full buffer when fewer than 8 fresh frames exist
        cutoff = t_all[-1] - window_sec
        mask = t_all >= cutoff
        if mask.sum() < 5:
            mask = np.ones(len(t_all), dtype=bool)

        p_w = p_all[mask, self._PALM, :]       # (N, 3) — palm is most stable point
        if len(p_w) < 5:
            return PATTERN_STATIC

        xs = p_w[:, 0]
        ys = p_w[:, 1]
        zs = p_w[:, 2]

        # --- Speed check (early exit, uses cached result) ---
        vel = self.get_velocity(dt_window=0.25, point_idx=self._PALM)
        speed = math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2])
        if speed < MOTION_MIN_SPEED:
            return PATTERN_STATIC

        # --- Displacement metrics (all vectorised) ---
        dx, dy, dz = float(xs[-1] - xs[0]), float(ys[-1] - ys[0]), float(zs[-1] - zs[0])
        net_disp = math.sqrt(dx * dx + dy * dy + dz * dz)

        diffs_x = np.diff(xs)
        diffs_y = np.diff(ys)
        path_len = float(np.sqrt(diffs_x * diffs_x + diffs_y * diffs_y +
                                  np.diff(zs) ** 2).sum())

        # --- 1. Wave: amplitude-weighted horizontal oscillation ---
        x_range = float(xs.max() - xs.min())
        jitter = max(0.004, x_range * 0.06)
        dx_sig = diffs_x[np.abs(diffs_x) > jitter]
        if len(dx_sig) >= WAVE_MIN_REVERSALS + 1:
            reversals = int((np.sign(dx_sig[:-1]) != np.sign(dx_sig[1:])).sum())
            if reversals >= WAVE_MIN_REVERSALS and x_range >= WAVE_MIN_AMPLITUDE:
                return PATTERN_WAVE

        # --- 2. Nod: amplitude-weighted vertical oscillation ---
        y_range = float(ys.max() - ys.min())
        jitter_y = max(0.004, y_range * 0.06)
        dy_sig = diffs_y[np.abs(diffs_y) > jitter_y]
        if len(dy_sig) >= WAVE_MIN_REVERSALS + 1:
            v_reversals = int((np.sign(dy_sig[:-1]) != np.sign(dy_sig[1:])).sum())
            if v_reversals >= WAVE_MIN_REVERSALS and y_range >= WAVE_MIN_AMPLITUDE:
                return PATTERN_NOD

        # --- 3. Circle: winding angle check ---
        if path_len >= 0.12 and (net_disp / max(1e-4, path_len)) < 0.45:
            cx, cy = float(xs.mean()), float(ys.mean())
            angles = np.arctan2(ys - cy, xs - cx)
            total_turn = float(np.unwrap(angles)[-1] - np.unwrap(angles)[0])
            if abs(total_turn) >= 1.2 * math.pi:
                return PATTERN_CIRCLE_CW if total_turn > 0 else PATTERN_CIRCLE_CCW

        # --- 4. Swipe: both speed AND displacement gate (stops false positives) ---
        if speed >= SWIPE_MIN_SPEED and net_disp >= SWIPE_MIN_DISPLACEMENT:
            if abs(dx) > 1.25 * abs(dy):
                return PATTERN_SWIPE_RIGHT if dx > 0 else PATTERN_SWIPE_LEFT
            if abs(dy) > 1.25 * abs(dx):
                return PATTERN_SWIPE_DOWN if dy > 0 else PATTERN_SWIPE_UP

        # --- 5. Depth push / pull ---
        if abs(dz) >= 0.12 and abs(dz) > 1.5 * max(abs(dx), abs(dy)):
            return PATTERN_PUSH if dz < 0 else PATTERN_PULL

        # --- Directional fallback (requires meaningful displacement) ---
        if net_disp >= SWIPE_MIN_DISPLACEMENT:
            if abs(dx) > abs(dy):
                return PATTERN_SWIPE_RIGHT if dx > 0 else PATTERN_SWIPE_LEFT
            return PATTERN_SWIPE_DOWN if dy > 0 else PATTERN_SWIPE_UP

        return PATTERN_STATIC


# ---------------------------------------------------------------------------
# HandMotionTracker
# ---------------------------------------------------------------------------

class HandMotionTracker:
    """Dual-hand motion tracking — minimal per-frame work via shared buffer cache."""

    def __init__(self, history_len: int = 45):
        self.right_traj = HandTrajectory(maxlen=history_len)
        self.left_traj  = HandTrajectory(maxlen=history_len)
        self.last_update_t = 0.0

        _static: Dict[str, Any] = {
            "dominant_hand": "Right",
            "speed": 0.0,
            "velocity": (0.0, 0.0, 0.0),
            "pattern": PATTERN_STATIC,
            "right_pattern": PATTERN_STATIC,
            "left_pattern": PATTERN_STATIC,
            "dual_motion": DUAL_STATIC,
            "relative_speed": 0.0,
            "hand_distance": 0.0,
            "is_moving": False,
        }
        self._latest_state: Dict[str, Any] = _static

    def update(
        self,
        hand_landmarks_list: List[Any],
        handedness_list: List[str],
        pose_landmarks: Optional[List[Any]] = None,
        timestamp: Optional[float] = None,
    ) -> Dict[str, Any]:
        now = timestamp if timestamp is not None else time.monotonic()
        self.last_update_t = now

        r_lms = l_lms = None
        for idx, lms in enumerate(hand_landmarks_list):
            if not lms:
                continue
            hnd = handedness_list[idx] if idx < len(handedness_list) else (
                "Right" if idx == 0 else "Left"
            )
            if hnd == "Right":
                r_lms = lms
            else:
                l_lms = lms

        if r_lms:
            self.right_traj.append(r_lms, timestamp=now)
        if l_lms:
            self.left_traj.append(l_lms, timestamp=now)

        # --- Per-hand velocity / speed / pattern ---
        # get_velocity() internally calls _get_sorted() which is now cached;
        # detect_pattern() re-uses that same cache — no redundant buffer scan.
        r_vel   = self.right_traj.get_velocity(point_idx=2)
        l_vel   = self.left_traj.get_velocity(point_idx=2)
        r_speed = math.sqrt(r_vel[0]**2 + r_vel[1]**2 + r_vel[2]**2)
        l_speed = math.sqrt(l_vel[0]**2 + l_vel[1]**2 + l_vel[2]**2)

        r_pat = self.right_traj.detect_pattern()
        l_pat = self.left_traj.detect_pattern()

        dominant  = "Right" if (r_speed >= l_speed or not l_lms) else "Left"
        dom_speed = r_speed if dominant == "Right" else l_speed
        dom_vel   = r_vel   if dominant == "Right" else l_vel
        dom_pat   = r_pat   if dominant == "Right" else l_pat

        # --- Dual-hand dynamics (FPS-independent via searchsorted) ---
        dual_pattern = DUAL_STATIC
        rel_speed    = 0.0
        h_dist       = 0.0

        if r_lms and l_lms:
            r_t, r_pts = self.right_traj._get_sorted()   # already cached
            l_t, l_pts = self.left_traj._get_sorted()    # already cached

            if len(r_t) >= 2 and len(l_t) >= 2:
                r_curr = r_pts[-1, 0, :]   # wrist xyz
                l_curr = l_pts[-1, 0, :]

                # Find entry ~0.15 s ago via binary search (O(log N))
                LOOKBACK = 0.15
                r_prev_i = max(0, np.searchsorted(r_t, r_t[-1] - LOOKBACK, side="right") - 1)
                l_prev_i = max(0, np.searchsorted(l_t, l_t[-1] - LOOKBACK, side="right") - 1)
                r_prev = r_pts[r_prev_i, 0, :]
                l_prev = l_pts[l_prev_i, 0, :]

                d_curr  = float(np.linalg.norm(r_curr - l_curr))
                d_prev  = float(np.linalg.norm(r_prev - l_prev))
                dt_dual = max(1e-4, float(r_t[-1] - r_t[r_prev_i]))
                rel_vel = (d_curr - d_prev) / dt_dual
                rel_speed = abs(rel_vel)
                h_dist    = d_curr

                if rel_vel < -0.18:
                    dual_pattern = DUAL_CONVERGING
                elif rel_vel > +0.18:
                    dual_pattern = DUAL_DIVERGING
                elif r_speed > MOTION_MIN_SPEED and l_speed > MOTION_MIN_SPEED:
                    r_dot_l = r_vel[0] * l_vel[0] + r_vel[1] * l_vel[1]
                    dual_pattern = (
                        DUAL_PARALLEL    if r_dot_l >  0.02 else
                        DUAL_ALTERNATING if r_dot_l < -0.02 else
                        DUAL_STATIC
                    )

        self._latest_state = {
            "dominant_hand": dominant,
            "speed":         float(dom_speed),
            "velocity":      dom_vel,
            "pattern":       dom_pat,
            "right_pattern": r_pat,
            "left_pattern":  l_pat,
            "dual_motion":   dual_pattern,
            "relative_speed": float(rel_speed),
            "hand_distance":  float(h_dist),
            "is_moving": bool(dom_speed >= MOTION_MIN_SPEED or rel_speed >= MOTION_MIN_SPEED),
        }
        return self._latest_state

    def get_motion_state(self) -> Dict[str, Any]:
        return self._latest_state

    def reset(self) -> None:
        self.right_traj.clear()
        self.left_traj.clear()
        self._latest_state = {
            "dominant_hand": "Right",
            "speed": 0.0,
            "velocity": (0.0, 0.0, 0.0),
            "pattern": PATTERN_STATIC,
            "right_pattern": PATTERN_STATIC,
            "left_pattern": PATTERN_STATIC,
            "dual_motion": DUAL_STATIC,
            "relative_speed": 0.0,
            "hand_distance": 0.0,
            "is_moving": False,
        }

    def draw_motion_trail(
        self,
        frame: np.ndarray,
        max_points: int = 22,
        draw_vector: bool = True,
    ) -> None:
        """Render smoothed glowing trails and velocity arrows.

        Trail smoothing is done via np.convolve — no Python loop over pixels.
        """
        h, w = frame.shape[:2]
        wf, hf = float(w), float(h)

        for traj, col_base in (
            (self.right_traj, (6, 182, 212)),    # Neon Cyan
            (self.left_traj,  (244, 63, 94)),    # Neon Rose
        ):
            _, pts = traj._get_sorted()           # cached — zero extra cost
            if len(pts) < 2:
                continue

            # Grab index-tip positions for last max_points frames
            n = min(len(pts), max_points)
            tips = pts[-n:, HandTrajectory._TIP, :2]   # (n, 2) — xy only

            # Vectorised pixel conversion
            px = (tips * np.array([wf, hf], dtype=np.float32)).astype(np.int32)  # (n, 2)

            # Convolve-smooth x and y independently (boundary='same' keeps array size)
            if n >= 3:
                px[:, 0] = np.convolve(px[:, 0], _SMOOTH_KERNEL, mode="same").astype(np.int32)
                px[:, 1] = np.convolve(px[:, 1], _SMOOTH_KERNEL, mode="same").astype(np.int32)

            # Bounds mask — avoid out-of-frame draws
            valid = (px[:, 0] >= 0) & (px[:, 0] < w) & (px[:, 1] >= 0) & (px[:, 1] < h)

            # Draw fading ribbon
            alphas = np.linspace(0.0, 1.0, n, dtype=np.float32)
            for i in range(1, n):
                if not (valid[i - 1] and valid[i]):
                    continue
                thickness = max(1, int(4 * alphas[i]))
                cv2.line(frame, tuple(px[i - 1]), tuple(px[i]), col_base, thickness, cv2.LINE_AA)

            # Apex glow
            if valid[-1]:
                tip_px = tuple(px[-1])
                cv2.circle(frame, tip_px, 6, col_base, -1, cv2.LINE_AA)
                cv2.circle(frame, tip_px, 9, (255, 255, 255), 1, cv2.LINE_AA)

                # Velocity arrow
                if draw_vector:
                    vx, vy, _ = traj.get_velocity(dt_window=0.20, point_idx=HandTrajectory._TIP)
                    spd = math.sqrt(vx * vx + vy * vy)
                    if spd >= MOTION_MIN_SPEED:
                        arrow_len = min(60.0, spd * 75.0)
                        angle = math.atan2(vy, vx)
                        end_pt = (
                            int(tip_px[0] + arrow_len * math.cos(angle)),
                            int(tip_px[1] + arrow_len * math.sin(angle)),
                        )
                        cv2.arrowedLine(
                            frame, tip_px, end_pt,
                            (52, 211, 153), 2, tipLength=0.35,
                        )


# ---------------------------------------------------------------------------
# Feature extraction and sign-motion matching
# ---------------------------------------------------------------------------

def extract_motion_features(tracker: HandMotionTracker) -> np.ndarray:
    """Extract a canonical 24D motion feature vector for ML consumption."""
    r_vel   = tracker.right_traj.get_velocity(point_idx=2)
    l_vel   = tracker.left_traj.get_velocity(point_idx=2)
    r_speed = math.sqrt(r_vel[0]**2 + r_vel[1]**2 + r_vel[2]**2)
    l_speed = math.sqrt(l_vel[0]**2 + l_vel[1]**2 + l_vel[2]**2)

    r_disp = tracker.right_traj.get_net_displacement(window_sec=0.4, point_idx=2)
    l_disp = tracker.left_traj.get_net_displacement(window_sec=0.4, point_idx=2)
    r_path = tracker.right_traj.get_path_length(window_sec=0.4, point_idx=2)
    l_path = tracker.left_traj.get_path_length(window_sec=0.4, point_idx=2)

    st = tracker.get_motion_state()
    rel_speed = st["relative_speed"]
    h_dist    = st["hand_distance"]
    pat       = st["pattern"]

    features = [
        float(r_vel[0]), float(r_vel[1]), float(r_vel[2]), float(r_speed),
        float(r_disp[0]), float(r_disp[1]), float(r_path),
        float(l_vel[0]), float(l_vel[1]), float(l_vel[2]), float(l_speed),
        float(l_disp[0]), float(l_disp[1]), float(l_path),
        float(rel_speed), float(h_dist),
        float(st["is_moving"]),
        1.0 if pat == PATTERN_WAVE else 0.0,
        1.0 if pat in (PATTERN_SWIPE_LEFT, PATTERN_SWIPE_RIGHT) else 0.0,
        1.0 if pat in (PATTERN_SWIPE_UP, PATTERN_SWIPE_DOWN) else 0.0,
        1.0 if "CIRCLE" in pat else 0.0,
        1.0 if st["dual_motion"] == DUAL_CONVERGING else 0.0,
        1.0 if st["dual_motion"] == DUAL_DIVERGING else 0.0,
        1.0 if pat == PATTERN_STATIC else 0.0,
    ]
    return np.array(features, dtype=np.float32)


def match_sign_motion(
    sign_name: str, motion_state: Dict[str, Any]
) -> Tuple[bool, float]:
    """Check if detected movement satisfies the required pattern for a dynamic sign.

    Returns:
        (is_match, confidence_factor)
    """
    clean_sign = sign_name.upper().strip()
    expected   = SIGN_MOTION_MAP.get(clean_sign)
    if expected is None:
        return True, 1.0

    actual_pat  = motion_state.get("pattern",    PATTERN_STATIC)
    actual_dual = motion_state.get("dual_motion", DUAL_STATIC)
    is_moving   = motion_state.get("is_moving",  False)

    if expected == DUAL_CONVERGING:
        if actual_dual == DUAL_CONVERGING or (
            actual_pat == PATTERN_STATIC and motion_state.get("hand_distance", 1.0) < 0.12
        ):
            return True, 1.25
        return False, 0.70

    if expected == PATTERN_WAVE:
        if actual_pat == PATTERN_WAVE:
            return True, 1.30
        if is_moving and actual_pat in (PATTERN_SWIPE_LEFT, PATTERN_SWIPE_RIGHT):
            return True, 1.10
        return False, 0.65

    if expected in (PATTERN_SWIPE_UP, PATTERN_SWIPE_DOWN, PATTERN_SWIPE_LEFT, PATTERN_SWIPE_RIGHT):
        if actual_pat == expected:
            return True, 1.25
        if is_moving:
            return True, 1.0
        return False, 0.75

    if expected == PATTERN_CIRCLE_CW:
        if "CIRCLE" in actual_pat:
            return True, 1.30
        return False, 0.80

    if expected == PATTERN_STATIC:
        if actual_pat == PATTERN_STATIC:
            return True, 1.10
        return False, 0.70

    return True, 1.0

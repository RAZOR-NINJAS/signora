"""Spatial Holistic Pipeline: Simultaneous 33-point Body Pose & 2-Hand Landmark Tracking.

Maps whole upper-body placement anchors (Forehead, Temple, Lips, Chin, Chest, Wrists, Waist)
alongside 21-point hand finger configurations for static spatial sign language recognition.
"""

import queue
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np

from signora.core.camera import Camera
from signora.core.hands import HAND_CONNECTIONS, HandPipeline, LandmarkPoint

# Core Upper-Body Skeleton Connections (MediaPipe Pose 11-16)
UPPER_BODY_CONNECTIONS: List[Tuple[int, int]] = [
    (11, 12),  # Shoulders
    (11, 13),  # Left Shoulder -> Elbow
    (13, 15),  # Left Elbow -> Wrist
    (12, 14),  # Right Shoulder -> Elbow
    (14, 16),  # Right Elbow -> Wrist
]


class SpatialPipeline:
    """High-performance asynchronous upper-body pose & dual-hand tracking capture pipeline."""

    def __init__(
        self,
        source: Any = 0,
        mirror: bool = True,
        detect_every: int = 1,
        use_fake: bool = False,
    ):
        self.source = source
        self.mirror = mirror
        self.detect_every = max(1, int(detect_every))
        self.use_fake = use_fake

        self._frames: "queue.Queue" = queue.Queue(maxsize=1)
        self._stop = threading.Event()
        self.started = threading.Event()
        self.error: Optional[Exception] = None

        # Underlying hand pipeline with auto GPU/CPU acceleration
        self._hand_pipe = HandPipeline(
            source=source,
            num_hands=2,
            mirror=mirror,
            detect_every=self.detect_every,
            use_fake=use_fake,
        )

        self._last_pose_landmarks: Optional[List[LandmarkPoint]] = None
        self._pose = None
        self._pending_pose_frame: Optional[np.ndarray] = None
        self._pose_lock = threading.Lock()
        self._pose_thread = None

        # Initialize MediaPipe Pose with BlazePose Lite (complexity 0) for sub-20ms tracking
        if not self.use_fake:
            try:
                self._pose = mp.solutions.pose.Pose(
                    static_image_mode=False,
                    model_complexity=0,
                    smooth_landmarks=True,
                    min_detection_confidence=0.4,
                    min_tracking_confidence=0.4,
                )
                self._pose_thread = threading.Thread(target=self._pose_worker_loop, daemon=True)
                self._pose_thread.start()
            except Exception as e:
                print(f"[Warning] Could not initialize MediaPipe Pose: {e}")
                self._pose = None

        self._thread = threading.Thread(target=self._capture_loop, daemon=True)
        self._thread.start()

    @property
    def delegate(self) -> str:
        """Hardware delegate used by underlying pipeline (GPU or CPU)."""
        return self._hand_pipe.delegate

    def _pose_worker_loop(self) -> None:
        """Asynchronous pose inference worker that decouples body tracking from video capture."""
        while not self._stop.is_set():
            frame_to_process = None
            with self._pose_lock:
                if self._pending_pose_frame is not None:
                    frame_to_process = self._pending_pose_frame
                    self._pending_pose_frame = None

            if frame_to_process is None:
                time.sleep(0.005)
                continue

            try:
                # Downsample maintaining aspect ratio for clean pose estimation
                h_orig, w_orig = frame_to_process.shape[:2]
                target_w = 320
                target_h = max(160, int(h_orig * (target_w / max(1, w_orig))))
                small = cv2.resize(frame_to_process, (target_w, target_h), interpolation=cv2.INTER_AREA)
                rgb = cv2.cvtColor(small, cv2.COLOR_BGR2RGB)
                res = self._pose.process(rgb)
                if res and res.pose_landmarks:
                    raw_pts = [
                        LandmarkPoint(lm.x, lm.y, lm.z)
                        for lm in res.pose_landmarks.landmark
                    ]
                    # Temporal exponential moving average (EMA) to eliminate skeleton jitter
                    alpha = 0.55
                    if self._last_pose_landmarks is not None and len(self._last_pose_landmarks) == len(raw_pts):
                        smoothed = []
                        for old_p, new_p in zip(self._last_pose_landmarks, raw_pts):
                            sx = alpha * new_p.x + (1.0 - alpha) * old_p.x
                            sy = alpha * new_p.y + (1.0 - alpha) * old_p.y
                            sz = alpha * new_p.z + (1.0 - alpha) * old_p.z
                            smoothed.append(LandmarkPoint(sx, sy, sz))
                        self._last_pose_landmarks = smoothed
                    else:
                        self._last_pose_landmarks = raw_pts
            except Exception:
                pass

    def _generate_synthetic_pose(self) -> List[LandmarkPoint]:
        """Generate realistic synthetic upper-body pose for headless/testing mode."""
        t = time.monotonic() * 1.5
        pts = [LandmarkPoint(0.5, 0.25, 0.0) for _ in range(33)]
        # Nose & Face
        pts[0] = LandmarkPoint(0.5, 0.25 + 0.01 * np.sin(t), 0.0)
        pts[2] = LandmarkPoint(0.47, 0.22, 0.0)  # Left eye
        pts[5] = LandmarkPoint(0.53, 0.22, 0.0)  # Right eye
        pts[7] = LandmarkPoint(0.42, 0.23, 0.0)  # Left ear
        pts[8] = LandmarkPoint(0.58, 0.23, 0.0)  # Right ear
        pts[9] = LandmarkPoint(0.48, 0.29, 0.0)  # Mouth L
        pts[10] = LandmarkPoint(0.52, 0.29, 0.0)  # Mouth R
        # Shoulders
        pts[11] = LandmarkPoint(0.35, 0.45, 0.0)  # Left shoulder
        pts[12] = LandmarkPoint(0.65, 0.45, 0.0)  # Right shoulder
        # Elbows
        pts[13] = LandmarkPoint(0.30, 0.65, 0.0)  # Left elbow
        pts[14] = LandmarkPoint(0.70, 0.65, 0.0)  # Right elbow
        # Wrists
        pts[15] = LandmarkPoint(0.35, 0.82, 0.0)  # Left wrist
        pts[16] = LandmarkPoint(0.65, 0.82, 0.0)  # Right wrist
        # Hips
        pts[23] = LandmarkPoint(0.40, 0.95, 0.0)  # Left hip
        pts[24] = LandmarkPoint(0.60, 0.95, 0.0)  # Right hip
        return pts

    def _capture_loop(self) -> None:
        """Capture loop merging hand tracking with whole-body pose estimation."""
        if not self._hand_pipe.started.wait(timeout=5):
            self.error = self._hand_pipe.error or RuntimeError("Hand pipeline failed to start")
            self.started.set()
            return

        self.started.set()

        while not self._stop.is_set():
            try:
                frame, hand_lms, handedness = self._hand_pipe.read(timeout=1.0)
                if frame is None:
                    continue

                if self.use_fake or self._pose is None:
                    pose_landmarks = self._generate_synthetic_pose()
                else:
                    # Submit latest frame to async pose worker without blocking hand tracking
                    with self._pose_lock:
                        self._pending_pose_frame = frame
                    pose_landmarks = self._last_pose_landmarks

                # Enqueue merged sample (drop older frame if consumer is behind)
                item = (frame, hand_lms, handedness, pose_landmarks)
                try:
                    self._frames.put_nowait(item)
                except queue.Full:
                    try:
                        self._frames.get_nowait()
                    except queue.Empty:
                        pass
                    self._frames.put_nowait(item)

            except Exception as e:
                self.error = e
                time.sleep(0.01)

    def read(
        self, timeout: float = 1.0
    ) -> Tuple[Optional[np.ndarray], Optional[Any], Optional[Any], Optional[List[LandmarkPoint]]]:
        """Fetch latest frame, dual-hand landmarks, handedness, and body pose landmarks."""
        try:
            return self._frames.get(timeout=timeout)
        except queue.Empty:
            return None, None, None, None

    def close(self) -> None:
        """Shut down capture loop and release models."""
        self._stop.set()
        self._hand_pipe.close()
        if self._pose:
            try:
                self._pose.close()
            except Exception:
                pass


def draw_cyber_spatial(
    frame: np.ndarray,
    hand_lms_list: List[Any],
    handedness_list: List[str],
    pose_landmarks: Optional[List[Any]],
    target_anchor_name: Optional[str] = None,
    target_anchor_pt: Optional[Tuple[int, int]] = None,
    is_matched: bool = False,
    active_anchor_name: Optional[str] = None,
    active_anchor_pt: Optional[Tuple[int, int]] = None,
) -> None:
    """Render high-contrast cybernetic torso skeleton, hand skeletons, and steady target anchor rings."""
    h, w = frame.shape[:2]

    # 1. Draw Body Torso & Arms Skeleton
    if pose_landmarks and len(pose_landmarks) >= 25:
        coords = [(int(p.x * w), int(p.y * h)) for p in pose_landmarks[:25]]

        # Torso glow bones
        torso_color = (147, 51, 234)  # Deep Neon Purple
        arm_color = (6, 182, 212)     # Vivid Cyan
        joint_color = (250, 204, 21)   # Bright Amber

        for s, e in UPPER_BODY_CONNECTIONS:
            if s < len(coords) and e < len(coords):
                p1, p2 = coords[s], coords[e]
                # Filter out points outside reasonable frame boundaries
                if 0 <= p1[0] <= w and 0 <= p1[1] <= h and 0 <= p2[0] <= w and 0 <= p2[1] <= h:
                    col = arm_color if s in (11, 12, 13, 14, 15, 16) else torso_color
                    cv2.line(frame, p1, p2, col, 2, cv2.LINE_AA)

        # Draw Torso Joints (Shoulders, Elbows, Wrists)
        for idx in (11, 12, 13, 14, 15, 16):
            if idx < len(coords):
                pt = coords[idx]
                if 0 <= pt[0] <= w and 0 <= pt[1] <= h:
                    cv2.circle(frame, pt, 5, joint_color, -1, cv2.LINE_AA)
                    cv2.circle(frame, pt, 7, (255, 255, 255), 1, cv2.LINE_AA)

        # Draw Chest Core Reactor (Midpoint between shoulders)
        p_lsh, p_rsh = coords[11], coords[12]
        chest_cx = (p_lsh[0] + p_rsh[0]) // 2
        chest_cy = (p_lsh[1] + p_rsh[1]) // 2
        cv2.circle(frame, (chest_cx, chest_cy), 8, (6, 182, 212), -1, cv2.LINE_AA)
        cv2.circle(frame, (chest_cx, chest_cy), 13, (52, 211, 153), 2, cv2.LINE_AA)

    # 2. Draw Hand Skeletons
    for idx, lms in enumerate(hand_lms_list):
        if not lms:
            continue
        is_right = handedness_list[idx] == "Right" if idx < len(handedness_list) else (idx == 1)
        bone_color = (52, 211, 153) if is_right else (244, 63, 94)  # Emerald vs Rose
        joint_col = (255, 255, 255)

        h_coords = [(int(p.x * w), int(p.y * h)) for p in lms]
        for s, e in HAND_CONNECTIONS:
            cv2.line(frame, h_coords[s], h_coords[e], bone_color, 2, cv2.LINE_AA)
        for pt in h_coords:
            cv2.circle(frame, pt, 4, joint_col, -1, cv2.LINE_AA)

    # 3. Draw Steady Target Body Placement Ring
    display_pt = target_anchor_pt if target_anchor_pt is not None else active_anchor_pt
    display_name = target_anchor_name if target_anchor_name else active_anchor_name

    if display_pt is not None and display_name and display_name.upper() not in ("OPEN_SPACE", "NONE"):
        ax, ay = display_pt
        if 0 <= ax <= w and 0 <= ay <= h:
            ring_col = (52, 211, 153) if is_matched else (6, 182, 212)  # Green when matched, Cyan when waiting
            label_text = f"✓ {display_name}" if is_matched else f"TARGET: {display_name}"
            cv2.circle(frame, (ax, ay), 22, ring_col, 2, cv2.LINE_AA)
            cv2.circle(frame, (ax, ay), 5, ring_col, -1, cv2.LINE_AA)
            cv2.putText(
                frame,
                label_text,
                (ax + 28, ay + 6),
                cv2.FONT_HERSHEY_DUPLEX,
                0.52,
                ring_col,
                1,
                cv2.LINE_AA,
            )

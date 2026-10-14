"""Lip landmark detection, Mouth Aspect Ratio (MAR) extraction, and Viseme classification."""

import collections
import time
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np

# Canonical MediaPipe FaceMesh lip indices
OUTER_LIP_INDICES = [
    61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291,
    375, 321, 405, 314, 17, 84, 181, 91, 146, 61
]

INNER_LIP_INDICES = [
    78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308,
    324, 318, 402, 317, 14, 87, 178, 88, 95, 78
]

# Key landmark pairs for aperture & width
IDX_LIP_LEFT_CORNER = 61
IDX_LIP_RIGHT_CORNER = 291
IDX_INNER_LEFT_CORNER = 78
IDX_INNER_RIGHT_CORNER = 308
IDX_INNER_TOP = 13
IDX_INNER_BOTTOM = 14
IDX_INNER_TOP_L = 81
IDX_INNER_BOT_L = 178
IDX_INNER_TOP_R = 311
IDX_INNER_BOT_R = 402
IDX_OUTER_TOP = 0
IDX_OUTER_BOTTOM = 17
IDX_NOSE_TIP = 1
IDX_CHIN = 152
IDX_LEFT_EYE_OUTER = 33
IDX_RIGHT_EYE_OUTER = 263


class Viseme(str, Enum):
    """Standard oral viseme categories used in speechreading and sign mouthing."""
    REST = "REST / SILENCE"
    OPEN_A = "OPEN 'A/AH' (Vowel)"
    ROUNDED_O = "ROUNDED 'O/U' (Pucker)"
    BILABIAL_M = "BILABIAL 'M/B/P' (Closed)"
    DENTAL_F = "DENTAL 'F/V' (Fricative)"
    SPREAD_EE = "SPREAD 'EE/S' (Smile)"
    NEUTRAL = "NEUTRAL / CONSONANT"


@dataclass
class LipMetrics:
    """Quantitative real-time metrics for lip configuration and speech cadence."""
    mar: float                    # Mouth Aspect Ratio (inner aperture / inner width)
    mar_outer: float              # Outer lip aspect ratio
    inner_width: float            # Inner corner-to-corner span (pixels)
    inner_height: float           # Inner vertical aperture (pixels)
    outer_width: float            # Outer corner-to-corner span (pixels)
    outer_height: float           # Outer vertical height (pixels)
    lip_width_ratio: float        # Outer lip width normalized by eye distance
    velocity: float               # Instantaneous change in aperture |dMAR/dt|
    is_speaking: bool             # Visual voice activity detection (V-VAD)
    viseme: Viseme                # Classified oral viseme shape
    speech_cadence_hz: float      # Detected syllable oscillation rate in Hz (syll/s)
    center_xy: Tuple[int, int]    # Pixel coordinates of mouth center
    bbox: Tuple[int, int, int, int] # Bounding box (x, y, w, h) for lip zoom inset
    landmarks_inner_px: List[Tuple[int, int]]
    landmarks_outer_px: List[Tuple[int, int]]


class LipDetector:
    """High-performance FaceMesh processor for real-time lip tracking and viseme extraction."""

    def __init__(
        self,
        max_num_faces: int = 1,
        min_detection_confidence: float = 0.5,
        min_tracking_confidence: float = 0.5,
        buffer_size: int = 45,
        use_fake: bool = False,
    ):
        self.use_fake = use_fake
        self.buffer_size = buffer_size
        self.mar_history: collections.deque = collections.deque(maxlen=buffer_size)
        self.time_history: collections.deque = collections.deque(maxlen=buffer_size)
        self.last_mar: float = 0.0
        self.last_time: float = time.monotonic()
        self.smoothed_velocity: float = 0.0

        if not use_fake:
            self.face_mesh = mp.solutions.face_mesh.FaceMesh(
                max_num_faces=max_num_faces,
                refine_landmarks=True,
                min_detection_confidence=min_detection_confidence,
                min_tracking_confidence=min_tracking_confidence,
            )
        else:
            self.face_mesh = None

    def process(self, frame: np.ndarray) -> Optional[LipMetrics]:
        """Process video frame and extract comprehensive lip metrics."""
        h, w = frame.shape[:2]
        now = time.monotonic()

        if self.use_fake:
            return self._generate_synthetic_metrics(w, h, now)

        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self.face_mesh.process(rgb)

        if not results or not results.multi_face_landmarks:
            return None

        face = results.multi_face_landmarks[0].landmark

        # Pixel coordinates helper
        def p2px(idx: int) -> np.ndarray:
            pt = face[idx]
            return np.array([pt.x * w, pt.y * h], dtype=np.float32)

        p_inner_l = p2px(IDX_INNER_LEFT_CORNER)
        p_inner_r = p2px(IDX_INNER_RIGHT_CORNER)
        p_inner_top = p2px(IDX_INNER_TOP)
        p_inner_bot = p2px(IDX_INNER_BOTTOM)
        p_inner_top_l = p2px(IDX_INNER_TOP_L)
        p_inner_bot_l = p2px(IDX_INNER_BOT_L)
        p_inner_top_r = p2px(IDX_INNER_TOP_R)
        p_inner_bot_r = p2px(IDX_INNER_BOT_R)

        p_outer_l = p2px(IDX_LIP_LEFT_CORNER)
        p_outer_r = p2px(IDX_LIP_RIGHT_CORNER)
        p_outer_top = p2px(IDX_OUTER_TOP)
        p_outer_bot = p2px(IDX_OUTER_BOTTOM)

        p_eye_l = p2px(IDX_LEFT_EYE_OUTER)
        p_eye_r = p2px(IDX_RIGHT_EYE_OUTER)

        # 1. Distances
        inner_w = float(np.linalg.norm(p_inner_r - p_inner_l))
        inner_h = float(
            (np.linalg.norm(p_inner_bot - p_inner_top) +
             np.linalg.norm(p_inner_bot_l - p_inner_top_l) +
             np.linalg.norm(p_inner_bot_r - p_inner_top_r)) / 3.0
        )
        outer_w = float(np.linalg.norm(p_outer_r - p_outer_l))
        outer_h = float(np.linalg.norm(p_outer_bot - p_outer_top))
        eye_dist = float(np.linalg.norm(p_eye_r - p_eye_l))

        # 2. Aspect Ratios
        mar = inner_h / max(inner_w, 1.0)
        mar_outer = outer_h / max(outer_w, 1.0)
        lip_width_ratio = outer_w / max(eye_dist, 1.0)

        # 3. Dynamic Velocity & Cadence
        dt = max(1e-3, now - self.last_time)
        raw_velocity = abs(mar - self.last_mar) / dt
        self.smoothed_velocity = 0.7 * self.smoothed_velocity + 0.3 * raw_velocity
        self.last_mar = mar
        self.last_time = now

        self.mar_history.append(mar)
        self.time_history.append(now)

        cadence_hz, is_speaking = self._evaluate_speech_activity()

        # 4. Viseme Classification
        viseme = self._classify_viseme(mar, lip_width_ratio, is_speaking)

        # 5. Extract coordinate list and bounding box
        inner_pts = [(int(p2px(i)[0]), int(p2px(i)[1])) for i in INNER_LIP_INDICES]
        outer_pts = [(int(p2px(i)[0]), int(p2px(i)[1])) for i in OUTER_LIP_INDICES]

        all_xs = [pt[0] for pt in outer_pts]
        all_ys = [pt[1] for pt in outer_pts]
        min_x, max_x = max(0, min(all_xs)), min(w, max(all_xs))
        min_y, max_y = max(0, min(all_ys)), min(h, max(all_ys))
        cx = int((min_x + max_x) / 2)
        cy = int((min_y + max_y) / 2)

        # Bounding box with generous padding for zoom inset
        pad_x = int((max_x - min_x) * 0.35)
        pad_y = int((max_y - min_y) * 0.45)
        bx1 = max(0, min_x - pad_x)
        by1 = max(0, min_y - pad_y)
        bw = min(w - bx1, (max_x - min_x) + 2 * pad_x)
        bh = min(h - by1, (max_y - min_y) + 2 * pad_y)

        return LipMetrics(
            mar=mar,
            mar_outer=mar_outer,
            inner_width=inner_w,
            inner_height=inner_h,
            outer_width=outer_w,
            outer_height=outer_h,
            lip_width_ratio=lip_width_ratio,
            velocity=self.smoothed_velocity,
            is_speaking=is_speaking,
            viseme=viseme,
            speech_cadence_hz=cadence_hz,
            center_xy=(cx, cy),
            bbox=(bx1, by1, bw, bh),
            landmarks_inner_px=inner_pts,
            landmarks_outer_px=outer_pts,
        )

    def _evaluate_speech_activity(self) -> Tuple[float, bool]:
        """Detect syllable rhythmic oscillations (V-VAD) in the 1.5 - 6.0 Hz band."""
        if len(self.mar_history) < 15:
            return 0.0, False

        mars = np.array(self.mar_history)
        std_mar = float(np.std(mars))

        # Detect local peaks in aperture
        peak_count = 0
        mean_val = float(np.mean(mars))
        for i in range(1, len(mars) - 1):
            if mars[i] > mean_val and mars[i] > mars[i - 1] and mars[i] > mars[i + 1]:
                peak_count += 1

        duration = max(1e-2, self.time_history[-1] - self.time_history[0])
        cadence_hz = float(peak_count / duration)

        # Speech characteristics: rhythmic oscillation (1.5 - 6 Hz) and significant movement
        is_speaking = (
            std_mar > 0.018 and
            self.smoothed_velocity > 0.12 and
            (1.2 <= cadence_hz <= 6.5)
        )
        return cadence_hz, is_speaking

    def _classify_viseme(self, mar: float, lip_width_ratio: float, is_speaking: bool) -> Viseme:
        """Classify current oral configuration into linguistic viseme groups."""
        if mar < 0.055:
            return Viseme.BILABIAL_M
        elif mar >= 0.22:
            return Viseme.OPEN_A
        elif lip_width_ratio < 0.65 and mar >= 0.09:
            return Viseme.ROUNDED_O
        elif lip_width_ratio > 0.85 and mar < 0.18:
            return Viseme.SPREAD_EE
        elif 0.055 <= mar < 0.14:
            return Viseme.DENTAL_F
        elif is_speaking:
            return Viseme.NEUTRAL
        else:
            return Viseme.REST

    def _generate_synthetic_metrics(self, w: int, h: int, now: float) -> LipMetrics:
        """Generate synthetic oscillating lip metrics for CI and automated tests."""
        phase = now * 4.0
        mar = float(0.12 + 0.10 * np.sin(phase))
        cx, cy = w // 2, int(h * 0.6)

        inner_w = 90.0
        inner_h = inner_w * mar
        outer_w = 120.0
        outer_h = inner_h + 20.0

        pts_inner = [
            (int(cx - inner_w/2), cy),
            (cx, int(cy - inner_h/2)),
            (int(cx + inner_w/2), cy),
            (cx, int(cy + inner_h/2)),
        ]
        pts_outer = [
            (int(cx - outer_w/2), cy),
            (cx, int(cy - outer_h/2)),
            (int(cx + outer_w/2), cy),
            (cx, int(cy + outer_h/2)),
        ]

        viseme = Viseme.OPEN_A if mar > 0.18 else (Viseme.ROUNDED_O if mar > 0.10 else Viseme.BILABIAL_M)
        return LipMetrics(
            mar=mar,
            mar_outer=outer_h / outer_w,
            inner_width=inner_w,
            inner_height=inner_h,
            outer_width=outer_w,
            outer_height=outer_h,
            lip_width_ratio=0.75,
            velocity=abs(np.cos(phase)),
            is_speaking=True,
            viseme=viseme,
            speech_cadence_hz=2.8,
            center_xy=(cx, cy),
            bbox=(cx - 70, cy - 40, 140, 80),
            landmarks_inner_px=pts_inner,
            landmarks_outer_px=pts_outer,
        )

    def close(self) -> None:
        """Release MediaPipe resources."""
        if self.face_mesh:
            self.face_mesh.close()

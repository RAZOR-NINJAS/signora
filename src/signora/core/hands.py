"""MediaPipe HandLandmarker wrapper with CPU/GPU delegation and landmark visualization."""

import os
import queue
import threading
import time
from pathlib import Path
from typing import Any, List, Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision

from signora.core.camera import Camera
from signora.models.manifest import get_model_path

HAND_CONNECTIONS = [
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 4),
    (0, 5),
    (5, 6),
    (6, 7),
    (7, 8),
    (5, 9),
    (9, 10),
    (10, 11),
    (11, 12),
    (9, 13),
    (13, 14),
    (14, 15),
    (15, 16),
    (13, 17),
    (17, 18),
    (18, 19),
    (19, 20),
    (0, 17),
]


class LandmarkPoint:
    """Lightweight duck-typed landmark coordinate."""

    __slots__ = ("x", "y", "z")

    def __init__(self, x: float, y: float, z: float = 0.0):
        self.x = float(x)
        self.y = float(y)
        self.z = float(z)


def _create_landmarker(
    model_path: Path, delegate: Any, num_hands: int = 1
) -> vision.HandLandmarker:
    return vision.HandLandmarker.create_from_options(
        vision.HandLandmarkerOptions(
            base_options=mpp.BaseOptions(model_asset_path=str(model_path), delegate=delegate),
            running_mode=vision.RunningMode.VIDEO,
            num_hands=num_hands,
            min_hand_detection_confidence=0.35,
            min_hand_presence_confidence=0.35,
            min_tracking_confidence=0.35,
        )
    )


class HandPipeline:
    """Captures camera frames and runs real-time spatial landmark tracking."""

    def __init__(
        self,
        source: Any = 0,
        num_hands: int = 1,
        mirror: bool = True,
        use_gpu: Optional[bool] = None,
        detect_every: int = 1,
        use_fake: bool = False,
    ):
        self.source = source
        self.num_hands = max(1, int(num_hands))
        self.mirror = mirror
        self.detect_every = max(1, int(detect_every))
        self.use_fake = use_fake

        self._frames: "queue.Queue" = queue.Queue(maxsize=2)
        self._stop = threading.Event()
        self.started = threading.Event()
        self.error: Optional[Exception] = None
        self.delegate = "CPU"
        self._landmarker = None

        if not self.use_fake:
            model_path = get_model_path("hand_landmarker")
            if not model_path.exists():
                raise FileNotFoundError(
                    f"MediaPipe model asset not found at {model_path}.\n"
                    "Run 'signora models fetch' or 'python scripts/fetch_models.py' to download weights."
                )

            # Auto-probe GPU acceleration first unless explicitly disabled
            use_gpu_pref = (
                use_gpu
                if use_gpu is not None
                else (os.environ.get("SIGNORA_FORCE_CPU", "0") not in ("1", "true", "True"))
            )

            if use_gpu_pref:
                try:
                    self._landmarker = _create_landmarker(
                        model_path, mpp.BaseOptions.Delegate.GPU, self.num_hands
                    )
                    self.delegate = "GPU"
                except Exception:
                    self._landmarker = None

            if self._landmarker is None:
                self._landmarker = _create_landmarker(
                    model_path, mpp.BaseOptions.Delegate.CPU, self.num_hands
                )
                self.delegate = "CPU"

        self._last_landmarks = None
        self._last_handedness = None
        self._skip_counter = 0
        self._ts = 0

        self._thread = threading.Thread(target=self._capture_loop, daemon=True)
        self._thread.start()

    def _generate_synthetic_landmarks(self) -> Tuple[List[Any], List[str]]:
        t = time.monotonic() * 2.0
        pts = []
        for i in range(21):
            x = 0.5 + 0.15 * np.cos(t + i * 0.2)
            y = 0.5 + 0.15 * np.sin(t + i * 0.2)
            z = 0.02 * np.sin(t * 0.5 + i)
            pts.append(LandmarkPoint(x, y, z))
        if self.num_hands > 1:
            return [pts, pts], ["Right", "Left"]
        return [pts], ["Right"]

    def _capture_loop(self) -> None:
        try:
            cam = Camera(
                source=self.source, width=640, height=480, buffer_size=1, use_fake=self.use_fake
            )
            with cam:
                self.started.set()
                while not self._stop.is_set():
                    ret, frame = cam.read()
                    if not ret or frame is None:
                        time.sleep(0.005)
                        continue

                    if self.mirror:
                        frame = cv2.flip(frame, 1)

                    landmarks = None
                    handedness = None

                    if self.use_fake or self._landmarker is None:
                        lms_list, hnds_list = self._generate_synthetic_landmarks()
                        landmarks = lms_list if self.num_hands > 1 else lms_list[0]
                        handedness = hnds_list if self.num_hands > 1 else hnds_list[0]
                    else:
                        self._skip_counter += 1
                        if self._skip_counter >= self.detect_every:
                            self._skip_counter = 0
                            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                            mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                            self._ts += 33
                            result = self._landmarker.detect_for_video(mp_img, self._ts)

                            if result and result.hand_landmarks:
                                if self.num_hands > 1:
                                    landmarks = list(result.hand_landmarks)
                                    handedness = [h[0].category_name for h in result.handedness]
                                else:
                                    landmarks = result.hand_landmarks[0]
                                    handedness = result.handedness[0][0].category_name
                                self._last_landmarks = landmarks
                                self._last_handedness = handedness
                            else:
                                self._last_landmarks = None
                                self._last_handedness = None
                        else:
                            landmarks = self._last_landmarks
                            handedness = self._last_handedness

                    # Deliver latest frame to output queue
                    try:
                        self._frames.put_nowait((frame, landmarks, handedness))
                    except queue.Full:
                        try:
                            self._frames.get_nowait()
                        except queue.Empty:
                            pass
                        self._frames.put_nowait((frame, landmarks, handedness))

        except Exception as exc:
            self.error = exc
            self.started.set()

    def read(
        self, timeout: float = 3.0
    ) -> Tuple[Optional[np.ndarray], Optional[Any], Optional[Any]]:
        """Read latest processed frame and landmarks."""
        try:
            return self._frames.get(timeout=timeout)
        except queue.Empty:
            return None, None, None

    def close(self) -> None:
        """Stop worker thread and close MediaPipe model."""
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=2.0)
        if self._landmarker is not None:
            self._landmarker.close()
            self._landmarker = None


def draw_landmarks(
    frame: np.ndarray, landmarks: Any, size: int = 2, color: Tuple[int, int, int] = (0, 200, 255)
) -> np.ndarray:
    """Render skeleton bones and joints onto image."""
    if not landmarks:
        return frame
    if (
        isinstance(landmarks, (list, tuple))
        and landmarks
        and isinstance(landmarks[0], (list, tuple))
    ):
        for hand in landmarks:
            draw_landmarks(frame, hand, size=size, color=color)
        return frame

    h, w = frame.shape[:2]
    for a, b in HAND_CONNECTIONS:
        p1 = (int(landmarks[a].x * w), int(landmarks[a].y * h))
        p2 = (int(landmarks[b].x * w), int(landmarks[b].y * h))
        cv2.line(frame, p1, p2, color, size, cv2.LINE_AA)
    for i, lm in enumerate(landmarks):
        cv2.circle(
            frame,
            (int(lm.x * w), int(lm.y * h)),
            size + 2 if i in (4, 8, 12, 16, 20) else size,
            (255, 255, 255),
            -1,
            cv2.LINE_AA,
        )
    return frame

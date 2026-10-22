"""Camera capture wrapper with single-frame buffering and graceful error reporting."""

import os
import time
from typing import Any, Optional, Tuple, Union

import cv2
import numpy as np

from signora.core.fake_camera import FakeCamera
from signora.platform_compat import get_camera_capture, is_windows


class CameraError(RuntimeError):
    """Raised when camera hardware or stream fails with actionable troubleshooting advice."""

    pass


class Camera:
    """Manages video capture lifecycle with single-frame queue and fps measurement."""

    def __init__(
        self,
        source: Union[int, str, Any] = 0,
        width: int = 1280,
        height: int = 720,
        buffer_size: int = 1,
        use_fake: bool = False,
    ):
        self.source = source
        self.target_width = width
        self.target_height = height
        self.buffer_size = max(1, int(buffer_size))
        self.use_fake = use_fake or os.environ.get("SIGNORA_FAKE_CAMERA", "0") in (
            "1",
            "true",
            "True",
        )
        self.cap: Optional[Union[cv2.VideoCapture, FakeCamera]] = None
        self._fps_actual = 0.0
        self._frame_count = 0
        self._last_fps_calc = time.monotonic()

    def open(self) -> None:
        """Open camera device or virtual camera."""
        if self.use_fake:
            self.cap = FakeCamera(width=self.target_width, height=self.target_height)
            return

        self.cap = get_camera_capture(
            self.source,
            buffer_size=self.buffer_size,
            width=self.target_width,
            height=self.target_height,
        )

        if not self.cap.isOpened():
            # If default camera failed, check if fake camera is requested as fallback
            if os.environ.get("SIGNORA_ALLOW_FAKE_FALLBACK", "0") in ("1", "true", "True"):
                self.cap = FakeCamera(width=self.target_width, height=self.target_height)
                return

            advice = (
                (
                    "Ensure your camera is connected and not in use by another app.\n"
                    "On Windows: Verify Settings -> Privacy & Security -> Camera permissions.\n"
                    "On Linux: Verify your user belongs to the 'video' group (sudo usermod -a -G video $USER)."
                )
                if not is_windows()
                else (
                    "Ensure your camera is connected and not blocked by Windows Privacy settings.\n"
                    "Check: Settings -> Privacy & Security -> Camera -> Allow apps to access your camera."
                )
            )
            raise CameraError(f"Could not open camera device source '{self.source}'.\n{advice}")

        # Set dimensions, target FPS, and enforce strict zero-latency hardware buffer limit
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.target_width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.target_height)
        try:
            self.cap.set(cv2.CAP_PROP_FPS, 30)
        except Exception:
            pass
        try:
            self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        except Exception:
            pass

    def read(self) -> Tuple[bool, Optional[np.ndarray]]:
        """Read a single frame from the camera, strictly guaranteeing 16:9 aspect ratio and zero buffer lag."""
        if self.cap is None or not self.cap.isOpened():
            return False, None

        if self.use_fake:
            ret, frame = self.cap.read()
        else:
            # Drain internal V4L2/DirectShow ring buffers to eliminate any stale frame delay
            ret = self.cap.grab()
            if not ret:
                return False, None
            ret, frame = self.cap.retrieve()

        if ret and frame is not None:
            h, w = frame.shape[:2]

            # 1. Enforce strict 16:9 aspect ratio (crop centered if hardware returns 4:3 or other ratios)
            target_aspect = 16.0 / 9.0
            actual_aspect = float(w) / float(max(1, h))
            if abs(actual_aspect - target_aspect) > 0.02:
                if actual_aspect < target_aspect:
                    # Too tall (e.g. 4:3 640x480) -> crop top/bottom to 16:9
                    desired_h = int(w / target_aspect)
                    y_off = max(0, (h - desired_h) // 2)
                    frame = frame[y_off : y_off + desired_h, :]
                else:
                    # Too wide -> crop left/right to 16:9
                    desired_w = int(h * target_aspect)
                    x_off = max(0, (w - desired_w) // 2)
                    frame = frame[:, x_off : x_off + desired_w]

            # 2. Scale to requested target dimensions
            h, w = frame.shape[:2]
            if (w, h) != (self.target_width, self.target_height):
                frame = cv2.resize(
                    frame, (self.target_width, self.target_height), interpolation=cv2.INTER_LINEAR
                )

            self._frame_count += 1
            now = time.monotonic()
            elapsed = now - self._last_fps_calc
            if elapsed >= 1.0:
                self._fps_actual = self._frame_count / elapsed
                self._frame_count = 0
                self._last_fps_calc = now
            return True, frame

        return False, None

    @property
    def fps(self) -> float:
        """Return measured capture frame rate."""
        return self._fps_actual

    @property
    def is_opened(self) -> bool:
        """Return True if capture is active."""
        return self.cap is not None and self.cap.isOpened()

    def release(self) -> None:
        """Release camera hardware."""
        if self.cap is not None:
            self.cap.release()
            self.cap = None

    def __enter__(self) -> "Camera":
        self.open()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.release()

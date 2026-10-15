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
        width: int = 640,
        height: int = 480,
        buffer_size: int = 1,
        use_fake: bool = False,
    ):
        self.source = source
        self.target_width = width
        self.target_height = height
        self.buffer_size = buffer_size
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

        self.cap = get_camera_capture(self.source, buffer_size=self.buffer_size)

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

        # Set dimensions and FPS target
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.target_width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.target_height)
        try:
            self.cap.set(cv2.CAP_PROP_FPS, 30)
        except Exception:
            pass

    def read(self) -> Tuple[bool, Optional[np.ndarray]]:
        """Read a single frame from the camera."""
        if self.cap is None or not self.cap.isOpened():
            return False, None

        if self.use_fake or not isinstance(self.source, str) or "://" not in self.source:
            ret, frame = self.cap.read()
        else:
            # On network IP/HTTP streams (e.g. iPhone DroidCam), drain buffered socket frames
            # to guarantee real-time latency (<40ms) and eliminate multi-second lag buildup
            self.cap.grab()
            ret, frame = self.cap.retrieve()

        if ret and frame is not None:
            # Rescale oversized frames (e.g. 1080p/4K network streams like iPhone DroidCam)
            h, w = frame.shape[:2]
            if w > self.target_width or h > self.target_height:
                scale = min(self.target_width / float(w), self.target_height / float(h))
                new_w, new_h = max(1, int(w * scale)), max(1, int(h * scale))
                frame = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_AREA)

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

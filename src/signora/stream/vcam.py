"""Virtual Camera hardware manager using v4l2loopback and pyvirtualcam.

Manages video device output for Zoom, Google Meet, Teams, Discord, and OBS.
Includes auto-detection, fallback handling, and system setup helpers.
"""

import glob
import os
import sys
from typing import Any, List, Optional, Tuple

import numpy as np

try:
    import pyvirtualcam
    PYVIRTUALCAM_AVAILABLE = True
except ImportError:
    PYVIRTUALCAM_AVAILABLE = False


def list_v4l2_devices() -> List[str]:
    """Return all available /dev/video* devices."""
    return sorted(glob.glob("/dev/video*"))


def get_v4l2_setup_instructions() -> str:
    """Generate exact terminal commands to install and configure v4l2loopback."""
    return """
================================================================================
  SIGNORA VIRTUAL CAMERA SETUP GUIDE (v4l2loopback)
================================================================================
To broadcast Signora directly into Zoom, Google Meet, or Teams, create a virtual
camera loopback device on your Linux system:

[Arch / Omarchy Linux]
  sudo pacman -S v4l2loopback-dkms linux-headers
  sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="Signora Virtual Cam" exclusive_caps=1

[Ubuntu / Debian]
  sudo apt install v4l2loopback-dkms
  sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="Signora Virtual Cam" exclusive_caps=1

[Fedora]
  sudo dnf install v4l2loopback
  sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="Signora Virtual Cam" exclusive_caps=1

Once created, /dev/video10 will appear as "Signora Virtual Cam" in your browser
or video meeting settings.
================================================================================
"""


class VirtualCameraManager:
    """Manages writing annotated frames to a virtual webcam device."""

    def __init__(
        self,
        width: int = 1280,
        height: int = 720,
        fps: int = 30,
        device: Optional[str] = None,
    ) -> None:
        self.width = width
        self.height = height
        self.fps = fps
        self.device = device
        self._cam: Optional[Any] = None
        self._is_open = False
        self._error_msg: Optional[str] = None

    @property
    def is_active(self) -> bool:
        """True if successfully streaming to a physical virtual webcam device."""
        return self._is_open and self._cam is not None

    @property
    def error_message(self) -> Optional[str]:
        return self._error_msg

    def open(self) -> bool:
        """Initialize the virtual camera device."""
        if not PYVIRTUALCAM_AVAILABLE:
            self._error_msg = (
                "pyvirtualcam package is not installed. Install via: pip install pyvirtualcam"
            )
            return False

        try:
            kwargs: dict = {
                "width": self.width,
                "height": self.height,
                "fps": self.fps,
                "fmt": pyvirtualcam.PixelFormat.BGR,
            }
            if self.device:
                kwargs["device"] = self.device

            self._cam = pyvirtualcam.Camera(**kwargs)
            self._is_open = True
            self.device = self._cam.device
            print(f"[VirtualCam] Attached to virtual camera device: {self.device} ({self.width}x{self.height} @ {self.fps}fps)")
            return True
        except Exception as e:
            self._error_msg = str(e)
            self._is_open = False
            self._cam = None
            return False

    def send_frame(self, frame: np.ndarray) -> bool:
        """Send a BGR frame to the virtual camera device."""
        if not self.is_active or self._cam is None:
            return False

        try:
            # Resize if incoming frame size differs from configured dimensions
            h, w = frame.shape[:2]
            if w != self.width or h != self.height:
                import cv2
                frame = cv2.resize(frame, (self.width, self.height))

            self._cam.send(frame)
            return True
        except Exception as e:
            print(f"[VirtualCam] Frame transmission failed: {e}", file=sys.stderr)
            return False

    def sleep_until_next_frame(self) -> None:
        """Paces the frame transmission according to the target frame rate."""
        if self._cam is not None:
            try:
                self._cam.sleep_until_next_frame()
            except Exception:
                pass

    def close(self) -> None:
        """Close and release the virtual camera device."""
        if self._cam is not None:
            try:
                self._cam.close()
            except Exception:
                pass
            self._cam = None
        self._is_open = False

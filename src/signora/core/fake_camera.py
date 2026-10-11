"""Synthetic Camera Generator for CI, headless tests, and hardware-free validation."""

import time
from typing import Optional, Tuple

import cv2
import numpy as np


class FakeCamera:
    """Simulates a cv2.VideoCapture device producing valid RGB frames.

    Generates synthetic hand frames with recognizable features so MediaPipe
    or downstream feature pipelines can execute without a physical webcam.
    """

    def __init__(self, width: int = 640, height: int = 480, fps: float = 30.0, seed: int = 42):
        self.width = width
        self.height = height
        self.fps = fps
        self.rng = np.random.default_rng(seed)
        self.opened = True
        self.frame_count = 0
        self.last_time = time.monotonic()

    def isOpened(self) -> bool:
        """Return True if virtual camera is open."""
        return self.opened

    def read(self) -> Tuple[bool, Optional[np.ndarray]]:
        """Generate and return next synthetic frame."""
        if not self.opened:
            return False, None

        self.frame_count += 1
        # Create dark neutral background
        frame = np.full((self.height, self.width, 3), 32, dtype=np.uint8)

        # Subtle noise
        noise = self.rng.integers(-4, 5, size=frame.shape, dtype=np.int16)
        frame = np.clip(frame.astype(np.int16) + noise, 0, 255).astype(np.uint8)

        # Render a clear synthetic hand shape in center
        cx, cy = self.width // 2, self.height // 2 + 30
        t = self.frame_count * 0.05
        ox = int(np.sin(t) * 10)
        oy = int(np.cos(t) * 10)

        # Palm ellipse (skin tone BGR)
        palm_color = (160, 190, 230)
        cv2.ellipse(frame, (cx + ox, cy + oy), (45, 60), 0, 0, 360, palm_color, -1)

        # 4 upright fingers + 1 thumb
        fingers = [
            (cx - 30 + ox, cy - 80 + oy, 12, 50),  # Index
            (cx - 10 + ox, cy - 95 + oy, 12, 55),  # Middle
            (cx + 10 + ox, cy - 85 + oy, 12, 50),  # Ring
            (cx + 30 + ox, cy - 65 + oy, 11, 40),  # Pinky
            (cx - 50 + ox, cy - 20 + oy, 14, 35),  # Thumb
        ]
        for fx, fy, fw, fh in fingers:
            cv2.ellipse(frame, (fx, fy), (fw, fh), 0, 0, 360, palm_color, -1)

        # Draw simulated tracking indicator
        cv2.putText(
            frame,
            f"SYNTHETIC CAMERA | Frame: {self.frame_count}",
            (20, 35),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (0, 255, 140),
            1,
            cv2.LINE_AA,
        )

        return True, frame

    def set(self, propId: int, value: float) -> bool:
        """Handle VideoCapture set property."""
        if propId == cv2.CAP_PROP_FRAME_WIDTH:
            self.width = int(value)
        elif propId == cv2.CAP_PROP_FRAME_HEIGHT:
            self.height = int(value)
        elif propId == cv2.CAP_PROP_FPS:
            self.fps = float(value)
        return True

    def get(self, propId: int) -> float:
        """Handle VideoCapture get property."""
        if propId == cv2.CAP_PROP_FRAME_WIDTH:
            return float(self.width)
        elif propId == cv2.CAP_PROP_FRAME_HEIGHT:
            return float(self.height)
        elif propId == cv2.CAP_PROP_FPS:
            return float(self.fps)
        elif propId == cv2.CAP_PROP_POS_FRAMES:
            return float(self.frame_count)
        return 0.0

    def release(self) -> None:
        """Close virtual camera."""
        self.opened = False

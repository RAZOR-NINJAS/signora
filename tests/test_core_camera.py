"""Tests for core camera capture and synthetic test camera."""

import numpy as np

from signora.core.camera import Camera
from signora.core.fake_camera import FakeCamera


def test_fake_camera_generation():
    fake = FakeCamera(width=640, height=480, fps=30)
    assert fake.isOpened()

    ret, frame = fake.read()
    assert ret is True
    assert isinstance(frame, np.ndarray)
    assert frame.shape == (480, 640, 3)
    assert frame.dtype == np.uint8

    fake.release()
    assert not fake.isOpened()


def test_camera_lifecycle_with_fake():
    cam = Camera(source=0, width=320, height=240, use_fake=True)
    with cam:
        assert cam.is_opened
        ret, frame = cam.read()
        assert ret is True
        assert frame is not None
        assert frame.shape == (240, 320, 3)
        assert cam.fps >= 0.0

    assert not cam.is_opened

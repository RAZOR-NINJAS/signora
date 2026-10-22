"""Unit tests for virtual webcam streaming and meeting broadcast modules."""

import time
import numpy as np

from signora.stream.overlay import render_meeting_overlay
from signora.stream.server import StreamingOutput, WebStreamServer
from signora.stream.vcam import (
    VirtualCameraManager,
    get_v4l2_setup_instructions,
    list_v4l2_devices,
)


def test_v4l2_helpers():
    """Ensure v4l2 device listing and setup instructions work."""
    devices = list_v4l2_devices()
    assert isinstance(devices, list)

    instructions = get_v4l2_setup_instructions()
    assert "SIGNORA VIRTUAL CAMERA SETUP GUIDE" in instructions
    assert "v4l2loopback" in instructions
    assert "modprobe" in instructions


def test_virtual_camera_manager_lifecycle():
    """Ensure VirtualCameraManager initializes and handles missing loopback safely."""
    mgr = VirtualCameraManager(width=640, height=480, fps=30)
    assert not mgr.is_active

    # Opening without a created v4l2loopback kernel device should fail gracefully without crashing
    mgr.open()

    # Sending a frame when inactive should return False safely
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    assert not mgr.send_frame(frame)

    # Closing should be safe and idempotent
    mgr.close()
    assert not mgr.is_active


def test_render_meeting_overlay():
    """Verify meeting lower-third captions render correctly on a video frame."""
    frame = np.zeros((720, 1280, 3), dtype=np.uint8)

    rendered = render_meeting_overlay(
        frame=frame,
        candidate="NAMASTE",
        dwell_ratio=0.75,
        in_cooldown=False,
        pending_glosses=["HELLO", "DOCTOR", "WHERE"],
        synthesized_sentence="Where is the doctor?",
        tts_speaking=True,
        last_spoken="Where is the doctor?",
        last_spoken_time=time.time(),
        clean_mode=False,
        mode_label="ISL DUAL-HAND",
        fps=29.8,
    )

    assert rendered.shape == (720, 1280, 3)
    assert np.any(rendered > 0)


def test_render_meeting_overlay_clean_mode():
    """Verify clean mode renders without throwing errors."""
    frame = np.zeros((720, 1280, 3), dtype=np.uint8)

    rendered = render_meeting_overlay(
        frame=frame,
        candidate="",
        dwell_ratio=0.0,
        in_cooldown=False,
        pending_glosses=[],
        synthesized_sentence="",
        tts_speaking=False,
        last_spoken="",
        last_spoken_time=0.0,
        clean_mode=True,
    )

    assert rendered.shape == (720, 1280, 3)


def test_streaming_output_buffer():
    """Test thread-safe frame encoding and status buffering."""
    output = StreamingOutput()
    frame = np.zeros((240, 320, 3), dtype=np.uint8)
    output.update_frame(frame, quality=50)

    assert output.frame is not None
    assert len(output.frame) > 0

    output.update_status(active_sign="THANK_YOU", dwell=0.9, sentence="Thank you.")
    assert output.status["active_sign"] == "THANK_YOU"
    assert output.status["dwell"] == 0.9
    assert output.status["sentence"] == "Thank you."


def test_web_stream_server_lifecycle():
    """Test web stream server start and stop on a random free port."""
    server = WebStreamServer(port=18889)
    started = server.start()
    if started:
        test_frame = np.zeros((100, 100, 3), dtype=np.uint8)
        server.push_frame(test_frame)
        server.push_status(fps=30.0)
        server.stop()
        assert not server._running

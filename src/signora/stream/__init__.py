"""Signora virtual webcam and meeting broadcast module."""

from signora.stream.overlay import render_meeting_overlay
from signora.stream.pipeline import run_virtual_stream
from signora.stream.server import WebStreamServer
from signora.stream.vcam import VirtualCameraManager, get_v4l2_setup_instructions

__all__ = [
    "run_virtual_stream",
    "VirtualCameraManager",
    "WebStreamServer",
    "render_meeting_overlay",
    "get_v4l2_setup_instructions",
]

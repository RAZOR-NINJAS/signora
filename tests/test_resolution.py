"""Unit tests for 16:9 resolution selection, aspect ratio enforcement, and CLI parsing."""

import os
import numpy as np
import pytest

from signora.cli import create_parser
from signora.core.camera import Camera
from signora.core.fake_camera import FakeCamera
from signora.core.resolution import (
    ALIAS_MAP,
    SUPPORTED_RESOLUTIONS_16_9,
    parse_resolution_16_9,
    prompt_resolution_16_9,
)


def test_supported_presets_are_16_9():
    """All preset resolutions must adhere to 16:9 aspect ratio within 1%."""
    for key, (label, width, height, desc) in SUPPORTED_RESOLUTIONS_16_9.items():
        aspect = width / height
        assert abs(aspect - (16.0 / 9.0)) < 0.02, f"Preset {label} ({width}x{height}) is not 16:9"


def test_parse_resolution_presets_and_aliases():
    """Verify alias mapping and key parsing."""
    assert parse_resolution_16_9("1") == (1280, 720, "720p")
    assert parse_resolution_16_9("2") == (854, 480, "480p")
    assert parse_resolution_16_9("3") == (640, 360, "360p")
    assert parse_resolution_16_9("4") == (1920, 1080, "1080p")

    assert parse_resolution_16_9("720p") == (1280, 720, "720p")
    assert parse_resolution_16_9("480p") == (854, 480, "480p")
    assert parse_resolution_16_9("360p") == (640, 360, "360p")
    assert parse_resolution_16_9("1080p") == (1920, 1080, "1080p")

    assert parse_resolution_16_9("1280x720") == (1280, 720, "720p")
    assert parse_resolution_16_9("640x360") == (640, 360, "360p")


def test_parse_resolution_custom_and_snapping():
    """Custom resolutions should be parsed, and non-16:9 should snap to 16:9."""
    # 1600x900 is exact 16:9
    w, h, label = parse_resolution_16_9("1600x900")
    assert w == 1600 and h == 900

    # 1000x800 is not 16:9 -> height should snap to int(1000 * 9 / 16) = 562
    w2, h2, label2 = parse_resolution_16_9("1000x800")
    assert w2 == 1000 and h2 == 562
    assert abs((w2 / h2) - (16.0 / 9.0)) < 0.01

    # Invalid string returns None
    assert parse_resolution_16_9("invalid") is None
    assert parse_resolution_16_9("") is None
    assert parse_resolution_16_9(None) is None


def test_prompt_resolution_cli_override():
    """Passing cli_arg should directly return the parsed resolution without prompting."""
    w, h, name = prompt_resolution_16_9(cli_arg="360p")
    assert (w, h, name) == (640, 360, "360p")

    w, h, name = prompt_resolution_16_9(cli_arg="480p")
    assert (w, h, name) == (854, 480, "480p")


def test_prompt_resolution_env_var(monkeypatch):
    """SIGNORA_RESOLUTION environment variable overrides defaults."""
    monkeypatch.setenv("SIGNORA_RESOLUTION", "360p")
    w, h, name = prompt_resolution_16_9(default_choice="720p", headless=False)
    assert (w, h, name) == (640, 360, "360p")


def test_prompt_resolution_headless():
    """Headless or non-interactive should safely return default without blocking on input."""
    w, h, name = prompt_resolution_16_9(default_choice="720p", headless=True)
    assert (w, h, name) == (1280, 720, "720p")

    w, h, name = prompt_resolution_16_9(default_choice="480p", headless=True)
    assert (w, h, name) == (854, 480, "480p")


def test_camera_enforces_16_9_aspect_ratio_from_4_3_sensor():
    """When the underlying sensor returns 4:3 (e.g. 640x480), Camera crops and scales to 16:9."""
    # Use FakeCamera generating 640x480 (4:3 aspect ratio)
    cam = Camera(source=0, width=640, height=360, use_fake=True)
    with cam:
        ret, frame = cam.read()
        assert ret is True
        assert frame is not None
        # Must be strictly 640x360 (16:9)
        assert frame.shape == (360, 640, 3)
        aspect = frame.shape[1] / frame.shape[0]
        assert abs(aspect - (16.0 / 9.0)) < 0.01


def test_cli_subparsers_accept_resolution_flags():
    """CLI subparsers must accept --res and --resolution flags."""
    parser = create_parser()

    # isl
    args_isl = parser.parse_args(["isl", "--res", "360p"])
    assert getattr(args_isl, "resolution", None) == "360p"

    # body
    args_body = parser.parse_args(["body", "--resolution", "480p"])
    assert getattr(args_body, "resolution", None) == "480p"

    # live / asl
    args_live = parser.parse_args(["live", "--res", "1080p"])
    assert getattr(args_live, "resolution", None) == "1080p"

    # words
    args_words = parser.parse_args(["words", "--res", "720p"])
    assert getattr(args_words, "resolution", None) == "720p"

    # stream
    args_stream = parser.parse_args(["stream", "--res", "360p"])
    assert getattr(args_stream, "resolution", None) == "360p"

    # root flag: signora --res 480p isl
    args_root = parser.parse_args(["--res", "480p", "isl"])
    assert getattr(args_root, "resolution", None) == "480p"

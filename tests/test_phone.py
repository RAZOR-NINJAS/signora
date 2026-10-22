"""Unit tests for phone webcam bridge utilities."""

from unittest.mock import patch, MagicMock
import pytest

from signora.core.phone import (
    check_adb_installed,
    check_scrcpy_installed,
    get_connected_adb_devices,
    find_v4l2_loopback_device,
    start_scrcpy_camera_sink,
)


def test_tool_presence():
    """Verify check_adb_installed and check_scrcpy_installed detect binaries."""
    # Both are installed on host system
    assert check_scrcpy_installed() is True
    assert check_adb_installed() is True


def test_get_connected_adb_devices_parsing():
    """Verify parsing of adb devices output."""
    mock_output = "List of devices attached\nemulator-5554\tdevice\nphone123\tunauthorized\n\n"
    with patch("subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(stdout=mock_output)
        devices = get_connected_adb_devices()
        assert devices == [("emulator-5554", "device"), ("phone123", "unauthorized")]


def test_find_v4l2_loopback_device():
    """Verify detection of v4l2 device returns a string path and integer index."""
    with patch("subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(returncode=0)
        dev_node, dev_idx = find_v4l2_loopback_device()
        assert dev_node.startswith("/dev/video")
        assert isinstance(dev_idx, int)
        assert dev_idx >= 0

"""Cross-platform compatibility abstractions for Signora.

Handles camera capture backends (DirectShow on Windows vs V4L2 on Linux),
browser kiosk launching, console keyboard input, and platform detection.
"""

import os
import platform
import shutil
import subprocess
import sys
import webbrowser
from pathlib import Path
from typing import Any, Dict, List, Optional

import cv2

# On Linux under Wayland, OpenCV Qt windows require XCB (XWayland) fallback
if sys.platform.startswith("linux") and "QT_QPA_PLATFORM" not in os.environ:
    os.environ["QT_QPA_PLATFORM"] = "xcb"


def is_windows() -> bool:
    """Return True if running on Microsoft Windows."""
    return sys.platform.startswith("win") or os.name == "nt"


def is_linux() -> bool:
    """Return True if running on Linux."""
    return sys.platform.startswith("linux")


def is_macos() -> bool:
    """Return True if running on macOS (Darwin)."""
    return sys.platform == "darwin"


def get_platform_info() -> Dict[str, str]:
    """Return platform metadata dictionary."""
    backend = "DirectShow" if is_windows() else ("V4L2" if is_linux() else "Default")
    return {
        "system": platform.system(),
        "release": platform.release(),
        "version": platform.version(),
        "machine": platform.machine(),
        "processor": platform.processor(),
        "python_version": platform.python_version(),
        "camera_backend": backend,
    }


# ============================================================================
# Camera Capture & Probe Abstraction
# ============================================================================


def get_camera_capture(
    source: Any = 0,
    buffer_size: int = 1,
    width: int = 1280,
    height: int = 720,
) -> cv2.VideoCapture:
    """Create cv2.VideoCapture with optimized platform backend.

    On Windows: DirectShow (cv2.CAP_DSHOW) avoids MSMF startup delay & freezes.
    On Linux: V4L2 (cv2.CAP_V4L2) gives low-latency kernel video capture.
    """
    # If source is a string representing an integer (e.g. '0'), convert to int
    if isinstance(source, str) and source.isdigit():
        source = int(source)

    if isinstance(source, int):
        if is_windows():
            cap = cv2.VideoCapture(source, cv2.CAP_DSHOW)
            if not cap.isOpened():
                cap = cv2.VideoCapture(source)
        elif is_linux():
            cap = cv2.VideoCapture(source, cv2.CAP_V4L2)
            if not cap.isOpened():
                cap = cv2.VideoCapture(f"/dev/video{source}", cv2.CAP_V4L2)
            if not cap.isOpened():
                cap = cv2.VideoCapture(f"/dev/video{source}")
            if not cap.isOpened():
                cap = cv2.VideoCapture(source)
        else:
            cap = cv2.VideoCapture(source)
    else:
        # Source is a file path, video URL, or RTSP stream
        cap = cv2.VideoCapture(str(source))

    if cap.isOpened():
        if hasattr(cv2, "VideoWriter_fourcc"):
            try:
                cap.set(cv2.CAP_PROP_FOURCC, cv2.VideoWriter_fourcc(*"MJPG"))
            except Exception:
                pass
        try:
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
            cap.set(cv2.CAP_PROP_FPS, 30)
        except Exception:
            pass
        if buffer_size > 0:
            try:
                cap.set(cv2.CAP_PROP_BUFFERSIZE, buffer_size)
            except cv2.error:
                pass

        if is_linux():
            dev_str = f"/dev/video{source}" if isinstance(source, int) else str(source)
            if dev_str.startswith("/dev/video"):
                try:
                    import subprocess
                    subprocess.run(
                        ["v4l2-ctl", "-d", dev_str, "-c", "exposure_dynamic_framerate=0"],
                        stdout=subprocess.DEVNULL,
                        stderr=subprocess.DEVNULL,
                        timeout=0.3,
                    )
                except Exception:
                    pass

    return cap


def probe_cameras(max_probe: int = 4) -> List[Dict[str, Any]]:
    """Probe system camera devices and return metadata for available devices."""
    detected = []
    for idx in range(max_probe):
        cap = get_camera_capture(idx, buffer_size=2)
        if cap.isOpened():
            ret, frame = cap.read()
            if ret and frame is not None:
                h, w = frame.shape[:2]
                fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
                backend = "DirectShow" if is_windows() else ("V4L2" if is_linux() else "Default")
                detected.append(
                    {
                        "index": idx,
                        "backend": backend,
                        "width": int(w),
                        "height": int(h),
                        "fps": round(float(fps), 1),
                        "working": True,
                    }
                )
            cap.release()
    return detected


# ============================================================================
# Browser & Kiosk Launcher Abstraction
# ============================================================================


def find_browser_executable() -> Optional[Path]:
    """Locate Microsoft Edge or Google Chrome executable."""
    if is_windows():
        candidates = [
            Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)"))
            / "Microsoft"
            / "Edge"
            / "Application"
            / "msedge.exe",
            Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
            / "Microsoft"
            / "Edge"
            / "Application"
            / "msedge.exe",
            Path(os.environ.get("LOCALAPPDATA", ""))
            / "Microsoft"
            / "Edge"
            / "Application"
            / "msedge.exe",
            Path(os.environ.get("ProgramFiles", r"C:\Program Files"))
            / "Google"
            / "Chrome"
            / "Application"
            / "chrome.exe",
            Path(os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)"))
            / "Google"
            / "Chrome"
            / "Application"
            / "chrome.exe",
            Path(os.environ.get("LOCALAPPDATA", ""))
            / "Google"
            / "Chrome"
            / "Application"
            / "chrome.exe",
        ]
        for p in candidates:
            if p.is_file():
                return p
        for name in ("msedge.exe", "chrome.exe"):
            found = shutil.which(name)
            if found:
                return Path(found)
        return None

    elif is_linux():
        for name in (
            "google-chrome-stable",
            "google-chrome",
            "chromium",
            "chromium-browser",
            "brave",
            "edge",
        ):
            found = shutil.which(name)
            if found:
                return Path(found)
        return None

    return None


def launch_kiosk_browser(url: str, kiosk: bool = True) -> Optional[subprocess.Popen]:
    """Launch browser pointing to url in app/kiosk mode or fallback to default browser."""
    exe = find_browser_executable()
    if exe is not None:
        args = [str(exe)]
        if kiosk:
            args.append(f"--app={url}")
            args.extend(
                [
                    "--disable-extensions",
                    "--disable-plugins",
                    "--no-first-run",
                ]
            )
        else:
            args.append(url)
        try:
            return subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except OSError:
            pass

    webbrowser.open(url)
    return None


# ============================================================================
# Non-blocking Console Keyboard Polling
# ============================================================================


def is_interactive() -> bool:
    """Return True if stdin is attached to an interactive terminal."""
    try:
        return sys.stdin is not None and sys.stdin.isatty()
    except Exception:
        return False


def poll_keyboard_char() -> Optional[str]:
    """Non-blocking single character read from console stdin.

    Returns the character string if a key was pressed, or None if no input.
    """
    if is_windows():
        try:
            import msvcrt

            if msvcrt.kbhit():
                ch = msvcrt.getwch()
                # Handle special keys / arrows (first byte is \x00 or \xe0)
                if ch in ("\x00", "\xe0") and msvcrt.kbhit():
                    ch2 = msvcrt.getwch()
                    return f"{ch}{ch2}"
                return ch
        except ImportError:
            pass
        return None

    # POSIX (Linux, macOS)
    if not is_interactive():
        return None

    try:
        import select

        r, _, _ = select.select([sys.stdin], [], [], 0)
        if r:
            return sys.stdin.read(1)
    except Exception:
        pass
    return None

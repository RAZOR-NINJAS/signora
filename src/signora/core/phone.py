"""Zero-Latency Phone Webcam Bridge for Signora.

Automates ADB device detection, V4L2 loopback virtual camera mapping, and scrcpy
hardware camera streaming into a single zero-friction command.
"""

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from signora.core.resolution import parse_resolution_16_9


def check_scrcpy_installed() -> bool:
    """Return True if scrcpy executable is available in PATH."""
    return shutil.which("scrcpy") is not None


def check_adb_installed() -> bool:
    """Return True if adb executable is available in PATH."""
    return shutil.which("adb") is not None


def get_connected_adb_devices() -> List[Tuple[str, str]]:
    """Return list of (serial, state) for connected ADB devices."""
    if not check_adb_installed():
        return []
    try:
        proc = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=5.0,
            check=False,
        )
        lines = proc.stdout.strip().splitlines()
        devices = []
        for line in lines[1:]:
            parts = line.strip().split()
            if len(parts) >= 2:
                devices.append((parts[0], parts[1]))
        return devices
    except Exception:
        return []


def wait_for_adb_phone(timeout: float = 12.0) -> Optional[str]:
    """Wait for an authorized phone to appear on ADB USB."""
    t0 = time.monotonic()
    first_attempt = True

    while time.monotonic() - t0 < timeout:
        devices = get_connected_adb_devices()
        authorized = [serial for serial, state in devices if state == "device"]
        if authorized:
            return authorized[0]

        unauthorized = [serial for serial, state in devices if state in ("unauthorized", "offline")]
        if unauthorized:
            print(
                "\n[Phone Webcam] Phone detected but unauthorized!\n"
                "  -> Please unlock your phone and tap 'Allow USB debugging' on the screen.",
                file=sys.stderr,
                flush=True,
            )
            time.sleep(1.5)
            continue

        if first_attempt:
            print(
                "[Phone Webcam] Waiting for Android phone via USB cable (USB Debugging enabled)...",
                file=sys.stderr,
                flush=True,
            )
            first_attempt = False

        time.sleep(1.0)

    return None


def find_v4l2_loopback_device() -> Tuple[str, int]:
    """Find or configure an appropriate V4L2 loopback device for scrcpy sink."""
    # 1. Search /sys/class/video4linux for virtual devices
    v4l_dir = Path("/sys/class/video4linux")
    if v4l_dir.exists():
        for dev_path in sorted(v4l_dir.glob("video*")):
            name_file = dev_path / "name"
            if name_file.exists():
                dev_name = name_file.read_text().strip().lower()
                if "phonewebcam" in dev_name or "signora" in dev_name or "loopback" in dev_name:
                    dev_node = f"/dev/{dev_path.name}"
                    dev_idx = int(dev_path.name.replace("video", ""))
                    return dev_node, dev_idx

            # Check if virtual device
            real_path = os.path.realpath(str(dev_path))
            if "virtual" in real_path:
                dev_node = f"/dev/{dev_path.name}"
                dev_idx = int(dev_path.name.replace("video", ""))
                return dev_node, dev_idx

    # 2. Check if /dev/video2 exists
    if Path("/dev/video2").exists():
        return "/dev/video2", 2

    # 3. If not loaded, attempt to auto-load with low-latency settings
    print("[Phone Webcam] Loading v4l2loopback kernel module (max_buffers=1)...", file=sys.stderr)
    try:
        subprocess.run(
            [
                "sudo",
                "-n",
                "modprobe",
                "v4l2loopback",
                "devices=1",
                "video_nr=2",
                "card_label=PhoneWebcam",
                "exclusive_caps=1",
                "max_buffers=1",
                "max_openers=1",
            ],
            check=False,
            timeout=10.0,
        )
    except Exception:
        pass

    if Path("/dev/video2").exists():
        return "/dev/video2", 2

    # Fallback to /dev/video0 if nothing else
    return "/dev/video0", 0


def start_scrcpy_camera_sink(
    v4l2_device: str,
    facing: str = "back",
    width: int = 1280,
    height: int = 720,
    fps: int = 30,
) -> subprocess.Popen:
    """Launch scrcpy configured for zero-latency camera forwarding into V4L2 sink."""
    if not check_scrcpy_installed():
        raise RuntimeError(
            "scrcpy is not installed on this system.\n"
            "Install it via: sudo pacman -S scrcpy"
        )

    cmd = [
        "scrcpy",
        "--video-source=camera",
        f"--camera-facing={facing}",
        f"--camera-size={width}x{height}",
        f"--camera-fps={fps}",
        f"--v4l2-sink={v4l2_device}",
        "--v4l2-buffer=0",
        "--video-buffer=0",
        "--no-audio",
        "--no-playback",
        "-b",
        "4M",
        "--video-codec-options=i-frame-interval=1",
    ]

    # Spawn process in background
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
    )

    # Wait up to 2 seconds to check if process failed immediately
    time.sleep(1.2)
    poll_code = proc.poll()
    if poll_code is not None:
        err = proc.stderr.read() if proc.stderr else ""
        raise RuntimeError(
            f"scrcpy failed to start with code {poll_code}.\nError details:\n{err}"
        )

    return proc


def launch_signora_with_phone(
    mode: str = "isl",
    facing: str = "back",
    resolution: Optional[str] = "720p",
    no_tts: bool = False,
    dwell_ms: Optional[int] = None,
    detect_every: int = 2,
    enable_body: bool = True,
    gemini_key: Optional[str] = None,
) -> int:
    """End-to-end launcher: verifies ADB, starts zero-latency scrcpy, and runs Signora."""
    print("=" * 65)
    print("  SIGNORA -- Zero-Latency Phone Webcam Launcher")
    print("=" * 65)

    if not check_adb_installed():
        print("[Error] 'adb' is not installed. Install via: sudo pacman -S android-tools", file=sys.stderr)
        return 1

    if not check_scrcpy_installed():
        print("[Error] 'scrcpy' is not installed. Install via: sudo pacman -S scrcpy", file=sys.stderr)
        return 1

    # 1. Connect Phone
    serial = wait_for_adb_phone(timeout=10.0)
    if not serial:
        print(
            "\n[Error] No Android phone connected via USB.\n"
            "Please plug in your phone, turn on USB Debugging in Developer Options,\n"
            "and run this command again.",
            file=sys.stderr,
        )
        return 1

    print(f"✓ Phone connected: device serial [{serial}]")

    # 2. V4L2 Loopback Sink
    v4l_dev, dev_idx = find_v4l2_loopback_device()
    print(f"✓ Virtual webcam device: {v4l_dev} (Source index: {dev_idx})")

    # 3. Resolution parsing
    res_parsed = parse_resolution_16_9(resolution or "720p")
    res_w, res_h = (res_parsed[0], res_parsed[1]) if res_parsed else (1280, 720)
    print(f"✓ Streaming format: {res_w}x{res_h} (16:9) @ 30 FPS [{facing.upper()} camera]")

    # 4. Start zero-latency scrcpy stream in background
    print("✓ Starting hardware camera encoder with zero-buffer direct sink...")
    scrcpy_proc = None
    try:
        scrcpy_proc = start_scrcpy_camera_sink(
            v4l2_device=v4l_dev,
            facing=facing,
            width=res_w,
            height=res_h,
            fps=30,
        )
        print("✓ Phone camera stream active! Launching Signora...")
        print("=" * 65)

        # 5. Dispatch to requested Signora recognition engine
        norm_mode = mode.lower().strip()
        if norm_mode in ("isl", "indian"):
            from signora.isl.live import run_isl_live

            return run_isl_live(
                source=dev_idx,
                headless=False,
                no_tts=no_tts,
                detect_every=detect_every,
                dwell_ms=dwell_ms,
                gemini_key=gemini_key,
                enable_body=enable_body,
                width=res_w,
                height=res_h,
            )
        elif norm_mode in ("body", "spatial"):
            from signora.body.live import run_body_live

            return run_body_live(
                source=dev_idx,
                headless=False,
                no_tts=no_tts,
                detect_every=detect_every,
                dwell_ms=dwell_ms,
                width=res_w,
                height=res_h,
            )
        elif norm_mode in ("words", "word", "asl-words"):
            from signora.words.live import run_words_live

            return run_words_live(
                source=dev_idx,
                headless=False,
                no_tts=no_tts,
                detect_every=detect_every,
                dwell_ms=dwell_ms,
                width=res_w,
                height=res_h,
            )
        else:
            # Default: ASL / Live single hand
            from signora.asl.live import run_live

            return run_live(
                source=dev_idx,
                headless=False,
                no_tts=no_tts,
                detect_every=detect_every,
                dwell_ms=dwell_ms,
                width=res_w,
                height=res_h,
            )

    except KeyboardInterrupt:
        print("\n[Phone Webcam] Shutting down...")
        return 0
    except Exception as e:
        print(f"\n[Phone Webcam Error] {e}", file=sys.stderr)
        return 1
    finally:
        if scrcpy_proc is not None:
            try:
                scrcpy_proc.terminate()
                scrcpy_proc.wait(timeout=2.0)
            except Exception:
                try:
                    scrcpy_proc.kill()
                except Exception:
                    pass

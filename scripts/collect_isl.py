#!/usr/bin/env python3
"""Interactive Webcam Collector for Indian Sign Language (ISL) Samples.

Records 164-dimensional MediaPipe landmark feature vectors for dual-handed
and single-handed ISL signs (A-Z + SPACE). Includes:
- Interactive letter carousel navigation
- 5-second preparation countdown timer
- Auto-burst collection of 300 samples
- In-place neural network training button [T]

Usage:
    # Launch interactive collection studio (300 samples, 5s countdown):
    python scripts/collect_isl.py

    # Collect for a specific sign:
    python scripts/collect_isl.py --sign D --count 300 --timer 5

    # Trigger neural model training directly on all collected data:
    python scripts/collect_isl.py --train
"""

import argparse
import os
import sys
from pathlib import Path

# Wayland/X11 compatibility for OpenCV HighGUI
os.environ.setdefault("QT_QPA_PLATFORM", "xcb")

# Auto-relaunch inside project .venv if run with system python lacking dependencies
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
_VENV_PYTHON = _PROJECT_ROOT / ".venv" / "bin" / "python"
if _VENV_PYTHON.exists() and sys.executable != str(_VENV_PYTHON):
    try:
        import cv2
    except ImportError:
        os.execv(str(_VENV_PYTHON), [str(_VENV_PYTHON)] + sys.argv)

if str(_PROJECT_ROOT / "src") not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT / "src"))
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

from signora.isl.collect import DATA_DIR, run_isl_collector


def main():
    parser = argparse.ArgumentParser(
        description="Interactive ISL Sample Collection Studio & Neural Trainer"
    )
    parser.add_argument(
        "-s", "--sign", default=None, help="Specific ISL letter/sign to collect (e.g. A-Z, SPACE)"
    )
    parser.add_argument(
        "-n", "--count", type=int, default=300, help="Target samples to collect automatically (default: 300)"
    )
    parser.add_argument(
        "--timer", type=float, default=5.0, help="Countdown timer duration before auto-capture in seconds (default: 5.0)"
    )
    parser.add_argument(
        "--source",
        default="0",
        help="Camera index (0, 1), device path (/dev/video2), or Phone IP Webcam URL",
    )
    parser.add_argument(
        "--train", action="store_true", help="Train PyTorch MLP model on all collected ISL data"
    )
    parser.add_argument(
        "--data-dir", type=Path, default=DATA_DIR, help="Directory to store .npy samples"
    )
    parser.add_argument(
        "--headless", action="store_true", help="Run without HighGUI display window"
    )
    parser.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )
    args = parser.parse_args()

    cam_source = args.source
    if str(cam_source).isdigit():
        cam_source = int(cam_source)

    return run_isl_collector(
        sign=args.sign,
        count=args.count,
        source=cam_source,
        timer_seconds=args.timer,
        data_dir=args.data_dir,
        headless=args.headless,
        use_fake=args.fake_camera,
        auto_train=args.train,
    )


if __name__ == "__main__":
    raise SystemExit(main())

"""16:9 Widescreen Resolution Selector and Enforcement for Signora Camera Pipelines."""

import os
import sys
from typing import Dict, Optional, Tuple

# Supported standard 16:9 widescreen resolutions
SUPPORTED_RESOLUTIONS_16_9: Dict[str, Tuple[str, int, int, str]] = {
    "1": ("720p", 1280, 720, "HD High Quality (Default)"),
    "2": ("480p", 854, 480, "Balanced & Smooth Performance"),
    "3": ("360p", 640, 360, "Fast & Low Latency (Best for Low-End CPUs)"),
    "4": ("1080p", 1920, 1080, "Full HD Maximum Detail"),
}

ALIAS_MAP: Dict[str, Tuple[int, int, str]] = {
    "1": (1280, 720, "720p"),
    "720": (1280, 720, "720p"),
    "720p": (1280, 720, "720p"),
    "1280x720": (1280, 720, "720p"),
    "hd": (1280, 720, "720p"),
    "2": (854, 480, "480p"),
    "480": (854, 480, "480p"),
    "480p": (854, 480, "480p"),
    "854x480": (854, 480, "480p"),
    "balanced": (854, 480, "480p"),
    "3": (640, 360, "360p"),
    "360": (640, 360, "360p"),
    "360p": (640, 360, "360p"),
    "640x360": (640, 360, "360p"),
    "fast": (640, 360, "360p"),
    "4": (1920, 1080, "1080p"),
    "1080": (1920, 1080, "1080p"),
    "1080p": (1920, 1080, "1080p"),
    "1920x1080": (1920, 1080, "1080p"),
    "fhd": (1920, 1080, "1080p"),
}


def parse_resolution_16_9(val: Optional[str]) -> Optional[Tuple[int, int, str]]:
    """Parse resolution string into (width, height, label) maintaining 16:9 aspect ratio."""
    if not val:
        return None

    cleaned = str(val).strip().lower()
    if cleaned in ALIAS_MAP:
        w, h, name = ALIAS_MAP[cleaned]
        return w, h, name

    # Custom WxH format (e.g. 1600x900)
    if "x" in cleaned:
        parts = cleaned.split("x")
        if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
            w, h = int(parts[0]), int(parts[1])
            aspect = float(w) / float(max(1, h))
            if abs(aspect - (16.0 / 9.0)) <= 0.05:
                return w, h, f"{h}p"
            else:
                # Snap height to enforce 16:9 aspect ratio
                h_16_9 = int(w * 9 / 16)
                return w, h_16_9, f"{h_16_9}p"

    return None


def prompt_resolution_16_9(
    default_choice: str = "720p",
    headless: bool = False,
    cli_arg: Optional[str] = None,
) -> Tuple[int, int, str]:
    """Prompt the user interactively to choose camera resolution (16:9 aspect ratio)."""
    # 1. If explicitly passed as CLI flag, parse directly
    if cli_arg:
        parsed = parse_resolution_16_9(cli_arg)
        if parsed:
            print(f"✓ Resolution selected via flag: {parsed[2]} ({parsed[0]}x{parsed[1]}) [16:9]")
            return parsed

    # 2. Check environment variable override
    env_res = os.environ.get("SIGNORA_RESOLUTION")
    if env_res:
        parsed = parse_resolution_16_9(env_res)
        if parsed:
            return parsed

    # 3. If running headless or non-interactive (CI, pipe, tests), return default
    if headless or not sys.stdin.isatty():
        default_parsed = parse_resolution_16_9(default_choice)
        return default_parsed if default_parsed else (1280, 720, "720p")

    # 4. Interactive menu prompt
    print("\n" + "=" * 62)
    print("  SELECT CAMERA RESOLUTION (16:9 Widescreen)")
    print("=" * 62)
    for key, (name, w, h, desc) in sorted(SUPPORTED_RESOLUTIONS_16_9.items()):
        print(f"  [{key}] {name:5s} : {w}x{h:<4d} - {desc}")
    print("=" * 62)

    try:
        raw = input(f"Select resolution [1/2/3/4 or 720p/480p/360p, Enter for {default_choice}]: ").strip()
        if not raw:
            raw = default_choice

        parsed = parse_resolution_16_9(raw)
        if parsed:
            print(f"✓ Resolution set to: {parsed[2]} ({parsed[0]}x{parsed[1]}) [16:9]\n")
            return parsed
        else:
            print(f"[Warning] Unknown resolution '{raw}'. Defaulting to {default_choice} (1280x720) [16:9].\n")
            return 1280, 720, "720p"
    except (EOFError, KeyboardInterrupt):
        print(f"\nDefaulting to {default_choice} (1280x720) [16:9].\n")
        return 1280, 720, "720p"

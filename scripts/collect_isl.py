#!/usr/bin/env python3
"""Interactive Webcam Collector for Indian Sign Language (ISL) Samples.

Records 164-dimensional MediaPipe landmark feature vectors for dual-handed
and single-handed ISL signs (e.g. Letter 'D', 'A', 'NAMASTE', etc.).

Usage:
    # Collect 20 samples for letter D:
    python scripts/collect_isl.py --sign D --count 20

    # Collect with manual trigger (press SPACE to capture each frame):
    python scripts/collect_isl.py --sign D --count 20 --manual
"""

import argparse
import sys
import time
from pathlib import Path
from typing import Any, List

import cv2
import numpy as np

from signora.core.hands import HAND_CONNECTIONS, HandPipeline
from signora.isl.features import extract_features

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "isl"


def draw_hud(
    frame: np.ndarray,
    sign: str,
    collected: int,
    target: int,
    is_recording: bool,
    countdown: float,
    manual: bool,
    hands_detected: int,
) -> None:
    """Render interactive collection HUD overlay."""
    h, w = frame.shape[:2]

    # Semi-transparent top header banner
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 80), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)

    # Title & Sign label
    cv2.putText(
        frame,
        f"ISL COLLECTOR: SIGN '{sign.upper()}'",
        (16, 32),
        cv2.FONT_HERSHEY_DUPLEX,
        0.8,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )

    # Mode / Instructions
    instr = "[SPACE] Capture sample" if manual else "Auto-burst recording"
    cv2.putText(
        frame,
        f"{instr} | Hands: {hands_detected}/2 | [Q] Quit",
        (16, 62),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # Progress pill (top right)
    pct = int((collected / max(target, 1)) * 100)
    pill_text = f"{collected}/{target} ({pct}%)"
    cv2.putText(
        frame,
        pill_text,
        (w - 180, 45),
        cv2.FONT_HERSHEY_DUPLEX,
        0.75,
        (52, 211, 153) if collected >= target else (99, 102, 241),
        2,
        cv2.LINE_AA,
    )

    # Center countdown or status
    if countdown > 0:
        cd_int = int(np.ceil(countdown))
        text = f"GET READY: {cd_int}"
        cv2.putText(
            frame,
            text,
            (w // 2 - 140, h // 2),
            cv2.FONT_HERSHEY_DUPLEX,
            1.2,
            (245, 158, 11),
            3,
            cv2.LINE_AA,
        )
    elif is_recording and not manual:
        cv2.putText(
            frame,
            "● RECORDING...",
            (w // 2 - 110, h // 2 - 140),
            cv2.FONT_HERSHEY_DUPLEX,
            0.8,
            (239, 68, 68),
            2,
            cv2.LINE_AA,
        )

    # ISL Letter D Gesture Guide Box (Bottom Left)
    if sign.upper() == "D":
        cv2.rectangle(frame, (12, h - 75), (w - 12, h - 12), (20, 20, 20), -1)
        cv2.rectangle(frame, (12, h - 75), (w - 12, h - 12), (99, 102, 241), 1)
        cv2.putText(
            frame,
            "ISL 'D' GUIDE: Left index points UP (stem). Right thumb & index curve into 'C'",
            (20, h - 48),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            "touching left index tip and base to complete the capital 'D' loop.",
            (20, h - 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            (148, 163, 184),
            1,
            cv2.LINE_AA,
        )


def draw_skeleton(frame: np.ndarray, landmarks_list: List[Any], handedness_list: List[str]) -> None:
    """Draw cybernetic hand skeletons on preview frame."""
    h, w = frame.shape[:2]
    for idx, lms in enumerate(landmarks_list):
        if not lms:
            continue
        is_right = (
            handedness_list[idx] == "Right" if idx < len(handedness_list) else (idx == 1)
        )
        bone_color = (238, 211, 34) if is_right else (244, 63, 94)  # Cyan vs Coral
        joint_color = (52, 211, 153) if is_right else (251, 146, 60)

        coords = [(int(p.x * w), int(p.y * h)) for p in lms]

        for s, e in HAND_CONNECTIONS:
            cv2.line(frame, coords[s], coords[e], bone_color, 2, cv2.LINE_AA)
        for pt in coords:
            cv2.circle(frame, pt, 4, joint_color, -1, cv2.LINE_AA)


def collect(sign: str, count: int, source: int = 0, manual: bool = False, delay: float = 0.12) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    out_file = DATA_DIR / f"{sign.upper()}.npy"

    print("=" * 65)
    print(f"  SIGNORA -- ISL Sample Collector for '{sign.upper()}'")
    print("=" * 65)
    print(f"  Target Samples : {count}")
    print(f"  Output Path    : {out_file}")
    print(f"  Trigger Mode   : {'[SPACE] Manual Trigger' if manual else f'Auto-burst ({delay}s delay)'}")
    print("=" * 65)

    pipe = HandPipeline(source=source, num_hands=2, mirror=True)
    if not pipe.started.wait(timeout=5) or pipe.error:
        print("[Error] Could not initialize webcam.", file=sys.stderr)
        return

    existing_samples: List[np.ndarray] = []
    if out_file.exists():
        try:
            arr = np.load(out_file)
            existing_samples = list(arr)
            print(f"Loaded {len(existing_samples)} existing samples from {out_file.name}")
        except Exception:
            pass

    collected: List[np.ndarray] = []
    start_time = time.time()
    countdown_duration = 3.0 if not manual else 0.0
    last_sample_time = 0.0

    cv2.namedWindow("ISL Collector", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("ISL Collector", 960, 720)

    try:
        while len(collected) < count:
            frame, landmarks, handedness = pipe.get()
            if frame is None:
                time.sleep(0.01)
                continue

            elapsed = time.time() - start_time
            countdown = max(0.0, countdown_duration - elapsed)
            num_hands = len(landmarks) if isinstance(landmarks, list) else (1 if landmarks else 0)

            # Draw skeleton
            lms_list = landmarks if isinstance(landmarks, list) else ([landmarks] if landmarks else [])
            hnds_list = handedness if isinstance(handedness, list) else ([handedness] if handedness else [])
            draw_skeleton(frame, lms_list, hnds_list)

            key = cv2.waitKey(1) & 0xFF
            if key == ord("q") or key == 27:
                print("\n[Aborted] Collection stopped by user.")
                break

            should_capture = False
            if countdown == 0.0:
                if manual:
                    if key == 32:  # SPACEBAR
                        should_capture = True
                else:
                    if time.time() - last_sample_time >= delay:
                        should_capture = True

            if should_capture and num_hands > 0:
                feats = extract_features(lms_list, hnds_list)
                if np.all(np.isfinite(feats)):
                    collected.append(feats)
                    last_sample_time = time.time()
                    # Visual capture pulse
                    cv2.circle(frame, (frame.shape[1] - 40, 40), 12, (52, 211, 153), -1)

            draw_hud(
                frame=frame,
                sign=sign,
                collected=len(collected),
                target=count,
                is_recording=(countdown == 0.0),
                countdown=countdown,
                manual=manual,
                hands_detected=num_hands,
            )

            cv2.imshow("ISL Collector", frame)

    finally:
        pipe.close()
        cv2.destroyAllWindows()

    if collected:
        combined = np.vstack(existing_samples + collected) if existing_samples else np.vstack(collected)
        np.save(out_file, combined.astype(np.float32))
        print(f"\n🎉 Successfully saved {len(combined)} samples ({len(collected)} new) to:")
        print(f"   {out_file}")
        print("\nNext step: Run training script:")
        print("   python scripts/train_isl.py")


def main():
    parser = argparse.ArgumentParser(description="Collect 164D feature samples for ISL signs")
    parser.add_argument("-s", "--sign", default="D", help="Sign label to record (e.g. D, T, NAMASTE)")
    parser.add_argument("-n", "--count", type=int, default=20, help="Number of samples to collect (default: 20)")
    parser.add_argument("--source", type=int, default=0, help="Camera device index (default: 0)")
    parser.add_argument("--manual", action="store_true", help="Press SPACEBAR to capture each sample manually")
    parser.add_argument("--delay", type=float, default=0.15, help="Delay between auto-burst samples in seconds (default: 0.15)")
    args = parser.parse_args()

    collect(sign=args.sign, count=args.count, source=args.source, manual=args.manual, delay=args.delay)


if __name__ == "__main__":
    main()

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
import os
import sys
import time
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

from typing import Any, List

import cv2
import numpy as np

from signora.core.hands import HAND_CONNECTIONS, HandPipeline
from signora.isl.features import extract_features

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "isl"

ISL_GUIDES = {
    "A": (
        "ISL 'A': Open left hand palm facing you. Right index touches left THUMB tip.",
        "Vowel 1 of 5 (Thumb = A, Index = E, Middle = I, Ring = O, Pinky = U).",
    ),
    "B": (
        "ISL 'B': Bring both hands together forming two circles (like binoculars or '8').",
        "Both index and thumbs meet to form two adjacent loops of 'B'.",
    ),
    "C": (
        "ISL 'C': Right hand curved in a 'C' shape in front of chest (1-handed).",
        "Open curved arc with thumb and fingers.",
    ),
    "D": (
        "ISL 'D': Left index points UP (stem). Right thumb & index curve into 'C'",
        "touching left index tip and base to complete the capital 'D' loop.",
    ),
    "E": (
        "ISL 'E': Open left hand palm facing you. Right index touches left INDEX fingertip.",
        "Vowel 2 of 5 (Thumb = A, Index = E, Middle = I, Ring = O, Pinky = U).",
    ),
    "F": (
        "ISL 'F': Right index and middle fingers cross horizontally over left index and middle.",
        "Forms a 2-finger cross / hashtag shape.",
    ),
    "G": (
        "ISL 'G': Both hands formed into fists, one fist resting on top of the other.",
        "Right fist placed directly above left fist.",
    ),
    "H": (
        "ISL 'H': Open left flat palm facing up. Right flat hand brushes across left palm.",
        "Straight sweeping motion from wrist to fingertips across palm.",
    ),
    "I": (
        "ISL 'I': Open left hand palm facing you. Right index touches left MIDDLE fingertip.",
        "Vowel 3 of 5 (Thumb = A, Index = E, Middle = I, Ring = O, Pinky = U).",
    ),
    "K": (
        "ISL 'K': Left index points UP. Right index hooks against left index knuckle.",
        "Right index bent like a hook touching left vertical stem.",
    ),
    "L": (
        "ISL 'L': Single hand forming an 'L' shape with thumb and index finger extended.",
        "Other 3 fingers folded into palm (1-handed).",
    ),
    "M": (
        "ISL 'M': Left open flat palm facing up. Right hand places 3 fingers (index, mid, ring) on palm.",
        "Represents 3 downward strokes of letter 'M'.",
    ),
    "N": (
        "ISL 'N': Left open flat palm facing up. Right hand places 2 fingers (index, middle) on palm.",
        "Represents 2 downward strokes of letter 'N'.",
    ),
    "O": (
        "ISL 'O': Open left hand palm facing you. Right index touches left RING fingertip.",
        "Vowel 4 of 5 (Thumb = A, Index = E, Middle = I, Ring = O, Pinky = U).",
    ),
    "P": (
        "ISL 'P': Left index points UP. Right index & thumb form a circle touching left index tip.",
        "Forms the upper loop of letter 'P'.",
    ),
    "Q": (
        "ISL 'Q': Left hand forms an 'O' circle. Right index hooks into the bottom of the circle.",
        "Forms the tail of letter 'Q'.",
    ),
    "R": (
        "ISL 'R': Right index finger hooked/curved resting on top of left open flat palm.",
        "Forms the rounded arc of letter 'R'.",
    ),
    "S": (
        "ISL 'S': Left pinky extended. Right pinky hooked with left pinky.",
        "Both pinky fingers interlocked like an 'S'.",
    ),
    "T": (
        "ISL 'T': Left open flat palm vertical. Right index points horizontally at left palm edge.",
        "Forms a 'T' shape intersection.",
    ),
    "U": (
        "ISL 'U': Open left hand palm facing you. Right index touches left PINKY fingertip.",
        "Vowel 5 of 5 (Thumb = A, Index = E, Middle = I, Ring = O, Pinky = U).",
    ),
    "V": (
        "ISL 'V': Form a 'V' peace sign with index and middle fingers (1-handed).",
        "Index and middle fingers spread apart.",
    ),
    "W": (
        "ISL 'W': Interlock fingers of both hands together pointing upwards.",
        "Forms 3 or 4 points of a 'W'.",
    ),
    "X": (
        "ISL 'X': Cross both extended index fingers to form an 'X'.",
        "Left index and right index crossed at mid-finger.",
    ),
    "Y": (
        "ISL 'Y': Left index extended pointing up. Right index points into V-crotch of left thumb & index.",
        "Forms a 'Y' junction.",
    ),
}


def draw_hud(
    frame: np.ndarray,
    sign: str,
    collected: int,
    target: int,
    is_recording: bool,
    countdown: float,
    manual: bool,
    is_paused: bool,
    hands_detected: int,
) -> None:
    """Render interactive collection HUD overlay."""
    h, w = frame.shape[:2]

    # Semi-transparent top header banner
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 80), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)

    # Horizontal progress fill bar
    bar_w = int(w * (collected / max(target, 1)))
    bar_color = (52, 211, 153) if collected >= target else (99, 102, 241)
    cv2.rectangle(frame, (0, 76), (bar_w, 82), bar_color, -1)

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
    instr = "[SPACE] Capture" if manual else ("[P] Pause/Resume" if not is_paused else "PAUSED - [P] Resume")
    cv2.putText(
        frame,
        f"{instr} | Hands: {hands_detected}/2 | [Q] Quit",
        (16, 62),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        (245, 158, 11) if is_paused else (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # Progress pill (top right)
    pct = int((collected / max(target, 1)) * 100)
    pill_text = f"{collected}/{target} ({pct}%)"
    cv2.putText(
        frame,
        pill_text,
        (w - 200, 45),
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
    elif is_paused:
        cv2.putText(
            frame,
            "|| PAUSED",
            (w // 2 - 100, h // 2),
            cv2.FONT_HERSHEY_DUPLEX,
            1.2,
            (245, 158, 11),
            3,
            cv2.LINE_AA,
        )
    elif countdown == 0.0:
        expected = 1 if sign.upper() in ("C", "L", "V", "SPACE", "GOOD", "BAD", "PEACE", "OK", "LOVE", "STOP") else 2
        if hands_detected < expected:
            cv2.putText(
                frame,
                f"WAITING FOR {expected} HANDS...",
                (w // 2 - 190, h // 2 - 140),
                cv2.FONT_HERSHEY_DUPLEX,
                0.75,
                (245, 158, 11),
                2,
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

    # Dynamic ISL Posture Guide Box
    guide = ISL_GUIDES.get(sign.upper())
    if guide:
        cv2.rectangle(frame, (12, h - 75), (w - 12, h - 12), (20, 20, 20), -1)
        cv2.rectangle(frame, (12, h - 75), (w - 12, h - 12), (99, 102, 241), 1)
        cv2.putText(
            frame,
            guide[0],
            (20, h - 48),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.45,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            guide[1],
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


def collect(sign: str, count: int, source: Any = 0, manual: bool = False, delay: float = 0.12) -> None:
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
    is_paused = False

    cv2.namedWindow("ISL Collector", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("ISL Collector", 960, 720)

    try:
        while len(collected) < count:
            frame, landmarks, handedness = pipe.read(timeout=2.0)
            if frame is None:
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
            elif key in (ord("p"), ord("P")):
                is_paused = not is_paused

            expected_hands = 1 if sign.upper() in ("C", "L", "V", "SPACE", "GOOD", "BAD", "PEACE", "OK", "LOVE", "STOP") else 2

            should_capture = False
            if countdown == 0.0 and not is_paused:
                if manual:
                    if key == 32:  # SPACEBAR
                        should_capture = True
                else:
                    if time.time() - last_sample_time >= delay:
                        should_capture = True

            if should_capture and num_hands >= expected_hands:
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
                is_recording=(countdown == 0.0 and num_hands >= expected_hands),
                countdown=countdown,
                manual=manual,
                is_paused=is_paused,
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
    parser.add_argument(
        "--source",
        default="0",
        help="Camera index (0, 1), device path (/dev/video2), or Phone IP Webcam URL (e.g. http://192.168.1.50:8080/video)",
    )
    parser.add_argument("--manual", action="store_true", help="Press SPACEBAR to capture each sample manually")
    parser.add_argument("--delay", type=float, default=0.15, help="Delay between auto-burst samples in seconds (default: 0.15)")
    args = parser.parse_args()

    cam_source = args.source
    if str(cam_source).isdigit():
        cam_source = int(cam_source)

    collect(sign=args.sign, count=args.count, source=cam_source, manual=args.manual, delay=args.delay)


if __name__ == "__main__":
    main()

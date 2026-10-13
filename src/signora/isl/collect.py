"""Interactive Studio for Indian Sign Language (ISL) Sample Collection & Neural Training.

Provides an interactive OpenCV studio allowing users to:
1. Select individual ISL letters (A-Z + SPACE) interactively via keyboard navigation.
2. View existing sample counts and live posture guidance per letter.
3. Start a 5-second countdown timer before auto-collecting 300 samples at ~30 FPS.
4. Train the 164D PyTorch MLP classifier on all collected data in data/isl/ via the [T] button,
   updating models/isl_mlp.pt and exporting Android mobile assets automatically.
"""

import os
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np

from signora.core.hands import HAND_CONNECTIONS, HandPipeline
from signora.isl.features import extract_features

# Wayland / X11 compatibility
os.environ.setdefault("QT_QPA_PLATFORM", "xcb")

ROOT_DIR = Path(__file__).resolve().parents[3]
DATA_DIR = ROOT_DIR / "data" / "isl"

ISL_LETTERS = [
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "SPACE"
]

ISL_GUIDES: Dict[str, Tuple[str, str]] = {
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
    "J": (
        "ISL 'J': Open left flat palm facing you. Right index finger draws curve of 'J' on left palm.",
        "Downwards stroke curving left at bottom on the palm.",
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
    "Z": (
        "ISL 'Z': Single right index finger traces a 'Z' zigzag horizontally in the air (1-handed).",
        "Zigzag gesture in front of chest.",
    ),
    "SPACE": (
        "ISL 'SPACE': Single flat hand held motionless facing forward or downward (1-handed).",
        "Static pause delimiter between words.",
    ),
}


def get_existing_counts(data_dir: Path = DATA_DIR) -> Dict[str, int]:
    """Scan data directory for existing recorded samples per sign."""
    counts: Dict[str, int] = {}
    if not data_dir.exists():
        return counts
    for sign in ISL_LETTERS:
        p = data_dir / f"{sign}.npy"
        if p.exists():
            try:
                arr = np.load(p)
                counts[sign] = len(arr) if arr.ndim > 1 else (1 if len(arr) > 0 else 0)
            except Exception:
                counts[sign] = 0
        else:
            counts[sign] = 0
    return counts


def draw_hud(
    frame: np.ndarray,
    current_sign: str,
    sign_idx: int,
    collected_in_batch: int,
    target_count: int,
    existing_count: int,
    state: str,  # 'IDLE', 'COUNTDOWN', 'COLLECTING', 'TRAINING', 'SUCCESS'
    countdown_val: float,
    hands_detected: int,
    status_msg: str = "",
    counts_dict: Optional[Dict[str, int]] = None,
) -> None:
    """Render interactive Glassmorphic Studio HUD."""
    h, w = frame.shape[:2]
    counts = counts_dict or {}

    # 1. Top Ribbon: Alphabet Carousel
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 105), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.88, frame, 0.12, 0, frame)

    # Title & Mode
    cv2.putText(
        frame,
        "ISL STUDIO: SAMPLE COLLECTOR & TRAINER",
        (16, 26),
        cv2.FONT_HERSHEY_DUPLEX,
        0.65,
        (255, 255, 255),
        1,
        cv2.LINE_AA,
    )

    # Alphabet Strip: Show a sliding window of ~9 letters centered around current_sign
    window_size = 9
    start_i = max(0, min(len(ISL_LETTERS) - window_size, sign_idx - window_size // 2))
    end_i = min(len(ISL_LETTERS), start_i + window_size)

    chip_x = 16
    for i in range(start_i, end_i):
        let = ISL_LETTERS[i]
        cnt = counts.get(let, 0)
        is_cur = (i == sign_idx)

        # Color: Green if >= target, Amber if > 0, Gray if 0
        if cnt >= target_count:
            bg_col = (52, 211, 153)  # Emerald
            txt_col = (15, 23, 42)
        elif cnt > 0:
            bg_col = (245, 158, 11)  # Amber
            txt_col = (15, 23, 42)
        else:
            bg_col = (51, 65, 85)   # Slate gray
            txt_col = (203, 213, 225)

        chip_w = 46 if len(let) <= 2 else 68
        cv2.rectangle(frame, (chip_x, 38), (chip_x + chip_w, 68), bg_col, -1)
        if is_cur:
            cv2.rectangle(frame, (chip_x - 2, 36), (chip_x + chip_w + 2, 70), (6, 182, 212), 2)  # Cyan border

        label = f"{let}:{cnt}" if cnt < 1000 else f"{let}:999+"
        cv2.putText(
            frame,
            label,
            (chip_x + 4, 58),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.4,
            txt_col,
            1,
            cv2.LINE_AA,
        )
        chip_x += chip_w + 6

    # Top right: Total Signs Collected Counter
    total_ready = sum(1 for v in counts.values() if v >= target_count)
    summary_text = f"Ready: {total_ready}/{len(ISL_LETTERS)} Signs"
    cv2.putText(
        frame,
        summary_text,
        (w - 220, 26),
        cv2.FONT_HERSHEY_DUPLEX,
        0.55,
        (52, 211, 153),
        1,
        cv2.LINE_AA,
    )

    # 2. Progress fill bar (under ribbon)
    bar_pct = collected_in_batch / max(target_count, 1)
    bar_w = int(w * min(1.0, bar_pct))
    bar_col = (52, 211, 153) if bar_pct >= 1.0 else (99, 102, 241)
    cv2.rectangle(frame, (0, 100), (w, 105), (30, 41, 59), -1)
    if bar_w > 0:
        cv2.rectangle(frame, (0, 100), (bar_w, 105), bar_col, -1)

    # Batch status text
    batch_info = (
        f"Sign: [{current_sign}] | Batch: {collected_in_batch}/{target_count} ({int(bar_pct*100)}%) "
        f"| Saved: {existing_count} | Hands: {hands_detected}"
    )
    cv2.putText(
        frame,
        batch_info,
        (16, 92),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.48,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )

    # 3. Center State Alerts
    if state == "COUNTDOWN":
        cd_int = max(1, int(np.ceil(countdown_val)))
        card_w, card_h = 420, 140
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2
        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), (245, 158, 11), 2)

        # Countdown number
        cv2.putText(
            frame,
            f"STARTING IN: {cd_int}",
            (cx + 60, cy + 55),
            cv2.FONT_HERSHEY_DUPLEX,
            1.1,
            (245, 158, 11),
            2,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            f"Hold posture for ISL '{current_sign}'",
            (cx + 40, cy + 105),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )

    elif state == "COLLECTING":
        # Recording pill top-center
        pill_w, pill_h = 360, 48
        px, py = w // 2 - pill_w // 2, 120
        overlay = frame.copy()
        cv2.rectangle(overlay, (px, py), (px + pill_w, py + pill_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
        cv2.rectangle(frame, (px, py), (px + pill_w, py + pill_h), (239, 68, 68), 2)

        # Flashing red dot
        if int(time.time() * 4) % 2 == 0:
            cv2.circle(frame, (px + 24, py + 24), 8, (239, 68, 68), -1)
        cv2.putText(
            frame,
            f"RECORDING: {collected_in_batch}/{target_count} SAMPLES",
            (px + 45, py + 31),
            cv2.FONT_HERSHEY_DUPLEX,
            0.55,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )

    elif state == "TRAINING":
        card_w, card_h = 560, 150
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2
        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.92, frame, 0.08, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), (6, 182, 212), 2)

        cv2.putText(
            frame,
            "TRAINING ISL NEURAL NETWORK...",
            (cx + 40, cy + 50),
            cv2.FONT_HERSHEY_DUPLEX,
            0.8,
            (6, 182, 212),
            2,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            "Optimizing 164D MLP weights on all data/isl/ classes",
            (cx + 40, cy + 90),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            (226, 232, 240),
            1,
            cv2.LINE_AA,
        )
        if status_msg:
            cv2.putText(
                frame,
                status_msg,
                (cx + 40, cy + 125),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.48,
                (52, 211, 153),
                1,
                cv2.LINE_AA,
            )

    elif state == "SUCCESS" and status_msg:
        # Success popup
        card_w, card_h = 520, 90
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2
        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.9, frame, 0.1, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), (52, 211, 153), 2)
        cv2.putText(
            frame,
            status_msg,
            (cx + 25, cy + 52),
            cv2.FONT_HERSHEY_DUPLEX,
            0.65,
            (52, 211, 153),
            1,
            cv2.LINE_AA,
        )

    # 4. Bottom Posture Guide Box
    guide = ISL_GUIDES.get(current_sign.upper())
    if guide:
        overlay = frame.copy()
        cv2.rectangle(overlay, (12, h - 110), (w - 12, h - 50), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
        cv2.rectangle(frame, (12, h - 110), (w - 12, h - 50), (99, 102, 241), 1)
        cv2.putText(
            frame,
            guide[0],
            (20, h - 85),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.48,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            guide[1],
            (20, h - 62),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.44,
            (148, 163, 184),
            1,
            cv2.LINE_AA,
        )

    # 5. Bottom Hotkey Navigation Bar
    cv2.rectangle(frame, (0, h - 45), (w, h), (10, 15, 30), -1)
    hotkeys = (
        "[SPACE] Start 5s Timer & Collect   "
        "[<-/-> or A-Z] Change Sign   "
        "[T] Train Model   "
        "[P] Pause   "
        "[Q] Quit"
    )
    cv2.putText(
        frame,
        hotkeys,
        (16, h - 17),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.45,
        (203, 213, 225),
        1,
        cv2.LINE_AA,
    )


def draw_skeleton(frame: np.ndarray, landmarks_list: List[Any], handedness_list: List[str]) -> None:
    """Draw cybernetic hand skeletons on preview frame."""
    h, w = frame.shape[:2]
    for idx, lms in enumerate(landmarks_list):
        if not lms:
            continue
        is_right = handedness_list[idx] == "Right" if idx < len(handedness_list) else (idx == 1)
        bone_color = (238, 211, 34) if is_right else (244, 63, 94)  # Cyan vs Magenta
        joint_color = (52, 211, 153) if is_right else (251, 146, 60)

        coords = [(int(p.x * w), int(p.y * h)) for p in lms]
        for s, e in HAND_CONNECTIONS:
            cv2.line(frame, coords[s], coords[e], bone_color, 2, cv2.LINE_AA)
        for pt in coords:
            cv2.circle(frame, pt, 4, joint_color, -1, cv2.LINE_AA)


def run_isl_collector(
    sign: Optional[str] = None,
    count: int = 300,
    source: Any = 0,
    timer_seconds: float = 5.0,
    data_dir: Path = DATA_DIR,
    headless: bool = False,
    use_fake: bool = False,
    auto_train: bool = False,
) -> int:
    """Main execution loop for interactive ISL Studio."""
    data_dir.mkdir(parents=True, exist_ok=True)

    # If requested direct training
    if auto_train:
        print("[Studio] Directly invoking ISL model training on all data...")
        from scripts.train_isl import train_isl_model

        res = train_isl_model(data_dir=data_dir)
        return 0 if res.get("success") else 1

    sign_name = sign.upper() if sign else "A"
    if sign_name not in ISL_LETTERS:
        sign_name = "A"
    sign_idx = ISL_LETTERS.index(sign_name)

    print("=" * 65)
    print("  SIGNORA -- Interactive ISL Collection Studio & Neural Trainer")
    print("=" * 65)
    print(f"  Target Samples Per Sign : {count}")
    print(f"  Start Countdown Timer   : {timer_seconds}s")
    print(f"  Storage Directory       : {data_dir}")
    print("  Controls:")
    print("    [SPACE]   : Start 5-second countdown & auto-collect 300 samples")
    print("    [<- / ->] : Previous / Next sign")
    print("    [A - Z]   : Directly jump to sign")
    print("    [T]       : Train neural network on all collected data")
    print("    [Q]       : Quit Studio")
    print("=" * 65)

    pipe = HandPipeline(source=source, num_hands=2, mirror=True, use_fake=use_fake)
    if not pipe.started.wait(timeout=5) or pipe.error:
        print("[Error] Could not initialize camera.", file=sys.stderr)
        return 1

    if not headless:
        cv2.namedWindow("Signora ISL Studio", cv2.WINDOW_NORMAL)
        cv2.resizeWindow("Signora ISL Studio", 960, 720)

    state = "IDLE"  # 'IDLE', 'COUNTDOWN', 'COLLECTING', 'TRAINING', 'SUCCESS'
    countdown_start = 0.0
    collected_samples: List[np.ndarray] = []
    status_msg = ""
    status_msg_expiry = 0.0
    last_sample_time = 0.0
    is_paused = False

    counts = get_existing_counts(data_dir)

    try:
        while True:
            frame, landmarks, handedness = pipe.read(timeout=1.0)
            if frame is None:
                continue

            current_sign = ISL_LETTERS[sign_idx]
            num_hands = len(landmarks) if isinstance(landmarks, list) else (1 if landmarks else 0)
            lms_list = landmarks if isinstance(landmarks, list) else ([landmarks] if landmarks else [])
            hnds_list = handedness if isinstance(handedness, list) else ([handedness] if handedness else [])

            # Draw skeletons
            draw_skeleton(frame, lms_list, hnds_list)

            # Key handling
            key = cv2.waitKey(1) & 0xFF if not headless else -1
            if key in (ord("q"), ord("Q"), 27):
                print("[Studio] Exiting ISL Studio.")
                break
            elif key in (ord("p"), ord("P")):
                is_paused = not is_paused
            elif key in (ord("["), 81):  # Previous sign
                sign_idx = (sign_idx - 1) % len(ISL_LETTERS)
                state = "IDLE"
                collected_samples = []
            elif key in (ord("]"), 83):  # Next sign
                sign_idx = (sign_idx + 1) % len(ISL_LETTERS)
                state = "IDLE"
                collected_samples = []
            elif ord("a") <= key <= ord("z"):
                char = chr(key).upper()
                if char in ISL_LETTERS:
                    sign_idx = ISL_LETTERS.index(char)
                    state = "IDLE"
                    collected_samples = []
            elif key in (ord("t"), ord("T")):
                # Trigger training
                state = "TRAINING"
                draw_hud(
                    frame=frame,
                    current_sign=current_sign,
                    sign_idx=sign_idx,
                    collected_in_batch=len(collected_samples),
                    target_count=count,
                    existing_count=counts.get(current_sign, 0),
                    state=state,
                    countdown_val=0.0,
                    hands_detected=num_hands,
                    status_msg="Training MLP classifier...",
                    counts_dict=counts,
                )
                if not headless:
                    cv2.imshow("Signora ISL Studio", frame)
                    cv2.waitKey(10)

                print("\n[Studio] Initiating neural network training on all data...")
                try:
                    from scripts.train_isl import train_isl_model

                    res = train_isl_model(data_dir=data_dir)
                    if res.get("success"):
                        acc = res.get("val_acc", 0.0) * 100
                        status_msg = f"✓ Training Complete! Val Acc: {acc:.1f}% | Checkpoint Updated"
                    else:
                        status_msg = f"Training Failed: {res.get('error', 'Unknown')}"
                except Exception as e:
                    status_msg = f"Training Error: {e}"

                state = "SUCCESS"
                status_msg_expiry = time.time() + 4.0
            elif key == 32:  # SPACE BAR
                if state in ("IDLE", "SUCCESS"):
                    state = "COUNTDOWN"
                    countdown_start = time.time()
                    collected_samples = []

            # State Logic
            countdown_val = 0.0
            if state == "COUNTDOWN":
                elapsed = time.time() - countdown_start
                countdown_val = max(0.0, timer_seconds - elapsed)
                if countdown_val <= 0.0:
                    state = "COLLECTING"
                    collected_samples = []

            elif state == "COLLECTING":
                expected_hands = 1 if current_sign in ("C", "L", "V", "Z", "SPACE") else 2
                if num_hands >= expected_hands and not is_paused:
                    now = time.time()
                    if now - last_sample_time >= 0.028:  # ~35 FPS burst capture
                        feats = extract_features(lms_list, hnds_list)
                        if np.all(np.isfinite(feats)):
                            collected_samples.append(feats)
                            last_sample_time = now

                # Check if batch reached target
                if len(collected_samples) >= count:
                    # Save to file
                    out_path = data_dir / f"{current_sign}.npy"
                    prev_samples: List[np.ndarray] = []
                    if out_path.exists():
                        try:
                            arr = np.load(out_path)
                            prev_samples = list(arr)
                        except Exception:
                            pass
                    combined = np.vstack(prev_samples + collected_samples) if prev_samples else np.vstack(collected_samples)
                    np.save(out_path, combined.astype(np.float32))

                    counts[current_sign] = len(combined)
                    status_msg = f"🎉 Saved {count} samples for '{current_sign}'! (Total: {len(combined)})"
                    status_msg_expiry = time.time() + 3.5
                    state = "SUCCESS"
                    print(f"\n[Studio] {status_msg}")

            elif state == "SUCCESS":
                if time.time() > status_msg_expiry:
                    state = "IDLE"
                    status_msg = ""

            # Render HUD
            draw_hud(
                frame=frame,
                current_sign=current_sign,
                sign_idx=sign_idx,
                collected_in_batch=len(collected_samples),
                target_count=count,
                existing_count=counts.get(current_sign, 0),
                state=state,
                countdown_val=countdown_val,
                hands_detected=num_hands,
                status_msg=status_msg,
                counts_dict=counts,
            )

            if not headless:
                cv2.imshow("Signora ISL Studio", frame)

            # In testing/fake camera mode with limited cycles
            if use_fake and headless:
                break

    finally:
        pipe.close()
        if not headless:
            cv2.destroyAllWindows()

    return 0

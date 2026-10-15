"""Interactive Studio for Indian Sign Language (ISL) Sample Collection & Neural Training.

Provides an interactive OpenCV studio allowing users to:
1. Select individual ISL letters (A-Z + SPACE) interactively via keyboard navigation.
2. View existing sample counts and live posture guidance per letter.
3. Start a 5-second countdown timer before auto-collecting 300 samples at ~30 FPS.
4. Train the 164D PyTorch MLP classifier on all collected data in data/isl/ via [Shift+T],
   updating models/isl_mlp.pt and exporting Android mobile assets automatically.
"""

import os
import sys
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np

from signora.core.hands import HAND_CONNECTIONS, HandPipeline
from signora.isl.config import STATIC_WORDS, get_expected_hands
from signora.isl.features import extract_features

# Wayland / X11 compatibility
os.environ.setdefault("QT_QPA_PLATFORM", "xcb")

ROOT_DIR = Path(__file__).resolve().parents[3]
DATA_DIR = ROOT_DIR / "data" / "isl"
DATA_SPATIAL_DIR = ROOT_DIR / "data" / "isl_spatial"

ISL_LETTERS = [
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "SPACE"
]

ISL_WORDS: List[str] = list(STATIC_WORDS)
ISL_ALL_SIGNS: List[str] = ISL_LETTERS + [w for w in ISL_WORDS if w not in ISL_LETTERS]


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

ISL_WORD_GUIDES: Dict[str, Tuple[str, str]] = {
    "NAMASTE": (
        "ISL 'NAMASTE': Both flat palms joined together at chest in prayer pose (2-handed).",
        "Universal Indian greeting gesture held steady with fingers pointing upward.",
    ),
    "HELLO": (
        "ISL 'HELLO': Open flat right palm held at shoulder/head height facing forward (1-handed).",
        "Classic greeting posture held steady facing the camera.",
    ),
    "WELCOME": (
        "ISL 'WELCOME': Both open flat palms upturned, extended forward at chest height (2-handed).",
        "Warm hospitality gesture receiving visitors.",
    ),
    "THANK_YOU": (
        "ISL 'THANK_YOU': Flat right hand with fingertips touching chin/lips facing forward (1-handed).",
        "Polite expression of sincere thanks and gratitude.",
    ),
    "PLEASE": (
        "ISL 'PLEASE': Open flat right palm placed directly over center of chest (1-handed).",
        "Respectful request posture held firmly against chest.",
    ),
    "CONGRATS": (
        "ISL 'CONGRATS': Both hands forming thumbs-up upright side-by-side at chest (2-handed).",
        "Celebration posture expressing congratulations and honor.",
    ),
    "RESPECT": (
        "ISL 'RESPECT': Right flat vertical hand at temple, left flat hand over chest (2-handed).",
        "Formal salutation expressing deep respect.",
    ),
    "PEACE": (
        "ISL 'PEACE': 'V' peace sign formed with index and middle fingers spread (1-handed).",
        "Universal peace posture held steady in front of shoulder.",
    ),
    "LOVE": (
        "ISL 'LOVE': Thumb, index, and pinky extended (classic ILY sign) (1-handed).",
        "Middle and ring fingers folded into palm.",
    ),
    "FRIEND": (
        "ISL 'FRIEND': Index fingers of both hands hooked into each other (2-handed).",
        "Symbolizes bond and close connection between comrades.",
    ),
    "FAMILY": (
        "ISL 'FAMILY': Both hands forming 'F' circles with index & thumb touching (2-handed).",
        "Both 'F' hands held together in front of chest.",
    ),
    "TOGETHER": (
        "ISL 'TOGETHER': Both open flat palms facing each other with fingers loosely interlaced (2-handed).",
        "Unified group solidarity posture.",
    ),
    "PROMISE": (
        "ISL 'PROMISE': Right index finger vertical against lips, left flat palm horizontal (2-handed).",
        "Solemn vow posture representing truth and commitment.",
    ),
    "APOLOGY": (
        "ISL 'APOLOGY': Closed right fist placed gently over the heart (1-handed).",
        "Conveys sincere regret and heartfelt apology.",
    ),
    "GOODBYE": (
        "ISL 'GOODBYE': Open flat right palm held upright tilted outward (1-handed).",
        "Parting wave gesture held stationary.",
    ),
    "YES": (
        "ISL 'YES': Closed right fist held at chin/shoulder height (1-handed).",
        "Nodding fist posture representing affirmative agreement.",
    ),
    "NO": (
        "ISL 'NO': Index and middle fingers extended together snapping onto thumb tip (1-handed).",
        "Clear negative response posture.",
    ),
    "OK": (
        "ISL 'OK': Thumb and index tips joined in a ring, other 3 fingers raised (1-handed).",
        "Universal approval and acknowledgment posture.",
    ),
    "GOOD": (
        "ISL 'GOOD': Single right thumb pointing straight up (thumbs up) (1-handed).",
        "Positive approval posture.",
    ),
    "BAD": (
        "ISL 'BAD': Single right pinky finger extended upright, others folded (1-handed).",
        "Disapproval or negative evaluation posture.",
    ),
    "HELP": (
        "ISL 'HELP': Closed right fist with thumb up resting on open flat left palm (2-handed).",
        "Assistance and support gesture held firmly at chest level.",
    ),
    "EMERGENCY": (
        "ISL 'EMERGENCY': Right hand forming 'E' shape held firmly upright at chest (1-handed).",
        "Urgent alert gesture calling for immediate intervention.",
    ),
    "DOCTOR": (
        "ISL 'DOCTOR': Three fingers of right hand resting on left inner wrist pulse (2-handed).",
        "Medical posture checking pulse on wrist.",
    ),
    "MEDICINE": (
        "ISL 'MEDICINE': Right middle fingertip placed on center of open left palm (2-handed).",
        "Pharmaceutical and treatment posture.",
    ),
    "HOSPITAL": (
        "ISL 'HOSPITAL': Right index & middle fingers form cross on left shoulder (2-handed).",
        "Sign of the Red Cross medical facility.",
    ),
    "WATER": (
        "ISL 'WATER': Right 'W' shape (3 fingers extended) with index touching bottom lip (1-handed).",
        "Hydration sign held against lower lip.",
    ),
    "FOOD": (
        "ISL 'FOOD': Flattened right hand with fingertips pinched touching lips (1-handed).",
        "Eating and meal posture held at mouth.",
    ),
    "TEA": (
        "ISL 'TEA': Right hand 'F' pinch holding imaginary cup handle over left flat palm (2-handed).",
        "Saucer and teacup posture.",
    ),
    "COFFEE": (
        "ISL 'COFFEE': Right fist resting directly on top of left fist (2-handed).",
        "Coffee grinder grinding posture held still.",
    ),
    "MILK": (
        "ISL 'MILK': Closed right fist held at chin level in milking grip pose (1-handed).",
        "Dairy nourishment sign.",
    ),
    "HOUSE": (
        "ISL 'HOUSE': Both open flat hands with fingertips touching at an apex to form a roof (2-handed).",
        "Shelter and dwelling roof posture.",
    ),
    "HOME": (
        "ISL 'HOME': Right flattened hand touching cheek then held at jawline (1-handed).",
        "Personal residence and hearth sign.",
    ),
    "BOOK": (
        "ISL 'BOOK': Both open flat palms facing up with pinky edges touching like open book (2-handed).",
        "Reading and literature posture.",
    ),
    "PHONE": (
        "ISL 'PHONE': Right 'Y' shape (thumb & pinky) held with thumb to ear, pinky to mouth (1-handed).",
        "Telephone handset posture.",
    ),
    "TIME": (
        "ISL 'TIME': Right index finger pointing at left wrist watch location (2-handed).",
        "Temporal and clock reference posture.",
    ),
    "STOP": (
        "ISL 'STOP': Open flat right palm facing forward, fingers upright (1-handed).",
        "Halt and pause posture held firmly.",
    ),
}


def get_sign_guide(sign: str) -> Tuple[str, str]:
    """Retrieve two-line posture guidance for any letter or word sign."""
    s = sign.upper()
    guide: Tuple[str, str]
    if s in ISL_GUIDES:
        guide = ISL_GUIDES[s]
    elif s in ISL_WORD_GUIDES:
        guide = ISL_WORD_GUIDES[s]
    else:
        hands = get_expected_hands(s)
        hand_str = "1 hand" if hands == 1 else "2 hands"
        clean_name = s.replace("_", " ").title()
        guide = (
            f"ISL '{clean_name}': Hold standard static sign posture ({hand_str}).",
            "Keep fingers and wrists steady and motionless to record clean 164D features.",
        )

    try:
        from signora.isl.spatial import SIGN_ANCHOR_MAP

        anchor = SIGN_ANCHOR_MAP.get(s)
        if anchor:
            return (guide[0], f"Body Anchor: [{anchor}] | {guide[1]}")
    except Exception:
        pass

    return guide


def get_existing_counts(data_dir: Path = DATA_DIR) -> Dict[str, int]:
    """Scan data directory for existing recorded samples per sign."""
    counts: Dict[str, int] = {}
    if not data_dir.exists():
        return counts
    for sign in ISL_ALL_SIGNS:
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
    enable_body: bool = False,
    active_anchor: str = "OPEN_SPACE",
    target_anchor: Optional[str] = None,
) -> None:
    """Render interactive Glassmorphic Studio HUD."""
    h, w = frame.shape[:2]
    counts = counts_dict or {}

    # 1. Top Ribbon: Alphabet Carousel
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 105), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.88, frame, 0.12, 0, frame)

    # Title & Mode
    title = (
        "SPATIAL ISL STUDIO [220D]: 26 LETTERS + 200 STATIC WORDS + BODY POSE"
        if enable_body
        else "ISL STUDIO: SAMPLE COLLECTOR & TRAINER"
    )
    cv2.putText(
        frame,
        title,
        (16, 26),
        cv2.FONT_HERSHEY_DUPLEX,
        0.54 if enable_body else 0.65,
        (6, 182, 212) if enable_body else (255, 255, 255),
        1,
        cv2.LINE_AA,
    )

    # Alphabet & Word Strip: Show a sliding window of ~9 signs centered around current_sign
    window_size = 9
    start_i = max(0, min(len(ISL_ALL_SIGNS) - window_size, sign_idx - window_size // 2))
    end_i = min(len(ISL_ALL_SIGNS), start_i + window_size)

    chip_x = 16
    for i in range(start_i, end_i):
        let = ISL_ALL_SIGNS[i]
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

        chip_w = 46 if len(let) <= 2 else max(68, len(let) * 8 + 12)
        cv2.rectangle(frame, (chip_x, 38), (chip_x + chip_w, 68), bg_col, -1)
        if is_cur:
            cv2.rectangle(frame, (chip_x - 2, 36), (chip_x + chip_w + 2, 70), (6, 182, 212), 2)  # Cyan border

        label = f"{let}:{cnt}" if cnt < 1000 else f"{let}:999+"
        cv2.putText(
            frame,
            label,
            (chip_x + 4, 58),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.36,
            txt_col,
            1,
            cv2.LINE_AA,
        )
        chip_x += chip_w + 6

    # Top right: Total Signs Collected Counter
    total_ready = sum(1 for v in counts.values() if v >= target_count)
    summary_text = f"Ready: {total_ready}/{len(ISL_ALL_SIGNS)} Signs"
    cv2.putText(
        frame,
        summary_text,
        (w - 240, 26),
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

    # Batch status text with body anchor placement
    anchor_info = ""
    if enable_body:
        if target_anchor:
            is_match = (active_anchor == target_anchor)
            match_sym = "✓ MATCH" if is_match else "⚠ MOVE"
            anchor_info = f"| Target: [{target_anchor}] | Anchor: [{active_anchor}] {match_sym} "
        else:
            anchor_info = f"| Anchor: [{active_anchor}] "

    batch_info = (
        f"Sign: [{current_sign}] {anchor_info}| Batch: {collected_in_batch}/{target_count} ({int(bar_pct*100)}%) "
        f"| Saved: {existing_count} | Hands: {hands_detected}"
    )
    cv2.putText(
        frame,
        batch_info,
        (16, 92),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.44 if enable_body else 0.48,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )

    # 3. Center State Alerts
    if state == "COUNTDOWN":
        cd_int = max(1, int(np.ceil(countdown_val)))
        card_w, card_h = 440, 150 if (enable_body and target_anchor) else 140
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2
        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), (245, 158, 11), 2)

        # Countdown number
        cv2.putText(
            frame,
            f"STARTING IN: {cd_int}",
            (cx + 70, cy + 50),
            cv2.FONT_HERSHEY_DUPLEX,
            1.1,
            (245, 158, 11),
            2,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            f"Hold posture for ISL '{current_sign}'",
            (cx + 40, cy + 95),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.56,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )
        if enable_body and target_anchor:
            cv2.putText(
                frame,
                f"Target Body Placement: [{target_anchor}]",
                (cx + 40, cy + 128),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.50,
                (6, 182, 212),
                1,
                cv2.LINE_AA,
            )

    elif state == "COLLECTING":
        # Recording pill top-center
        pill_w, pill_h = 420 if enable_body else 360, 48
        px, py = w // 2 - pill_w // 2, 120
        overlay = frame.copy()
        cv2.rectangle(overlay, (px, py), (px + pill_w, py + pill_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
        cv2.rectangle(frame, (px, py), (px + pill_w, py + pill_h), (239, 68, 68), 2)

        # Flashing red dot
        if int(time.time() * 4) % 2 == 0:
            cv2.circle(frame, (px + 24, py + 24), 8, (239, 68, 68), -1)
        rec_title = (
            f"RECORDING 220D [{active_anchor}]: {collected_in_batch}/{target_count}"
            if enable_body
            else f"RECORDING: {collected_in_batch}/{target_count} SAMPLES"
        )
        cv2.putText(
            frame,
            rec_title,
            (px + 45, py + 31),
            cv2.FONT_HERSHEY_DUPLEX,
            0.48 if enable_body else 0.55,
            (255, 255, 255),
            1,
            cv2.LINE_AA,
        )

    elif state == "TRAINING":
        card_w, card_h = 580, 160
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2
        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.92, frame, 0.08, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), (6, 182, 212), 2)

        spinner_chars = ["|", "/", "-", "\\"]
        spin = spinner_chars[int(time.time() * 8) % len(spinner_chars)]

        cv2.putText(
            frame,
            f"TRAINING ISL NEURAL NETWORK... [{spin}]",
            (cx + 35, cy + 45),
            cv2.FONT_HERSHEY_DUPLEX,
            0.75,
            (6, 182, 212),
            2,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            "Optimizing 164D MLP weights on all data/isl/ classes",
            (cx + 35, cy + 85),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.50,
            (226, 232, 240),
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            status_msg or "Preserving baseline exemplars & training PyTorch model...",
            (cx + 35, cy + 120),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.46,
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
    guide = get_sign_guide(current_sign.upper())
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
        "[SPACE] Record 300   "
        "[<-/->] Change Sign   "
        "[TAB] Jump +10   "
        "[a-z] Jump to Sign   "
        "[Shift+T] Train Model   "
        "[P] Pause   "
        "[Q] Quit"
    )
    cv2.putText(
        frame,
        hotkeys,
        (16, h - 17),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.42,
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
    data_dir: Optional[Path] = None,
    headless: bool = False,
    use_fake: bool = False,
    auto_train: bool = False,
    enable_body: bool = False,
    auto_start: bool = False,
) -> int:
    """Main execution loop for interactive ISL Studio (supporting hands & body pose)."""
    target_data_dir = (
        data_dir if data_dir is not None else (DATA_SPATIAL_DIR if enable_body else DATA_DIR)
    )
    target_data_dir.mkdir(parents=True, exist_ok=True)

    # If requested direct training
    if auto_train:
        print("[Studio] Directly invoking ISL model training on all data...")
        from signora.isl.train import train_isl_model

        res = train_isl_model(data_dir=target_data_dir)
        return 0 if res.get("success") else 1

    sign_name = sign.upper() if sign else "A"
    if sign_name not in ISL_ALL_SIGNS:
        sign_name = "A"
    sign_idx = ISL_ALL_SIGNS.index(sign_name)

    mode_name = (
        "Spatial ISL Studio (220D Hand + Body Position)"
        if enable_body
        else "ISL Collection Studio (164D Hand Gestures)"
    )
    print("=" * 65)
    print(f"  SIGNORA -- {mode_name}")
    print("=" * 65)
    print(f"  Target Samples Per Sign : {count}")
    print(f"  Start Countdown Timer   : {timer_seconds}s")
    print(f"  Storage Directory       : {target_data_dir}")
    print(f"  Total Sign Vocabulary   : {len(ISL_ALL_SIGNS)} Signs (A-Z + 200 Static Daily Words)")
    if enable_body:
        print("  Spatial Body Tracking   : ENABLED (33-point Pose + 2-Hand Articulation)")
    print("  Controls:")
    print("    [SPACE]   : Start 5-second countdown & auto-collect 300 samples")
    print("    [<- / ->] : Previous / Next sign")
    print("    [TAB]     : Jump forward 10 signs")
    print("    [a - z]   : Jump to signs starting with letter")
    print("    [Shift+T] : Train neural network on all collected data (background worker)")
    print("    [P]       : Pause / Resume")
    print("    [Q]       : Quit Studio")
    print("=" * 65)

    if enable_body:
        from signora.core.spatial import SpatialPipeline, draw_cyber_spatial
        from signora.isl.spatial import SIGN_ANCHOR_MAP, detect_body_anchor, extract_spatial_features

        pipe = SpatialPipeline(source=source, mirror=True, detect_every=1, use_fake=use_fake)
    else:
        pipe = HandPipeline(source=source, num_hands=2, mirror=True, use_fake=use_fake)

    if not pipe.started.wait(timeout=5) or pipe.error:
        print("[Error] Could not initialize camera.", file=sys.stderr)
        return 1

    win_name = "Signora Spatial Body ISL Studio" if enable_body else "Signora ISL Studio"
    if not headless:
        cv2.namedWindow(win_name, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(win_name, 960, 720)

    state = "COLLECTING" if auto_start else "IDLE"  # 'IDLE', 'COUNTDOWN', 'COLLECTING', 'TRAINING', 'SUCCESS'
    countdown_start = 0.0
    collected_samples: List[np.ndarray] = []
    status_msg = ""
    status_msg_expiry = 0.0
    last_sample_time = 0.0
    is_paused = False

    counts = get_existing_counts(target_data_dir)

    train_worker_info: Dict[str, Any] = {}
    train_thread: Optional[threading.Thread] = None

    def _train_worker():
        try:
            from signora.isl.train import train_isl_model

            res = train_isl_model(data_dir=target_data_dir)
            train_worker_info["res"] = res
            train_worker_info["success"] = res.get("success", False)
        except Exception as exc:
            train_worker_info["error"] = str(exc)
            train_worker_info["success"] = False
        finally:
            train_worker_info["done"] = True

    try:
        while True:
            pose_lms = None
            # Training background check
            if state == "TRAINING":
                time.sleep(0.02)
                if train_worker_info.get("done"):
                    if train_worker_info.get("success"):
                        res = train_worker_info.get("res", {})
                        acc = res.get("val_acc", 0.0) * 100
                        status_msg = f"✓ Training Complete! Val Acc: {acc:.1f}% | Checkpoint Updated"
                        counts = get_existing_counts(target_data_dir)
                        state = "SUCCESS"
                    else:
                        err = train_worker_info.get("error") or train_worker_info.get("res", {}).get(
                            "error", "Unknown error"
                        )
                        status_msg = f"Training Failed: {err}"
                        state = "IDLE"
                    status_msg_expiry = time.time() + 4.5
                    train_thread = None

                # Keep reading camera or reuse last frame so display stays live
                if enable_body:
                    frame, landmarks, handedness, pose_lms = pipe.read(timeout=0.05)
                else:
                    frame, landmarks, handedness = pipe.read(timeout=0.05)
                if frame is None:
                    continue
                num_hands = len(landmarks) if isinstance(landmarks, list) else (1 if landmarks else 0)
            else:
                if enable_body:
                    frame, landmarks, handedness, pose_lms = pipe.read(timeout=1.0)
                else:
                    frame, landmarks, handedness = pipe.read(timeout=1.0)
                if frame is None:
                    continue

                current_sign = ISL_ALL_SIGNS[sign_idx]
                num_hands = len(landmarks) if isinstance(landmarks, list) else (1 if landmarks else 0)
                lms_list = (
                    landmarks if isinstance(landmarks, list) else ([landmarks] if landmarks else [])
                )
                hnds_list = (
                    handedness
                    if isinstance(handedness, list)
                    else ([handedness] if handedness else [])
                )

                active_anchor_zone = "OPEN_SPACE"
                target_anchor = None
                target_anchor_pt = None
                is_anchor_matched = False
                if enable_body:
                    from signora.isl.spatial import (
                        SIGN_ANCHOR_MAP,
                        detect_body_anchor,
                        extract_spatial_features,
                        get_body_anchor_pt,
                    )

                    target_anchor = SIGN_ANCHOR_MAP.get(current_sign.upper())
                    if lms_list:
                        active_anchor_zone, _, _ = detect_body_anchor(
                            lms_list[0], pose_lms, w=frame.shape[1], h=frame.shape[0]
                        )
                    if target_anchor:
                        target_anchor_pt = get_body_anchor_pt(
                            target_anchor, pose_lms, w=frame.shape[1], h=frame.shape[0]
                        )
                        is_anchor_matched = (active_anchor_zone == target_anchor)

                    draw_cyber_spatial(
                        frame=frame,
                        hand_lms_list=lms_list,
                        handedness_list=hnds_list,
                        pose_landmarks=pose_lms,
                        target_anchor_name=target_anchor,
                        target_anchor_pt=target_anchor_pt,
                        is_matched=is_anchor_matched,
                    )
                else:
                    draw_skeleton(frame, lms_list, hnds_list)

            # Key handling (always call cv2.waitKey to keep window event loop active)
            key = cv2.waitKey(20 if state == "TRAINING" else 1) & 0xFF if not headless else -1
            if key in (ord("q"), ord("Q"), 27):
                print("[Studio] Exiting ISL Studio.")
                break
            elif state != "TRAINING":
                if key in (ord("p"), ord("P")):
                    is_paused = not is_paused
                elif key in (ord("["), 81):  # Previous sign / Left Arrow
                    sign_idx = (sign_idx - 1) % len(ISL_ALL_SIGNS)
                    state = "IDLE"
                    collected_samples = []
                elif key in (ord("]"), 83):  # Next sign / Right Arrow
                    sign_idx = (sign_idx + 1) % len(ISL_ALL_SIGNS)
                    state = "IDLE"
                    collected_samples = []
                elif key == 9:  # TAB: Jump forward 10 signs
                    sign_idx = (sign_idx + 10) % len(ISL_ALL_SIGNS)
                    state = "IDLE"
                    collected_samples = []
                elif key in (ord("T"), 84, 20):  # Shift + T (ASCII 84) or Ctrl+T (20)
                    state = "TRAINING"
                    status_msg = "Initiating background neural training..."
                    train_worker_info.clear()
                    train_thread = threading.Thread(target=_train_worker, daemon=True)
                    train_thread.start()
                    print("\n[Studio] Initiated neural network training in background worker...")
                elif (ord("a") <= key <= ord("z")) or (ord("A") <= key <= ord("Z")):
                    if key == ord("t"):
                        status_msg = "ℹ Press [Shift+T] to Train Neural Model"
                        status_msg_expiry = time.time() + 2.5
                    else:
                        char = chr(key).upper()
                        for idx, s in enumerate(ISL_ALL_SIGNS):
                            if s == char or s.startswith(char):
                                sign_idx = idx
                                state = "IDLE"
                                collected_samples = []
                                break
                elif key == 32:  # SPACE BAR
                    if state in ("IDLE", "SUCCESS"):
                        state = "COUNTDOWN"
                        countdown_start = time.time()
                        collected_samples = []

            current_sign = ISL_ALL_SIGNS[sign_idx]

            # State Logic
            countdown_val = 0.0
            if state == "COUNTDOWN":
                elapsed = time.time() - countdown_start
                countdown_val = max(0.0, timer_seconds - elapsed)
                if countdown_val <= 0.0:
                    state = "COLLECTING"
                    collected_samples = []

            elif state == "COLLECTING":
                expected_hands = get_expected_hands(current_sign)
                if num_hands >= expected_hands and not is_paused:
                    now = time.time()
                    if now - last_sample_time >= 0.028:  # ~35 FPS burst capture
                        if enable_body:
                            feats = extract_spatial_features(lms_list, hnds_list, pose_lms)
                        else:
                            feats = extract_features(lms_list, hnds_list)
                        if np.all(np.isfinite(feats)):
                            collected_samples.append(feats)
                            last_sample_time = now

                # Check if batch reached target
                if len(collected_samples) >= count:
                    # Save to file
                    out_path = target_data_dir / f"{current_sign}.npy"
                    prev_samples: List[np.ndarray] = []
                    if out_path.exists():
                        try:
                            arr = np.load(out_path)
                            prev_samples = list(arr)
                        except Exception:
                            pass
                    combined = (
                        np.vstack(prev_samples + collected_samples)
                        if prev_samples
                        else np.vstack(collected_samples)
                    )
                    np.save(out_path, combined.astype(np.float32))

                    counts[current_sign] = len(combined)
                    dim_str = "220D Hand+Body" if enable_body else "164D Hand"
                    status_msg = (
                        f"🎉 Saved {count} {dim_str} samples for '{current_sign}'! (Total: {len(combined)})"
                    )
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
                enable_body=enable_body,
                active_anchor=active_anchor_zone if enable_body else "OPEN_SPACE",
                target_anchor=target_anchor if enable_body else None,
            )

            if not headless:
                cv2.imshow(win_name, frame)

            # In testing/fake camera mode with limited cycles
            if use_fake and headless and (not auto_start or state == "SUCCESS"):
                break

    finally:
        pipe.close()
        if not headless:
            cv2.destroyAllWindows()

    return 0

"""Professional broadcast overlay rendering for video calls (Zoom, Google Meet, Teams).

Renders clean lower-third caption cards, active gloss stream chips, speaking indicators,
and optional minimal cyber-hand skeletal highlights suited for live webcam streaming.
"""

import time
from typing import List, Optional, Tuple

import cv2
import numpy as np

from signora.core.hud import (
    COLOR_BG_GLASS,
    COLOR_BORDER_GLASS,
    COLOR_CYAN_NEON,
    COLOR_EMERALD,
    COLOR_KEYCAP_BG,
    COLOR_TEXT_DIM,
    COLOR_WHITE,
    draw_glass_card,
    draw_rounded_rectangle,
)


def render_meeting_overlay(
    frame: np.ndarray,
    candidate: str,
    dwell_ratio: float,
    in_cooldown: bool,
    pending_glosses: List[str],
    synthesized_sentence: str,
    tts_speaking: bool,
    last_spoken: str,
    last_spoken_time: float,
    clean_mode: bool = False,
    mode_label: str = "ISL DUAL-HAND",
    fps: float = 0.0,
) -> np.ndarray:
    """Render a broadcast-ready lower-third overlay on the video frame.

    Args:
        frame: BGR video frame from webcam.
        candidate: Current candidate sign being held.
        dwell_ratio: Progress of sign dwell (0.0 to 1.0).
        in_cooldown: True if candidate just confirmed and is in cooldown.
        pending_glosses: List of gloss tokens collected in current sentence buffer.
        synthesized_sentence: Most recently synthesized fluent English sentence.
        tts_speaking: True if TTS is actively speaking audio.
        last_spoken: Text of the most recent spoken sentence.
        last_spoken_time: Timestamp when last_spoken was triggered.
        clean_mode: If True, renders minimal lower-third without technical cards.
        mode_label: Text badge indicating active mode (e.g. "ISL DUAL-HAND" or "ASL").
        fps: Live frames per second.

    Returns:
        Frame with rendered meeting broadcast overlays.
    """
    h, w = frame.shape[:2]
    now = time.time()

    # ─── 1. Top Status Badge (Subtle Pill in Top-Left) ────────────────────────
    badge_w, badge_h = 240, 36
    bx, by = 20, 20
    draw_glass_card(frame, bx, by, badge_w, badge_h, alpha=0.80, radius=10)

    # Glowing Live Dot
    dot_color = COLOR_EMERALD if candidate or pending_glosses else COLOR_CYAN_NEON
    cv2.circle(frame, (bx + 18, by + 18), 5, dot_color, -1, cv2.LINE_AA)

    cv2.putText(
        frame,
        "SIGNORA STREAM",
        (bx + 32, by + 18),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.38,
        COLOR_WHITE,
        1,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        f"{mode_label} • {int(fps)} FPS" if fps > 0 else mode_label,
        (bx + 32, by + 30),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.30,
        COLOR_CYAN_NEON,
        1,
        cv2.LINE_AA,
    )

    # ─── 2. Top-Right Active Dwell Pill (Only if not clean mode or holding sign)
    cand = candidate.strip().upper() if candidate else ""
    if cand and cand != "NONE":
        cw, ch = 200, 42
        cx = w - cw - 20
        cy = 20
        draw_glass_card(
            frame,
            cx,
            cy,
            cw,
            ch,
            border_color=COLOR_EMERALD if in_cooldown else COLOR_CYAN_NEON,
            alpha=0.88,
            radius=10,
        )

        # Cand Label
        disp_cand = "[SPACE]" if cand == "SPACE" else cand
        cv2.putText(
            frame,
            disp_cand[:12],
            (cx + 14, cy + 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            COLOR_WHITE,
            2,
            cv2.LINE_AA,
        )

        # Compact Progress Bar
        bar_x, bar_y, bar_w, bar_h = cx + 110, cy + 18, 76, 8
        draw_rounded_rectangle(
            frame,
            (bar_x, bar_y),
            (bar_x + bar_w, bar_y + bar_h),
            COLOR_KEYCAP_BG,
            radius=3,
            thickness=-1,
        )
        fill_w = int(bar_w * (1.0 if in_cooldown else max(0.0, min(1.0, dwell_ratio))))
        if fill_w > 2:
            fill_col = COLOR_EMERALD if in_cooldown else COLOR_CYAN_NEON
            draw_rounded_rectangle(
                frame,
                (bar_x, bar_y),
                (bar_x + fill_w, bar_y + bar_h),
                fill_col,
                radius=3,
                thickness=-1,
            )

    # ─── 3. Professional Lower-Third Broadcast Subtitle Banner ────────────────
    # Card sits at bottom edge with high legibility
    is_speaking = tts_speaking or (now - last_spoken_time < 3.5 and bool(last_spoken))
    display_sentence = last_spoken if is_speaking else synthesized_sentence

    card_h = 100 if pending_glosses else 78
    card_w = min(w - 40, 960)
    lx = (w - card_w) // 2
    ly = h - card_h - 18

    # Background Glass Plate
    draw_glass_card(
        frame,
        lx,
        ly,
        card_w,
        card_h,
        border_color=COLOR_EMERALD if is_speaking else COLOR_BORDER_GLASS,
        alpha=0.90,
        radius=14,
    )

    # Speaking or Caption Header
    if is_speaking:
        cv2.circle(frame, (lx + 20, ly + 22), 5, COLOR_EMERALD, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "CAPTIONS (LIVE AUDIO):",
            (lx + 34, ly + 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.40,
            COLOR_EMERALD,
            1,
            cv2.LINE_AA,
        )
    else:
        cv2.circle(frame, (lx + 20, ly + 22), 4, COLOR_CYAN_NEON, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "REAL-TIME CAPTIONING:",
            (lx + 34, ly + 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            COLOR_CYAN_NEON,
            1,
            cv2.LINE_AA,
        )

    # Main Sentence Display (with elegant drop shadow for video conference readability)
    sentence_text = display_sentence if display_sentence else "Waiting for signed speech..."
    text_color = COLOR_WHITE if display_sentence else COLOR_TEXT_DIM
    font_scale = 0.65 if len(sentence_text) <= 50 else 0.52
    text_y = ly + 54

    # Drop shadow
    cv2.putText(
        frame,
        sentence_text,
        (lx + 21, text_y + 1),
        cv2.FONT_HERSHEY_SIMPLEX,
        font_scale,
        (0, 0, 0),
        3,
        cv2.LINE_AA,
    )
    # Main text
    cv2.putText(
        frame,
        sentence_text,
        (lx + 20, text_y),
        cv2.FONT_HERSHEY_SIMPLEX,
        font_scale,
        text_color,
        2,
        cv2.LINE_AA,
    )

    # Gloss Token Stream Row (if any pending glosses exist)
    if pending_glosses:
        gx = lx + 20
        gy = ly + 82
        cv2.putText(
            frame,
            "Tokens:",
            (gx, gy),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.34,
            COLOR_TEXT_DIM,
            1,
            cv2.LINE_AA,
        )
        gx += 56
        for g in pending_glosses[-6:]:
            label = g.upper()
            gw = max(40, len(label) * 8 + 14)
            if gx + gw < lx + card_w - 20:
                draw_rounded_rectangle(
                    frame,
                    (gx, gy - 12),
                    (gx + gw, gy + 5),
                    COLOR_KEYCAP_BG,
                    radius=4,
                    thickness=-1,
                )
                cv2.putText(
                    frame,
                    label,
                    (gx + 7, gy),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.34,
                    COLOR_CYAN_NEON,
                    1,
                    cv2.LINE_AA,
                )
                gx += gw + 8

    return frame

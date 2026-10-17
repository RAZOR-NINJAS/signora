"""Modern, glassmorphic HUD rendering and glowing cybernetic hand landmarks for OpenCV.

Provides anti-aliased floating glass cards, dual-hand color differentiation,
smooth dwell progress animations, audio speech indicators, and sleek keyboard toolbars.
"""

import time
from typing import Any, List, Optional, Tuple

import cv2
import numpy as np

# ==============================================================================
# Cyberpunk & Glassmorphic Color Palette (BGR for OpenCV)
# ==============================================================================
COLOR_BG_GLASS = (30, 20, 15)  # Slate 950 deep navy (#0f172a)
COLOR_BORDER_GLASS = (85, 65, 51)  # Slate 700 (#334155)
COLOR_CYAN_NEON = (248, 189, 56)  # Sky 400 (#38bdf8)
COLOR_CYAN_GLOW = (212, 182, 6)  # Cyan 500 (#06b6d4)
COLOR_MAGENTA_NEON = (236, 72, 153)  # Pink/Magenta (#ec4899)
COLOR_MAGENTA_GLOW = (192, 132, 252)  # Purple 400 (#c084fc)
COLOR_EMERALD = (153, 211, 52)  # Emerald 400 (#34d399)
COLOR_AMBER = (36, 191, 251)  # Amber 400 (#fbbf24)
COLOR_WHITE = (255, 255, 255)
COLOR_TEXT_DIM = (184, 163, 148)  # Slate 400 (#94a3b8)
COLOR_KEYCAP_BG = (51, 35, 25)  # Slate 800 (#1e293b)
COLOR_KEYCAP_BORDER = (100, 80, 60)  # Slate 600 (#475569)

# Standard MediaPipe 21-landmark 3D hand topology connections
HAND_CONNECTIONS = (
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 4),  # Thumb
    (0, 5),
    (5, 6),
    (6, 7),
    (7, 8),  # Index
    (5, 9),
    (9, 10),
    (10, 11),
    (11, 12),  # Middle
    (9, 13),
    (13, 14),
    (14, 15),
    (15, 16),  # Ring
    (13, 17),
    (17, 18),
    (18, 19),
    (19, 20),  # Pinky
    (0, 17),  # Palm base
)

FINGERTIP_INDICES = {4, 8, 12, 16, 20}


def draw_rounded_rectangle(
    img: np.ndarray,
    pt1: Tuple[int, int],
    pt2: Tuple[int, int],
    color: Tuple[int, int, int],
    radius: int = 12,
    thickness: int = -1,
    alpha: float = 1.0,
) -> None:
    """Draw a smooth anti-aliased rounded rectangle with optional transparency."""
    x1, y1 = min(pt1[0], pt2[0]), min(pt1[1], pt2[1])
    x2, y2 = max(pt1[0], pt2[0]), max(pt1[1], pt2[1])

    # Clamp bounds to image dimensions
    h, w = img.shape[:2]
    if x1 >= w or y1 >= h or x2 <= 0 or y2 <= 0:
        return
    x1, y1 = max(0, x1), max(0, y1)
    x2, y2 = min(w, x2), min(h, y2)

    width = x2 - x1
    height = y2 - y1
    if width <= 0 or height <= 0:
        return

    r = min(radius, width // 2, height // 2)
    if r < 1:
        r = 1

    roi = img[y1:y2, x1:x2]

    if alpha < 1.0 and thickness < 0:
        sub = roi.copy()
        cv2.rectangle(sub, (r, 0), (width - r, height), color, -1)
        cv2.rectangle(sub, (0, r), (width, height - r), color, -1)
        cv2.circle(sub, (r, r), r, color, -1, cv2.LINE_AA)
        cv2.circle(sub, (width - r, r), r, color, -1, cv2.LINE_AA)
        cv2.circle(sub, (r, height - r), r, color, -1, cv2.LINE_AA)
        cv2.circle(sub, (width - r, height - r), r, color, -1, cv2.LINE_AA)
        cv2.addWeighted(sub, alpha, roi, 1.0 - alpha, 0, roi)
    elif thickness < 0:
        cv2.rectangle(roi, (r, 0), (width - r, height), color, -1)
        cv2.rectangle(roi, (0, r), (width, height - r), color, -1)
        cv2.circle(roi, (r, r), r, color, -1, cv2.LINE_AA)
        cv2.circle(roi, (width - r, r), r, color, -1, cv2.LINE_AA)
        cv2.circle(roi, (r, height - r), r, color, -1, cv2.LINE_AA)
        cv2.circle(roi, (width - r, height - r), r, color, -1, cv2.LINE_AA)
    else:
        cv2.line(roi, (r, 0), (width - r, 0), color, thickness, cv2.LINE_AA)
        cv2.line(roi, (r, height - 1), (width - r, height - 1), color, thickness, cv2.LINE_AA)
        cv2.line(roi, (0, r), (0, height - r), color, thickness, cv2.LINE_AA)
        cv2.line(roi, (width - 1, r), (width - 1, height - r), color, thickness, cv2.LINE_AA)
        cv2.ellipse(roi, (r, r), (r, r), 180, 0, 90, color, thickness, cv2.LINE_AA)
        cv2.ellipse(roi, (width - r - 1, r), (r, r), 270, 0, 90, color, thickness, cv2.LINE_AA)
        cv2.ellipse(
            roi, (width - r - 1, height - r - 1), (r, r), 0, 0, 90, color, thickness, cv2.LINE_AA
        )
        cv2.ellipse(roi, (r, height - r - 1), (r, r), 90, 0, 90, color, thickness, cv2.LINE_AA)


def draw_glass_card(
    img: np.ndarray,
    x: int,
    y: int,
    w: int,
    h: int,
    bg_color: Tuple[int, int, int] = COLOR_BG_GLASS,
    border_color: Tuple[int, int, int] = COLOR_BORDER_GLASS,
    alpha: float = 0.82,
    radius: int = 14,
) -> None:
    """Draw a frosted floating glass card with smooth drop outline."""
    draw_rounded_rectangle(
        img, (x, y), (x + w, y + h), bg_color, radius=radius, thickness=-1, alpha=alpha
    )
    draw_rounded_rectangle(
        img, (x, y), (x + w, y + h), border_color, radius=radius, thickness=1, alpha=1.0
    )


def draw_cyber_hands(
    img: np.ndarray,
    landmarks_list: Any,
    handedness_list: Any = None,
) -> np.ndarray:
    """Draw glowing cybernetic skeleton hand landmarks with dual-hand color distinction."""
    if not landmarks_list:
        return img

    h, w = img.shape[:2]

    # Normalize structure to list of hands
    if isinstance(landmarks_list, (list, tuple)) and landmarks_list:
        if isinstance(landmarks_list[0], (list, tuple)):
            hands = landmarks_list
            hands_meta = handedness_list if isinstance(handedness_list, (list, tuple)) else []
        else:
            hands = [landmarks_list]
            hands_meta = [handedness_list] if handedness_list else []
    else:
        hands = [landmarks_list]
        hands_meta = []

    for idx, lms in enumerate(hands):
        # Determine hand label & themed colors
        label = "Right"
        if idx < len(hands_meta) and hands_meta[idx]:
            label = str(hands_meta[idx])
        elif idx == 0 and len(hands) > 1:
            label = "Left"

        if "left" in label.lower():
            bone_color = COLOR_CYAN_NEON
            glow_color = COLOR_CYAN_GLOW
            tag_text = "LEFT"
        else:
            bone_color = COLOR_MAGENTA_NEON
            glow_color = COLOR_MAGENTA_GLOW
            tag_text = "RIGHT"

        coords = [(int(lm.x * w), int(lm.y * h)) for lm in lms]

        # 1. Outer bone glow lines (thicker)
        for a, b in HAND_CONNECTIONS:
            cv2.line(img, coords[a], coords[b], glow_color, 4, cv2.LINE_AA)

        # 2. Inner crisp bone lines
        for a, b in HAND_CONNECTIONS:
            cv2.line(img, coords[a], coords[b], COLOR_WHITE, 1, cv2.LINE_AA)

        # 3. Draw joint halos and centers
        for i, pt in enumerate(coords):
            if i in FINGERTIP_INDICES:
                # Glowing fingertip halo
                cv2.circle(img, pt, 7, glow_color, 2, cv2.LINE_AA)
                cv2.circle(img, pt, 4, bone_color, -1, cv2.LINE_AA)
                cv2.circle(img, pt, 2, COLOR_WHITE, -1, cv2.LINE_AA)
            else:
                cv2.circle(img, pt, 4, bone_color, -1, cv2.LINE_AA)
                cv2.circle(img, pt, 1, COLOR_WHITE, -1, cv2.LINE_AA)

        # 4. Wrist label badge
        wrist = coords[0]
        bx, by = max(10, wrist[0] - 28), max(30, wrist[1] + 20)
        draw_rounded_rectangle(
            img, (bx, by), (bx + 56, by + 18), COLOR_BG_GLASS, radius=6, thickness=-1, alpha=0.85
        )
        draw_rounded_rectangle(img, (bx, by), (bx + 56, by + 18), bone_color, radius=6, thickness=1)
        cv2.putText(
            img,
            tag_text,
            (bx + 8, by + 13),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            bone_color,
            1,
            cv2.LINE_AA,
        )

    return img


def draw_keycap(
    img: np.ndarray,
    x: int,
    y: int,
    key_char: str,
    action_text: str,
) -> int:
    """Draw stylish tactile keycap badge and action label. Returns total width."""
    # Keycap badge
    kw = 24 if len(key_char) <= 2 else 32
    kh = 20
    draw_rounded_rectangle(
        img, (x, y), (x + kw, y + kh), COLOR_KEYCAP_BG, radius=5, thickness=-1, alpha=0.9
    )
    draw_rounded_rectangle(
        img, (x, y), (x + kw, y + kh), COLOR_KEYCAP_BORDER, radius=5, thickness=1
    )

    tx = x + (7 if len(key_char) == 1 else 3)
    cv2.putText(
        img, key_char, (tx, y + 14), cv2.FONT_HERSHEY_SIMPLEX, 0.42, COLOR_CYAN_NEON, 1, cv2.LINE_AA
    )

    # Action label
    cv2.putText(
        img,
        action_text,
        (x + kw + 6, y + 14),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.40,
        COLOR_TEXT_DIM,
        1,
        cv2.LINE_AA,
    )

    label_w = int(len(action_text) * 7.5)
    return kw + 6 + label_w + 14


_CANVAS_CACHE: dict = {}


def prepare_window_canvas(
    frame: np.ndarray,
    window_name: Optional[str] = None,
    bg_color: Tuple[int, int, int] = COLOR_BG_GLASS,
) -> np.ndarray:
    """Scale and fit camera frame onto a window-matching dark canvas to eliminate white borders."""
    if not window_name:
        return frame

    try:
        rect = cv2.getWindowImageRect(window_name)
        if not rect or len(rect) < 4:
            return frame
        rx, ry, rw, rh = int(rect[0]), int(rect[1]), int(rect[2]), int(rect[3])
    except Exception:
        return frame

    fh, fw = frame.shape[:2]
    # In OpenCV Qt, (rx, ry) represents the letterbox margin offset of the image inside the viewport.
    win_w = rw + max(0, rx) * 2 if rx > 0 else rw
    win_h = rh + max(0, ry) * 2 if ry > 0 else rh

    if win_w <= 120 or win_h <= 120 or (win_w == fw and win_h == fh and rx == 0 and ry == 0):
        return frame

    # Scale camera frame to fit window while preserving aspect ratio
    scale = min(win_w / fw, win_h / fh)
    new_w = max(1, int(fw * scale))
    new_h = max(1, int(fh * scale))

    cache_key = (win_h, win_w, bg_color)
    if cache_key not in _CANVAS_CACHE:
        _CANVAS_CACHE.clear()
        _CANVAS_CACHE[cache_key] = np.full((win_h, win_w, 3), bg_color, dtype=np.uint8)

    canvas = _CANVAS_CACHE[cache_key].copy()
    ox = (win_w - new_w) // 2
    oy = (win_h - new_h) // 2
    cv2.resize(
        frame,
        (new_w, new_h),
        dst=canvas[oy : oy + new_h, ox : ox + new_w],
        interpolation=cv2.INTER_LINEAR,
    )
    return canvas


def render_flagship_hud(
    frame: np.ndarray,
    mode_name: str,
    st: dict,
    transcript: str,
    fps: float,
    delegate: str = "CPU",
    tts_speaking: bool = False,
    last_spoken: str = "",
    last_spoken_time: float = 0.0,
    window_name: Optional[str] = None,
    pending_glosses: Optional[List[str]] = None,
    synthesized_sentence: str = "",
) -> np.ndarray:
    """Render high-polish cyberpunk glassmorphic HUD over camera frame."""
    if window_name:
        frame = prepare_window_canvas(frame, window_name=window_name)

    h, w = frame.shape[:2]
    now = time.monotonic()

    # --------------------------------------------------------------------------
    # 1. Top Status Pill
    # --------------------------------------------------------------------------
    header_w = min(w - 32, 680)
    hx = (w - header_w) // 2
    draw_glass_card(frame, hx, 14, header_w, 42, alpha=0.82, radius=12)

    # Live indicator dot (blinks every second)
    dot_color = COLOR_EMERALD if int(now * 2) % 2 == 0 else COLOR_CYAN_NEON
    cv2.circle(frame, (hx + 20, 35), 5, dot_color, -1, cv2.LINE_AA)

    # Brand & Mode Name
    cv2.putText(
        frame, "SIGNORA", (hx + 34, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.58, COLOR_WHITE, 2, cv2.LINE_AA
    )
    cv2.putText(
        frame,
        f"//  {mode_name.upper()}",
        (hx + 130, 38),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.50,
        COLOR_CYAN_NEON,
        1,
        cv2.LINE_AA,
    )

    # Metrics on the right
    fps_color = (
        COLOR_EMERALD if fps >= 20.0 else (COLOR_AMBER if fps >= 10.0 else COLOR_MAGENTA_NEON)
    )
    audio_str = "SPEAKING" if tts_speaking else "TTS READY"
    metrics_str = f"{fps:4.1f} FPS   |   {delegate.upper()}   |   {audio_str}"
    cv2.putText(
        frame,
        metrics_str,
        (hx + header_w - 275, 38),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.42,
        fps_color,
        1,
        cv2.LINE_AA,
    )

    # --------------------------------------------------------------------------
    # 2. Right Floating Candidate Prediction Card
    # --------------------------------------------------------------------------
    cand = st.get("candidate")
    dwell = float(st.get("dwell_progress", 0.0))
    in_cooldown = bool(st.get("in_cooldown", False))

    if cand:
        cw, ch = 210, 118
        cx = w - cw - 20
        cy = 68

        border_c = COLOR_EMERALD if (dwell >= 0.95 or in_cooldown) else COLOR_CYAN_NEON
        draw_glass_card(frame, cx, cy, cw, ch, border_color=border_c, alpha=0.90, radius=14)

        # Card Title
        title_txt = "LOCKED SIGN" if in_cooldown else "DETECTED SIGN"
        title_col = COLOR_EMERALD if in_cooldown else COLOR_TEXT_DIM
        cv2.putText(
            frame,
            title_txt,
            (cx + 16, cy + 24),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.40,
            title_col,
            1,
            cv2.LINE_AA,
        )

        # Active Sign Display
        disp_cand = "[SPACE]" if cand == "SPACE" else cand
        font_scale = 1.35 if len(disp_cand) <= 2 else 0.82
        sign_color = COLOR_EMERALD if in_cooldown else COLOR_WHITE
        cv2.putText(
            frame,
            disp_cand,
            (cx + 18, cy + 68),
            cv2.FONT_HERSHEY_SIMPLEX,
            font_scale,
            sign_color,
            2,
            cv2.LINE_AA,
        )

        # Smooth Neon Dwell Progress Bar
        bar_x, bar_y, bar_w, bar_h = cx + 16, cy + 88, cw - 32, 10
        draw_rounded_rectangle(
            frame,
            (bar_x, bar_y),
            (bar_x + bar_w, bar_y + bar_h),
            COLOR_KEYCAP_BG,
            radius=4,
            thickness=-1,
        )

        fill_w = int(bar_w * (1.0 if in_cooldown else max(0.0, min(1.0, dwell))))
        if fill_w > 4:
            bar_color = COLOR_EMERALD if (dwell >= 0.95 or in_cooldown) else COLOR_CYAN_NEON
            draw_rounded_rectangle(
                frame,
                (bar_x, bar_y),
                (bar_x + fill_w, bar_y + bar_h),
                bar_color,
                radius=4,
                thickness=-1,
            )

        # Status text (Percentage or Confirmed)
        pct_text = "CONFIRMED" if in_cooldown else f"{int(dwell * 100)}%"
        pct_col = COLOR_EMERALD if in_cooldown else COLOR_CYAN_NEON
        cv2.putText(
            frame,
            pct_text,
            (cx + cw - (78 if in_cooldown else 46), cy + 24),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            pct_col,
            1,
            cv2.LINE_AA,
        )
    else:
        # Subtle scanning radar pill
        cw, ch = 200, 50
        cx = w - cw - 20
        cy = 68
        draw_glass_card(
            frame, cx, cy, cw, ch, border_color=COLOR_BORDER_GLASS, alpha=0.72, radius=12
        )
        dot_c = COLOR_CYAN_NEON if int(now * 3) % 2 == 0 else COLOR_TEXT_DIM
        cv2.circle(frame, (cx + 18, cy + 25), 4, dot_c, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "AWAITING GESTURE",
            (cx + 30, cy + 22),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            COLOR_TEXT_DIM,
            1,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            "Hold hand in frame to sign",
            (cx + 30, cy + 38),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.32,
            COLOR_BORDER_GLASS,
            1,
            cv2.LINE_AA,
        )

    # --------------------------------------------------------------------------
    # 3. Audio TTS Speaking Banner (Floating Center Popup)
    # --------------------------------------------------------------------------
    speech_active = tts_speaking or (now - last_spoken_time < 2.2 and bool(last_spoken))
    if speech_active and last_spoken:
        tw, th = min(w - 40, 520), 40
        tx = (w - tw) // 2
        ty = 68
        draw_glass_card(frame, tx, ty, tw, th, border_color=COLOR_EMERALD, alpha=0.92, radius=12)

        # Glowing audio soundwave icon
        cv2.circle(frame, (tx + 22, ty + 20), 6, COLOR_EMERALD, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "SPEAKING:",
            (tx + 36, ty + 25),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.48,
            COLOR_EMERALD,
            2,
            cv2.LINE_AA,
        )

        clean_spoken = last_spoken[:38] + ("..." if len(last_spoken) > 38 else "")
        cv2.putText(
            frame,
            f'"{clean_spoken}"',
            (tx + 130, ty + 25),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            COLOR_WHITE,
            1,
            cv2.LINE_AA,
        )

    # --------------------------------------------------------------------------
    # 4. Bottom Floating Transcript Console (Dual-Layer or Single-Layer)
    # --------------------------------------------------------------------------
    has_dual_layer = (pending_glosses is not None and len(pending_glosses) > 0) or bool(synthesized_sentence)
    card_w = min(w - 32, 880)
    card_h = 138 if has_dual_layer else 105
    bx = (w - card_w) // 2
    by = h - card_h - 14

    draw_glass_card(frame, bx, by, card_w, card_h, alpha=0.88, radius=16)

    if has_dual_layer:
        # Header Row: Indicator + "GLOSS STREAM" + Chips
        cv2.circle(frame, (bx + 20, by + 22), 4, COLOR_CYAN_NEON, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "GLOSS STREAM",
            (bx + 32, by + 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.40,
            COLOR_CYAN_NEON,
            1,
            cv2.LINE_AA,
        )

        # Draw Gloss Chips
        chip_x = bx + 160
        gloss_list = pending_glosses[-5:] if pending_glosses else []
        for g in gloss_list:
            clean_g = g.upper()
            gw = max(45, len(clean_g) * 9 + 16)
            if chip_x + gw < bx + card_w - 140:
                draw_rounded_rectangle(
                    frame,
                    (chip_x, by + 12),
                    (chip_x + gw, by + 32),
                    COLOR_KEYCAP_BG,
                    radius=4,
                    thickness=-1,
                )
                draw_rounded_rectangle(
                    frame,
                    (chip_x, by + 12),
                    (chip_x + gw, by + 32),
                    COLOR_BORDER_GLASS,
                    radius=4,
                    thickness=1,
                )
                cv2.putText(
                    frame,
                    clean_g,
                    (chip_x + 8, by + 26),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.38,
                    COLOR_WHITE,
                    1,
                    cv2.LINE_AA,
                )
                chip_x += gw + 6

        # Metrics on right
        g_count = len(pending_glosses) if pending_glosses else 0
        meta_str = f"{g_count} {'SIGN' if g_count == 1 else 'SIGNS'}"
        cv2.putText(
            frame,
            meta_str,
            (bx + card_w - 110, by + 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            COLOR_TEXT_DIM,
            1,
            cv2.LINE_AA,
        )

        # Middle Row: Subtitle + Synthesized Natural Sentence
        cv2.putText(
            frame,
            "NEURAL GRAMMAR",
            (bx + 24, by + 56),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            COLOR_EMERALD,
            1,
            cv2.LINE_AA,
        )

        if synthesized_sentence:
            disp_txt = synthesized_sentence[-50:] if len(synthesized_sentence) > 50 else synthesized_sentence
            cursor = "_" if int(now * 2.5) % 2 == 0 else ""
            cv2.putText(
                frame,
                f'"{disp_txt}"{cursor}',
                (bx + 24, by + 86),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.78,
                COLOR_WHITE,
                2,
                cv2.LINE_AA,
            )
        else:
            cursor = "_" if int(now * 2) % 2 == 0 else ""
            hint = f"Present gestures to synthesize natural English sentence{cursor}"
            cv2.putText(
                frame,
                hint,
                (bx + 24, by + 86),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.54,
                COLOR_TEXT_DIM,
                1,
                cv2.LINE_AA,
            )

        ky = by + 106
    else:
        # Standard Single-layer transcript
        cv2.circle(frame, (bx + 20, by + 24), 4, COLOR_EMERALD, -1, cv2.LINE_AA)
        cv2.putText(
            frame,
            "LIVE TRANSCRIPT",
            (bx + 32, by + 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.42,
            COLOR_TEXT_DIM,
            1,
            cv2.LINE_AA,
        )

        words = transcript.strip().split()
        w_count = len(words)
        c_count = len(transcript)
        meta_str = f"{w_count} {'WORD' if w_count == 1 else 'WORDS'}   |   {c_count} CHARS"
        cv2.putText(
            frame,
            meta_str,
            (bx + card_w - 200, by + 28),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.38,
            COLOR_TEXT_DIM,
            1,
            cv2.LINE_AA,
        )

        # Transcript Text with glowing cursor
        char_count = len(transcript)
        if char_count > 0:
            disp_txt = transcript[-45:] if char_count > 45 else transcript
            cursor = "_" if int(now * 2.5) % 2 == 0 else ""
            cv2.putText(
                frame,
                f"{disp_txt}{cursor}",
                (bx + 24, by + 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.88,
                COLOR_EMERALD,
                2,
                cv2.LINE_AA,
            )
        else:
            cursor = "_" if int(now * 2) % 2 == 0 else ""
            hint = f"Present hand gestures in camera view to dictate transcript{cursor}"
            cv2.putText(
                frame,
                hint,
                (bx + 24, by + 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.56,
                COLOR_TEXT_DIM,
                1,
                cv2.LINE_AA,
            )

        ky = by + 74

    # Hotkey Badges in Footer Row
    kx = bx + 20
    kx += draw_keycap(frame, kx, ky, "Space", "Speak")
    kx += draw_keycap(frame, kx, ky, "C", "Clear")
    kx += draw_keycap(frame, kx, ky, "Bksp", "Undo")
    kx += draw_keycap(frame, kx, ky, "F", "Fullscreen")
    kx += draw_keycap(frame, kx, ky, "Q", "Quit")

    return frame

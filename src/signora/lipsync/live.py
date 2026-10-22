"""Interactive live visual Lip Sync & Speech Activity monitor for Signora."""

import os
import sys
import time
from typing import Any, Optional, Tuple

import cv2
import numpy as np

from signora.lipsync.audio import AudioEnergyTracker, SyncState
from signora.lipsync.detector import LipDetector, LipMetrics, Viseme

# Wayland / X11 compatibility
os.environ.setdefault("QT_QPA_PLATFORM", "xcb")


def render_lipsync_hud(
    frame: np.ndarray,
    metrics: Optional[LipMetrics],
    sync_score: float,
    sync_state: SyncState,
    lag_ms: float,
    audio_energy: float,
    fps: float,
    audio_enabled: bool,
) -> np.ndarray:
    """Render glassmorphic cybernetic HUD overlay for lip tracking and audio-visual synchronization."""
    h, w = frame.shape[:2]

    # 1. Overlay Lip Contours
    if metrics is not None:
        # Draw outer lips in glowing neon fuchsia / rose
        outer_pts = np.array(metrics.landmarks_outer_px, dtype=np.int32)
        cv2.polylines(frame, [outer_pts], isClosed=True, color=(147, 51, 234), thickness=3, lineType=cv2.LINE_AA)
        cv2.polylines(frame, [outer_pts], isClosed=True, color=(244, 114, 182), thickness=1, lineType=cv2.LINE_AA)

        # Draw inner aperture in glowing electric cyan
        inner_pts = np.array(metrics.landmarks_inner_px, dtype=np.int32)
        cv2.polylines(frame, [inner_pts], isClosed=True, color=(8, 145, 178), thickness=2, lineType=cv2.LINE_AA)
        cv2.polylines(frame, [inner_pts], isClosed=True, color=(6, 182, 212), thickness=1, lineType=cv2.LINE_AA)

        # Draw mouth center
        cx, cy = metrics.center_xy
        cv2.circle(frame, (cx, cy), 4, (6, 182, 212), -1, lineType=cv2.LINE_AA)

        # Draw subtle bounding box around mouth
        bx, by, bw, bh = metrics.bbox
        if 0 <= bx < w and 0 <= by < h and bw > 10 and bh > 10:
            cv2.rectangle(frame, (bx, by), (bx + bw, by + bh), (147, 51, 234), 1, lineType=cv2.LINE_AA)

    # 2. Top Status Pill
    pill_w, pill_h = min(700, w - 40), 44
    px = (w - pill_w) // 2
    py = 16

    overlay = frame.copy()
    cv2.rectangle(overlay, (px, py), (px + pill_w, py + pill_h), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.88, frame, 0.12, 0, frame)
    cv2.rectangle(frame, (px, py), (px + pill_w, py + pill_h), (51, 65, 85), 1)

    # App Title & FPS
    cv2.putText(
        frame,
        f"LIP SYNC & VISUAL SPEECH | {int(fps)} FPS",
        (px + 20, py + 28),
        cv2.FONT_HERSHEY_DUPLEX,
        0.58,
        (255, 255, 255),
        1,
        cv2.LINE_AA,
    )

    # V-VAD Visual Speaking Dot
    is_speaking = metrics.is_speaking if metrics else False
    vad_color = (52, 211, 153) if is_speaking else (100, 116, 139)
    vad_label = "SPEAKING" if is_speaking else "IDLE"
    cv2.circle(frame, (px + pill_w - 110, py + 22), 6, vad_color, -1, lineType=cv2.LINE_AA)
    cv2.putText(
        frame,
        vad_label,
        (px + pill_w - 95, py + 28),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.5,
        vad_color,
        1,
        cv2.LINE_AA,
    )

    # 3. Main Glassmorphic Lip Analytics Card (Left Side)
    card_w, card_h = 320, 240
    card_x = 24
    card_y = 76

    overlay = frame.copy()
    cv2.rectangle(overlay, (card_x, card_y), (card_x + card_w, card_y + card_h), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.90, frame, 0.10, 0, frame)
    cv2.rectangle(frame, (card_x, card_y), (card_x + card_w, card_y + card_h), (51, 65, 85), 1)

    # Section header
    cv2.putText(
        frame,
        "ORAL VISEME & KINEMATICS",
        (card_x + 16, card_y + 28),
        cv2.FONT_HERSHEY_DUPLEX,
        0.52,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # Active Viseme Pill
    viseme_val = metrics.viseme.value if metrics else "NO FACE DETECTED"
    cv2.rectangle(frame, (card_x + 16, card_y + 40), (card_x + card_w - 16, card_y + 74), (30, 41, 59), -1)
    cv2.rectangle(frame, (card_x + 16, card_y + 40), (card_x + card_w - 16, card_y + 74), (6, 182, 212), 1)
    cv2.putText(
        frame,
        viseme_val,
        (card_x + 24, card_y + 63),
        cv2.FONT_HERSHEY_DUPLEX,
        0.52,
        (255, 255, 255),
        1,
        cv2.LINE_AA,
    )

    # Quantitative Metrics (MAR, LWR, Cadence)
    mar_val = metrics.mar if metrics else 0.0
    lwr_val = metrics.lip_width_ratio if metrics else 0.0
    cadence = metrics.speech_cadence_hz if metrics else 0.0

    # Mouth Aspect Ratio bar
    cv2.putText(
        frame,
        f"Mouth Aperture (MAR): {mar_val:.3f}",
        (card_x + 16, card_y + 98),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )
    bar_w = card_w - 32
    mar_fill = int(min(1.0, mar_val / 0.45) * bar_w)
    cv2.rectangle(frame, (card_x + 16, card_y + 104), (card_x + 16 + bar_w, card_y + 112), (30, 41, 59), -1)
    cv2.rectangle(frame, (card_x + 16, card_y + 104), (card_x + 16 + mar_fill, card_y + 112), (6, 182, 212), -1)

    # Lip Width Ratio bar
    cv2.putText(
        frame,
        f"Normalized Width (LWR): {lwr_val:.2f}",
        (card_x + 16, card_y + 134),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )
    lwr_fill = int(min(1.0, lwr_val / 1.1) * bar_w)
    cv2.rectangle(frame, (card_x + 16, card_y + 140), (card_x + 16 + bar_w, card_y + 148), (30, 41, 59), -1)
    cv2.rectangle(frame, (card_x + 16, card_y + 140), (card_x + 16 + lwr_fill, card_y + 148), (244, 114, 182), -1)

    # Speech cadence
    cv2.putText(
        frame,
        f"Speech Cadence: {cadence:.1f} syll/sec",
        (card_x + 16, card_y + 172),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )

    # Audio Energy Level
    audio_label = f"Mic Level: {int(audio_energy * 100)}%" if audio_enabled else "Mic: DISABLED [M]"
    cv2.putText(
        frame,
        audio_label,
        (card_x + 16, card_y + 200),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (226, 232, 240) if audio_enabled else (148, 163, 184),
        1,
        cv2.LINE_AA,
    )
    aud_fill = int(min(1.0, audio_energy) * bar_w)
    cv2.rectangle(frame, (card_x + 16, card_y + 206), (card_x + 16 + bar_w, card_y + 214), (30, 41, 59), -1)
    if audio_enabled:
        cv2.rectangle(frame, (card_x + 16, card_y + 206), (card_x + 16 + aud_fill, card_y + 214), (52, 211, 153), -1)

    # 4. Audio-Visual Synchronization Card (Right Side)
    card2_w, card2_h = 320, 160
    card2_x = w - card2_w - 24
    card2_y = 76

    overlay = frame.copy()
    cv2.rectangle(overlay, (card2_x, card2_y), (card2_x + card2_w, card2_y + card2_h), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.90, frame, 0.10, 0, frame)
    cv2.rectangle(frame, (card2_x, card2_y), (card2_x + card2_w, card2_y + card2_h), (51, 65, 85), 1)

    # Header
    cv2.putText(
        frame,
        "AUDIO-VISUAL SYNC",
        (card2_x + 16, card2_y + 28),
        cv2.FONT_HERSHEY_DUPLEX,
        0.52,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # Sync Status Tag
    state_color = (52, 211, 153) if sync_state == SyncState.SYNCED else (
        (6, 182, 212) if sync_state == SyncState.MOUTHING else (
            (251, 146, 60) if sync_state == SyncState.BACKGROUND_AUDIO else (100, 116, 139)
        )
    )

    cv2.putText(
        frame,
        sync_state.value,
        (card2_x + 16, card2_y + 60),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.44,
        state_color,
        1,
        cv2.LINE_AA,
    )

    # Sync score bar
    cv2.putText(
        frame,
        f"Correlation Score: {int(sync_score * 100)}%",
        (card2_x + 16, card2_y + 92),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )
    score_fill = int(min(1.0, sync_score) * bar_w)
    cv2.rectangle(frame, (card2_x + 16, card2_y + 98), (card2_x + 16 + bar_w, card2_y + 106), (30, 41, 59), -1)
    cv2.rectangle(frame, (card2_x + 16, card2_y + 98), (card2_x + 16 + score_fill, card2_y + 106), state_color, -1)

    # Offset lag
    lag_str = f"A/V Temporal Offset: {lag_ms:+.0f} ms" if sync_state == SyncState.SYNCED else "A/V Temporal Offset: --"
    cv2.putText(
        frame,
        lag_str,
        (card2_x + 16, card2_y + 132),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.44,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # 5. Inset Zoom of Mouth (Right side, beneath sync card)
    if metrics is not None:
        bx, by, bw, bh = metrics.bbox
        if 0 <= bx < w and 0 <= by < h and bx + bw <= w and by + bh <= h and bw > 10 and bh > 10:
            inset_w, inset_h = 160, 90
            inset_x = w - inset_w - 24
            inset_y = card2_y + card2_h + 16

            mouth_roi = frame[by:by+bh, bx:bx+bw]
            if mouth_roi.size > 0:
                resized = cv2.resize(mouth_roi, (inset_w, inset_h))
                frame[inset_y:inset_y+inset_h, inset_x:inset_x+inset_w] = resized
                cv2.rectangle(frame, (inset_x, inset_y), (inset_x + inset_w, inset_y + inset_h), (6, 182, 212), 2)
                cv2.putText(
                    frame,
                    "MOUTH ZOOM",
                    (inset_x + 8, inset_y + 18),
                    cv2.FONT_HERSHEY_DUPLEX,
                    0.4,
                    (255, 255, 255),
                    1,
                    cv2.LINE_AA,
                )

    # 6. Bottom Hotkey Help Bar
    bar_h = 36
    by = h - bar_h - 10
    overlay = frame.copy()
    cv2.rectangle(overlay, (px, by), (px + pill_w, by + bar_h), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.88, frame, 0.12, 0, frame)
    cv2.rectangle(frame, (px, by), (px + pill_w, by + bar_h), (51, 65, 85), 1)

    cv2.putText(
        frame,
        "[M] Toggle Mic    [F] Fullscreen    [Q] Quit Monitor",
        (px + 40, by + 23),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.48,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    return frame


def run_lipsync_live(
    source: Any = 0,
    enable_audio: bool = True,
    headless: bool = False,
    fake_camera: bool = False,
    max_frames: Optional[int] = None,
    width: int = 1280,
    height: int = 720,
) -> int:
    """Run interactive real-time Lip Sync & Speech Activity monitor."""
    print("=" * 65)
    print("  SIGNORA LIP SYNC -- Visual Speech Activity & Viseme Monitor")
    print(f"  Resolution          : {width}x{height} (16:9 Widescreen)")
    print("=" * 65)
    print(f"  Camera Source       : {source if not fake_camera else 'SYNTHETIC BENCH'}")
    print(f"  Microphone Sync     : {'ENABLED' if enable_audio else 'DISABLED'}")
    print("  Controls:")
    print("    [M]   : Toggle microphone audio stream")
    print("    [F]   : Toggle fullscreen")
    print("    [Q]   : Quit monitor")
    print("=" * 65)

    detector = LipDetector(use_fake=fake_camera)
    audio_tracker = AudioEnergyTracker(enable_audio=enable_audio)

    cap = None
    if not fake_camera:
        try:
            cam_idx = int(source) if str(source).isdigit() else source
            cap = cv2.VideoCapture(cam_idx)
            if cap.isOpened():
                cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
                cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
            if not cap.isOpened():
                print(f"[Notice] Could not open camera {source}. Switching to synthetic camera.")
                fake_camera = True
                detector.use_fake = True
                cap = None
        except Exception:
            fake_camera = True
            detector.use_fake = True

    window_name = "Signora - Lip Sync & Speech Activity"
    if not headless:
        cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
        win_w = min(1280, width)
        win_h = max(360, int(win_w * 9 / 16))
        cv2.resizeWindow(window_name, win_w, win_h)

    fps = 30.0
    frame_count = 0
    t_prev = time.time()
    fullscreen = False

    try:
        while True:
            if fake_camera:
                # Generate synthetic test frame
                frame = np.full((600, 960, 3), (20, 24, 33), dtype=np.uint8)
                # Draw subtle oval for face outline
                cv2.ellipse(frame, (480, 300), (140, 190), 0, 0, 360, (40, 50, 65), -1)
                time.sleep(0.033)
            else:
                ret, frame = cap.read()
                if not ret or frame is None:
                    print("[End of video stream]")
                    break
                # Mirror for natural self-view
                frame = cv2.flip(frame, 1)

            h, w = frame.shape[:2]

            # 1. Extract Lip Metrics via FaceMesh
            metrics = detector.process(frame)

            # 2. Compute Audio-Visual Sync Correlation
            is_speaking = metrics.is_speaking if metrics else False
            sync_score, sync_state, lag_ms = audio_tracker.compute_sync(
                visual_motion_history=detector.mar_history,
                visual_time_history=detector.time_history,
                is_visual_speaking=is_speaking,
            )
            audio_energy = audio_tracker.get_latest_energy()

            # 3. Compute FPS
            t_now = time.time()
            dt = t_now - t_prev
            t_prev = t_now
            if dt > 0:
                fps = 0.9 * fps + 0.1 * (1.0 / dt)

            # 4. Render HUD
            render_lipsync_hud(
                frame=frame,
                metrics=metrics,
                sync_score=sync_score,
                sync_state=sync_state,
                lag_ms=lag_ms,
                audio_energy=audio_energy,
                fps=fps,
                audio_enabled=audio_tracker.enable_audio,
            )

            frame_count += 1
            if max_frames and frame_count >= max_frames:
                break

            if not headless:
                cv2.imshow(window_name, frame)
                key = cv2.waitKey(1) & 0xFF
                if key in (ord("q"), ord("Q"), 27):
                    break
                elif key in (ord("m"), ord("M")):
                    audio_tracker.toggle()
                    print(f"[Audio] Microphone synchronization: {'ON' if audio_tracker.enable_audio else 'OFF'}")
                elif key in (ord("f"), ord("F")):
                    fullscreen = not fullscreen
                    prop = cv2.WINDOW_FULLSCREEN if fullscreen else cv2.WINDOW_NORMAL
                    cv2.setWindowProperty(window_name, cv2.WND_PROP_FULLSCREEN, prop)

    finally:
        if cap:
            cap.release()
        audio_tracker.close()
        detector.close()
        if not headless:
            cv2.destroyAllWindows()

    return 0

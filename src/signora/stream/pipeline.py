"""Real-time virtual webcam streaming pipeline.

Connects camera perception, sign classification, grammar synthesis, Piper speech,
meeting broadcast overlay, and virtual video device output.
"""

import sys
import time
from typing import Any, List, Optional

import cv2
import numpy as np

from signora.core.debounce import DebounceEngine
from signora.core.hands import HandPipeline
from signora.core.hud import draw_cyber_hands
from signora.isl.config import (
    CONFIRM_THRESHOLD,
    DWELL_MS,
    REPEAT_COOLDOWN_MS,
    SMOOTH_MS,
    SPACE_COOLDOWN_MS,
    SPACE_DWELL_MS,
)
from signora.isl.model import Predictor
from signora.nlp import GlossToken, IslGrammarSynthesizer, SentenceBuffer
from signora.stream.overlay import render_meeting_overlay
from signora.stream.server import WebStreamServer
from signora.stream.vcam import VirtualCameraManager, get_v4l2_setup_instructions
from signora.tts.piper import PiperTTS

WINDOW_NAME = "Signora - Virtual Webcam Stream"


def run_virtual_stream(
    source: Any = 0,
    mode: str = "isl",
    device: Optional[str] = None,
    width: int = 1280,
    height: int = 720,
    fps: int = 30,
    clean_mode: bool = False,
    show_hands: bool = True,
    no_tts: bool = False,
    headless: bool = False,
    enable_web: bool = True,
    web_port: int = 8888,
    use_fake: bool = False,
    gemini_key: Optional[str] = None,
) -> int:
    """Run real-time virtual webcam streaming session."""
    print("=" * 68)
    print("  SIGNORA -- Virtual Webcam & Meeting Streamer (Zoom / Meet / Teams)")
    print("=" * 68)

    if gemini_key:
        from signora.ai.gemini import GeminiAIEngine
        GeminiAIEngine.save_api_key(gemini_key)

    # 1. Initialize Neural Classifier
    try:
        predictor = Predictor()
    except FileNotFoundError:
        print("[Auto-Healing] ISL neural model weights missing. Fetching...", file=sys.stderr)
        from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models
        from signora.models.manifest import get_default_models_dir

        fetch_models(DEFAULT_MANIFEST, get_default_models_dir())
        predictor = Predictor()

    engine = DebounceEngine(
        predictor=predictor,
        smooth_ms=SMOOTH_MS,
        dwell_ms=DWELL_MS,
        space_dwell_ms=SPACE_DWELL_MS,
        threshold=CONFIRM_THRESHOLD,
        repeat_cooldown_ms=REPEAT_COOLDOWN_MS,
        space_cooldown_ms=SPACE_COOLDOWN_MS,
    )

    # 2. TTS and Grammar Synthesizer
    tts = PiperTTS(enabled=not no_tts)
    last_synthesized = ""
    last_spoken_text = ""
    last_spoken_time = 0.0

    def on_sentence_complete(tokens: List[GlossToken]) -> None:
        nonlocal last_synthesized, last_spoken_text, last_spoken_time
        sentence = IslGrammarSynthesizer.synthesize(tokens)
        if sentence:
            last_synthesized = sentence
            last_spoken_text = sentence
            last_spoken_time = time.time()
            print(f"[VirtualCam Subtitle] {sentence}")
            if tts.is_ready:
                tts.speak_async(sentence)

    sentence_buffer = SentenceBuffer(
        sentence_pause_s=4.5,
        on_sentence_complete=on_sentence_complete,
    )

    # 3. Virtual Camera Device
    vcam = VirtualCameraManager(width=width, height=height, fps=fps, device=device)
    vcam_ok = vcam.open()
    if not vcam_ok:
        print(f"[VirtualCam Info] Physical loopback device (/dev/video*) not active: {vcam.error_message}")
        print("  -> Streaming will automatically broadcast to local HTTP MJPEG server.")
        print("  -> To activate direct /dev/video device for Zoom/Meet, run: signora stream --setup-v4l2\n")

    # 4. Web Stream Server
    web_server: Optional[WebStreamServer] = None
    if enable_web:
        web_server = WebStreamServer(port=web_port)
        if web_server.start():
            print(f"[WebStream] Live browser preview: http://localhost:{web_port}")
            print(f"[WebStream] OBS Browser source:  http://localhost:{web_port}/video\n")

    # 5. Hand Perception Pipeline
    num_hands = 2 if mode == "isl" else 1
    pipe = HandPipeline(
        source=source,
        num_hands=num_hands,
        mirror=True,
        detect_every=1,
        use_fake=use_fake,
        width=width,
        height=height,
    )

    if not pipe.started.wait(timeout=5) or pipe.error:
        print("[Auto-Healing] Primary camera failed to start. Probing alternatives...", file=sys.stderr)
        from signora.platform_compat import get_camera_capture
        found = False
        for probe_source in ("/dev/video0", 0, 1, 2):
            test_cap = get_camera_capture(probe_source, buffer_size=1, width=width, height=height)
            if test_cap.isOpened():
                test_cap.release()
                print(f"[Auto-Healing] Discovered working webcam at source '{probe_source}'. Retrying...")
                pipe.stop()
                pipe = HandPipeline(
                    source=probe_source,
                    num_hands=num_hands,
                    mirror=True,
                    width=width,
                    height=height,
                )
                if pipe.started.wait(timeout=5) and not pipe.error:
                    found = True
                    break
        if not found:
            print("[Error] Could not initialize video source.", file=sys.stderr)
            vcam.close()
            if web_server:
                web_server.stop()
            return 1

    print("=" * 68)
    print("  CONTROLS:")
    print("    [C] Toggle Clean Broadcast Mode (Captions only vs Full HUD)")
    print("    [H] Toggle Hand Skeletal Overlays")
    print("    [M] Toggle Audio Mute")
    print("    [R] Reset Transcript & Clear Buffer")
    print("    [Q] / [ESC] Stop Virtual Stream")
    print("=" * 68)

    # Interactive runtime states
    active_clean_mode = clean_mode
    active_show_hands = show_hands
    fps_calc = 30.0
    frame_count = 0
    t_start = time.time()

    try:
        while True:
            t_frame_start = time.time()
            data = pipe.read()
            if data is None:
                if not pipe.running:
                    break
                time.sleep(0.005)
                continue

            frame = data.frame
            raw_hands = data.hands
            fh, fw = frame.shape[:2]

            # Recognition step
            cand, dwell, in_cool, confirmed = engine.update(raw_hands)

            if confirmed and confirmed != "NONE":
                sentence_buffer.add_token(confirmed)

            sentence_buffer.tick()

            # Render Hand Skeleton (if enabled and hands present)
            if active_show_hands and raw_hands:
                draw_cyber_hands(frame, raw_hands, fw, fh)

            # Render Broadcast Overlay
            pending_tokens = sentence_buffer.current_tokens
            pending_glosses = [t.text for t in pending_tokens]

            overlay_frame = render_meeting_overlay(
                frame=frame,
                candidate=cand,
                dwell_ratio=dwell,
                in_cooldown=in_cool,
                pending_glosses=pending_glosses,
                synthesized_sentence=last_synthesized,
                tts_speaking=tts.is_speaking,
                last_spoken=last_spoken_text,
                last_spoken_time=last_spoken_time,
                clean_mode=active_clean_mode,
                mode_label=f"{mode.upper()} DUAL-HAND" if mode == "isl" else "ASL",
                fps=fps_calc,
            )

            # Transmit to Virtual Camera Device (/dev/video*)
            if vcam.is_active:
                vcam.send_frame(overlay_frame)

            # Transmit to Web Server (http://localhost:8888)
            if web_server is not None:
                web_server.push_frame(overlay_frame)
                web_server.push_status(
                    active_sign=cand,
                    dwell=dwell,
                    sentence=last_synthesized,
                    speaking=tts.is_speaking,
                    fps=fps_calc,
                )

            # Display on desktop GUI if not headless
            if not headless:
                cv2.imshow(WINDOW_NAME, overlay_frame)
                key = cv2.waitKey(1) & 0xFF
                if key in (27, ord("q"), ord("Q")):
                    print("\n[VirtualCam] Stopping stream session...")
                    break
                elif key in (ord("c"), ord("C")):
                    active_clean_mode = not active_clean_mode
                    print(f"[VirtualCam] Clean Broadcast Mode: {'ON' if active_clean_mode else 'OFF'}")
                elif key in (ord("h"), ord("H")):
                    active_show_hands = not active_show_hands
                    print(f"[VirtualCam] Hand Skeletons: {'SHOWN' if active_show_hands else 'HIDDEN'}")
                elif key in (ord("m"), ord("M")):
                    tts.enabled = not tts.enabled
                    print(f"[VirtualCam] Piper Audio Speech: {'UNMUTED' if tts.enabled else 'MUTED'}")
                elif key in (ord("r"), ord("R")):
                    sentence_buffer.clear()
                    last_synthesized = ""
                    last_spoken_text = ""
                    print("[VirtualCam] Buffer cleared.")

            # Frame rate pacing
            frame_count += 1
            elapsed = time.time() - t_start
            if elapsed >= 1.0:
                fps_calc = frame_count / elapsed
                frame_count = 0
                t_start = time.time()

            vcam.sleep_until_next_frame()

    finally:
        pipe.stop()
        vcam.close()
        if web_server:
            web_server.stop()
        if not headless:
            cv2.destroyAllWindows()

    print("[VirtualCam] Stream successfully stopped.")
    return 0

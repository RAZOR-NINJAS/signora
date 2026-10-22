"""Real-time ISL recognition loop with OpenCV HUD and Piper speech output."""

import argparse
import sys
import time
from typing import Any, List, Optional

import cv2
import numpy as np

from signora.core.debounce import DebounceEngine
from signora.core.hands import HandPipeline
from signora.core.hud import draw_cyber_hands, render_flagship_hud
from signora.core.spatial import SpatialPipeline, draw_cyber_spatial
from signora.isl.config import (
    CONFIRM_THRESHOLD,
    DWELL_MS,
    REPEAT_COOLDOWN_MS,
    SMOOTH_MS,
    SPACE_COOLDOWN_MS,
    SPACE_DWELL_MS,
)
from signora.isl.features import extract_features
from signora.isl.model import Predictor
from signora.isl.spatial import (
    SIGN_ANCHOR_MAP,
    detect_body_anchor,
    get_body_anchor_pt,
)
from signora.core.motion import HandMotionTracker, match_sign_motion
from signora.nlp import GlossToken, IslGrammarSynthesizer, SentenceBuffer
from signora.tts.piper import PiperTTS

WINDOW_NAME = "Signora - ISL Two-Handed Mode"


def run_isl_live(
    source: Any = 0,
    headless: bool = False,
    no_tts: bool = False,
    detect_every: int = 2,
    max_frames: Optional[int] = None,
    use_fake: bool = False,
    dwell_ms: Optional[int] = None,
    gemini_key: Optional[str] = None,
    enable_body: bool = True,
    width: int = 1280,
    height: int = 720,
    enable_topology: bool = True,
) -> int:
    """Run real-time ISL recognition loop with integrated anatomical body map."""
    print("=" * 60)
    print("  SIGNORA -- Indian Sign Language (ISL) Holistic Mode")
    print(f"  Resolution: {width}x{height} (16:9 Widescreen)")
    if not enable_topology:
        print("  Topology engine: DISABLED (Using pure neural model only)")
    print("=" * 60)

    if gemini_key:
        from signora.ai.gemini import GeminiAIEngine
        GeminiAIEngine.save_api_key(gemini_key)

    if not enable_topology:
        predictor = Predictor(enable_topology=False)
    else:
        try:
            predictor = Predictor(enable_topology=True)
        except FileNotFoundError:
            print(
                "\n[Auto-Healing] ISL neural model weights missing. Auto-downloading...",
                file=sys.stderr,
            )
            from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models
            from signora.models.manifest import get_default_models_dir

            fetch_models(DEFAULT_MANIFEST, get_default_models_dir())
            predictor = Predictor(enable_topology=True)

    if not predictor.has_engine and not enable_topology:
        print(
            "\n[Error] No trained neural model weights found.\n"
            "Train a clean model using: signora isl --train --fresh\n"
            "Or run sample collection first using: signora collect -s <SIGN>",
            file=sys.stderr,
        )
        return 1

    engine = DebounceEngine(
        predictor=predictor,
        smooth_ms=SMOOTH_MS,
        dwell_ms=dwell_ms if dwell_ms is not None else DWELL_MS,
        space_dwell_ms=SPACE_DWELL_MS,
        threshold=CONFIRM_THRESHOLD,
        repeat_cooldown_ms=REPEAT_COOLDOWN_MS,
        space_cooldown_ms=SPACE_COOLDOWN_MS,
    )

    tts = PiperTTS(enabled=not no_tts)

    last_synthesized = ""

    def on_sentence_complete(tokens: List[GlossToken]) -> None:
        nonlocal last_synthesized
        sentence = IslGrammarSynthesizer.synthesize(tokens)
        if sentence:
            last_synthesized = sentence
            print(f"[Synthesized Sentence] {sentence}")
            if tts.is_ready:
                tts.speak_async(sentence)

    sentence_buffer = SentenceBuffer(
        sentence_pause_s=1.8,
        on_sentence_complete=on_sentence_complete,
    )

    # Holistic Spatial Pipeline (Hands + Upper Body Pose)
    pipe = SpatialPipeline(
        source=source,
        mirror=True,
        detect_every=detect_every,
        use_fake=use_fake,
        width=width,
        height=height,
    )

    if not pipe.started.wait(timeout=5) or pipe.error:
        print(
            "[Auto-Healing] Primary camera failed to start. Probing alternative webcams...",
            file=sys.stderr,
        )
        from signora.platform_compat import probe_cameras

        cams = probe_cameras(max_probe=4)
        alt_source = None
        for c in cams:
            if c["index"] != source:
                alt_source = c["index"]
                break
        pipe.close()
        if alt_source is not None:
            print(f"[Auto-Healing] Automatically switched to Camera {alt_source}.", file=sys.stderr)
            pipe = SpatialPipeline(
                source=alt_source, mirror=True, detect_every=detect_every, width=width, height=height
            )
            pipe.started.wait(timeout=5)
        else:
            print(
                "[Auto-Healing] No physical camera found. Switching to synthetic simulation mode.",
                file=sys.stderr,
            )
            pipe = SpatialPipeline(
                source=0, mirror=True, detect_every=detect_every, use_fake=True, width=width, height=height
            )
            pipe.started.wait(timeout=5)

    if not headless:
        cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL | cv2.WINDOW_GUI_NORMAL)
        win_w = min(1280, width)
        win_h = max(360, int(win_w * 9 / 16))
        cv2.resizeWindow(WINDOW_NAME, win_w, win_h)

    frame_count = 0
    t0 = time.monotonic()
    fps_v = 30.0
    fullscreen = False
    show_body_map = enable_body
    hands_rest_start_t = time.monotonic()
    motion_tracker = HandMotionTracker()

    try:
        while True:
            frame, landmarks, handedness, pose_lms = pipe.read(timeout=3.0)
            if frame is None:
                continue

            frame_count += 1
            now = time.monotonic()
            sentence_buffer.tick(now=now)

            is_hands_at_rest = not bool(landmarks)

            if frame_count % 30 == 0:
                elapsed = now - t0
                if elapsed > 0:
                    fps_v = 30.0 / elapsed
                t0 = now

            cand_label = None
            cand_prob = 0.0
            active_anchor_zone = "OPEN_SPACE"
            anchor_pt = None
            target_anchor_pt = None
            is_anchor_matched = False
            required_anchor = None

            if landmarks:
                lms_list = (
                    landmarks
                    if isinstance(landmarks, list) and isinstance(landmarks[0], (list, tuple))
                    else [landmarks]
                )
                hnd_list = handedness if isinstance(handedness, list) else [handedness]

                # Update multi-frame dynamic hand motion tracker
                motion_state = motion_tracker.update(
                    lms_list, hnd_list, pose_landmarks=pose_lms, timestamp=now
                )

                if lms_list and len(lms_list[0]) > 0:
                    pt0 = lms_list[0][0]
                    y_val = pt0.y if hasattr(pt0, "y") else (pt0[1] if isinstance(pt0, (list, tuple)) else 0.5)
                    if y_val > 0.82:
                        is_hands_at_rest = True
            else:
                motion_state = motion_tracker.update([], [], pose_landmarks=pose_lms, timestamp=now)

            if is_hands_at_rest:
                if (now - hands_rest_start_t) >= 0.5 and sentence_buffer.tokens:
                    sentence_buffer.flush()
            else:
                hands_rest_start_t = now

                # 1. Detect anatomical placement anchor zone using whole-body pose
                if pose_lms and len(pose_lms) >= 25:
                    active_anchor_zone, _, anchor_pt = detect_body_anchor(
                        lms_list[0], pose_lms, w=frame.shape[1], h=frame.shape[0]
                    )

                # 2. Real AI Invariant 3D Topology with body anchor context
                top_label, top_prob = predictor.predict_from_landmarks(
                    lms_list, hnd_list, body_anchor=active_anchor_zone
                )
                if top_label:
                    cand_label = top_label
                    cand_prob = top_prob
                else:
                    feats = extract_features(lms_list, hnd_list)
                    probs = predictor.probs(feats)
                    if len(probs) > 0 and len(predictor.labels) > 0:
                        top_idx = int(np.argmax(probs))
                        cand_prob = float(probs[top_idx])
                        cand_label = predictor.labels[top_idx]
                    else:
                        cand_label = None
                        cand_prob = 0.0

                # 3. Anatomical Body Anchor Verification
                if cand_label:
                    required_anchor = SIGN_ANCHOR_MAP.get(cand_label.upper())
                    if required_anchor is not None and pose_lms and len(pose_lms) >= 25:
                        target_anchor_pt = get_body_anchor_pt(
                            required_anchor, pose_lms, w=frame.shape[1], h=frame.shape[0]
                        )
                        is_anchor_matched = (active_anchor_zone == required_anchor)
                        if is_anchor_matched:
                            cand_prob = min(1.0, cand_prob * 1.25)
                        elif active_anchor_zone != "OPEN_SPACE" and active_anchor_zone != required_anchor:
                            # Hand is actively in a different conflicting body zone
                            cand_prob = cand_prob * 0.85

                # 4. Dynamic Movement Verification
                if cand_label:
                    is_motion_match, motion_factor = match_sign_motion(cand_label, motion_state)
                    cand_prob = min(1.0, cand_prob * motion_factor)

                if not headless:
                    motion_tracker.draw_motion_trail(frame)
                    if show_body_map and pose_lms:
                        draw_cyber_spatial(
                            frame=frame,
                            hand_lms_list=lms_list,
                            handedness_list=hnd_list,
                            pose_landmarks=pose_lms,
                            target_anchor_name=required_anchor,
                            target_anchor_pt=target_anchor_pt,
                            is_matched=is_anchor_matched,
                            active_anchor_name=active_anchor_zone,
                            active_anchor_pt=anchor_pt,
                        )
                    else:
                        draw_cyber_hands(frame, landmarks, handedness)

            active_lms = lms_list[0] if landmarks else None
            st = engine.update(cand_label, cand_prob, landmarks=active_lms)

            if st.get("confirmed"):
                confirmed = st["confirmed"]
                print(f"[Confirmed] {confirmed} -> Transcript: {engine.buffer}")
                if confirmed == "SPACE":
                    sentence_buffer.flush()
                else:
                    sentence_buffer.push(
                        GlossToken(
                            label=confirmed,
                            timestamp=now,
                            confidence=cand_prob,
                        )
                    )

            pending_tokens = sentence_buffer.peek()
            pending_labels = [t.label for t in pending_tokens]
            live_preview = IslGrammarSynthesizer.synthesize(pending_tokens) if pending_tokens else ""
            display_sentence = live_preview if live_preview else last_synthesized

            pat = motion_state.get("pattern", "STATIC")
            ai_name = "Gemini 2.0" if predictor.gemini_ai.is_available else "Topological AI"
            mode_display = f"ISL Holistic [{active_anchor_zone} | {pat}]" if show_body_map else f"ISL Dual-Handed [{pat}]"

            if not headless:
                shown = render_flagship_hud(
                    frame=frame,
                    mode_name=mode_display,
                    st=st,
                    transcript=engine.buffer,
                    fps=fps_v,
                    delegate=pipe.delegate,
                    tts_speaking=tts.is_speaking,
                    last_spoken=tts.last_spoken_text,
                    last_spoken_time=tts.last_spoken_time,
                    window_name=WINDOW_NAME,
                    pending_glosses=pending_labels,
                    synthesized_sentence=display_sentence,
                    ai_model_name=ai_name,
                )
                cv2.imshow(WINDOW_NAME, shown)

                k = cv2.waitKey(1) & 0xFF
                if k != 255:
                    if k in (ord("q"), ord("Q"), 27):
                        break
                    elif k in (ord("b"), ord("B")):
                        show_body_map = not show_body_map
                        print(f"[Body Map] Anatomical mapping {'ENABLED' if show_body_map else 'DISABLED'}")
                    elif k in (ord("c"), ord("C")):
                        engine.clear()
                        sentence_buffer.clear()
                        last_synthesized = ""
                    elif k in (32, 10, 13, ord("r"), ord("R"), ord("s"), ord("S")):
                        if sentence_buffer.peek():
                            sentence_buffer.flush()
                        elif tts.is_ready and display_sentence:
                            tts.speak_async(display_sentence)
                        elif tts.is_ready and engine.buffer:
                            tts.speak_async(engine.buffer)
                    elif k in (8, 127):  # Backspace & Delete (8=Win Bksp, 127=Linux Bksp)
                        engine.backspace()
                        if sentence_buffer.tokens:
                            sentence_buffer.tokens.pop()
                    elif k in (ord("g"), ord("G")):
                        print("\n[AI Setup] Configure Google Gemini Vision & LLM Engine")
                        print("Get a free API key at: https://aistudio.google.com/app/apikey")
                        try:
                            val = input("Paste your GEMINI_API_KEY (or Enter to skip): ").strip()
                            if val:
                                from signora.ai.gemini import GeminiAIEngine
                                GeminiAIEngine.save_api_key(val)
                                predictor.gemini_ai.api_key = val
                                print("✓ Real AI Engine Activated: Gemini 2.0 Flash!\n")
                        except Exception:
                            pass
                    elif k in (ord("f"), ord("F")):
                        fullscreen = not fullscreen
                        prop = cv2.WINDOW_FULLSCREEN if fullscreen else cv2.WINDOW_NORMAL
                        cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, prop)

            if max_frames and frame_count >= max_frames:
                break

    except KeyboardInterrupt:
        pass
    finally:
        pipe.close()
        tts.close()
        if not headless:
            cv2.destroyAllWindows()

    print(f"\nFinal Transcript: {engine.buffer!r}")
    return 0


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        prog="signora isl", description="Indian Sign Language (ISL) recognition."
    )
    parser.add_argument("--source", default="0", help="Camera index or video file (default: 0).")
    parser.add_argument("--headless", action="store_true", help="Run without graphical window.")
    parser.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech.")
    parser.add_argument(
        "--detect-every", type=int, default=1, help="Process landmarks every N frames."
    )
    parser.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing."
    )
    parser.add_argument(
        "--gemini-key", default=None, help="Google Gemini API key for Real AI vision & LLM grammar."
    )
    args = parser.parse_args(argv)

    return run_isl_live(
        source=args.source,
        headless=args.headless,
        no_tts=args.no_tts,
        detect_every=args.detect_every,
        use_fake=args.fake_camera,
        gemini_key=args.gemini_key,
    )


if __name__ == "__main__":
    raise SystemExit(main())

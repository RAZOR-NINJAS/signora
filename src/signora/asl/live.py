"""Real-time ASL fingerspelling recognition loop with HUD and Piper speech output."""

import argparse
import sys
import time
from typing import Any, List, Optional

import cv2
import numpy as np

from signora.asl.config import (
    CONFIRM_THRESHOLD,
    DWELL_MS,
    REPEAT_COOLDOWN_MS,
    SMOOTH_MS,
    SPACE_COOLDOWN_MS,
    SPACE_DWELL_MS,
)
from signora.asl.model import load_asl_predictor
from signora.core.debounce import DebounceEngine
from signora.core.features import extract_features
from signora.core.hands import HandPipeline
from signora.core.hud import draw_cyber_hands, render_flagship_hud
from signora.tts.piper import PiperTTS

WINDOW_NAME = "Signora - ASL Fingerspelling"


def run_asl_live(
    source: Any = 0,
    headless: bool = False,
    no_tts: bool = False,
    detect_every: int = 1,
    max_frames: Optional[int] = None,
    use_fake: bool = False,
) -> int:
    """Run real-time ASL fingerspelling recognition loop."""
    print("=" * 60)
    print("  SIGNORA -- American Sign Language (ASL) Fingerspelling")
    print("=" * 60)

    try:
        predictor = load_asl_predictor()
    except FileNotFoundError as exc:
        print(f"\n[Error] {exc}", file=sys.stderr)
        return 1

    engine = DebounceEngine(
        predictor=predictor,
        smooth_ms=SMOOTH_MS,
        dwell_ms=DWELL_MS,
        space_dwell_ms=SPACE_DWELL_MS,
        threshold=CONFIRM_THRESHOLD,
        repeat_cooldown_ms=REPEAT_COOLDOWN_MS,
        space_cooldown_ms=SPACE_COOLDOWN_MS,
    )

    tts = PiperTTS(enabled=not no_tts)

    pipe = HandPipeline(
        source=source,
        num_hands=1,
        mirror=True,
        detect_every=detect_every,
        use_fake=use_fake,
    )

    if not pipe.started.wait(timeout=10):
        print("[Error] Camera pipeline failed to start.", file=sys.stderr)
        pipe.close()
        return 1

    if pipe.error:
        print(f"[Error] Camera error: {pipe.error}", file=sys.stderr)
        pipe.close()
        return 1

    if not headless:
        cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(WINDOW_NAME, 960, 540)

    frame_count = 0
    t0 = time.monotonic()
    fps_v = 30.0
    fullscreen = False

    try:
        while True:
            frame, landmarks, handedness = pipe.read(timeout=3.0)
            if frame is None:
                continue

            frame_count += 1
            now = time.monotonic()
            if frame_count % 30 == 0:
                elapsed = now - t0
                if elapsed > 0:
                    fps_v = 30.0 / elapsed
                t0 = now

            cand_label = None
            cand_prob = 0.0

            if landmarks:
                hnd_str = handedness if isinstance(handedness, str) else "Right"
                feats = extract_features(landmarks, handedness=hnd_str)
                probs = predictor.probs(feats)
                top_idx = int(np.argmax(probs))
                cand_prob = float(probs[top_idx])
                cand_label = predictor.labels[top_idx]

                if not headless:
                    draw_cyber_hands(frame, landmarks, handedness)

            st = engine.update(cand_label, cand_prob)

            if not headless:
                shown = render_flagship_hud(
                    frame=frame,
                    mode_name="ASL Fingerspelling",
                    st=st,
                    transcript=engine.buffer,
                    fps=fps_v,
                    delegate=pipe.delegate,
                    tts_speaking=tts.is_speaking,
                    last_spoken=tts.last_spoken_text,
                    last_spoken_time=tts.last_spoken_time,
                )
                cv2.imshow(WINDOW_NAME, shown)

                k = cv2.waitKey(1) & 0xFF
                if k in (ord("q"), ord("Q"), 27):
                    break
                elif k in (ord("c"), ord("C")):
                    engine.clear()
                elif k in (ord("r"), ord("R")):
                    if tts.is_ready and engine.buffer:
                        tts.speak_async(engine.buffer)
                elif k == 8:  # Backspace
                    engine.backspace()
                elif k in (ord("f"), ord("F")):
                    fullscreen = not fullscreen
                    prop = cv2.WINDOW_FULLSCREEN if fullscreen else cv2.WINDOW_NORMAL
                    cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, prop)
            else:
                if st.get("confirmed"):
                    print(f"[Confirmed] {st['confirmed']} -> Transcript: {engine.buffer}")

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
        prog="signora live", description="ASL fingerspelling recognition."
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
    args = parser.parse_args(argv)

    return run_asl_live(
        source=args.source,
        headless=args.headless,
        no_tts=args.no_tts,
        detect_every=args.detect_every,
        use_fake=args.fake_camera,
    )


if __name__ == "__main__":
    raise SystemExit(main())

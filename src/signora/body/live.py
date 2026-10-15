"""Real-time Upper-Body Pose & Hand Spatial Recognition Loop."""

import argparse
import sys
import time
from typing import Any, List, Optional

import cv2
import numpy as np

from signora.core.debounce import DebounceEngine
from signora.core.hud import render_flagship_hud
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
from signora.isl.spatial import SIGN_ANCHOR_MAP, detect_body_anchor
from signora.tts.piper import PiperTTS

WINDOW_NAME = "Signora - Body Spatial Mode"


def run_body_live(
    source: Any = 0,
    headless: bool = False,
    no_tts: bool = False,
    detect_every: int = 1,
    max_frames: Optional[int] = None,
    use_fake: bool = False,
    dwell_ms: Optional[int] = None,
) -> int:
    """Run real-time whole-body pose and dual-hand spatial recognition loop."""
    print("=" * 65)
    print("  SIGNORA -- Upper-Body Pose & Hand Spatial Recognition Mode")
    print("=" * 65)
    print("  Combines static hand gestures with anatomical body placement anchors:")
    print("    [Forehead]  : FATHER, THINK, FEVER, STUDENT")
    print("    [Chin/Lips] : MOTHER, FOOD, WATER, THANK_YOU, EAT, SWEET")
    print("    [Chest]     : PLEASE, APOLOGY, LOVE, NAMASTE, HELP")
    print("    [Wrist]     : TIME, WATCH, DOCTOR, MEDICINE")
    print("    [Open Space]: HELLO, GOOD, BAD, OK, PEACE, STOP")
    print("=" * 65)

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
        dwell_ms=dwell_ms if dwell_ms is not None else DWELL_MS,
        space_dwell_ms=SPACE_DWELL_MS,
        threshold=CONFIRM_THRESHOLD,
        repeat_cooldown_ms=REPEAT_COOLDOWN_MS,
        space_cooldown_ms=SPACE_COOLDOWN_MS,
    )

    tts = PiperTTS(enabled=not no_tts)

    pipe = SpatialPipeline(
        source=source,
        mirror=True,
        detect_every=detect_every,
        use_fake=use_fake,
    )

    if not pipe.started.wait(timeout=5) or pipe.error:
        print("[Auto-Healing] Physical camera unavailable. Launching synthetic mode...", file=sys.stderr)
        pipe.close()
        pipe = SpatialPipeline(
            source=0, mirror=True, detect_every=detect_every, use_fake=True
        )
        pipe.started.wait(timeout=5)

    if not headless:
        cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL | cv2.WINDOW_GUI_NORMAL)
        cv2.resizeWindow(WINDOW_NAME, 960, 720)

    frame_count = 0
    t0 = time.monotonic()
    fps_v = 30.0
    fullscreen = False
    active_anchor_zone = "OPEN_SPACE"
    anchor_pt = None

    try:
        while True:
            frame, hand_lms, handedness, pose_lms = pipe.read(timeout=2.0)
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

            if hand_lms:
                lms_list = (
                    hand_lms
                    if isinstance(hand_lms, list) and isinstance(hand_lms[0], (list, tuple))
                    else [hand_lms]
                )
                hnd_list = handedness if isinstance(handedness, list) else [handedness]

                # Base probability from hand articulation
                feats = extract_features(lms_list, hnd_list)
                probs = predictor.probs(feats)
                top_idx = int(np.argmax(probs))
                raw_prob = float(probs[top_idx])
                raw_label = predictor.labels[top_idx]

                # Detect active body anchor for dominant hand
                active_anchor_zone, anchor_dist, anchor_pt = detect_body_anchor(
                    lms_list[0], pose_lms, w=frame.shape[1], h=frame.shape[0]
                )

                # Spatial Body Anchor Verification:
                # If target sign requires a specific body placement anchor, verify proximity
                required_anchor = SIGN_ANCHOR_MAP.get(raw_label.upper())

                if required_anchor is not None:
                    if active_anchor_zone == required_anchor:
                        cand_label = raw_label
                        cand_prob = min(1.0, raw_prob * 1.15)  # Spatial alignment boost
                    else:
                        # Anchor mismatch: suppress false trigger
                        cand_label = None
                        cand_prob = 0.0
                else:
                    cand_label = raw_label
                    cand_prob = raw_prob

                if not headless:
                    draw_cyber_spatial(
                        frame=frame,
                        hand_lms_list=lms_list,
                        handedness_list=hnd_list,
                        pose_landmarks=pose_lms,
                        active_anchor_name=active_anchor_zone if active_anchor_zone != "OPEN_SPACE" else None,
                        active_anchor_pt=anchor_pt,
                    )
            else:
                active_anchor_zone = "OPEN_SPACE"
                anchor_pt = None

            st = engine.update(cand_label, cand_prob)

            if st.get("confirmed"):
                confirmed = st["confirmed"]
                print(f"[Spatial Confirmed] {confirmed} (Anchor: {active_anchor_zone}) -> Transcript: {engine.buffer}")
                if tts.is_ready:
                    if len(confirmed) == 1:
                        tts.speak_async(confirmed)
                    elif confirmed == "SPACE":
                        words = engine.buffer.strip().split()
                        tts.speak_async(words[-1] if words else "space")
                    else:
                        tts.speak_async(confirmed)

            if not headless:
                # Render HUD with spatial placement status
                shown = render_flagship_hud(
                    frame=frame,
                    mode_name=f"Spatial ISL [{active_anchor_zone}]",
                    st=st,
                    transcript=engine.buffer,
                    fps=fps_v,
                    delegate=pipe.delegate,
                    tts_speaking=tts.is_speaking,
                    last_spoken=tts.last_spoken_text,
                    last_spoken_time=tts.last_spoken_time,
                    window_name=WINDOW_NAME,
                )

                # Add Spatial Body Anchor Banner top-center
                anchor_color = (6, 182, 212) if active_anchor_zone != "OPEN_SPACE" else (148, 163, 184)
                banner_text = f"BODY ANCHOR: {active_anchor_zone}"
                cv2.putText(
                    shown,
                    banner_text,
                    (frame.shape[1] // 2 - 120, 30),
                    cv2.FONT_HERSHEY_DUPLEX,
                    0.55,
                    anchor_color,
                    1,
                    cv2.LINE_AA,
                )

                cv2.imshow(WINDOW_NAME, shown)

                k = cv2.waitKey(1) & 0xFF
                if k != 255:
                    if k in (ord("q"), ord("Q"), 27):
                        break
                    elif k in (ord("c"), ord("C")):
                        engine.clear()
                    elif k in (32, 10, 13, ord("r"), ord("R"), ord("s"), ord("S")):
                        if tts.is_ready and engine.buffer:
                            tts.speak_async(engine.buffer)
                    elif k in (8, 127):  # Backspace
                        engine.backspace()
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

    print(f"\nFinal Spatial Transcript: {engine.buffer!r}")
    return 0


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        prog="signora body",
        description="Spatial Whole-Body Pose & Hand Gesture Sign Language Recognition.",
    )
    parser.add_argument("-s", "--source", default="0", help="Camera index, video file, or IP stream URL (default: 0).")
    parser.add_argument("--headless", action="store_true", help="Run without graphical window.")
    parser.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback.")
    parser.add_argument("--detect-every", type=int, default=1, help="Process landmarks every N frames.")
    parser.add_argument("--fake-camera", action="store_true", help="Use synthetic simulation for testing.")
    args = parser.parse_args(argv)

    return run_body_live(
        source=args.source,
        headless=args.headless,
        no_tts=args.no_tts,
        detect_every=args.detect_every,
        use_fake=args.fake_camera,
    )


if __name__ == "__main__":
    raise SystemExit(main())

"""Real-time live ASL Words recognition using MediaPipe Holistic and DTW-kNN.

Features:
- Optimized for low-power dual-core CPU: 640x480 capture, FRAME_SKIP=2, model_complexity=0.
- Motion energy and sliding-window sign start/end detection.
- High-contrast HUD overlay displaying live status, recognized word, confidence, and transcript.
"""

import argparse
import sys
import time
from collections import deque
from typing import Any, Deque, Dict, List, Optional

import cv2
import numpy as np

from signora.core.hud import draw_glass_card

from .classifier import DTWKNNClassifier
from .config import (
    CONFIDENCE_THRESHOLD,
    COOLDOWN_FRAMES,
    DEFAULT_SOURCE,
    DRAW_LANDMARKS,
    ENERGY_ACTIVE_THRESHOLD,
    ENERGY_QUIET_THRESHOLD,
    FRAME_HEIGHT,
    FRAME_SKIP,
    FRAME_WIDTH,
    KNN_NEIGHBORS,
    LANDMARK_EPSILON,
    MAX_DROPOUT_FRAME_FRACTION,
    MIN_DETECTION_CONFIDENCE,
    MIN_PRESENT_HAND_LANDMARKS,
    MIN_TRACKING_CONFIDENCE,
    MODEL_COMPLEXITY,
    MODEL_DIR,
    NUM_HAND_LANDMARKS,
    SEQUENCE_LENGTH,
    WORD_GLOSS,
)
from .dataset import generate_synthetic_dataset, load_dataset
from .normalize import (
    extract_landmarks_masked,
    impute_invalid,
    normalize_sequence,
)

WINDOW_TITLE = "Signora Words - Live Recognition"


def _warn_if_synthetic_heavy() -> None:
    """Warn when the model was trained mostly on synthetic templates.

    Synthetic sequences cluster tightly around their generating template, so a
    model dominated by them matches other synthetic samples almost exactly and
    genuine human performances much less well. Without this warning, a user
    reads the CV score as real-world accuracy and concludes the recognizer is
    broken when the dataset is.
    """
    try:
        from .dataset import load_source_tags
    except ImportError:
        return
    try:
        counts = load_source_tags()
    except Exception:
        return
    total = sum(counts.values())
    if not total:
        return
    real = sum(v for k, v in counts.items() if not k.startswith("synthetic"))
    frac = real / total
    if frac < 0.5:
        print()
        print(
            f"  WARNING: only {real}/{total} training sequences ({frac:.0%}) come from a real camera."
        )
        print("           Accuracy on synthetic data is not real-world accuracy.")
        print("           Record real samples to improve this:")
        print("             python main.py --mode words record --camera --samples 20")


def resample_sequence(sequence: np.ndarray, target_length: int = SEQUENCE_LENGTH) -> np.ndarray:
    """Linearly interpolate sequence of shape (M, ...) to (target_length, ...)."""
    seq = np.asarray(sequence, dtype=np.float32)
    M = len(seq)
    if M == target_length:
        return seq
    if M == 1:
        return np.repeat(seq, target_length, axis=0)

    orig_indices = np.linspace(0.0, 1.0, M)
    target_indices = np.linspace(0.0, 1.0, target_length)

    flat = seq.reshape(M, -1)
    D = flat.shape[1]
    resampled = np.empty((target_length, D), dtype=np.float32)

    for d in range(D):
        resampled[:, d] = np.interp(target_indices, orig_indices, flat[:, d])

    return resampled.reshape((target_length,) + seq.shape[1:])


def hand_centroids(
    landmarks: np.ndarray,
    valid: Optional[np.ndarray] = None,
) -> List[Optional[np.ndarray]]:
    """Return the centroid of each hand, or None where the hand is not tracked.

    A hand counts as tracked only when at least ``MIN_PRESENT_HAND_LANDMARKS``
    of its 21 points are present. MediaPipe reports a dropped hand as exact
    zeros, so treating those zeros as real coordinates would make a hand
    appearing or disappearing look like a large burst of motion.

    Args:
        landmarks: Array of shape (51, 3).
        valid: Optional boolean array of shape (51,) from
            :func:`extract_landmarks_masked`. If omitted, presence is inferred
            from non-zero coordinates.

    Returns:
        List of two entries (left, right); each is a (3,) array or None.
    """
    out: List[Optional[np.ndarray]] = []
    for start in (9, 30):
        idx = np.arange(start, start + NUM_HAND_LANDMARKS)
        block = landmarks[idx]
        if valid is not None:
            mask = np.asarray(valid, dtype=bool)[idx]
            if int(mask.sum()) < MIN_PRESENT_HAND_LANDMARKS:
                out.append(None)
                continue
            out.append((block * mask[:, None].astype(np.float32)).sum(axis=0) / float(mask.sum()))
        else:
            present = np.linalg.norm(block, axis=-1) > LANDMARK_EPSILON
            if int(present.sum()) < MIN_PRESENT_HAND_LANDMARKS:
                out.append(None)
                continue
            out.append(block[present].mean(axis=0))
    return out


def compute_motion_energy(
    prev_lms: np.ndarray,
    curr_lms: np.ndarray,
    prev_valid: Optional[np.ndarray] = None,
    curr_valid: Optional[np.ndarray] = None,
) -> float:
    """Motion energy of a frame pair, in shoulder-width units.

    Uses the largest displacement between the two hand centroids, which tracks
    the thing that actually drives an ASL sign.

    An earlier version averaged displacement over 44 landmarks (2 wrists plus
    all 42 hand points). That diluted wrist-driven motion by a factor of 22 and
    half-diluted every one-handed sign, so the fixed energy threshold could not
    be reached and the state machine never left REST. The hand centroid is a
    single stable point instead, so its displacement is not diluted by how many
    landmarks happen to move with it.

    Hands that are untracked in either frame are skipped, so a hand appearing
    or disappearing contributes no motion.

    Args:
        prev_lms: Previous frame landmarks, shape (51, 3).
        curr_lms: Current frame landmarks, shape (51, 3).
        prev_valid: Optional validity mask for the previous frame.
        curr_valid: Optional validity mask for the current frame.

    Returns:
        Maximum hand-centroid displacement, or 0.0 when no hand is trackable.
    """
    prev_c = hand_centroids(prev_lms, prev_valid)
    curr_c = hand_centroids(curr_lms, curr_valid)

    best = 0.0
    for p, c in zip(prev_c, curr_c):
        if p is None or c is None:
            continue  # hand untracked on one side: no real displacement to measure
        best = max(best, float(np.linalg.norm(c - p)))
    return best


def draw_hud(
    frame: np.ndarray,
    status: str,
    last_word: Optional[str],
    last_conf: float,
    energy: float,
    transcript: List[str],
    fps: float,
    details: Optional[Dict[str, Any]] = None,
    segment_len: int = 0,
    dropped: int = 0,
) -> None:
    """Draw high-contrast HUD overlay on live frame.

    Colors are given here in BGR order to match the OpenCV frame that the
    MediaPipe results are drawn onto. (Passing RGB here would render every
    panel in swapped colours against the BGR video.)

    Args:
        frame: BGR frame, modified in place.
        status: One of "REST", "SIGNING", "CONFIRMED".
        last_word: Most recently recognized word, if any.
        last_conf: Confidence of that word in [0, 1].
        energy: Current smoothed motion energy.
        transcript: Words recognized so far.
        fps: Frames per second.
        details: Optional classifier diagnostics dict from the last attempt.
        segment_len: Frames captured in the in-progress segment.
        dropped: Frames in the in-progress segment with no visible shoulders.
    """
    h, w = frame.shape[:2]

    # Palette in BGR.
    INK = (252, 250, 248)  # near-white
    MUTED = (184, 163, 148)  # slate
    PANEL = (20, 14, 11)  # near-black
    EDGE = (59, 41, 30)  # border
    SKY = (248, 189, 56)  # amber-ish blue in BGR
    EMERALD = (129, 185, 16)  # green
    AMBER = (11, 158, 245)  # orange in BGR
    ROSE = (68, 68, 235)  # red in BGR

    # 1. Top bar
    draw_glass_card(frame, 12, 10, w - 24, 44, alpha=0.82, radius=12)

    cv2.putText(frame, "SIGNORA", (26, 38), cv2.FONT_HERSHEY_DUPLEX, 0.65, INK, 1, cv2.LINE_AA)
    cv2.putText(frame, "WORDS", (132, 38), cv2.FONT_HERSHEY_DUPLEX, 0.65, SKY, 1, cv2.LINE_AA)

    if status == "SIGNING":
        badge_col, badge_txt = SKY, "SIGNING..."
    elif status == "CONFIRMED":
        badge_col, badge_txt = EMERALD, "CONFIRMED"
    else:
        badge_col, badge_txt = MUTED, "READY"

    cv2.putText(
        frame,
        f"[{badge_txt}]",
        (230, 38),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
        badge_col,
        2,
        cv2.LINE_AA,
    )

    # Energy meter with an explicit threshold tick, so the trigger level is visible.
    energy_norm = min(1.0, energy / (ENERGY_ACTIVE_THRESHOLD * 2.5))
    bar_w, bar_x = 120, w - 155
    cv2.rectangle(frame, (bar_x, 24), (bar_x + bar_w, 36), EDGE, -1)
    fill_col = EMERALD if energy < ENERGY_ACTIVE_THRESHOLD else AMBER
    cv2.rectangle(frame, (bar_x, 24), (bar_x + int(bar_w * energy_norm), 36), fill_col, -1)
    tick_x = bar_x + int(bar_w / 2.5)
    cv2.line(frame, (tick_x, 21), (tick_x, 39), INK, 1)
    cv2.putText(
        frame, "MOTION", (bar_x - 55, 33), cv2.FONT_HERSHEY_SIMPLEX, 0.40, MUTED, 1, cv2.LINE_AA
    )

    # 2. Reading card
    if last_word:
        gloss = WORD_GLOSS.get(last_word, last_word.lower())
        card_w, card_h = 240, 75
        card_x, card_y = 16, 68
        draw_glass_card(frame, card_x, card_y, card_w, card_h, alpha=0.88, radius=12)

        cv2.putText(
            frame,
            last_word,
            (card_x + 14, card_y + 36),
            cv2.FONT_HERSHEY_DUPLEX,
            0.9,
            AMBER,
            2,
            cv2.LINE_AA,
        )
        cv2.putText(
            frame,
            f"'{gloss}'",
            (card_x + 14, card_y + 58),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            MUTED,
            1,
            cv2.LINE_AA,
        )

        cv2.putText(
            frame,
            f"{int(last_conf * 100)}%",
            (card_x + card_w - 55, card_y + 36),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            EMERALD,
            2,
            cv2.LINE_AA,
        )
        cv2.rectangle(
            frame,
            (card_x + card_w - 65, card_y + 46),
            (card_x + card_w - 15, card_y + 52),
            EDGE,
            -1,
        )
        cv2.rectangle(
            frame,
            (card_x + card_w - 65, card_y + 46),
            (card_x + card_w - 65 + int(50 * min(1.0, last_conf)), card_y + 52),
            EMERALD,
            -1,
        )

    # 3. Diagnostics panel: makes a silent rejection explainable.
    if details:
        p_x, p_y, p_w, p_h = 16, 154, 246, 88
        cv2.rectangle(frame, (p_x, p_y), (p_x + p_w, p_y + p_h), PANEL, -1)
        cv2.rectangle(frame, (p_x, p_y), (p_x + p_w, p_y + p_h), EDGE, 1)

        reason = details.get("reject_reason")
        head_col = ROSE if reason else EMERALD
        head = "REJECTED" if reason else "MATCHED"
        cv2.putText(
            frame,
            head,
            (p_x + 10, p_y + 17),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.44,
            head_col,
            1,
            cv2.LINE_AA,
        )

        best = details.get("best_dist", float("nan"))
        runner = details.get("runner_up_dist", float("nan"))
        rows = [
            f"best d  {best:.3f}",
            f"next d  {runner:.3f}",
            f"margin  {details.get('separation', 0.0):.2f}  vote {details.get('vote_prob', 0.0):.2f}",
            f"segsz {segment_len}/{SEQUENCE_LENGTH}  drop {dropped}",
        ]
        for i, row in enumerate(rows):
            cv2.putText(
                frame,
                row,
                (p_x + 10, p_y + 33 + i * 13),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.36,
                MUTED,
                1,
                cv2.LINE_AA,
            )
        if reason and "too far" in str(reason):
            cv2.putText(
                frame,
                "input unlike any known sign",
                (p_x + 10, p_y + p_h - 5),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.34,
                ROSE,
                1,
                cv2.LINE_AA,
            )

    # 4. Bottom panel (Floating glass pill)
    bot_h = 58
    by = h - bot_h - 10
    draw_glass_card(frame, 12, by, w - 24, bot_h, alpha=0.86, radius=14)

    trans_text = " ".join(transcript[-8:]) if transcript else "(start signing to form a sentence)"
    trans_col = EMERALD if transcript else MUTED
    cv2.putText(
        frame,
        f"TRANSCRIPT: {trans_text}",
        (26, by + 26),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.58,
        trans_col,
        2 if transcript else 1,
        cv2.LINE_AA,
    )

    cv2.putText(
        frame,
        f"{fps:.1f} FPS",
        (w - 95, by + 45),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.42,
        MUTED,
        1,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        "Controls: [Q] Quit   [C] Clear   [D] Diagnostics",
        (26, by + 46),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.42,
        MUTED,
        1,
        cv2.LINE_AA,
    )


def run_live(
    source: str = DEFAULT_SOURCE,
    classifier: Optional[DTWKNNClassifier] = None,
    threshold: Optional[float] = None,
    max_frames: Optional[int] = None,
    headless: bool = False,
    display: bool = True,
) -> int:
    """Run real-time ASL Words live recognition.

    Args:
        source: Video capture source (device index '0' or video file path).
        classifier: Fitted DTWKNNClassifier. If None, loaded from disk.
        threshold: Confidence threshold. Overrides classifier threshold if given.
        max_frames: Optional maximum frames to process (useful for tests).
        headless: If True, skips cv2 window display.
        display: If False, skips cv2.imshow.

    Returns:
        Exit code (0 for clean termination).
    """
    try:
        import mediapipe as mp
    except ImportError:
        print("Error: mediapipe is not installed.")
        return 1

    # Load or initialize classifier
    if classifier is None:
        model_path = MODEL_DIR / "words_classifier.npz"
        if model_path.exists():
            print(f"Loading classifier from {model_path}...")
            classifier = DTWKNNClassifier.load(model_path)
        else:
            print("No saved classifier found. Loading dataset to train classifier...")
            X, y = load_dataset()
            if len(X) == 0:
                print("No samples found. Generating synthetic dataset...")
                generate_synthetic_dataset(samples_per_word=10)
                X, y = load_dataset()
            classifier = DTWKNNClassifier(
                n_neighbors=KNN_NEIGHBORS, confidence_threshold=CONFIDENCE_THRESHOLD
            )
            classifier.fit(X, y)
            classifier.save(model_path)

    if threshold is not None:
        classifier.confidence_threshold = threshold

    # Open Video Source
    src_val = int(source) if source.isdigit() else source
    if isinstance(src_val, int) and sys.platform == "win32":
        cap = cv2.VideoCapture(src_val, cv2.CAP_DSHOW)
        if not cap.isOpened():
            cap.release()
            cap = cv2.VideoCapture(src_val)
    elif isinstance(src_val, int) and sys.platform.startswith("linux"):
        cap = cv2.VideoCapture(src_val, cv2.CAP_V4L2)
        if not cap.isOpened():
            cap.release()
            cap = cv2.VideoCapture(src_val)
    else:
        cap = cv2.VideoCapture(src_val)

    if not cap.isOpened():
        print(f"Error: Could not open video source {source!r}")
        print("Tip: If running without a webcam, test with a recorded video or words.eval.")
        return 1

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

    # Initialize MediaPipe Holistic
    try:
        mp_holistic = mp.solutions.holistic
    except AttributeError:
        import mediapipe.python.solutions.holistic as mp_holistic

    holistic = mp_holistic.Holistic(
        model_complexity=MODEL_COMPLEXITY,
        min_detection_confidence=MIN_DETECTION_CONFIDENCE,
        min_tracking_confidence=MIN_TRACKING_CONFIDENCE,
    )
    # Note: Legacy mp.solutions.holistic doesn't expose GPU delegate.
    # For GPU acceleration, would need to migrate to MediaPipe Tasks API
    # (separate HandLandmarker + PoseLandmarker), which is a larger refactor.
    mp_draw = mp.solutions.drawing_utils

    if not headless and display:
        cv2.namedWindow(WINDOW_TITLE, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(WINDOW_TITLE, FRAME_WIDTH, FRAME_HEIGHT)

    # State variables
    frame_count = 0
    fps_time = time.time()
    fps = 0.0
    fps_counter = 0

    sliding_window: Deque[np.ndarray] = deque(maxlen=SEQUENCE_LENGTH)
    active_segment: List[np.ndarray] = []
    dropped_frames = 0  # frames in the current segment that could not be normalized
    state = "REST"  # REST, SIGNING, CONFIRMED
    cooldown = 0
    smooth_energy = 0.0
    peak_energy = 0.0
    prev_landmarks: Optional[np.ndarray] = None
    prev_valid: Optional[np.ndarray] = None
    smoothed_landmarks: Optional[np.ndarray] = None  # EMA-smoothed landmarks for current frame

    last_word: Optional[str] = None
    last_conf: float = 0.0
    last_details: Dict[str, Any] = {}
    transcript: List[str] = []
    show_debug = True

    print("=" * 60)
    print("  SIGNORA WORDS - Live Recognition")
    print(f"  Vocabulary ({len(classifier.classes_)} words): {', '.join(classifier.classes_)}")
    print(
        f"  Confidence threshold: {classifier.confidence_threshold:.3f} (relative class separation)"
    )
    if classifier.inter_scale_:
        print(
            f"  Calibration: same-sign d~{classifier.intra_scale_:.3f}, "
            f"different-sign d~{classifier.inter_scale_:.3f}, "
            f"reject beyond {classifier.max_accept_distance_ratio:g}x"
        )
    _warn_if_synthetic_heavy()
    print("  Controls: [Q] Quit   [C] Clear transcript   [D] Toggle diagnostics")
    print("=" * 60)

    try:
        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                break

            frame_count += 1
            fps_counter += 1
            now = time.time()
            if now - fps_time >= 1.0:
                fps = fps_counter / (now - fps_time)
                fps_counter = 0
                fps_time = now

            # Process detection at FRAME_SKIP rate (including frame 1)
            if (frame_count - 1) % FRAME_SKIP == 0:
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                results = holistic.process(rgb)

                raw_lms, valid_mask = extract_landmarks_masked(results)

                # A frame is usable only if both shoulders were tracked, since the
                # whole feature space is defined relative to them. Imputing a
                # missing shoulder would invent a normalization for that frame.
                shoulders_ok = bool(valid_mask[1] and valid_mask[2])

                if not shoulders_ok:
                    dropped_frames += 1
                else:
                    # Fill untracked points (e.g. a dropped hand) from the nearest
                    # tracked anchor so they do not normalize into large artifacts.
                    raw_lms = impute_invalid(raw_lms, valid_mask)
                    sliding_window.append(raw_lms)

                # Draw landmark skeleton preview (skip in headless mode or when disabled for performance)
                if DRAW_LANDMARKS and display and not headless:
                    if results.pose_landmarks:
                        mp_draw.draw_landmarks(
                            frame,
                            results.pose_landmarks,
                            mp_holistic.POSE_CONNECTIONS,
                            mp_draw.DrawingSpec(color=(10, 110, 80), thickness=1, circle_radius=1),
                            mp_draw.DrawingSpec(color=(121, 176, 80), thickness=1, circle_radius=1),
                        )
                    if results.left_hand_landmarks:
                        mp_draw.draw_landmarks(
                            frame,
                            results.left_hand_landmarks,
                            mp_holistic.HAND_CONNECTIONS,
                            mp_draw.DrawingSpec(color=(76, 22, 121), thickness=2, circle_radius=2),
                            mp_draw.DrawingSpec(color=(250, 44, 121), thickness=2, circle_radius=1),
                        )
                    if results.right_hand_landmarks:
                        mp_draw.draw_landmarks(
                            frame,
                            results.right_hand_landmarks,
                            mp_holistic.HAND_CONNECTIONS,
                            mp_draw.DrawingSpec(color=(66, 117, 245), thickness=2, circle_radius=2),
                            mp_draw.DrawingSpec(color=(230, 66, 245), thickness=2, circle_radius=1),
                        )

                # Motion energy calculation, dropout-aware
                # Use smoothed landmarks for motion energy and state tracking
                smoothed_lms = raw_lms
                if prev_landmarks is not None:
                    inst_energy = compute_motion_energy(
                        prev_landmarks, raw_lms, prev_valid, valid_mask
                    )
                    smooth_energy = 0.7 * smooth_energy + 0.3 * inst_energy
                    # Temporal EMA smoothing of landmarks to reduce jitter (alpha=0.3)
                    smoothed_lms = 0.7 * prev_landmarks + 0.3 * raw_lms
                prev_landmarks = smoothed_lms
                smoothed_landmarks = smoothed_lms
                prev_valid = valid_mask

                # Gesture state machine
                if cooldown > 0:
                    cooldown -= 1
                    if cooldown == 0 and state == "CONFIRMED":
                        state = "REST"

                elif state == "REST":
                    if smooth_energy >= ENERGY_ACTIVE_THRESHOLD:
                        state = "SIGNING"
                        peak_energy = smooth_energy
                        dropped_frames = 0
                        # Seed with recent frames
                        active_segment = list(sliding_window)[-5:]

                elif state == "SIGNING":
                    # Use smoothed landmarks for the active segment
                    if smoothed_landmarks is not None:
                        active_segment.append(smoothed_landmarks)
                    peak_energy = max(peak_energy, smooth_energy)

                    # Trigger condition:
                    # 1. Motion slowed down to quiet rest after sufficient duration
                    motion_stopped = (
                        smooth_energy < ENERGY_QUIET_THRESHOLD and len(active_segment) >= 10
                    )
                    # 2. Continuous signing: velocity valley after motion peak (inter-sign coarticulation)
                    velocity_valley = (
                        len(active_segment) >= 14
                        and smooth_energy < peak_energy * 0.5
                        and peak_energy >= ENERGY_ACTIVE_THRESHOLD
                    )
                    # 3. Buffer full reached maximum length
                    buffer_full = len(active_segment) >= SEQUENCE_LENGTH

                    if motion_stopped or velocity_valley or buffer_full:
                        seg_arr = np.stack(active_segment, axis=0)
                        total = len(seg_arr)
                        dropout_frac = dropped_frames / total if total else 1.0

                        if dropout_frac > MAX_DROPOUT_FRAME_FRACTION:
                            # Too much of the sign was untrackable to judge fairly.
                            print(
                                f"  Segment discarded: {dropout_frac:.0%} of frames had no visible shoulders."
                            )
                            state = "REST"
                            active_segment = []
                            peak_energy = 0.0
                            dropped_frames = 0
                        else:
                            resampled = resample_sequence(seg_arr, target_length=SEQUENCE_LENGTH)
                            norm_seq = normalize_sequence(resampled, per_frame=True)

                            pred, conf, details = classifier.predict_single(norm_seq)
                            last_details = details

                            if pred is not None and conf >= classifier.confidence_threshold:
                                last_word = pred
                                last_conf = conf
                                transcript.append(pred)
                                state = "CONFIRMED"
                                cooldown = COOLDOWN_FRAMES
                                active_segment = []
                                peak_energy = 0.0
                                dropped_frames = 0
                                print(
                                    f"Recognized word: {pred} (conf {conf:.2f}, "
                                    f"d={details['best_dist']:.3f} vs next {details['runner_up_dist']:.3f})"
                                )
                            elif motion_stopped or buffer_full:
                                # Explain the rejection instead of failing silently.
                                print(
                                    f"  Segment rejected: {details.get('raw_label')} "
                                    f"conf={conf:.2f} (need {classifier.confidence_threshold:.2f}), "
                                    f"d={details['best_dist']:.3f}, next={details['runner_up_dist']:.3f}"
                                    f"{' - ' + details['reject_reason'] if details.get('reject_reason') else ''}"
                                )
                                state = "REST"
                                active_segment = []
                                peak_energy = 0.0
                                dropped_frames = 0
                            # If velocity_valley was reached but confidence was below threshold,
                            # continue accumulating frames as the sign may still be evolving.

            # Draw HUD
            draw_hud(
                frame=frame,
                status=state,
                last_word=last_word,
                last_conf=last_conf,
                energy=smooth_energy,
                transcript=transcript,
                fps=fps,
                details=last_details if show_debug else None,
                segment_len=len(active_segment),
                dropped=dropped_frames,
            )

            if not headless and display:
                cv2.imshow(WINDOW_TITLE, frame)
                k = cv2.waitKey(1) & 0xFF
                if k in (ord("q"), ord("Q"), 27):
                    break
                elif k in (ord("c"), ord("C")):
                    transcript.clear()
                    last_word = None
                    last_conf = 0.0
                    last_details = {}
                elif k in (ord("d"), ord("D")):
                    show_debug = not show_debug

            if max_frames is not None and frame_count >= max_frames:
                break

    except KeyboardInterrupt:
        pass
    finally:
        cap.release()
        if not headless and display:
            cv2.destroyAllWindows()
        holistic.close()

    print(f"\nFinal transcript: {' '.join(transcript)}")
    return 0


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        prog="words.live",
        description="Real-time ASL Words live recognition.",
        epilog=(
            "Environment variables:\n"
            "  SIGNORA_WORDS_DETECT_EVERY=N   Run detector every N frames (default: 2)\n"
            "  SIGNORA_WORDS_GPU=1            Enable GPU delegate (experimental)\n"
            "  SIGNORA_WORDS_DRAW_LANDMARKS=0 Disable landmark drawing for max FPS"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--source",
        default=DEFAULT_SOURCE,
        help="Video capture source (device index '0' or file path, default: 0).",
    )
    parser.add_argument(
        "--threshold",
        type=float,
        default=CONFIDENCE_THRESHOLD,
        help="Confidence threshold for recognition (default: 0.18).",
    )
    parser.add_argument(
        "--max-frames",
        type=int,
        default=None,
        help="Maximum frames to process before exiting (useful for testing).",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run without GUI window.",
    )

    args = parser.parse_args(argv)

    return run_live(
        source=args.source,
        threshold=args.threshold,
        max_frames=args.max_frames,
        headless=args.headless,
    )


if __name__ == "__main__":
    sys.exit(main())

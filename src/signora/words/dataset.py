"""Dataset management and synthetic sequence generation for ASL Words.

Stores 30-frame landmark sequences for the 16-word ASL motion vocabulary.
Supports loading, appending, saving, and generating synthetic sequences so the
system can be trained and evaluated without requiring a live webcam.
"""

from pathlib import Path
from typing import Dict, List, Optional, Tuple

import numpy as np

from .config import (
    DATA_DIR,
    FEATURE_DIM,
    NUM_HAND_LANDMARKS,
    SEQUENCE_LENGTH,
    TOTAL_LANDMARKS,
    WORDS,
)
from .normalize import (
    normalize_sequence,
)

SAMPLE_DIR = DATA_DIR / "samples"


def get_sample_dir() -> Path:
    """Return Path to sample storage directory, creating it if needed."""
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    return SAMPLE_DIR


def path_for_word(word: str) -> Path:
    """Return file path for a word's numpy sample array."""
    safe = word.upper().replace(" ", "_")
    return get_sample_dir() / f"{safe}.npy"


def sources_path_for_word(word: str) -> Path:
    """Return file path for a word's sources numpy array."""
    safe = word.upper().replace(" ", "_")
    return get_sample_dir() / f"{safe}.sources.npy"


def save_word_samples(
    word: str,
    sequences: np.ndarray,
    source: str = "synthetic",
    append: bool = True,
) -> int:
    """Save or append landmark sequences for a given word.

    Args:
        word: The ASL word label (e.g. 'HELLO').
        sequences: Array of shape (N, 30, 51, 3) or (N, 30, 153).
        source: Provenance tag (e.g. 'synthetic', 'cam0').
        append: If True, appends to existing samples on disk.

    Returns:
        Total number of samples for the word after saving.
    """
    word = word.upper()
    arr = np.asarray(sequences, dtype=np.float32)

    # Normalize dimensions to (N, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3)
    if arr.ndim == 2 and arr.shape[0] == SEQUENCE_LENGTH and arr.shape[1] == FEATURE_DIM:
        arr = arr.reshape(1, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3)
    elif arr.ndim == 3 and arr.shape[1] == SEQUENCE_LENGTH and arr.shape[2] == FEATURE_DIM:
        arr = arr.reshape(-1, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3)
    elif (
        arr.ndim == 3
        and arr.shape[0] == SEQUENCE_LENGTH
        and arr.shape[1] == TOTAL_LANDMARKS
        and arr.shape[2] == 3
    ):
        # Single sequence passed
        arr = arr[None, ...]
        if arr.shape[-1] == FEATURE_DIM:
            arr = arr.reshape(1, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3)

    if (
        arr.ndim != 4
        or arr.shape[1] != SEQUENCE_LENGTH
        or arr.shape[2] != TOTAL_LANDMARKS
        or arr.shape[3] != 3
    ):
        raise ValueError(
            f"Expected sequences of shape (N, {SEQUENCE_LENGTH}, {TOTAL_LANDMARKS}, 3), got {arr.shape}"
        )

    path = path_for_word(word)
    spath = sources_path_for_word(word)

    if append and path.exists():
        existing = np.load(path)
        arr = np.concatenate([existing, arr], axis=0)

    np.save(path, arr)

    # Provenance tags
    new_count = len(arr)
    tags = np.array([source] * new_count, dtype=object)
    if append and spath.exists():
        existing_tags = np.load(spath, allow_pickle=True)
        n_new = new_count - len(existing_tags)
        if n_new > 0:
            tags = np.concatenate([existing_tags, np.array([source] * n_new, dtype=object)])
        else:
            tags = existing_tags
    np.save(spath, tags)

    return new_count


def load_word_samples(word: str) -> Optional[np.ndarray]:
    """Load samples for a single word. Returns None if file does not exist."""
    path = path_for_word(word)
    if not path.exists():
        return None
    return np.load(path)


def load_dataset(
    words: Optional[List[str]] = None,
    flatten: bool = True,
) -> Tuple[np.ndarray, np.ndarray]:
    """Load full dataset for training or evaluation.

    Args:
        words: List of words to include. If None, includes all words with samples.
        flatten: If True, flattens sequences to (Total, 30, 153). Otherwise (Total, 30, 51, 3).

    Returns:
        Tuple of (X, y):
        - X: np.ndarray of shape (Total, 30, 153) or (Total, 30, 51, 3)
        - y: np.ndarray of shape (Total,) containing str labels
    """
    vocab = words or WORDS
    X_list: List[np.ndarray] = []
    y_list: List[str] = []

    for word in vocab:
        samples = load_word_samples(word)
        if samples is not None and len(samples) > 0:
            for s in samples:
                X_list.append(s)
                y_list.append(word)

    if not X_list:
        empty_shape = (
            (0, SEQUENCE_LENGTH, FEATURE_DIM)
            if flatten
            else (0, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3)
        )
        return (
            np.empty(empty_shape, dtype=np.float32),
            np.empty(0, dtype=object),
        )

    X = np.stack(X_list, axis=0)
    if flatten:
        X = X.reshape(len(X), SEQUENCE_LENGTH, FEATURE_DIM)
    y = np.array(y_list, dtype=object)
    return X, y


def load_source_tags(words: Optional[List[str]] = None) -> Dict[str, int]:
    """Return the number of stored sequences per provenance tag.

    Samples carry a source sidecar (``<WORD>.sources.npy``) recording where each
    sequence came from, e.g. "synthetic" or "webcam0". Synthetic sequences match
    their generating template almost exactly, so any accuracy figure computed
    over a synthetic-dominated dataset overstates real-world performance. This
    makes that split visible instead of hidden.

    Args:
        words: Words to include. Defaults to all WORDS.

    Returns:
        Dict mapping source tag to sequence count, ordered by descending count.
    """
    counts: Dict[str, int] = {}
    for word in words or WORDS:
        spath = sources_path_for_word(word)
        if not spath.exists():
            continue
        try:
            tags = np.load(spath, allow_pickle=True)
        except (ValueError, OSError):
            continue
        for tag in tags:
            key = str(tag)
            counts[key] = counts.get(key, 0) + 1
    return dict(sorted(counts.items(), key=lambda kv: -kv[1]))


def load_sample_sources(
    words: Optional[List[str]] = None,
) -> List[str]:
    """Return the provenance tag for every sequence in ``load_dataset`` order.

    Returns one tag per sequence, matching the order samples are stacked by
    ``load_dataset`` (vocab order, then sequence order within each word).
    Sequences without a sidecar are tagged ``"unknown"``.
    """
    vocab = words or WORDS
    tags: List[str] = []
    for word in vocab:
        samples = load_word_samples(word)
        if samples is None or len(samples) == 0:
            continue
        spath = sources_path_for_word(word)
        word_tags: List[str] = []
        if spath.exists():
            try:
                raw = np.load(spath, allow_pickle=True)
                word_tags = [str(t) for t in raw]
            except (ValueError, OSError):
                word_tags = []
        if len(word_tags) != len(samples):
            word_tags = ["unknown"] * len(samples)
        tags.extend(word_tags)
    return tags


def count_samples() -> Dict[str, int]:
    """Return dictionary of sample count per word."""
    counts = {}
    for w in WORDS:
        path = path_for_word(w)
        counts[w] = int(np.load(path, mmap_mode="r").shape[0]) if path.exists() else 0
    return counts


# ---------------------------------------------------------------------------
# Synthetic Sample Generator
# ---------------------------------------------------------------------------


def _ease_in_out(t: np.ndarray) -> np.ndarray:
    """Smooth cosine ease-in-out curve for natural human motion."""
    return 0.5 * (1.0 - np.cos(np.pi * t))


def _generate_synthetic_sequence_for_word(
    word: str,
    rng: np.random.Generator,
    T: int = SEQUENCE_LENGTH,
) -> np.ndarray:
    """Generate a realistic 30-frame landmark sequence for an ASL word.

    Creates distinct, kinematically sound trajectories for pose and hands,
    normalized to shoulder reference.
    """
    t_lin = np.linspace(0.0, 1.0, T)
    seq = np.zeros((T, TOTAL_LANDMARKS, 3), dtype=np.float32)

    # 1. Base torso/body reference landmarks
    # Body indices: 0: nose, 1: L-shoulder, 2: R-shoulder, 3: L-elbow, 4: R-elbow,
    #               5: L-wrist, 6: R-wrist, 7: L-hip, 8: R-hip
    seq[:, 0] = [0.0, -0.45, 0.0]  # Nose
    seq[:, 1] = [-0.5, 0.0, 0.0]  # Left shoulder
    seq[:, 2] = [0.5, 0.0, 0.0]  # Right shoulder
    seq[:, 3] = [-0.65, 0.45, 0.0]  # Left elbow resting
    seq[:, 4] = [0.65, 0.45, 0.0]  # Right elbow resting
    seq[:, 5] = [-0.4, 0.8, -0.1]  # Left wrist resting
    seq[:, 6] = [0.4, 0.8, -0.1]  # Right wrist resting
    seq[:, 7] = [-0.3, 1.0, 0.0]  # Left hip
    seq[:, 8] = [0.3, 1.0, 0.0]  # Right hip

    # Left hand resting template (indices 9..29)
    # Right hand resting template (indices 30..50)
    for i in range(NUM_HAND_LANDMARKS):
        offset = i * 0.005
        seq[:, 9 + i] = seq[:, 5] + np.array([-0.05, 0.05 + offset, 0.0])
        seq[:, 30 + i] = seq[:, 6] + np.array([0.05, 0.05 + offset, 0.0])

    # 2. Characteristic word motion trajectory definition
    rw = np.zeros((T, 3), dtype=np.float32)  # Right wrist
    lw = np.zeros((T, 3), dtype=np.float32)  # Left wrist
    rh_state = 0.0  # 0.0=open, 1.0=closed fist, -0.5=pinch
    lh_state = 0.0

    if word == "HELLO":
        # Wave/salute: Right hand starts near right temple/forehead, arcs outward
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.35 + 0.35 * e
        rw[:, 1] = -0.45 - 0.15 * e + 0.05 * np.sin(4 * np.pi * t_lin)
        rw[:, 2] = -0.2 - 0.1 * e
        lw[:] = seq[:, 5]  # Left hand at rest

    elif word == "THANKYOU":
        # Right fingertips touch chin/mouth then move forward/down toward recipient
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.05 * (1.0 - e)
        rw[:, 1] = -0.35 + 0.30 * e
        rw[:, 2] = -0.1 - 0.40 * e
        lw[:] = seq[:, 5]

    elif word == "PLEASE":
        # Open right hand rubs clockwise circle on chest
        theta = 2.5 * np.pi * t_lin
        rw[:, 0] = 0.0 + 0.18 * np.cos(theta)
        rw[:, 1] = 0.1 + 0.14 * np.sin(theta)
        rw[:, 2] = -0.25
        lw[:] = seq[:, 5]

    elif word == "YES":
        # Right hand fist nods up and down twice
        nod = np.sin(3.5 * np.pi * t_lin)
        rw[:, 0] = 0.30
        rw[:, 1] = -0.05 + 0.15 * nod
        rw[:, 2] = -0.30
        rh_state = 1.0  # Fist
        lw[:] = seq[:, 5]

    elif word == "NO":
        # Right hand index/middle fingers snap against thumb (closing)
        snap = np.sin(np.pi * t_lin)
        rw[:, 0] = 0.25 + 0.05 * snap
        rw[:, 1] = -0.20 + 0.05 * snap
        rw[:, 2] = -0.30
        rh_state = 0.8 * snap  # Snapping close
        lw[:] = seq[:, 5]

    elif word == "HELP":
        # Left hand flat palm up at chest; right fist thumbs-up on left palm, lift up together
        e = _ease_in_out(t_lin)
        lw[:, 0] = -0.05
        lw[:, 1] = 0.35 - 0.30 * e
        lw[:, 2] = -0.25

        rw[:, 0] = 0.05
        rw[:, 1] = 0.25 - 0.30 * e
        rw[:, 2] = -0.25
        rh_state = 1.0  # Fist

    elif word == "SORRY":
        # Right fist circles on center of chest
        theta = 2.5 * np.pi * t_lin
        rw[:, 0] = 0.0 + 0.16 * np.cos(theta)
        rw[:, 1] = 0.1 + 0.12 * np.sin(theta)
        rw[:, 2] = -0.22
        rh_state = 1.0  # Fist
        lw[:] = seq[:, 5]

    elif word == "GOOD":
        # Right flat hand touches chin, then moves down onto flat left palm
        e = _ease_in_out(t_lin)
        lw[:, 0] = -0.10
        lw[:, 1] = 0.30
        lw[:, 2] = -0.25

        rw[:, 0] = 0.0 - 0.10 * e
        rw[:, 1] = -0.35 + 0.60 * e
        rw[:, 2] = -0.10 - 0.15 * e

    elif word == "BAD":
        # Right hand at chin moves down and twists away with palm down
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.0 + 0.30 * e
        rw[:, 1] = -0.35 + 0.50 * e
        rw[:, 2] = -0.10 - 0.20 * e
        lw[:] = seq[:, 5]

    elif word == "NAME":
        # Both hands H-shape tap across each other twice
        tap = np.abs(np.sin(3.0 * np.pi * t_lin))
        lw[:, 0] = -0.08 - 0.08 * tap
        lw[:, 1] = 0.15
        lw[:, 2] = -0.25

        rw[:, 0] = 0.08 + 0.08 * tap
        rw[:, 1] = 0.12
        rw[:, 2] = -0.25

    elif word == "MORE":
        # Both hands pinched tap together at center repeatedly
        tap = np.abs(np.cos(3.5 * np.pi * t_lin))
        lw[:, 0] = -0.25 + 0.20 * (1.0 - tap)
        lw[:, 1] = 0.20
        lw[:, 2] = -0.25
        lh_state = -0.5  # Pinched

        rw[:, 0] = 0.25 - 0.20 * (1.0 - tap)
        rw[:, 1] = 0.20
        rw[:, 2] = -0.25
        rh_state = -0.5  # Pinched

    elif word == "LOVE":
        # Both hands cross fists over chest (hug)
        e = _ease_in_out(t_lin)
        lw[:, 0] = -0.4 + 0.55 * e  # Moves to right side
        lw[:, 1] = 0.4 - 0.30 * e
        lw[:, 2] = -0.20

        rw[:, 0] = 0.4 - 0.55 * e  # Moves to left side
        rw[:, 1] = 0.4 - 0.30 * e
        rw[:, 2] = -0.22
        lh_state = 1.0
        rh_state = 1.0

    elif word == "EAT":
        # Right hand pinched moves to mouth repeatedly
        cycle = np.abs(np.sin(3.0 * np.pi * t_lin))
        rw[:, 0] = 0.02
        rw[:, 1] = -0.10 - 0.25 * cycle
        rw[:, 2] = -0.25 + 0.15 * cycle
        rh_state = -0.5  # Pinched fingers
        lw[:] = seq[:, 5]

    elif word == "DRINK":
        # Right hand C-shape lifts to mouth and tilts
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.10 * (1.0 - e)
        rw[:, 1] = 0.20 - 0.55 * e
        rw[:, 2] = -0.25 + 0.15 * e
        lw[:] = seq[:, 5]

    elif word == "WHERE":
        # Both hands open palm up shake gently side-to-side (shrug)
        shake = np.sin(4.0 * np.pi * t_lin)
        lw[:, 0] = -0.35 + 0.10 * shake
        lw[:, 1] = 0.25
        lw[:, 2] = -0.30

        rw[:, 0] = 0.35 + 0.10 * shake
        rw[:, 1] = 0.25
        rw[:, 2] = -0.30

    elif word == "FINISHED":
        # Both hands flick outward and downward
        e = _ease_in_out(t_lin)
        lw[:, 0] = -0.15 - 0.35 * e
        lw[:, 1] = 0.10 + 0.25 * e
        lw[:, 2] = -0.20 - 0.20 * e

        rw[:, 0] = 0.15 + 0.35 * e
        rw[:, 1] = 0.10 + 0.25 * e
        rw[:, 2] = -0.20 - 0.20 * e

    elif word == "GOOD_MORNING":
        # Compound: hand touches chin (good), then arcs up and outward (morning).
        e = _ease_in_out(t_lin)
        tap = np.abs(np.sin(4.0 * np.pi * t_lin))  # brief chin touch, then clear
        rw[:, 0] = 0.05 + 0.40 * e
        rw[:, 1] = -0.30 - 0.30 * e + 0.10 * np.sin(2 * np.pi * t_lin)
        rw[:, 2] = -0.15 - 0.15 * e
        lw[:] = seq[:, 5]

    elif word == "MORNING":
        # Sun rising: hand starts at chin and rises in a wide wave across the face.
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.10 + 0.45 * e
        rw[:, 1] = -0.35 - 0.25 * e + 0.06 * np.sin(4 * np.pi * t_lin)
        rw[:, 2] = -0.10 - 0.20 * e
        lw[:] = seq[:, 5]

    elif word == "AFTERNOON":
        # Forearm sweeps across the body from the shoulder line, flattening out.
        e = _ease_in_out(t_lin)
        rw[:, 0] = -0.25 + 0.60 * e
        rw[:, 1] = -0.25 + 0.40 * e
        rw[:, 2] = -0.25 + 0.15 * e
        lw[:] = seq[:, 5]

    elif word == "NIGHT":
        # Hand arcs down from the eye across the chest, like pulling a curtain.
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.15 * (1.0 - e) - 0.05
        rw[:, 1] = -0.45 + 0.75 * e
        rw[:, 2] = -0.15 - 0.10 * e
        lw[:] = seq[:, 5]

    elif word == "HOW":
        # Two thumbs-up knock together twice in front of the chest.
        tap = np.abs(np.sin(4.0 * np.pi * t_lin))
        rw[:, 0] = 0.20 + 0.15 * (1.0 - tap)
        rw[:, 1] = 0.10 - 0.05 * np.sin(2 * np.pi * t_lin)
        rw[:, 2] = -0.28
        lw[:, 0] = -0.20 - 0.15 * (1.0 - tap)
        lw[:, 1] = 0.10 - 0.05 * np.sin(2 * np.pi * t_lin)
        lw[:, 2] = -0.28
        lh_state = 1.0
        rh_state = 1.0

    elif word == "WELCOME":
        # Open palm sweeps in toward the chest in a welcoming arc.
        e = _ease_in_out(t_lin)
        theta = np.pi * (0.2 + 0.6 * e)
        rw[:, 0] = 0.40 * np.cos(theta)
        rw[:, 1] = 0.15 + 0.18 * np.sin(theta)
        rw[:, 2] = -0.30 - 0.10 * e
        lw[:] = seq[:, 5]

    elif word == "HAVE":
        # Both hands in a C shape pull inward to the chest (possessive).
        e = _ease_in_out(t_lin)
        rw[:, 0] = 0.40 - 0.32 * e
        rw[:, 1] = 0.20 - 0.02 * np.sin(2 * np.pi * t_lin)
        rw[:, 2] = -0.30
        lw[:, 0] = -0.40 + 0.32 * e
        lw[:, 1] = 0.20 + 0.02 * np.sin(2 * np.pi * t_lin)
        lw[:, 2] = -0.30

    elif word == "WATER":
        # W-hand taps the chin twice.
        tap = np.abs(np.sin(4.0 * np.pi * t_lin))
        rw[:, 0] = 0.05
        rw[:, 1] = -0.25 - 0.15 * tap
        rw[:, 2] = -0.25
        lw[:] = seq[:, 5]

    elif word == "FOOD":
        # F-hand (pinch) taps the mouth twice.
        tap = np.abs(np.sin(4.0 * np.pi * t_lin))
        rw[:, 0] = 0.02
        rw[:, 1] = -0.20 - 0.18 * tap
        rw[:, 2] = -0.25 + 0.10 * tap
        rh_state = -0.5  # Pinched fingertips
        lw[:] = seq[:, 5]

    else:
        # Default smooth motion
        rw[:, 0] = 0.3 + 0.1 * np.sin(2 * np.pi * t_lin)
        rw[:, 1] = 0.0 + 0.1 * np.cos(2 * np.pi * t_lin)
        rw[:, 2] = -0.2

    # Assign wrists and adjust elbows accordingly
    seq[:, 6] = rw
    seq[:, 5] = lw
    seq[:, 4] = (seq[:, 2] + rw) * 0.5 + np.array([0.15, 0.1, 0.0], dtype=np.float32)  # R-elbow
    seq[:, 3] = (seq[:, 1] + lw) * 0.5 + np.array([-0.15, 0.1, 0.0], dtype=np.float32)  # L-elbow

    # Expand hand landmarks relative to wrists
    for i in range(NUM_HAND_LANDMARKS):
        # Distribute hand points around wrist
        finger_spread = (i % 5 - 2) * 0.02
        joint_depth = (i // 5) * 0.03

        # Right hand points
        seq[:, 30 + i, 0] = rw[:, 0] + finger_spread * (1.0 - 0.7 * abs(rh_state))
        seq[:, 30 + i, 1] = rw[:, 1] - joint_depth * (1.0 - 0.5 * abs(rh_state))
        seq[:, 30 + i, 2] = rw[:, 2] + (i * 0.005)

        # Left hand points
        seq[:, 9 + i, 0] = lw[:, 0] + finger_spread * (1.0 - 0.7 * abs(lh_state))
        seq[:, 9 + i, 1] = lw[:, 1] - joint_depth * (1.0 - 0.5 * abs(lh_state))
        seq[:, 9 + i, 2] = lw[:, 2] + (i * 0.005)

    # 3. Add realistic natural variations
    # Amplitude scale jitter (0.90 .. 1.10)
    amp_jitter = float(rng.uniform(0.90, 1.10))
    seq *= amp_jitter

    # Spatial drift/translation
    drift = rng.normal(0.0, 0.02, size=(1, 1, 3)).astype(np.float32)
    seq += drift

    # Small per-landmark noise (jitter)
    noise = rng.normal(0.0, 0.005, size=seq.shape).astype(np.float32)
    seq += noise

    # Time-warping: subtle non-linear speed change
    warp_factor = float(rng.uniform(0.85, 1.15))
    warped_t = np.clip(np.power(t_lin, warp_factor), 0.0, 1.0)
    warped_indices = warped_t * (T - 1)

    warped_seq = np.empty_like(seq)
    for dim_idx in range(3):
        for lm_idx in range(TOTAL_LANDMARKS):
            warped_seq[:, lm_idx, dim_idx] = np.interp(
                warped_indices, np.arange(T), seq[:, lm_idx, dim_idx]
            )

    # 4. Shoulder normalization
    norm_seq = normalize_sequence(warped_seq, per_frame=True)
    return norm_seq.astype(np.float32)


def generate_synthetic_dataset(
    samples_per_word: int = 15,
    seed: int = 42,
    overwrite: bool = True,
    words: Optional[List[str]] = None,
) -> Dict[str, int]:
    """Generate synthetic landmark sequences for words and save them to disk.

    Args:
        samples_per_word: Number of 30-frame sequences to generate per word.
        seed: Random seed for reproducibility.
        overwrite: If True, replaces existing files for each word.
        words: List of words to generate. Defaults to all 16 WORDS.

    Returns:
        Dictionary mapping word name to sample count.
    """
    vocab = words or WORDS
    rng = np.random.default_rng(seed)
    counts: Dict[str, int] = {}

    for word in vocab:
        seqs = np.empty((samples_per_word, SEQUENCE_LENGTH, TOTAL_LANDMARKS, 3), dtype=np.float32)
        for i in range(samples_per_word):
            seqs[i] = _generate_synthetic_sequence_for_word(word, rng)

        total = save_word_samples(
            word=word,
            sequences=seqs,
            source="synthetic",
            append=(not overwrite),
        )
        counts[word] = total

    return counts

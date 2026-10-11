"""Configuration constants and paths for ISL (Indian Sign Language) mode."""

from pathlib import Path

from signora.models.manifest import get_model_path

# Manual Alphabet: ISLRTC standard 2-handed alphabet
ALL_LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
# Phase 1 static letters (excluding dynamic motion letters J and Z)
STATIC_LETTERS = [c for c in ALL_LETTERS if c not in ("J", "Z")]
SPECIAL_SIGNS = ["SPACE"]

# Static ISL Words (iconic signs requiring no hand movement)
STATIC_WORDS = [
    "NAMASTE",  # Both flat hands joined at chest in prayer/greeting [2 hands]
    "GOOD",  # Thumbs up gesture [1 hand]
    "BAD",  # Pinky finger extended upright [1 hand]
    "PEACE",  # V-sign with index & middle fingers [1 hand]
    "OK",  # Thumb & index forming a circle/ring, 3 fingers up [1 hand]
    "HOUSE",  # Both hands with fingertips touching to form a roof [2 hands]
    "BOOK",  # Both open palms facing upward touching pinky edges [2 hands]
    "TIME",  # Dominant index pointing at base wrist [2 hands]
    "LOVE",  # Thumb, index, and pinky extended [1 hand]
    "STOP",  # Open flat palm facing forward [1 hand]
]

TRAIN_LABELS = STATIC_LETTERS + SPECIAL_SIGNS + STATIC_WORDS
SIGNS = TRAIN_LABELS
NUM_SIGNS = len(SIGNS)

EXPECTED_HANDS = {
    "C": 1,
    "L": 1,
    "V": 1,
    "SPACE": 1,
    "GOOD": 1,
    "BAD": 1,
    "PEACE": 1,
    "OK": 1,
    "LOVE": 1,
    "STOP": 1,
}

# Feature dimensionality
SINGLE_HAND_DIM = 74
INTER_HAND_DIM = 14
PRESENCE_DIM = 2
FEATURE_DIM = SINGLE_HAND_DIM * 2 + INTER_HAND_DIM + PRESENCE_DIM  # 164

# Dwell & Recognition timings
BURST_FRAMES = 14
DWELL_MS = 600
SPACE_DWELL_MS = 500
SMOOTH_MS = 300
CONFIRM_THRESHOLD = 0.52
REPEAT_COOLDOWN_MS = 550
SPACE_COOLDOWN_MS = 600


def get_isl_weights_path() -> Path:
    return get_model_path("isl_signs")

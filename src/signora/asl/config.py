"""Configuration constants for ASL fingerspelling recognition."""

from pathlib import Path

from signora.models.manifest import get_model_path

LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
SPECIAL_SIGNS = ["SPACE"]
TRAIN_LABELS = LETTERS + SPECIAL_SIGNS

FEATURE_DIM = 74
BURST_FRAMES = 14
DWELL_MS = 1000
SPACE_DWELL_MS = 800
SMOOTH_MS = 300
CONFIRM_THRESHOLD = 0.52
REPEAT_COOLDOWN_MS = 550
SPACE_COOLDOWN_MS = 600


def get_asl_weights_path() -> Path:
    return get_model_path("asl_letters")

"""Configuration constants for ASL fingerspelling recognition."""

from pathlib import Path

from signora.models.manifest import get_model_path

LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
SPECIAL_SIGNS = ["SPACE"]
TRAIN_LABELS = LETTERS + SPECIAL_SIGNS

FEATURE_DIM = 74
BURST_FRAMES = 14
DWELL_MS = 250
SPACE_DWELL_MS = 350
SMOOTH_MS = 180
CONFIRM_THRESHOLD = 0.50
REPEAT_COOLDOWN_MS = 220
SPACE_COOLDOWN_MS = 320


def get_asl_weights_path() -> Path:
    return get_model_path("asl_letters")

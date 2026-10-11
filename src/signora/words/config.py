"""Configuration constants and landmark specifications for ASL Words mode."""

import os
from pathlib import Path

from signora.models.manifest import get_model_path

FRAME_WIDTH = 640
FRAME_HEIGHT = 480
FRAME_SKIP = max(1, int(os.environ.get("SIGNORA_WORDS_DETECT_EVERY", "2")))
SEQUENCE_LENGTH = 30

WORDS = [
    "HELLO",
    "THANKYOU",
    "PLEASE",
    "YES",
    "NO",
    "HELP",
    "SORRY",
    "GOOD",
    "BAD",
    "NAME",
    "MORE",
    "LOVE",
    "EAT",
    "DRINK",
    "WHERE",
    "FINISHED",
    "GOOD_MORNING",
    "MORNING",
    "AFTERNOON",
    "NIGHT",
    "HOW",
    "WELCOME",
    "HAVE",
    "WATER",
    "FOOD",
]
VOCABULARY = WORDS
NUM_WORDS = len(WORDS)

WORD_GLOSS = {w: w.lower().replace("_", " ") for w in WORDS}

# Hand landmarks (21 points)
NUM_HAND_LANDMARKS = 21

# Pose landmarks
NOSE = 0
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24

BODY_REFERENCE_INDICES = [
    NOSE,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
]
NUM_BODY_LANDMARKS = len(BODY_REFERENCE_INDICES)  # 9
TOTAL_LANDMARKS = NUM_BODY_LANDMARKS + NUM_HAND_LANDMARKS * 2  # 9 + 21 + 21 = 51
COORDS_PER_LANDMARK = 3
FEATURE_DIM = TOTAL_LANDMARKS * COORDS_PER_LANDMARK  # 153

CONFIDENCE_THRESHOLD = 0.18
KNN_NEIGHBORS = 3
MAX_ACCEPT_DISTANCE_RATIO = 0.85
POSE_VISIBILITY_MIN = 0.5
LANDMARK_EPSILON = 1e-6


def get_words_weights_path() -> Path:
    return get_model_path("asl_words")


WORDS_MODEL_PATH = get_words_weights_path()
WORDS_DATASET_PATH = Path(__file__).resolve().parent / "dataset.npz"

"""Geometric feature extraction from hand landmarks for Signora."""

from typing import Any, List, Union

import numpy as np

WRIST = 0
THUMB_CMC, THUMB_MCP, THUMB_IP, THUMB_TIP = 1, 2, 3, 4
INDEX_MCP, INDEX_PIP, INDEX_DIP, INDEX_TIP = 5, 6, 7, 8
MIDDLE_MCP, MIDDLE_PIP, MIDDLE_DIP, MIDDLE_TIP = 9, 10, 11, 12
RING_MCP, RING_PIP, RING_DIP, RING_TIP = 13, 14, 15, 16
PINKY_MCP, PINKY_PIP, PINKY_DIP, PINKY_TIP = 17, 18, 19, 20

DIGIT_CHAINS = [
    (THUMB_MCP, THUMB_IP, THUMB_TIP),
    (INDEX_MCP, INDEX_PIP, INDEX_TIP),
    (MIDDLE_MCP, MIDDLE_PIP, MIDDLE_TIP),
    (RING_MCP, RING_PIP, RING_TIP),
    (PINKY_MCP, PINKY_PIP, PINKY_TIP),
]

LANDMARK_DIM = 63  # 21 * 3
FEATURE_DIM = 74


def _dist(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.linalg.norm(a - b))


def normalize_hand(points: Union[np.ndarray, List[Any]], handedness: str = "Right") -> np.ndarray:
    """Normalize hand translation, scale, and lateral orientation."""
    if not isinstance(points, np.ndarray):
        p = np.array(
            [
                [
                    lm.x if hasattr(lm, "x") else lm[0],
                    lm.y if hasattr(lm, "y") else lm[1],
                    lm.z if hasattr(lm, "z") else lm[2],
                ]
                for lm in points
            ],
            dtype=np.float32,
        )
    else:
        p = np.array(points, dtype=np.float32).reshape(21, 3).copy()

    if handedness == "Left":
        p[:, 0] = -p[:, 0]

    p = p - p[WRIST]
    scale = _dist(p[MIDDLE_MCP], p[WRIST])
    if scale < 1e-6:
        scale = 1.0
    return p / scale


def landmark_features(p: Union[np.ndarray, List[Any]]) -> np.ndarray:
    """Compute 74D geometric feature vector from normalized 21-landmark array."""
    p = np.asarray(p, dtype=np.float32).reshape(21, 3)
    palm = _dist(p[WRIST], p[MIDDLE_MCP])
    if palm < 1e-6:
        palm = 1.0

    coords = p.reshape(-1)

    extensions = [_dist(p[tip], p[mcp]) / palm for mcp, _pip, tip in DIGIT_CHAINS]

    pinch = _dist(p[THUMB_TIP], p[INDEX_TIP]) / palm

    spread_pairs = [
        (INDEX_TIP, MIDDLE_TIP),
        (MIDDLE_TIP, RING_TIP),
        (RING_TIP, PINKY_TIP),
    ]
    spread = sum(_dist(p[a], p[b]) for a, b in spread_pairs) / (3.0 * palm)

    normal = np.cross(p[INDEX_MCP] - p[WRIST], p[PINKY_MCP] - p[WRIST])
    depth = float(p[[THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP], 2].mean())

    return np.concatenate(
        [
            coords,
            np.asarray(extensions, dtype=np.float32),
            np.asarray([pinch, spread, depth], dtype=np.float32),
            normal.astype(np.float32),
        ]
    ).astype(np.float32)


def extract_features(
    landmarks: Union[np.ndarray, List[Any]], handedness: str = "Right"
) -> np.ndarray:
    """Full pipeline: normalizes hand points and calculates canonical 74D features."""
    return landmark_features(normalize_hand(landmarks, handedness))


def get_feature_dim() -> int:
    return FEATURE_DIM

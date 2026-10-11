"""Canonical 164-dimensional feature extraction for Indian Sign Language (ISL)."""

from typing import Any, List, Optional, Tuple

import numpy as np

from signora.isl.config import FEATURE_DIM, SINGLE_HAND_DIM

WRIST = 0
THUMB_CMC = 1
THUMB_MCP = 2
THUMB_IP = 3
THUMB_TIP = 4
INDEX_MCP = 5
INDEX_PIP = 6
INDEX_DIP = 7
INDEX_TIP = 8
MIDDLE_MCP = 9
MIDDLE_PIP = 10
MIDDLE_DIP = 11
MIDDLE_TIP = 12
RING_MCP = 13
RING_PIP = 14
RING_DIP = 15
RING_TIP = 16
PINKY_MCP = 17
PINKY_PIP = 18
PINKY_DIP = 19
PINKY_TIP = 20

FINGERTIPS = [THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP]
DIGIT_CHAINS = [
    (THUMB_MCP, THUMB_IP, THUMB_TIP),
    (INDEX_MCP, INDEX_PIP, INDEX_TIP),
    (MIDDLE_MCP, MIDDLE_PIP, MIDDLE_TIP),
    (RING_MCP, RING_PIP, RING_TIP),
    (PINKY_MCP, PINKY_PIP, PINKY_TIP),
]


def _dist(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.linalg.norm(a - b))


def normalize_single_hand(points: np.ndarray) -> Tuple[np.ndarray, float]:
    """Translate wrist to origin and scale by palm distance (wrist to middle MCP)."""
    p = np.asarray(points, dtype=np.float32).copy()
    p = p - p[WRIST]
    scale = _dist(p[MIDDLE_MCP], p[WRIST])
    if scale < 1e-6:
        scale = 1.0
    return p / scale, scale


def single_hand_features(p: np.ndarray) -> np.ndarray:
    """Compute 74-dimensional geometric shape descriptor for one normalized hand."""
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
    norm_val = np.linalg.norm(normal)
    if norm_val > 1e-6:
        normal = normal / norm_val

    depth = float(p[FINGERTIPS, 2].mean())

    return np.concatenate(
        [
            coords,
            np.asarray(extensions, dtype=np.float32),
            np.asarray([pinch, spread, depth], dtype=np.float32),
            normal.astype(np.float32),
        ]
    ).astype(np.float32)


def canonicalize_hands(
    landmarks_list: Optional[List[Any]],
    handedness_list: Optional[List[str]],
) -> Tuple[Optional[np.ndarray], Optional[np.ndarray]]:
    """Assign hands to canonical [left_slot, right_slot]."""
    if not landmarks_list:
        return None, None

    parsed = []
    for lm in landmarks_list:
        if lm is None:
            continue
        if isinstance(lm, np.ndarray):
            parsed.append(lm.astype(np.float32).reshape(21, 3))
        elif isinstance(lm, (list, tuple)):
            if len(lm) > 0 and hasattr(lm[0], "x"):
                parsed.append(np.array([[pt.x, pt.y, pt.z] for pt in lm], dtype=np.float32))
            else:
                parsed.append(np.asarray(lm, dtype=np.float32).reshape(21, 3))

    if not parsed:
        return None, None

    h_labels = list(handedness_list or [])
    while len(h_labels) < len(parsed):
        h_labels.append("Unknown")

    left_hand: Optional[np.ndarray] = None
    right_hand: Optional[np.ndarray] = None

    if len(parsed) == 1:
        lbl = h_labels[0]
        if lbl == "Left":
            left_hand = parsed[0]
        elif lbl == "Right":
            right_hand = parsed[0]
        else:
            if parsed[0][WRIST][0] < 0.5:
                left_hand = parsed[0]
            else:
                right_hand = parsed[0]
        return left_hand, right_hand

    # Two hands
    has_l = "Left" in h_labels[:2]
    has_r = "Right" in h_labels[:2]
    if has_l and has_r and h_labels[0] != h_labels[1]:
        idx_l = h_labels.index("Left")
        idx_r = h_labels.index("Right")
        left_hand = parsed[idx_l]
        right_hand = parsed[idx_r]
    else:
        hands_sorted = sorted(parsed[:2], key=lambda h: float(h[WRIST][0]))
        left_hand = hands_sorted[0]
        right_hand = hands_sorted[1]

    return left_hand, right_hand


def extract_features(
    landmarks_list: Optional[List[Any]],
    handedness_list: Optional[List[str]],
    dominant: str = "right",
) -> np.ndarray:
    """Extract canonical 164-dimensional feature vector for ISL recognition."""
    left_hand, right_hand = canonicalize_hands(landmarks_list, handedness_list)

    has_left = 1.0 if left_hand is not None else 0.0
    has_right = 1.0 if right_hand is not None else 0.0

    left_feat = np.zeros(SINGLE_HAND_DIM, dtype=np.float32)
    right_feat = np.zeros(SINGLE_HAND_DIM, dtype=np.float32)
    left_norm: Optional[np.ndarray] = None
    right_norm: Optional[np.ndarray] = None
    scale_left, scale_right = 1.0, 1.0

    if left_hand is not None:
        left_norm, scale_left = normalize_single_hand(left_hand)
        left_feat = single_hand_features(left_norm)

    if right_hand is not None:
        right_norm, scale_right = normalize_single_hand(right_hand)
        right_feat = single_hand_features(right_norm)

    inter_feat = np.zeros(14, dtype=np.float32)

    if (
        left_hand is not None
        and right_hand is not None
        and left_norm is not None
        and right_norm is not None
    ):
        avg_scale = max(0.5 * (scale_left + scale_right), 1e-4)
        wrist_offset = (right_hand[WRIST] - left_hand[WRIST]) / avg_scale
        inter_wrist_dist = _dist(right_hand[WRIST], left_hand[WRIST]) / avg_scale

        is_right_dom = dominant.lower() == "right"
        dom_hand = right_hand if is_right_dom else left_hand
        base_hand = left_hand if is_right_dom else right_hand

        contact_dists = [
            _dist(dom_hand[INDEX_TIP], base_hand[tip]) / avg_scale for tip in FINGERTIPS
        ]
        dom_idx_to_base_wrist = _dist(dom_hand[INDEX_TIP], base_hand[WRIST]) / avg_scale
        dom_thumb_to_base_idx = _dist(dom_hand[THUMB_TIP], base_hand[INDEX_TIP]) / avg_scale
        min_tip_dist = (
            min(_dist(dom_hand[f1], base_hand[f2]) for f1 in FINGERTIPS for f2 in FINGERTIPS)
            / avg_scale
        )

        left_normal = np.cross(
            left_norm[INDEX_MCP] - left_norm[WRIST], left_norm[PINKY_MCP] - left_norm[WRIST]
        )
        right_normal = np.cross(
            right_norm[INDEX_MCP] - right_norm[WRIST], right_norm[PINKY_MCP] - right_norm[WRIST]
        )
        nl_val = np.linalg.norm(left_normal)
        nr_val = np.linalg.norm(right_normal)
        if nl_val > 1e-6:
            left_normal /= nl_val
        if nr_val > 1e-6:
            right_normal /= nr_val
        normal_dot = float(np.dot(left_normal, right_normal))

        rel_height = float(dom_hand[WRIST][1] - base_hand[WRIST][1]) / avg_scale

        inter_feat = np.concatenate(
            [
                wrist_offset,
                np.array([inter_wrist_dist], dtype=np.float32),
                np.array(contact_dists, dtype=np.float32),
                np.array(
                    [
                        dom_idx_to_base_wrist,
                        dom_thumb_to_base_idx,
                        min_tip_dist,
                        normal_dot,
                        rel_height,
                    ],
                    dtype=np.float32,
                ),
            ]
        ).astype(np.float32)

    presence_feat = np.array([has_left, has_right], dtype=np.float32)

    vec = np.concatenate([left_feat, right_feat, inter_feat, presence_feat]).astype(np.float32)
    assert vec.shape[0] == FEATURE_DIM, f"Expected {FEATURE_DIM}D vector, got {vec.shape[0]}D"
    return vec

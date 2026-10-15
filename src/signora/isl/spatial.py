"""Spatial Feature Extraction & Anatomical Body Anchor Tracking for ISL.

Extracts 220D spatial features combining 164D dual-hand poses with 56D scale-invariant
distances and vectors relative to anatomical body landmarks (Forehead, Mouth, Chin,
Chest, Wrists, Waist).
"""

from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from signora.isl.features import extract_features

SPATIAL_FEATURE_DIM = 220  # 164D hands + 56D body spatial placement

# Canonical Body Anchor Targets for Static Signs
SIGN_ANCHOR_MAP: Dict[str, str] = {
    # Head & Forehead
    "FATHER": "FOREHEAD",
    "THINK": "FOREHEAD",
    "FEVER": "FOREHEAD",
    "STUDENT": "FOREHEAD",
    "BOY": "FOREHEAD",
    "REMEMBER": "FOREHEAD",

    # Face, Mouth & Lips
    "FOOD": "MOUTH_LIPS",
    "WATER": "MOUTH_LIPS",
    "EAT": "MOUTH_LIPS",
    "DRINK": "MOUTH_LIPS",
    "THANK_YOU": "MOUTH_LIPS",
    "SILENT": "MOUTH_LIPS",
    "PROMISE": "MOUTH_LIPS",
    "FALSE": "MOUTH_LIPS",

    # Chin & Jaw
    "MOTHER": "CHIN",
    "GIRL": "CHIN",
    "SUGAR": "CHIN",
    "SWEET": "CHIN",
    "TRUE": "CHIN",
    "WRONG": "CHIN",
    "PATIENT": "CHIN",

    # Temple & Ears
    "LISTEN": "TEMPLE",
    "HEADACHE": "TEMPLE",
    "RESPECT": "TEMPLE",
    "TEACHER": "TEMPLE",

    # Chest, Sternum & Heart
    "PLEASE": "CHEST",
    "APOLOGY": "CHEST",
    "LOVE": "CHEST",
    "NAMASTE": "CHEST",
    "HELP": "CHEST",
    "HAPPY": "CHEST",
    "TIRED": "CHEST",
    "EMERGENCY": "CHEST",
    "PROUD": "CHEST",
    "HUNGRY": "CHEST",

    # Contralateral Wrist & Pulse
    "TIME": "WRIST",
    "WATCH": "WRIST",
    "DOCTOR": "WRIST",
    "DOCTOR_PERSON": "WRIST",
    "MEDICINE": "WRIST",
    "INJURY": "WRIST",
    "BUSY": "WRIST",

    # Shoulders
    "HOSPITAL": "SHOULDER",
    "REST": "SHOULDER",
    "SAFE": "SHOULDER",
    "STRONG": "SHOULDER",

    # Waist & Lower Torso
    "NOW": "WAIST",
    "TABLE": "WAIST",
    "HERE": "WAIST",
    "READY": "WAIST",
    "CHILD": "WAIST",
}


def _get_point_coords(pt: Any) -> Tuple[float, float, float]:
    """Extract (x, y, z) from LandmarkPoint or MediaPipe NormalizedLandmark."""
    if hasattr(pt, "x") and hasattr(pt, "y"):
        z = getattr(pt, "z", 0.0)
        return float(pt.x), float(pt.y), float(z)
    if isinstance(pt, (list, tuple)) and len(pt) >= 2:
        z = float(pt[2]) if len(pt) > 2 else 0.0
        return float(pt[0]), float(pt[1]), z
    return 0.5, 0.5, 0.0


def extract_spatial_features(
    hand_lms_list: List[Any],
    handedness_list: List[str],
    pose_landmarks: Optional[List[Any]] = None,
) -> np.ndarray:
    """Compute 220D vector combining 164D hand articulation and 56D anatomical body placement."""
    # 1. Base 164D hand features
    base_feats = extract_features(hand_lms_list, handedness_list)
    if pose_landmarks is None or len(pose_landmarks) < 25:
        # Fallback to zero-padded spatial features if body is absent
        body_spatial = np.zeros(SPATIAL_FEATURE_DIM - len(base_feats), dtype=np.float32)
        return np.concatenate([base_feats, body_spatial]).astype(np.float32)

    # 2. Extract anatomical body anchors from Pose landmarks (0-24)
    # Nose (0), Shoulders (11, 12), Mouth (9, 10), Hips (23, 24), Wrists (15, 16)
    p_nose = _get_point_coords(pose_landmarks[0])
    p_lsh = _get_point_coords(pose_landmarks[11])
    p_rsh = _get_point_coords(pose_landmarks[12])
    p_ml = _get_point_coords(pose_landmarks[9])
    p_mr = _get_point_coords(pose_landmarks[10])
    p_lwrist = _get_point_coords(pose_landmarks[15])
    p_rwrist = _get_point_coords(pose_landmarks[16])
    p_lhip = _get_point_coords(pose_landmarks[23])
    p_rhip = _get_point_coords(pose_landmarks[24])

    # Invariant scale: distance between left and right shoulders
    shoulder_dist = np.sqrt(
        (p_lsh[0] - p_rsh[0]) ** 2 + (p_lsh[1] - p_rsh[1]) ** 2 + (p_lsh[2] - p_rsh[2]) ** 2
    )
    scale = max(float(shoulder_dist), 0.15)

    # Derived anchor centers
    mouth_center = ((p_ml[0] + p_mr[0]) / 2.0, (p_ml[1] + p_mr[1]) / 2.0, (p_ml[2] + p_mr[2]) / 2.0)
    forehead = (p_nose[0], p_nose[1] - 0.22 * scale, p_nose[2])
    chin = (mouth_center[0], mouth_center[1] + 0.12 * scale, mouth_center[2])
    chest_center = ((p_lsh[0] + p_rsh[0]) / 2.0, (p_lsh[1] + p_rsh[1]) / 2.0, (p_lsh[2] + p_rsh[2]) / 2.0)
    waist_center = ((p_lhip[0] + p_rhip[0]) / 2.0, (p_lhip[1] + p_rhip[1]) / 2.0, (p_lhip[2] + p_rhip[2]) / 2.0)

    anchors = [
        forehead,
        p_nose,
        mouth_center,
        chin,
        chest_center,
        p_lsh,
        p_rsh,
        p_lwrist,
        waist_center,
    ]  # 9 anchors

    # 3. Compute relative vectors for each hand
    body_features: List[float] = []

    # Map detected hands to Right and Left
    right_hand_pts = None
    left_hand_pts = None
    for idx, lms in enumerate(hand_lms_list):
        if not lms:
            continue
        side = handedness_list[idx] if idx < len(handedness_list) else ("Right" if idx == 0 else "Left")
        if side == "Right" and right_hand_pts is None:
            right_hand_pts = lms
        elif side == "Left" and left_hand_pts is None:
            left_hand_pts = lms

    for hand_pts, is_right in ((right_hand_pts, True), (left_hand_pts, False)):
        if hand_pts is not None and len(hand_pts) >= 21:
            wrist = _get_point_coords(hand_pts[0])
            idx_tip = _get_point_coords(hand_pts[8])
            opp_wrist = p_lwrist if is_right else p_rwrist

            # 9 anchor distance vectors from wrist
            for anc in anchors:
                dx = (wrist[0] - anc[0]) / scale
                dy = (wrist[1] - anc[1]) / scale
                dz = (wrist[2] - anc[2]) / scale
                dist = np.sqrt(dx * dx + dy * dy + dz * dz)
                body_features.extend([dx, dy, dist])

            # Distance from index fingertip to mouth & forehead
            df_mouth = np.sqrt(
                ((idx_tip[0] - mouth_center[0]) / scale) ** 2
                + ((idx_tip[1] - mouth_center[1]) / scale) ** 2
            )
            df_forehead = np.sqrt(
                ((idx_tip[0] - forehead[0]) / scale) ** 2
                + ((idx_tip[1] - forehead[1]) / scale) ** 2
            )
            body_features.append(df_mouth)
        else:
            # 9 anchors * 3 features + 1 = 28 features per hand
            body_features.extend([0.0] * 28)

    body_vec = np.array(body_features, dtype=np.float32)
    # Ensure exact feature dimensionality
    combined = np.concatenate([base_feats, body_vec]).astype(np.float32)
    if len(combined) < SPATIAL_FEATURE_DIM:
        pad = np.zeros(SPATIAL_FEATURE_DIM - len(combined), dtype=np.float32)
        combined = np.concatenate([combined, pad])
    return combined[:SPATIAL_FEATURE_DIM]


def detect_body_anchor(
    hand_lms: Any,
    pose_landmarks: Optional[List[Any]],
    w: int = 640,
    h: int = 480,
) -> Tuple[str, float, Optional[Tuple[int, int]]]:
    """Identify the nearest active anatomical body target zone for a hand."""
    if not hand_lms or not pose_landmarks or len(pose_landmarks) < 25:
        return "OPEN_SPACE", 1.0, None

    wrist = _get_point_coords(hand_lms[0])
    p_nose = _get_point_coords(pose_landmarks[0])
    p_lsh = _get_point_coords(pose_landmarks[11])
    p_rsh = _get_point_coords(pose_landmarks[12])
    p_ml = _get_point_coords(pose_landmarks[9])
    p_mr = _get_point_coords(pose_landmarks[10])
    p_lw = _get_point_coords(pose_landmarks[15])
    p_rw = _get_point_coords(pose_landmarks[16])
    p_lh = _get_point_coords(pose_landmarks[23])
    p_rh = _get_point_coords(pose_landmarks[24])

    scale = max(
        0.15,
        float(
            np.sqrt(
                (p_lsh[0] - p_rsh[0]) ** 2
                + (p_lsh[1] - p_rsh[1]) ** 2
                + (p_lsh[2] - p_rsh[2]) ** 2
            )
        ),
    )

    mouth = ((p_ml[0] + p_mr[0]) / 2.0, (p_ml[1] + p_mr[1]) / 2.0, (p_ml[2] + p_mr[2]) / 2.0)
    forehead = (p_nose[0], p_nose[1] - 0.22 * scale, p_nose[2])
    chin = (mouth[0], mouth[1] + 0.12 * scale, mouth[2])
    chest = ((p_lsh[0] + p_rsh[0]) / 2.0, (p_lsh[1] + p_rsh[1]) / 2.0, (p_lsh[2] + p_rsh[2]) / 2.0)
    waist = ((p_lh[0] + p_rh[0]) / 2.0, (p_lh[1] + p_rh[1]) / 2.0, (p_lh[2] + p_rh[2]) / 2.0)

    anchor_candidates = {
        "FOREHEAD": forehead,
        "CHIN": chin,
        "MOUTH_LIPS": mouth,
        "CHEST": chest,
        "WRIST": p_lw,
        "SHOULDER": p_lsh,
        "WAIST": waist,
    }

    best_zone = "OPEN_SPACE"
    min_dist = 999.0
    best_px_pt = None

    for zone_name, pt in anchor_candidates.items():
        d = float(
            np.sqrt(
                ((wrist[0] - pt[0]) / scale) ** 2
                + ((wrist[1] - pt[1]) / scale) ** 2
                + ((wrist[2] - pt[2]) / scale) ** 2
            )
        )
        if d < min_dist:
            min_dist = d
            best_zone = zone_name
            best_px_pt = (int(pt[0] * w), int(pt[1] * h))

    # Threshold: if further than 0.65 normalized scale, it's open space
    if min_dist > 0.65:
        return "OPEN_SPACE", min_dist, None

    return best_zone, min_dist, best_px_pt

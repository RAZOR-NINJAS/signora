"""Unit tests for Signora Upper-Body Pose & Hand Spatial Recognition Engine."""

import numpy as np
import pytest

from signora.body.live import run_body_live
from signora.cli import main
from signora.core.hands import LandmarkPoint
from signora.core.spatial import SpatialPipeline, draw_cyber_spatial
from signora.isl.spatial import (
    SIGN_ANCHOR_MAP,
    SPATIAL_FEATURE_DIM,
    detect_body_anchor,
    extract_spatial_features,
)


def _make_hand_landmarks(wrist_x=0.5, wrist_y=0.5):
    """Generate dummy 21-point hand landmarks centered at wrist."""
    pts = []
    for i in range(21):
        pts.append(LandmarkPoint(wrist_x + 0.005 * i, wrist_y - 0.008 * i, 0.0))
    return pts


def _make_pose_landmarks():
    """Generate dummy 33-point pose landmarks."""
    pts = [LandmarkPoint(0.5, 0.5, 0.0) for _ in range(33)]
    # Nose & face
    pts[0] = LandmarkPoint(0.5, 0.25, 0.0)
    pts[9] = LandmarkPoint(0.48, 0.30, 0.0)   # Mouth L
    pts[10] = LandmarkPoint(0.52, 0.30, 0.0)  # Mouth R
    # Shoulders
    pts[11] = LandmarkPoint(0.35, 0.45, 0.0)  # Left shoulder
    pts[12] = LandmarkPoint(0.65, 0.45, 0.0)  # Right shoulder
    # Wrists
    pts[15] = LandmarkPoint(0.35, 0.80, 0.0)  # Left wrist
    pts[16] = LandmarkPoint(0.65, 0.80, 0.0)  # Right wrist
    # Hips
    pts[23] = LandmarkPoint(0.40, 0.95, 0.0)
    pts[24] = LandmarkPoint(0.60, 0.95, 0.0)
    return pts


def test_spatial_feature_shape_and_values():
    """Verify 220D spatial feature extraction output."""
    hand_r = _make_hand_landmarks(0.5, 0.5)
    pose = _make_pose_landmarks()

    feats = extract_spatial_features([hand_r], ["Right"], pose)
    assert isinstance(feats, np.ndarray)
    assert feats.shape == (SPATIAL_FEATURE_DIM,)
    assert feats.dtype == np.float32
    assert not np.isnan(feats).any()
    assert not np.isinf(feats).any()


def test_spatial_feature_fallback_without_pose():
    """Verify graceful zero-padded fallback when body pose is None."""
    hand_r = _make_hand_landmarks(0.5, 0.5)
    feats = extract_spatial_features([hand_r], ["Right"], None)
    assert feats.shape == (SPATIAL_FEATURE_DIM,)
    assert not np.isnan(feats).any()


def test_detect_body_anchors():
    """Test anatomical body anchor classification."""
    pose = _make_pose_landmarks()
    # Left shoulder = (0.35, 0.45), Right shoulder = (0.65, 0.45) => scale = 0.30
    # Mouth = (0.5, 0.30)
    # Forehead = (0.5, 0.25 - 0.22*0.30) = (0.5, 0.184)
    # Chin = (0.5, 0.30 + 0.12*0.30) = (0.5, 0.336)
    # Chest = (0.5, 0.45)
    # Left wrist = (0.35, 0.80)

    # 1. Hand near forehead
    hand_forehead = _make_hand_landmarks(0.50, 0.18)
    anchor, dist, pt = detect_body_anchor(hand_forehead, pose, 640, 480)
    assert anchor == "FOREHEAD"
    assert pt is not None

    # 2. Hand near mouth
    hand_mouth = _make_hand_landmarks(0.50, 0.30)
    anchor, dist, pt = detect_body_anchor(hand_mouth, pose, 640, 480)
    assert anchor == "MOUTH_LIPS"

    # 3. Hand near chest
    hand_chest = _make_hand_landmarks(0.50, 0.45)
    anchor, dist, pt = detect_body_anchor(hand_chest, pose, 640, 480)
    assert anchor == "CHEST"

    # 4. Hand near contralateral wrist
    hand_wrist = _make_hand_landmarks(0.35, 0.80)
    anchor, dist, pt = detect_body_anchor(hand_wrist, pose, 640, 480)
    assert anchor == "WRIST"

    # 5. Hand far away in open space
    hand_far = _make_hand_landmarks(0.95, 0.95)
    anchor, dist, pt = detect_body_anchor(hand_far, pose, 640, 480)
    assert anchor == "OPEN_SPACE"
    assert pt is None


def test_sign_anchor_map_integrity():
    """Verify key static words are mapped to canonical body anchors."""
    assert SIGN_ANCHOR_MAP["FATHER"] == "FOREHEAD"
    assert SIGN_ANCHOR_MAP["MOTHER"] == "CHIN"
    assert SIGN_ANCHOR_MAP["FOOD"] == "MOUTH_LIPS"
    assert SIGN_ANCHOR_MAP["WATER"] == "MOUTH_LIPS"
    assert SIGN_ANCHOR_MAP["PLEASE"] == "CHEST"
    assert SIGN_ANCHOR_MAP["TIME"] == "WRIST"
    assert SIGN_ANCHOR_MAP["DOCTOR"] == "WRIST"


def test_draw_cyber_spatial():
    """Verify spatial cybernetic visualization draws cleanly without errors."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    hand_r = _make_hand_landmarks(0.5, 0.4)
    pose = _make_pose_landmarks()

    draw_cyber_spatial(
        frame=frame,
        hand_lms_list=[hand_r],
        handedness_list=["Right"],
        pose_landmarks=pose,
        active_anchor_name="CHEST",
        active_anchor_pt=(320, 240),
    )
    # Check that pixels were actually modified
    assert np.any(frame > 0)


def test_spatial_pipeline_synthetic_lifecycle():
    """Verify SpatialPipeline starts, reads frames, and shuts down cleanly."""
    pipe = SpatialPipeline(source=0, mirror=True, use_fake=True)
    assert pipe.started.wait(timeout=5)

    frame, hand_lms, handedness, pose_lms = pipe.read(timeout=2.0)
    assert frame is not None
    assert frame.shape == (480, 640, 3)
    assert pose_lms is not None
    assert len(pose_lms) == 33

    pipe.close()


def test_run_body_live_headless():
    """Verify real-time spatial live loop runs headless with fake camera."""
    ret = run_body_live(
        source=0,
        headless=True,
        no_tts=True,
        max_frames=5,
        use_fake=True,
    )
    assert ret == 0

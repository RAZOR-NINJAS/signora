"""Tests for 74D geometric feature extraction and landmark normalization."""

import numpy as np

from signora.asl.config import FEATURE_DIM
from signora.core.features import extract_features, landmark_features, normalize_hand


def test_normalize_hand():
    # 21 synthetic landmarks
    raw_pts = [(float(i) * 0.05, float(i) * 0.04, 0.0) for i in range(21)]
    norm_pts = normalize_hand(raw_pts, handedness="Right")
    assert len(norm_pts) == 21
    # Wrist should be translated to origin (0, 0, 0)
    assert np.allclose(norm_pts[0], [0.0, 0.0, 0.0])


def test_feature_extraction_dimension():
    # 21 normalized landmarks
    pts = [(float(i) * 0.05, float(i) * 0.04, 0.01) for i in range(21)]
    feats = landmark_features(pts)
    assert isinstance(feats, np.ndarray)
    assert feats.shape == (FEATURE_DIM,), f"Expected shape ({FEATURE_DIM},), got {feats.shape}"
    assert np.all(np.isfinite(feats))


def test_extract_features_pipeline():
    pts = [(0.1 * i, 0.2 * i, 0.0) for i in range(21)]
    feats = extract_features(pts, handedness="Left")
    assert len(feats) == FEATURE_DIM


def test_empty_and_undersized_landmarks():
    """Verify empty and undersized landmark lists safely return zero feature vectors without raising."""
    empty_feats = extract_features([])
    assert len(empty_feats) == FEATURE_DIM
    assert np.all(empty_feats == 0.0)

    partial_feats = extract_features([(0.1, 0.2, 0.0)] * 5)
    assert len(partial_feats) == FEATURE_DIM
    assert np.all(partial_feats == 0.0)

    none_feats = extract_features(None)
    assert len(none_feats) == FEATURE_DIM
    assert np.all(none_feats == 0.0)


def test_zero_palm_distance_degeneracy():
    """Verify that coincident wrist and middle MCP do not cause division by zero."""
    coincident_pts = [(0.5, 0.5, 0.0) for _ in range(21)]
    feats = extract_features(coincident_pts)
    assert len(feats) == FEATURE_DIM
    assert np.all(np.isfinite(feats))


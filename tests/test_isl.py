"""Tests for Indian Sign Language (ISL) two-handed recognition and 164D features."""

import numpy as np

from signora.isl.config import FEATURE_DIM, NUM_SIGNS, SIGNS
from signora.isl.features import extract_features
from signora.isl.model import load_isl_predictor


def test_isl_config():
    assert len(SIGNS) == NUM_SIGNS
    assert FEATURE_DIM == 164
    assert "A" in SIGNS
    assert "NAMASTE" in SIGNS


def test_isl_feature_extraction():
    pts_left = [(0.05 * i, 0.04 * i, 0.0) for i in range(21)]
    pts_right = [(0.06 * i, 0.05 * i, 0.01) for i in range(21)]

    feats = extract_features([pts_left, pts_right], ["Left", "Right"])
    assert isinstance(feats, np.ndarray)
    assert feats.shape == (FEATURE_DIM,)
    assert np.all(np.isfinite(feats))


def test_isl_predictor_inference():
    predictor = load_isl_predictor()
    dummy_feats = np.zeros(FEATURE_DIM, dtype=np.float32)

    probs = predictor.probs(dummy_feats)
    assert len(probs) == len(predictor.labels)
    assert np.isclose(np.sum(probs), 1.0, atol=1e-4)

    label, conf = predictor.predict(dummy_feats)
    assert label in predictor.labels
    assert 0.0 <= conf <= 1.0

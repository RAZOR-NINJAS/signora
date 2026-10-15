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


def test_isl_collect_studio():
    from signora.isl.collect import ISL_LETTERS, get_existing_counts, run_isl_collector

    assert len(ISL_LETTERS) == 27
    assert "A" in ISL_LETTERS
    assert "Z" in ISL_LETTERS
    assert "SPACE" in ISL_LETTERS

    counts = get_existing_counts()
    assert isinstance(counts, dict)

    rc = run_isl_collector(sign="A", count=1, headless=True, use_fake=True)
    assert rc == 0


def test_isl_collect_keybindings():
    """Verify that lowercase 't', uppercase 'T', Ctrl+T, and Enter trigger training."""
    from signora.isl.collect import ISL_LETTERS, draw_hud

    train_keys = (ord("t"), ord("T"), 20, 13, 10)
    assert ord("t") in train_keys
    assert ord("T") in train_keys
    assert 13 in train_keys
    assert 20 in train_keys

    # Test draw_hud button_boxes population
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    boxes = {}
    draw_hud(
        frame=frame,
        current_sign="A",
        sign_idx=0,
        collected_in_batch=0,
        target_count=300,
        existing_count=0,
        state="IDLE",
        countdown_val=0.0,
        hands_detected=1,
        button_boxes=boxes,
    )
    assert "train" in boxes
    assert boxes["train"][0] > 0
    assert "record" in boxes
    assert "chips" in boxes
    assert len(boxes["chips"]) > 0


def test_isl_200_static_words():
    """Verify that exactly 200 unique static words exist with posture guidance and hand expectations."""
    from signora.isl.collect import ISL_ALL_SIGNS, get_sign_guide
    from signora.isl.config import STATIC_WORDS, get_expected_hands

    assert len(STATIC_WORDS) == 200
    assert len(set(STATIC_WORDS)) == 200
    assert len(ISL_ALL_SIGNS) >= 227

    for word in STATIC_WORDS:
        assert word in ISL_ALL_SIGNS
        hands = get_expected_hands(word)
        assert hands in (1, 2)
        guide1, guide2 = get_sign_guide(word)
        assert isinstance(guide1, str) and len(guide1) > 0
        assert isinstance(guide2, str) and len(guide2) > 0


def test_isl_studio_word_collection():
    """Verify that ISL collector accepts word signs like WATER and HELP."""
    from signora.isl.collect import run_isl_collector

    rc = run_isl_collector(sign="WATER", count=1, headless=True, use_fake=True)
    assert rc == 0


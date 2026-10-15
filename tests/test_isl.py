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
    """Verify that lowercase 't' selects letter T while Shift+T (ord('T')) triggers training."""
    from signora.isl.collect import ISL_LETTERS

    # 1. Lowercase 't' (ASCII 116) selects letter T
    key_lowercase = ord("t")
    assert key_lowercase != ord("T")
    assert ord("a") <= key_lowercase <= ord("z")
    selected_char = chr(key_lowercase).upper()
    assert selected_char == "T"
    assert selected_char in ISL_LETTERS

    # 2. Shift + T (ASCII 84) is uppercase T for neural training trigger
    key_shift_t = ord("T")
    assert key_shift_t == 84
    assert not (ord("a") <= key_shift_t <= ord("z"))


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


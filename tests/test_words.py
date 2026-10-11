"""Tests for ASL dynamic words recognition, DTW-kNN classifier, and dataset loader."""

import numpy as np

from signora.words.classifier import DTWKNNClassifier
from signora.words.config import WORDS, WORDS_MODEL_PATH
from signora.words.normalize import normalize_sequence


def test_words_vocabulary():
    assert len(WORDS) >= 20
    assert "HELLO" in WORDS
    assert "THANKYOU" in WORDS


def test_words_sequence_normalization():
    # Synthetic trajectory: 30 frames, 153 features (51 landmarks * 3 coords)
    raw_seq = np.random.randn(30, 153).astype(np.float32)
    norm_seq = normalize_sequence(raw_seq)
    assert norm_seq.shape == (30, 153)
    assert np.all(np.isfinite(norm_seq))


def test_words_classifier_inference():
    if not WORDS_MODEL_PATH.exists():
        return
    clf = DTWKNNClassifier.load(WORDS_MODEL_PATH)
    assert len(clf.classes_) > 0

    # Test inference against first reference sample
    sample_seq = clf.X_[0]
    pred_label, conf, details = clf.predict_single(sample_seq)
    assert pred_label == clf.y_[0]
    assert conf > 0.5

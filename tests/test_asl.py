"""Tests for ASL fingerspelling recognition, PyTorch SignMLP model, and labels."""

import numpy as np
import torch

from signora.asl.config import FEATURE_DIM, LETTERS, TRAIN_LABELS
from signora.asl.model import SignMLP, load_asl_predictor


def test_asl_labels():
    assert len(LETTERS) == 26
    assert "SPACE" in TRAIN_LABELS
    assert FEATURE_DIM == 74


def test_asl_sign_mlp_forward():
    model = SignMLP(in_features=FEATURE_DIM, num_classes=len(TRAIN_LABELS))
    model.eval()

    dummy_input = torch.zeros((1, FEATURE_DIM), dtype=torch.float32)
    with torch.no_grad():
        out = model(dummy_input)
    assert out.shape == (1, len(TRAIN_LABELS))


def test_asl_predictor_inference():
    predictor = load_asl_predictor()
    dummy_feats = np.zeros(FEATURE_DIM, dtype=np.float32)

    probs = predictor.probs(dummy_feats)
    assert len(probs) == len(predictor.labels)
    assert np.isclose(np.sum(probs), 1.0, atol=1e-4)

    label, conf = predictor.predict(dummy_feats)
    assert label in predictor.labels
    assert 0.0 <= conf <= 1.0

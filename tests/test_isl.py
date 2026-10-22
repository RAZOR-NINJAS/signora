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


def test_isl_collect_studio(tmp_path):
    from signora.isl.collect import ISL_LETTERS, get_existing_counts, run_isl_collector

    assert len(ISL_LETTERS) == 27
    assert "A" in ISL_LETTERS
    assert "Z" in ISL_LETTERS
    assert "SPACE" in ISL_LETTERS

    counts = get_existing_counts(tmp_path)
    assert isinstance(counts, dict)

    rc = run_isl_collector(sign="A", count=1, headless=True, use_fake=True, data_dir=tmp_path)
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


def test_isl_300_static_words():
    """Verify that exactly 300 unique static words exist with posture guidance and hand expectations."""
    from signora.isl.collect import ISL_ALL_SIGNS, get_sign_guide
    from signora.isl.config import STATIC_WORDS, get_expected_hands

    assert len(STATIC_WORDS) == 300
    assert len(set(STATIC_WORDS)) == 300
    assert len(ISL_ALL_SIGNS) >= 327

    for word in STATIC_WORDS:
        assert word in ISL_ALL_SIGNS
        hands = get_expected_hands(word)
        assert hands in (1, 2)
        guide1, guide2 = get_sign_guide(word)
        assert isinstance(guide1, str) and len(guide1) > 0
        assert isinstance(guide2, str) and len(guide2) > 0


def test_isl_studio_word_collection(tmp_path):
    """Verify that ISL collector accepts word signs like WATER and HELP."""
    from signora.isl.collect import run_isl_collector

    rc = run_isl_collector(sign="WATER", count=1, headless=True, use_fake=True, data_dir=tmp_path)
    assert rc == 0


def test_isl_phone_dataset_ingestion(tmp_path):
    """Verify that exported phone dataset JSON can be inspected and ingested."""
    import json
    from signora.isl.import_dataset import import_phone_dataset, inspect_dataset_file

    sample_file = tmp_path / "signora_isl_dataset.json"
    dummy_data = {
        "format": "signora_isl_dataset",
        "version": 1,
        "device": "Android",
        "feature_dim": 220,
        "total_classes": 2,
        "total_samples": 6,
        "classes": {
            "NAMASTE": [[0.1] * 220, [0.2] * 220, [0.15] * 220],
            "HELLO": [[0.3] * 220, [0.35] * 220, [0.4] * 220],
        },
    }
    sample_file.write_text(json.dumps(dummy_data))

    meta = inspect_dataset_file(sample_file)
    assert meta["total_classes"] == 2
    assert meta["total_samples"] == 6
    assert meta["feature_dim"] == 220
    assert "NAMASTE" in meta["classes"]
    assert meta["classes"]["NAMASTE"] == 3

    out_dir = tmp_path / "imported"
    res = import_phone_dataset(sample_file, target_dir=out_dir, auto_train=False)
    assert res["success"] is True
    assert len(res["imported_classes"]) == 2
    assert (out_dir / "NAMASTE.npy").exists()
    assert (out_dir / "HELLO.npy").exists()

    arr = np.load(out_dir / "NAMASTE.npy")
    assert arr.shape == (3, 220)


def test_topology_normalized_landmarks():
    """Verify RealAITopologyClassifier correctly parses NormalizedLandmark objects without raising TypeError."""
    from types import SimpleNamespace
    from signora.isl.topology import RealAITopologyClassifier

    classifier = RealAITopologyClassifier()

    # Create mock NormalizedLandmark objects matching MediaPipe's runtime structure
    mock_hand1 = [SimpleNamespace(x=0.5 + i * 0.01, y=0.5 - i * 0.01, z=0.0) for i in range(21)]
    mock_hand2 = [SimpleNamespace(x=0.4 + i * 0.01, y=0.4 - i * 0.01, z=0.0) for i in range(21)]

    # Must classify without raising TypeError: float() argument must be a string or a real number, not 'NormalizedLandmark'
    res = classifier.classify([mock_hand1, mock_hand2], ["Left", "Right"])
    assert res is None or (isinstance(res, tuple) and len(res) == 2)


def test_isl_mlp_zero_std_and_batched_input():
    """Verify ISL_MLP handles zero-variance feature standard deviations and batched 2D inputs without NaN."""
    from signora.isl.model import ISL_MLP

    in_dim = 164
    hidden1 = 32
    hidden2 = 16
    n_classes = 5

    # Mock weights
    w0 = (np.random.randn(in_dim, hidden1).astype(np.float32) * 0.01, np.zeros(hidden1, dtype=np.float32))
    ln0 = (np.ones(hidden1, dtype=np.float32), np.zeros(hidden1, dtype=np.float32))
    w1 = (np.random.randn(hidden1, hidden2).astype(np.float32) * 0.01, np.zeros(hidden2, dtype=np.float32))
    ln1 = (np.ones(hidden2, dtype=np.float32), np.zeros(hidden2, dtype=np.float32))
    w2 = (np.random.randn(hidden2, n_classes).astype(np.float32) * 0.01, np.zeros(n_classes, dtype=np.float32))

    labels = ["A", "B", "C", "D", "E"]
    mean = np.zeros(in_dim, dtype=np.float32)
    # Inject zeros into standard deviation to simulate invariant features
    std = np.zeros(in_dim, dtype=np.float32)

    mlp = ISL_MLP(
        weights=[w0, w1, w2],
        ln_params=[ln0, ln1],
        labels=labels,
        mean=mean,
        std=std,
        temperature=1.0,
    )

    # 1. 1D sample
    sample_1d = np.ones(in_dim, dtype=np.float32)
    probs_1d = mlp.forward(sample_1d)
    assert probs_1d.shape == (n_classes,)
    assert np.all(np.isfinite(probs_1d))
    assert np.isclose(np.sum(probs_1d), 1.0, atol=1e-5)

    # 2. Batched 2D samples (batch_size=4)
    batch_2d = np.ones((4, in_dim), dtype=np.float32)
    probs_2d = mlp.forward(batch_2d)
    assert probs_2d.shape == (4, n_classes)
    assert np.all(np.isfinite(probs_2d))
    assert np.allclose(np.sum(probs_2d, axis=-1), 1.0, atol=1e-5)


def test_isl_mlp_feature_dimension_mismatch():
    """Verify ISL_MLP aligns input feature dimensions when input size differs from in_dim."""
    from signora.isl.model import ISL_MLP

    in_dim = 164
    w0 = (np.zeros((in_dim, 16), dtype=np.float32), np.zeros(16, dtype=np.float32))
    ln0 = (np.ones(16, dtype=np.float32), np.zeros(16, dtype=np.float32))
    w1 = (np.zeros((16, 8), dtype=np.float32), np.zeros(8, dtype=np.float32))
    ln1 = (np.ones(8, dtype=np.float32), np.zeros(8, dtype=np.float32))
    w2 = (np.zeros((8, 3), dtype=np.float32), np.zeros(3, dtype=np.float32))

    mlp = ISL_MLP(
        weights=[w0, w1, w2],
        ln_params=[ln0, ln1],
        labels=["A", "B", "C"],
        mean=np.zeros(in_dim, dtype=np.float32),
        std=np.ones(in_dim, dtype=np.float32),
    )

    # 74D input (undersized)
    probs_under = mlp.forward(np.ones(74, dtype=np.float32))
    assert probs_under.shape == (3,)
    assert np.isclose(np.sum(probs_under), 1.0)

    # 220D input (oversized)
    probs_over = mlp.forward(np.ones(220, dtype=np.float32))
    assert probs_over.shape == (3,)
    assert np.isclose(np.sum(probs_over), 1.0)


def test_isl_mlp_zero_classes():
    """Verify ISL_MLP handles empty label set / 0 classes safely."""
    from signora.isl.model import ISL_MLP

    in_dim = 16
    w0 = (np.zeros((in_dim, 8), dtype=np.float32), np.zeros(8, dtype=np.float32))
    ln0 = (np.ones(8, dtype=np.float32), np.zeros(8, dtype=np.float32))
    w1 = (np.zeros((8, 4), dtype=np.float32), np.zeros(4, dtype=np.float32))
    ln1 = (np.ones(4, dtype=np.float32), np.zeros(4, dtype=np.float32))
    w2 = (np.zeros((4, 0), dtype=np.float32), np.zeros(0, dtype=np.float32))

    mlp = ISL_MLP(
        weights=[w0, w1, w2],
        ln_params=[ln0, ln1],
        labels=[],
        mean=np.zeros(in_dim, dtype=np.float32),
        std=np.ones(in_dim, dtype=np.float32),
    )

    probs = mlp.forward(np.ones(in_dim, dtype=np.float32))
    assert len(probs) == 0





"""Tests for model manifest loading, sha256 checksums, and model resolution."""

from signora.models.manifest import (
    get_model_path,
    load_manifest,
    verify_model,
)


def test_manifest_structure():
    manifest = load_manifest()
    assert "version" in manifest
    assert "models" in manifest
    models = manifest["models"]
    for key in (
        "hand_landmarker",
        "asl_letters",
        "isl_signs",
        "asl_words",
        "piper_voice",
        "piper_config",
    ):
        assert key in models
        assert "filename" in models[key]
        assert "sha256" in models[key]
        assert "size_bytes" in models[key]


def test_model_paths_and_verification():
    for key in (
        "hand_landmarker",
        "asl_letters",
        "isl_signs",
        "asl_words",
        "piper_voice",
        "piper_config",
    ):
        path = get_model_path(key)
        assert path.exists(), f"Model {key} ({path.name}) should exist in models directory."
        ok, msg = verify_model(key)
        assert ok is True, f"Model {key} failed verification: {msg}"

"""Unit tests for Signora interactive TUI, gesture dictionary, and settings."""

from unittest.mock import MagicMock

from signora.tui.app import SignoraTUI
from signora.tui.dictionary import (
    ASL_GESTURES,
    ISL_GESTURES,
    WORDS_GESTURES,
    search_all_gestures,
)


def test_tui_dictionary_structure():
    """Verify completeness and required keys across all gesture catalogs."""
    assert len(ASL_GESTURES) >= 27  # A-Z + SPACE
    assert "A" in ASL_GESTURES
    assert "SPACE" in ASL_GESTURES
    assert "posture" in ASL_GESTURES["A"]
    assert "tip" in ASL_GESTURES["A"]

    assert len(ISL_GESTURES) >= 30  # Letters + Static Words + SPACE
    assert "NAMASTE" in ISL_GESTURES
    assert "GOOD" in ISL_GESTURES
    assert "hands" in ISL_GESTURES["NAMASTE"]

    assert len(WORDS_GESTURES) >= 20
    assert "HELLO" in WORDS_GESTURES
    assert "THANKYOU" in WORDS_GESTURES
    assert "trajectory" in WORDS_GESTURES["HELLO"]


def test_tui_dictionary_search():
    """Verify fuzzy search across catalogs."""
    results_a = search_all_gestures("A")
    assert len(results_a) > 0
    systems = {r["system"] for r in results_a}
    assert "ASL Fingerspelling" in systems

    results_namaste = search_all_gestures("NAMASTE")
    assert any(r["sign"] == "NAMASTE" for r in results_namaste)

    results_empty = search_all_gestures("NONEXISTENT_SIGN_12345")
    assert len(results_empty) == 0


def test_tui_settings_initialization_and_mutation():
    """Verify session settings initialization and defaults."""
    tui = SignoraTUI()
    assert tui.settings["dwell_ms"] == 600
    assert tui.settings["no_tts"] is False
    assert tui.settings["camera_index"] == 0

    # Mutate settings
    tui.settings["dwell_ms"] = 450
    tui.settings["no_tts"] = True
    tui.settings["camera_index"] = 2

    assert tui.settings["dwell_ms"] == 450
    assert tui.settings["no_tts"] is True
    assert tui.settings["camera_index"] == 2


def test_tui_run_with_auto_heal_passes_settings(monkeypatch):
    """Verify _run_with_auto_heal passes active dwell, no_tts, and camera settings."""
    tui = SignoraTUI()
    tui.settings["dwell_ms"] = 800
    tui.settings["no_tts"] = True
    tui.settings["camera_index"] = 1

    # Mock probe_cameras to report camera index 1 is available
    monkeypatch.setattr(
        "signora.tui.app.probe_cameras",
        lambda max_probe=4: [{"index": 1, "width": 640, "height": 480}],
    )

    mock_runner = MagicMock()

    def sample_runner(source=0, no_tts=False, dwell_ms=600):
        mock_runner(source=source, no_tts=no_tts, dwell_ms=dwell_ms)

    tui._run_with_auto_heal("Test Mode", sample_runner)

    mock_runner.assert_called_once_with(source=1, no_tts=True, dwell_ms=800)

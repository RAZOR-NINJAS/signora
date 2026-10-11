"""Tests for temporal debounce state machine, dwell confirmation, and space gesture filter."""

from signora.core.debounce import DebounceEngine


def test_debounce_dwell_confirmation():
    engine = DebounceEngine(dwell_ms=200, smooth_ms=100, confirm_threshold=0.6)

    # 1. Initial observation
    st = engine.update("A", 0.9, now_ms=1000)
    assert st["candidate"] == "A"
    assert st["confirmed"] is None
    assert engine.buffer == ""

    # 2. Before dwell threshold (150ms later)
    st = engine.update("A", 0.9, now_ms=1150)
    assert st["confirmed"] is None
    assert engine.buffer == ""

    # 3. Exactly at/after dwell threshold (250ms later)
    st = engine.update("A", 0.9, now_ms=1250)
    assert st["confirmed"] == "A"
    assert engine.buffer == "A"


def test_debounce_space_gesture_dwell():
    engine = DebounceEngine(dwell_ms=200, space_dwell_ms=400, confirm_threshold=0.6)

    engine.buffer = "HELLO"

    # Observe SPACE sign
    engine.update("SPACE", 0.85, now_ms=2000)
    assert engine.buffer == "HELLO"

    # Before space dwell (250ms)
    engine.update("SPACE", 0.85, now_ms=2250)
    assert engine.buffer == "HELLO"

    # Past space dwell (450ms)
    st = engine.update("SPACE", 0.85, now_ms=2450)
    assert st["confirmed"] in ("SPACE", " ")
    assert engine.buffer == "HELLO "


def test_debounce_backspace_and_clear():
    engine = DebounceEngine()
    engine.buffer = "TESTING"

    engine.backspace()
    assert engine.buffer == "TESTIN"

    engine.clear()
    assert engine.buffer == ""


def test_debounce_default_monotonic_clock():
    engine = DebounceEngine(dwell_ms=50, smooth_ms=50, threshold=0.5)
    # Call without timestamp_ms or now_ms (tests default time.monotonic fallback)
    st = engine.update("B", 0.95)
    assert st["candidate"] == "B"
    assert st["confirmed"] is None


def test_debounce_holds_transcript_across_updates():
    """Ensure that once confirmed, letters remain in buffer across frames."""
    engine = DebounceEngine(dwell_ms=100, smooth_ms=50, threshold=0.5)

    # Dwell confirm 'A'
    engine.update("A", 0.9, now_ms=1000)
    st = engine.update("A", 0.9, now_ms=1150)
    assert st["confirmed"] == "A"
    assert engine.buffer == "A"

    # Subsequent frames (resting or transitioning) must hold 'A' in buffer
    st2 = engine.update(None, 0.0, now_ms=1200)
    assert engine.buffer == "A"
    assert st2["transcript"] == "A"

    st3 = engine.update(None, 0.0, now_ms=1500)
    assert engine.buffer == "A"
    assert st3["transcript"] == "A"

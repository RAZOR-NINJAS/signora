"""Unit tests for offline Piper neural TTS engine."""

import time

from signora.tts.piper import PiperTTS


def test_piper_initialization():
    """Verify PiperTTS initializes cleanly without blocking startup."""
    tts = PiperTTS(enabled=True)
    # Even if models are missing or present, it must initialize gracefully
    assert hasattr(tts, "is_ready")
    assert hasattr(tts, "is_speaking")
    assert tts.is_speaking is False
    tts.close()


def test_piper_speak_async():
    """Verify speak_async enqueues and executes without wave or thread errors."""
    tts = PiperTTS(enabled=True)
    if tts.is_ready:
        tts.speak_async("Signora")
        time.sleep(0.3)
        assert tts.last_spoken_text == "Signora"
        assert tts.last_spoken_time > 0.0
    tts.close()

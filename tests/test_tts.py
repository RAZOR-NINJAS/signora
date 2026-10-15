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


def test_piper_phonetic_normalization():
    """Verify that single letters and sign words are phonetically expanded for natural articulation."""
    from signora.tts.piper import SPOKEN_PHONETICS

    tts = PiperTTS(enabled=False)
    assert tts._normalize_speech_text("A") == "Aye"
    assert tts._normalize_speech_text("b") == "Bee"
    assert tts._normalize_speech_text("SPACE") == "Space"
    assert tts._normalize_speech_text("THANK_YOU") == "THANK YOU"
    assert len(SPOKEN_PHONETICS) >= 27


def test_piper_smooth_audio():
    """Verify that _smooth_audio eliminates abrupt clipping with cosine fades."""
    import numpy as np
    from signora.tts.piper import _smooth_audio

    raw = np.ones(2205, dtype=np.float32)
    smoothed = _smooth_audio(raw, 22050)
    assert len(smoothed) == len(raw)
    assert smoothed[0] < 0.1  # Smooth fade-in at onset
    assert smoothed[-1] < 0.1  # Smooth fade-out at end
    assert np.all(np.isfinite(smoothed))


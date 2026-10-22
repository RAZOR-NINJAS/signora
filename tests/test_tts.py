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


def test_piper_extended_vocabulary():
    """Verify digits, ordinals, sign acronyms, and hyphenated fingerspelling."""
    tts = PiperTTS(enabled=False)
    assert tts._normalize_speech_text("1") == "One"
    assert tts._normalize_speech_text("0") == "Zero"
    assert tts._normalize_speech_text("1ST") == "First"
    assert tts._normalize_speech_text("ISL") == "Indian Sign Language"
    assert tts._normalize_speech_text("SOS") == "S O S"
    assert tts._normalize_speech_text("OK") == "Okay"
    assert tts._normalize_speech_text("DOC") == "Doctor"
    assert tts._normalize_speech_text("C-A-T") == "Cat"


def test_piper_lru_cache():
    """Verify LRUAudioCache get, put, eviction, and dict compatibility."""
    import numpy as np
    from signora.tts.piper import LRUAudioCache

    cache = LRUAudioCache(maxsize=3)
    a1 = (np.zeros(10, dtype=np.float32), 22050)
    a2 = (np.ones(10, dtype=np.float32), 22050)
    a3 = (np.full(10, 0.5, dtype=np.float32), 22050)
    a4 = (np.full(10, 0.8, dtype=np.float32), 22050)

    cache.put("HELLO", a1)
    cache.put("YES", a2)
    cache.put("NO", a3)
    assert len(cache) == 3
    assert "HELLO" in cache

    # Access HELLO to make it recently used
    assert cache.get("HELLO") is not None
    # Add a 4th item, which should evict YES
    cache.put("OK", a4)
    assert len(cache) == 3
    assert "OK" in cache
    assert "HELLO" in cache
    assert "YES" not in cache


def test_piper_adaptive_prosody():
    """Verify that prosody parameters dynamically adapt to sentence context."""
    tts = PiperTTS(enabled=False)
    cfg_urgent = tts._get_adaptive_synthesis_config("Emergency! Please call an ambulance!")
    if cfg_urgent is not None:
        assert cfg_urgent.length_scale < 0.90  # Faster delivery
        assert cfg_urgent.volume > 1.05  # Boosted alert volume

    cfg_question = tts._get_adaptive_synthesis_config("Where is the doctor?")
    if cfg_question is not None:
        assert cfg_question.length_scale >= 0.90


def test_piper_speed_volume_controls():
    """Verify speed, volume, and muting setters update cleanly."""
    tts = PiperTTS(enabled=False)
    tts.set_speed(1.3)
    assert tts.speed == 1.3
    tts.set_volume(0.8)
    assert tts.volume == 0.8
    tts.set_muted(True)
    assert tts.is_muted is True
    tts.set_muted(False)
    assert tts.is_muted is False


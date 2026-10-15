"""Offline Piper neural text-to-speech engine with non-blocking audio playback."""

import atexit
import os
import queue
import threading
import time
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import numpy as np

from signora.models.manifest import get_default_models_dir, get_model_path

try:
    import sounddevice as sd
    from piper import PiperVoice
    try:
        from piper.config import SynthesisConfig
        _HAS_SYN_CONFIG = True
    except ImportError:
        _HAS_SYN_CONFIG = False

    _HAS_TTS_LIBS = True
except (ImportError, OSError):
    _HAS_TTS_LIBS = False
    sd = None
    PiperVoice = None
    _HAS_SYN_CONFIG = False

# Articulate phonetic pronunciation for fingerspelled single letters & punctuation
SPOKEN_PHONETICS: Dict[str, str] = {
    "A": "Aye",
    "B": "Bee",
    "C": "See",
    "D": "Dee",
    "E": "Ee",
    "F": "Eff",
    "G": "Jee",
    "H": "Aitch",
    "I": "Eye",
    "J": "Jay",
    "K": "Kay",
    "L": "Ell",
    "M": "Emm",
    "N": "Enn",
    "O": "Oh",
    "P": "Pee",
    "Q": "Queue",
    "R": "Arr",
    "S": "Ess",
    "T": "Tee",
    "U": "You",
    "V": "Vee",
    "W": "Double you",
    "X": "Ex",
    "Y": "Why",
    "Z": "Zed",
    "SPACE": "Space",
}


def _smooth_audio(audio: np.ndarray, sr: int) -> np.ndarray:
    """Apply raised-cosine window ramps (5ms) to eliminate audio clipping clicks and pops."""
    if len(audio) < 16:
        return audio
    fade_len = min(int(sr * 0.005), len(audio) // 4)
    if fade_len > 0:
        fade_in = 0.5 * (1.0 - np.cos(np.linspace(0, np.pi, fade_len)))
        fade_out = 0.5 * (1.0 + np.cos(np.linspace(0, np.pi, fade_len)))
        audio = audio.copy()
        audio[:fade_len] *= fade_in
        audio[-fade_len:] *= fade_out
    return audio


class PiperTTS:
    """Asynchronous offline text-to-speech engine with fast audio chunk dispatch."""

    def __init__(self, model_path: Optional[Path] = None, enabled: bool = True):
        self.enabled = enabled and _HAS_TTS_LIBS
        self._voice: Optional[Any] = None
        self._queue: "queue.Queue" = queue.Queue(maxsize=32)
        self._stop_event = threading.Event()
        self._worker_thread: Optional[threading.Thread] = None
        self._cache: Dict[str, Tuple[np.ndarray, int]] = {}
        self.is_speaking = False
        self.is_ready = False
        self.last_spoken_text = ""
        self.last_spoken_time = 0.0

        if not self.enabled:
            return

        try:
            # Voice resolution: check custom path, env var, Amy (natural female), or Lessac fallback
            models_dir = get_default_models_dir()
            env_voice = os.environ.get("SIGNORA_VOICE", "").strip()

            target_model = model_path
            if target_model is None and env_voice:
                candidate = models_dir / f"{env_voice}.onnx"
                if candidate.exists():
                    target_model = candidate

            if target_model is None:
                # Check for high-fidelity Amy voice
                amy_path = models_dir / "en_US-amy-medium.onnx"
                if amy_path.exists() and amy_path.with_suffix(".onnx.json").exists():
                    target_model = amy_path
                else:
                    target_model = get_model_path("piper_voice")

            self.model_path = target_model
            self.config_path = self.model_path.with_suffix(".onnx.json")

            if self.model_path.exists() and self.config_path.exists():
                self._voice = PiperVoice.load(str(self.model_path), str(self.config_path))
                self.is_ready = True
                self._worker_thread = threading.Thread(
                    target=self._worker_loop, daemon=True, name="PiperTTSWorker"
                )
                self._worker_thread.start()
                atexit.register(self.close)
            else:
                self.is_ready = False
        except Exception:
            self.is_ready = False

    def _normalize_speech_text(self, text: str) -> str:
        """Expand single fingerspelled letters and sign words to clear natural speech."""
        cleaned = text.strip()
        upper = cleaned.upper()
        if upper in SPOKEN_PHONETICS:
            return SPOKEN_PHONETICS[upper]
        # Replace underscores with spaces (e.g. THANK_YOU -> Thank You)
        if "_" in cleaned:
            cleaned = cleaned.replace("_", " ")
        return cleaned

    def _synthesize_audio(self, text: str) -> Optional[Tuple[np.ndarray, int]]:
        """Synthesize text into raw float32 audio numpy array and sample rate."""
        if not self._voice:
            return None
        try:
            spoken_text = self._normalize_speech_text(text)
            chunks = []
            sr = 22050

            syn_cfg = None
            if _HAS_SYN_CONFIG:
                syn_cfg = SynthesisConfig(
                    length_scale=0.92,
                    noise_scale=0.45,
                    noise_w_scale=0.75,
                    normalize_audio=True,
                    volume=1.0,
                )

            kwargs = {}
            if syn_cfg is not None:
                kwargs["syn_config"] = syn_cfg

            for chunk in self._voice.synthesize(spoken_text, **kwargs):
                chunks.append(chunk.audio_float_array)
                sr = chunk.sample_rate

            if not chunks:
                return None

            audio = np.concatenate(chunks)
            audio = _smooth_audio(audio, sr)
            return audio, sr
        except Exception:
            return None

    def _worker_loop(self) -> None:
        """Background audio synthesis and playback loop."""
        while not self._stop_event.is_set():
            try:
                text = self._queue.get(timeout=0.2)
            except queue.Empty:
                continue

            if not text or not self._voice or sd is None:
                continue

            self.is_speaking = True
            self.last_spoken_text = text.strip()
            self.last_spoken_time = time.monotonic()

            try:
                upper = text.strip().upper()
                if upper in self._cache:
                    audio, sr = self._cache[upper]
                else:
                    res = self._synthesize_audio(text.strip())
                    if res is None:
                        continue
                    audio, sr = res
                    # Cache short words and characters for instant repeat playback
                    if len(upper) <= 16 and len(self._cache) < 512:
                        self._cache[upper] = (audio, sr)

                sd.play(audio, samplerate=sr)
                sd.wait()
            except Exception:
                pass
            finally:
                self.is_speaking = False

    def speak_async(self, text: str) -> None:
        """Enqueue text for non-blocking speech playback."""
        if not self.is_ready or not text.strip():
            return
        self.last_spoken_text = text.strip()
        self.last_spoken_time = time.monotonic()
        try:
            self._queue.put_nowait(text.strip())
        except queue.Full:
            pass

    def close(self) -> None:
        """Shut down background TTS thread."""
        self._stop_event.set()
        if sd is not None:
            try:
                sd.stop()
            except Exception:
                pass
        if self._worker_thread and self._worker_thread.is_alive():
            self._worker_thread.join(timeout=0.5)


_GLOBAL_TTS_INSTANCE: Optional[PiperTTS] = None


def get_tts() -> PiperTTS:
    """Return shared singleton Piper text-to-speech instance."""
    global _GLOBAL_TTS_INSTANCE
    if _GLOBAL_TTS_INSTANCE is None:
        _GLOBAL_TTS_INSTANCE = PiperTTS()
    return _GLOBAL_TTS_INSTANCE


def speak_async(text: str) -> None:
    """Enqueue spoken text on the shared global TTS instance."""
    tts = get_tts()
    if tts:
        tts.speak_async(text)


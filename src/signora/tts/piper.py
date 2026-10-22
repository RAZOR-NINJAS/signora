"""Offline Piper neural text-to-speech engine with non-blocking audio playback."""

import atexit
import os
import queue
import threading
import time
from collections import OrderedDict
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
    # Alphabet fingerspelling
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
    # Digits
    "0": "Zero",
    "1": "One",
    "2": "Two",
    "3": "Three",
    "4": "Four",
    "5": "Five",
    "6": "Six",
    "7": "Seven",
    "8": "Eight",
    "9": "Nine",
    # Ordinals
    "1ST": "First",
    "2ND": "Second",
    "3RD": "Third",
    "4TH": "Fourth",
    "5TH": "Fifth",
    # Special sign tokens
    "SPACE": "Space",
    "DEL": "Delete",
    "BACKSPACE": "Delete",
    "ISL": "Indian Sign Language",
    "ASL": "American Sign Language",
    "SOS": "S O S",
    "OK": "Okay",
    "PLS": "Please",
    "PLZ": "Please",
    "THX": "Thank you",
    "TY": "Thank you",
    "DR": "Doctor",
    "DOC": "Doctor",
    "HOSP": "Hospital",
    "MED": "Medicine",
    "MEDS": "Medicine",
    "EMRG": "Emergency",
    "EMERG": "Emergency",
    "AMB": "Ambulance",
    "POL": "Police",
    "WTR": "Water",
    "BATH": "Washroom",
    "WASH": "Washroom",
}


def _smooth_audio(audio: np.ndarray, sr: int) -> np.ndarray:
    """Apply raised-cosine window ramps (5ms) and peak normalization to eliminate clipping clicks and pops."""
    if len(audio) < 16:
        return audio
    fade_len = min(int(sr * 0.005), len(audio) // 4)
    audio = audio.copy()
    if fade_len > 0:
        fade_in = 0.5 * (1.0 - np.cos(np.linspace(0, np.pi, fade_len)))
        fade_out = 0.5 * (1.0 + np.cos(np.linspace(0, np.pi, fade_len)))
        audio[:fade_len] *= fade_in
        audio[-fade_len:] *= fade_out

    # Peak normalization to -0.8 dBFS (0.912) to ensure loud, crisp audio without digital clipping
    peak = float(np.max(np.abs(audio)))
    if peak > 1e-4:
        audio = (audio / peak) * 0.912

    return audio.astype(np.float32)


class LRUAudioCache:
    """Thread-safe bounded LRU audio buffer cache with dict duck-typing."""

    def __init__(self, maxsize: int = 512):
        self.maxsize = maxsize
        self._cache: OrderedDict[str, Tuple[np.ndarray, int]] = OrderedDict()
        self._lock = threading.Lock()

    def get(self, key: str, default: Optional[Tuple[np.ndarray, int]] = None) -> Optional[Tuple[np.ndarray, int]]:
        with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
                return self._cache[key]
            return default

    def put(self, key: str, val: Tuple[np.ndarray, int]) -> None:
        with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
            self._cache[key] = val
            if len(self._cache) > self.maxsize:
                self._cache.popitem(last=False)

    def clear(self) -> None:
        with self._lock:
            self._cache.clear()

    def __getitem__(self, key: str) -> Tuple[np.ndarray, int]:
        with self._lock:
            if key in self._cache:
                self._cache.move_to_end(key)
                return self._cache[key]
            raise KeyError(key)

    def __setitem__(self, key: str, val: Tuple[np.ndarray, int]) -> None:
        self.put(key, val)

    def __contains__(self, key: str) -> bool:
        with self._lock:
            return key in self._cache

    def __len__(self) -> int:
        with self._lock:
            return len(self._cache)


class PiperTTS:
    """Asynchronous offline text-to-speech engine with fast audio chunk dispatch."""

    def __init__(self, model_path: Optional[Path] = None, enabled: bool = True):
        self.enabled = enabled and _HAS_TTS_LIBS
        self._voice: Optional[Any] = None
        self._queue: "queue.Queue" = queue.Queue(maxsize=32)
        self._stop_event = threading.Event()
        self._worker_thread: Optional[threading.Thread] = None
        self._cache = LRUAudioCache(maxsize=512)
        self.is_speaking = False
        self.is_ready = False
        self.last_spoken_text = ""
        self.last_spoken_time = 0.0

        # Dynamic prosody & volume controls
        try:
            self.speed = float(os.environ.get("SIGNORA_TTS_SPEED", "1.0"))
        except ValueError:
            self.speed = 1.0

        try:
            self.volume = float(os.environ.get("SIGNORA_TTS_VOLUME", "1.0"))
        except ValueError:
            self.volume = 1.0

        self.is_muted = os.environ.get("SIGNORA_NO_TTS", "").lower() in ("1", "true", "yes")

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

                # Warm up ONNX execution graphs asynchronously
                threading.Thread(target=self._prewarm, daemon=True, name="PiperTTSPrewarm").start()
            else:
                self.is_ready = False
        except Exception:
            self.is_ready = False

    def _prewarm(self) -> None:
        """Run asynchronous neural graph pre-warming and populate instant cache."""
        if not self._voice:
            return
        try:
            # Prime ONNX runtime execution providers and tensor scratch buffers
            self._synthesize_audio(".")

            # Pre-cache common singletons and letters for zero-latency instant playback
            prewarm_tokens = [
                "YES", "NO", "OK", "HELLO", "HELP", "EMERGENCY", "PLEASE", "SPACE",
                "A", "B", "C", "D", "E", "1", "2", "3"
            ]
            for tok in prewarm_tokens:
                if self._stop_event.is_set():
                    break
                upper = tok.upper()
                if upper not in self._cache:
                    res = self._synthesize_audio(tok)
                    if res is not None:
                        self._cache.put(upper, res)
        except Exception:
            pass

    def _normalize_speech_text(self, text: str) -> str:
        """Expand single fingerspelled letters, digits, and sign words to clear natural speech."""
        cleaned = text.strip()
        if not cleaned:
            return ""

        upper = cleaned.upper()
        if upper in SPOKEN_PHONETICS:
            return SPOKEN_PHONETICS[upper]

        # Replace underscores with spaces (e.g. THANK_YOU -> THANK YOU)
        if "_" in cleaned:
            cleaned = cleaned.replace("_", " ")

        # Check again after underscore replacement
        upper_no_us = cleaned.upper()
        if upper_no_us in SPOKEN_PHONETICS:
            return SPOKEN_PHONETICS[upper_no_us]

        # Fingerspelled hyphenated / space-separated letters: e.g. "C-A-T" or "H-E-L-L-O"
        if "-" in cleaned:
            parts = cleaned.split("-")
            if all(len(p.strip()) == 1 and p.strip().isalpha() for p in parts if p.strip()):
                return "".join(parts).capitalize()

        return cleaned

    def _get_adaptive_synthesis_config(self, text: str) -> Optional[Any]:
        """Compute context-aware neural prosody parameters (pace, emotion, and inflection)."""
        if not _HAS_SYN_CONFIG:
            return None

        lower = text.lower().strip()
        is_urgent = any(w in lower for w in [
            "emergency", "help", "danger", "police", "ambulance",
            "hospital", "hurts", "bleeding", "severe", "pain"
        ]) or text.endswith("!")

        is_question = text.endswith("?") or lower.startswith((
            "where", "what", "who", "when", "why", "how", "which", "can", "is", "are"
        ))

        is_short_token = len(text) <= 3
        rate_factor = max(0.5, min(2.0, self.speed))

        if is_urgent:
            # Energetic, fast, slightly louder vocalization
            length_scale = 0.85 / rate_factor
            noise_scale = 0.65
            noise_w_scale = 0.80
            vol = min(1.2 * self.volume, 2.0)
        elif is_question:
            # Inquiring intonation, slightly more measured cadence
            length_scale = 0.94 / rate_factor
            noise_scale = 0.62
            noise_w_scale = 0.82
            vol = 1.0 * self.volume
        elif is_short_token:
            # Crisp articulation for single fingerspelled letters / digits
            length_scale = 1.00 / rate_factor
            noise_scale = 0.40
            noise_w_scale = 0.70
            vol = 1.0 * self.volume
        else:
            # Fluent natural conversational prosody
            length_scale = 0.92 / rate_factor
            noise_scale = 0.667
            noise_w_scale = 0.80
            vol = 1.0 * self.volume

        return SynthesisConfig(
            length_scale=float(length_scale),
            noise_scale=float(noise_scale),
            noise_w_scale=float(noise_w_scale),
            normalize_audio=True,
            volume=float(vol),
        )

    def _synthesize_audio(self, text: str) -> Optional[Tuple[np.ndarray, int]]:
        """Synthesize text into raw float32 audio numpy array and sample rate."""
        if not self._voice:
            return None
        try:
            spoken_text = self._normalize_speech_text(text)
            if not spoken_text:
                return None

            chunks = []
            sr = 22050

            syn_cfg = self._get_adaptive_synthesis_config(spoken_text)
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
                item = self._queue.get(timeout=0.2)
            except queue.Empty:
                continue

            if not item or not self._voice or sd is None or self.is_muted:
                continue

            text = item.strip()
            if not text:
                continue

            self.is_speaking = True
            self.last_spoken_text = text
            self.last_spoken_time = time.monotonic()

            try:
                upper = text.upper()
                cached = self._cache.get(upper)
                if cached is not None:
                    audio, sr = cached
                else:
                    res = self._synthesize_audio(text)
                    if res is None:
                        continue
                    audio, sr = res
                    # Cache short words, letters, and frequent phrases (up to 80 chars)
                    if len(upper) <= 80:
                        self._cache.put(upper, (audio, sr))

                if not self._stop_event.is_set() and not self.is_muted:
                    sd.play(audio, samplerate=sr)
                    sd.wait()
            except Exception:
                pass
            finally:
                self.is_speaking = False

    def speak_async(self, text: str, interrupt: bool = False) -> None:
        """Enqueue text for non-blocking speech playback.

        Args:
            text: Sentence or token to vocalize.
            interrupt: If True, flushes pending speech queue and stops active audio immediately.
        """
        if not self.is_ready or not text.strip() or self.is_muted:
            return

        cleaned = text.strip()
        now = time.monotonic()

        # Debounce identical single-character utterances in rapid succession (< 180ms)
        if len(cleaned) <= 2 and cleaned == self.last_spoken_text and (now - self.last_spoken_time) < 0.18:
            return

        self.last_spoken_text = cleaned
        self.last_spoken_time = now

        if interrupt:
            # Drain queue immediately
            while not self._queue.empty():
                try:
                    self._queue.get_nowait()
                except queue.Empty:
                    break
            # Stop current sound device output
            if sd is not None:
                try:
                    sd.stop()
                except Exception:
                    pass

        try:
            self._queue.put_nowait(cleaned)
        except queue.Full:
            pass

    def set_speed(self, speed: float) -> None:
        """Set speaking rate multiplier (e.g. 0.8 for slower, 1.2 for faster)."""
        self.speed = float(np.clip(speed, 0.5, 2.0))
        self._cache.clear()

    def set_volume(self, volume: float) -> None:
        """Set audio volume multiplier (0.0 to 2.0)."""
        self.volume = float(np.clip(volume, 0.0, 2.0))

    def set_muted(self, muted: bool) -> None:
        """Mute or unmute speech playback."""
        self.is_muted = muted
        if muted and sd is not None:
            try:
                sd.stop()
            except Exception:
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


def speak_async(text: str, interrupt: bool = False) -> None:
    """Enqueue spoken text on the shared global TTS instance."""
    tts = get_tts()
    if tts:
        tts.speak_async(text, interrupt=interrupt)


"""Offline Piper neural text-to-speech engine with non-blocking audio playback."""

import atexit
import io
import queue
import threading
import wave
from pathlib import Path
from typing import Dict, Optional, Tuple

import numpy as np

from signora.models.manifest import get_model_path

try:
    import sounddevice as sd
    from piper import PiperVoice

    _HAS_TTS_LIBS = True
except (ImportError, OSError):
    _HAS_TTS_LIBS = False
    sd = None
    PiperVoice = None


class PiperTTS:
    """Asynchronous offline text-to-speech engine with pre-cached letter phonetics."""

    def __init__(self, model_path: Optional[Path] = None, enabled: bool = True):
        self.enabled = enabled and _HAS_TTS_LIBS
        self._voice = None
        self._queue: "queue.Queue" = queue.Queue(maxsize=32)
        self._stop_event = threading.Event()
        self._worker_thread: Optional[threading.Thread] = None
        self._cache: Dict[str, Tuple[np.ndarray, int]] = {}
        self.is_speaking = False
        self.is_ready = False

        if not self.enabled:
            return

        try:
            self.model_path = model_path or get_model_path("piper_voice")
            self.config_path = self.model_path.with_suffix(".onnx.json")

            if self.model_path.exists() and self.config_path.exists():
                self._voice = PiperVoice.load(str(self.model_path), str(self.config_path))
                self.is_ready = True
                self._precache_common()
                self._worker_thread = threading.Thread(target=self._worker_loop, daemon=True)
                self._worker_thread.start()
                atexit.register(self.close)
            else:
                self.is_ready = False
        except Exception:
            self.is_ready = False

    def _precache_common(self) -> None:
        if not self._voice:
            return
        common_words = [chr(c) for c in range(ord("A"), ord("Z") + 1)]
        common_words.extend(["space", "clear", "hello", "thank you", "yes", "no"])
        for item in common_words:
            try:
                buf = io.BytesIO()
                with wave.open(buf, "wb") as wf:
                    self._voice.synthesize(item, wf)
                buf.seek(0)
                with wave.open(buf, "rb") as wf:
                    sr = wf.getframerate()
                    n = wf.getnframes()
                    raw = wf.readframes(n)
                    audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
                    self._cache[item.upper()] = (audio, sr)
            except Exception:
                continue

    def _worker_loop(self) -> None:
        while not self._stop_event.is_set():
            try:
                text = self._queue.get(timeout=0.2)
            except queue.Empty:
                continue

            if not text or not self._voice or sd is None:
                continue

            self.is_speaking = True
            try:
                upper = text.strip().upper()
                if upper in self._cache:
                    audio, sr = self._cache[upper]
                else:
                    buf = io.BytesIO()
                    with wave.open(buf, "wb") as wf:
                        self._voice.synthesize(text, wf)
                    buf.seek(0)
                    with wave.open(buf, "rb") as wf:
                        sr = wf.getframerate()
                        n = wf.getnframes()
                        raw = wf.readframes(n)
                        audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0

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
        try:
            self._queue.put_nowait(text.strip())
        except queue.Full:
            pass

    def close(self) -> None:
        """Shut down background TTS thread."""
        self._stop_event.set()
        if self._worker_thread and self._worker_thread.is_alive():
            self._worker_thread.join(timeout=1.0)

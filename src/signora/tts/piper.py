"""Offline Piper neural text-to-speech engine with non-blocking audio playback."""

import atexit
import queue
import threading
import time
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

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
            self.model_path = model_path or get_model_path("piper_voice")
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

    def _synthesize_audio(self, text: str) -> Optional[Tuple[np.ndarray, int]]:
        """Synthesize text into raw float32 audio numpy array and sample rate."""
        if not self._voice:
            return None
        try:
            chunks = []
            sr = 22050
            for chunk in self._voice.synthesize(text):
                chunks.append(chunk.audio_float_array)
                sr = chunk.sample_rate
            if not chunks:
                return None
            return np.concatenate(chunks), sr
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
                    if len(upper) <= 16 and len(self._cache) < 256:
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
        if self._worker_thread and self._worker_thread.is_alive():
            self._worker_thread.join(timeout=1.0)

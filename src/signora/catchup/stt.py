"""Speech-to-Text caption stream engine for SIGNORA Catch-Up."""

import queue
import threading
import time
from typing import Callable, List, Optional

import numpy as np

from signora.catchup.history import CaptionEntry, CaptionHistory

try:
    import sounddevice as sd
    SOUNDDEVICE_AVAILABLE = True
except Exception:
    sd = None
    SOUNDDEVICE_AVAILABLE = False


# Classroom and meeting simulated speech announcements for demonstration and offline fallback
SAMPLE_ANNOUNCEMENTS = [
    "Good morning everyone, please take your seats.",
    "Today we are reviewing chapter 4 on accessible interfaces.",
    "Please remember that your project proposal is due on Friday by 5 PM.",
    "Does anyone have questions regarding the previous assignment?",
    "We will have a ten-minute break before starting the lab exercise.",
    "Attention: the guest speaker presentation has moved to Room 204.",
    "Make sure to submit your team peer reviews by the end of the week.",
    "Great work today everyone, that concludes our session.",
]


class CatchUpEngine:
    """Manages audio capture, speech transcription, and caption history."""

    def __init__(
        self,
        history: Optional[CaptionHistory] = None,
        sample_rate: int = 16000,
        use_fake_mic: bool = False,
    ):
        self.history = history or CaptionHistory()
        self.sample_rate = sample_rate
        self.use_fake_mic = use_fake_mic or not SOUNDDEVICE_AVAILABLE
        self.running = False
        self.paused = False

        self.subscribers: List[Callable[[CaptionEntry], None]] = []
        self._lock = threading.Lock()
        self._sim_thread: Optional[threading.Thread] = None

    def subscribe(self, callback: Callable[[CaptionEntry], None]) -> None:
        """Register a callback for newly transcribed captions."""
        with self._lock:
            self.subscribers.append(callback)

    def add_caption(
        self,
        text: str,
        speaker: str = "Speaker",
        confidence: float = 0.95,
    ) -> CaptionEntry:
        """Add a caption and dispatch to subscribers."""
        if self.paused:
            return None

        entry = self.history.add(text=text, speaker=speaker, confidence=confidence)
        with self._lock:
            for sub in list(self.subscribers):
                try:
                    sub(entry)
                except Exception:
                    pass
        return entry

    def start(self) -> None:
        """Start listening and transcribing."""
        if self.running:
            return
        self.running = True
        self.paused = False

        if self.use_fake_mic:
            self._sim_thread = threading.Thread(target=self._fake_mic_loop, daemon=True)
            self._sim_thread.start()

    def _fake_mic_loop(self) -> None:
        """Periodically emit realistic classroom/meeting captions for live review testing."""
        idx = 0
        while self.running:
            time.sleep(3.5)
            if not self.running:
                break
            if not self.paused:
                text = SAMPLE_ANNOUNCEMENTS[idx % len(SAMPLE_ANNOUNCEMENTS)]
                speaker = "Instructor" if idx % 3 == 0 else "Host"
                self.add_caption(text=text, speaker=speaker, confidence=0.96)
                idx += 1

    def pause(self) -> None:
        self.paused = True

    def resume(self) -> None:
        self.paused = False

    def stop(self) -> None:
        self.running = False

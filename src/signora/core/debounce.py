"""Debounce state machine and dwell confirmation recognizer for Signora."""

import time
from collections import deque
from typing import Any, Dict, Optional


def now_ms() -> float:
    """Return monotonic clock timestamp in milliseconds."""
    return time.monotonic() * 1000.0


class DebounceEngine:
    """Temporal smoothing and dwell confirmation state machine for gesture recognition.

    Prevents flicker by requiring a prediction to remain stable above a confidence
    threshold for a continuous duration (dwell_ms) before confirming it into the transcript.
    """

    def __init__(
        self,
        predictor: Any = None,
        smooth_ms: float = 420.0,
        dwell_ms: float = 1000.0,
        space_dwell_ms: float = 800.0,
        threshold: float = 0.70,
        confirm_threshold: Optional[float] = None,
        repeat_cooldown_ms: float = 900.0,
        space_cooldown_ms: float = 900.0,
    ):
        self.predictor = predictor
        self.smooth_ms = smooth_ms
        self.dwell_ms = dwell_ms
        self.space_dwell_ms = space_dwell_ms
        self.threshold = confirm_threshold if confirm_threshold is not None else threshold
        self.repeat_cooldown_ms = repeat_cooldown_ms
        self.space_cooldown_ms = space_cooldown_ms

        self.buffer = ""
        self._hist = deque()  # (t_ms, label, prob)
        self._candidate: Optional[str] = None
        self._dwell_start: Optional[float] = None
        self._just_emitted: Optional[str] = None
        self._just_emitted_at: float = 0.0
        self._last_emit_time: Dict[str, float] = {}

    def set_dwell(self, ms: int) -> None:
        self.dwell_ms = float(max(100, ms))

    def set_threshold(self, th: float) -> None:
        self.threshold = float(max(0.1, min(0.99, th)))

    def clear(self) -> None:
        """Clear transcript and reset state machine."""
        self.buffer = ""
        self._hist.clear()
        self._candidate = None
        self._dwell_start = None
        self._just_emitted = None
        self._last_emit_time.clear()

    def backspace(self) -> None:
        """Delete last character or word from transcript buffer."""
        if self.buffer:
            self.buffer = self.buffer[:-1]

    def update(
        self,
        label: Optional[str],
        confidence: float,
        timestamp_ms: Optional[float] = None,
        now_ms: Optional[float] = None,
    ) -> Dict[str, Any]:
        """Process a raw prediction and return state dictionary."""
        t = (
            timestamp_ms
            if timestamp_ms is not None
            else (now_ms if now_ms is not None else now_ms())
        )

        # Add to history window
        if label is not None and confidence >= self.threshold:
            self._hist.append((t, label, confidence))

        # Evict old entries outside smooth window
        cutoff = t - self.smooth_ms
        while self._hist and self._hist[0][0] < cutoff:
            self._hist.popleft()

        # Find dominant label in smoothing window
        counts: Dict[str, float] = {}
        for _, lbl, conf in self._hist:
            counts[lbl] = counts.get(lbl, 0.0) + conf

        dominant = None
        if counts:
            dominant = max(counts, key=counts.get)

        # Track candidate and dwell
        needed_dwell = self.space_dwell_ms if dominant == "SPACE" else self.dwell_ms
        cooldown = self.space_cooldown_ms if dominant == "SPACE" else self.repeat_cooldown_ms

        confirmed = None
        dwell_progress = 0.0

        if dominant is not None:
            # Check cooldown against last emission of this specific sign
            last_emitted_t = self._last_emit_time.get(dominant, 0.0)
            in_cooldown = (t - last_emitted_t) < cooldown

            if not in_cooldown:
                if dominant == self._candidate:
                    if self._dwell_start is not None:
                        elapsed = t - self._dwell_start
                        dwell_progress = min(1.0, elapsed / needed_dwell)
                        if elapsed >= needed_dwell:
                            confirmed = dominant
                            self._dwell_start = None
                            self._candidate = None
                            self._just_emitted = dominant
                            self._just_emitted_at = t
                            self._last_emit_time[dominant] = t
                            self._append_to_buffer(dominant)
                else:
                    self._candidate = dominant
                    self._dwell_start = t
                    dwell_progress = 0.0
            else:
                self._candidate = None
                self._dwell_start = None
        else:
            self._candidate = None
            self._dwell_start = None

        return {
            "candidate": self._candidate,
            "dwell_progress": dwell_progress,
            "dwell_needed_ms": needed_dwell,
            "confirmed": confirmed,
            "transcript": self.buffer,
        }

    def _append_to_buffer(self, token: str) -> None:
        if token == "SPACE":
            if self.buffer and not self.buffer.endswith(" "):
                self.buffer += " "
        elif len(token) == 1:
            self.buffer += token
        else:
            # Multi-letter word gloss
            if self.buffer and not self.buffer.endswith(" "):
                self.buffer += " "
            self.buffer += token + " "

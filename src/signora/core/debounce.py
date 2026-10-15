"""Debounce state machine and dwell confirmation recognizer for Signora."""

import time
from collections import deque
from typing import Any, Dict, Optional


def now_ms() -> float:
    """Return monotonic clock timestamp in milliseconds."""
    return time.monotonic() * 1000.0


class ConfidenceGuard:
    """Confidence and stability validator for sign recognition.

    Prevents false guesses by detecting ambiguous predictions, requiring repeated-frame
    consistency, and prompting the user with 'Not sure, please try again' or an interactive confirmation.
    """

    def __init__(
        self,
        high_threshold: float = 0.82,
        ambiguous_threshold: float = 0.48,
        min_stable_frames: int = 4,
        require_confirmation: bool = False,
    ):
        self.high_threshold = high_threshold
        self.ambiguous_threshold = ambiguous_threshold
        self.min_stable_frames = min_stable_frames
        self.require_confirmation = require_confirmation

        self.consecutive_frames: int = 0
        self.current_label: Optional[str] = None
        self.pending_candidate: Optional[str] = None
        self.pending_confidence: float = 0.0
        self.status: str = "IDLE"  # "CONFIDENT", "AMBIGUOUS", "LOW", "STABILIZING", "IDLE"
        self.guidance_message: str = ""

    def evaluate(self, label: Optional[str], confidence: float) -> Dict[str, Any]:
        """Evaluate prediction against multi-tier confidence and stability requirements."""
        if not label:
            self.consecutive_frames = 0
            self.current_label = None
            self.pending_candidate = None
            self.status = "IDLE"
            self.guidance_message = ""
            return {
                "status": self.status,
                "is_confident": False,
                "is_ambiguous": False,
                "guidance": "",
                "pending": None,
            }

        if label == self.current_label:
            self.consecutive_frames += 1
        else:
            self.current_label = label
            self.consecutive_frames = 1

        if confidence >= self.high_threshold:
            if self.consecutive_frames >= self.min_stable_frames:
                self.status = "CONFIDENT"
                self.guidance_message = f"✓ Confirmed: {label}"
                self.pending_candidate = label if self.require_confirmation else None
                self.pending_confidence = confidence
                return {
                    "status": self.status,
                    "is_confident": True,
                    "is_ambiguous": False,
                    "guidance": self.guidance_message,
                    "pending": self.pending_candidate,
                }
            else:
                self.status = "STABILIZING"
                self.guidance_message = f"Hold steady ({self.consecutive_frames}/{self.min_stable_frames})..."
                return {
                    "status": self.status,
                    "is_confident": False,
                    "is_ambiguous": False,
                    "guidance": self.guidance_message,
                    "pending": None,
                }
        elif confidence >= self.ambiguous_threshold:
            self.status = "AMBIGUOUS"
            self.guidance_message = f"Not sure, please try again 🤔 (Maybe: {label}?)"
            self.pending_candidate = label
            self.pending_confidence = confidence
            return {
                "status": self.status,
                "is_confident": False,
                "is_ambiguous": True,
                "guidance": self.guidance_message,
                "pending": label,
            }
        else:
            self.status = "LOW"
            self.guidance_message = "Low confidence. Adjust hand position."
            self.pending_candidate = None
            return {
                "status": self.status,
                "is_confident": False,
                "is_ambiguous": False,
                "guidance": self.guidance_message,
                "pending": None,
            }

    def confirm(self) -> Optional[str]:
        """Manually confirm pending ambiguous candidate."""
        res = self.pending_candidate
        self.pending_candidate = None
        self.guidance_message = f"Confirmed: {res}" if res else ""
        return res

    def reject(self) -> None:
        """Reject and clear pending candidate."""
        self.pending_candidate = None
        self.guidance_message = "Rejected. Please try again."


class DebounceEngine:
    """Temporal smoothing and dwell confirmation state machine for gesture recognition.

    Prevents flicker by requiring a prediction to remain stable above a confidence
    threshold for a continuous duration (dwell_ms) before confirming it into the transcript.
    Includes ConfidenceGuard for ambiguity detection and user confirmation checks.
    """

    def __init__(
        self,
        predictor: Any = None,
        smooth_ms: float = 300.0,
        dwell_ms: float = 600.0,
        space_dwell_ms: float = 500.0,
        threshold: float = 0.52,
        confirm_threshold: Optional[float] = None,
        repeat_cooldown_ms: float = 550.0,
        space_cooldown_ms: float = 600.0,
        enable_confidence_check: bool = True,
        high_threshold: float = 0.82,
        ambiguous_threshold: float = 0.48,
        min_stable_frames: int = 3,
    ):
        self.predictor = predictor
        self.smooth_ms = smooth_ms
        self.dwell_ms = dwell_ms
        self.space_dwell_ms = space_dwell_ms
        self.threshold = confirm_threshold if confirm_threshold is not None else threshold
        self.repeat_cooldown_ms = repeat_cooldown_ms
        self.space_cooldown_ms = space_cooldown_ms

        self.confidence_guard = ConfidenceGuard(
            high_threshold=high_threshold,
            ambiguous_threshold=ambiguous_threshold,
            min_stable_frames=min_stable_frames,
        ) if enable_confidence_check else None

        self.buffer = ""
        self._hist = deque()  # (t_ms, label, prob)
        self._candidate: Optional[str] = None
        self._active_dwell_cand: Optional[str] = None
        self._dwell_start: Optional[float] = None
        self._last_seen_cand_t: float = 0.0
        self._just_emitted: Optional[str] = None
        self._just_emitted_at: float = 0.0
        self._last_emit_time: Dict[str, float] = {}

    @property
    def word_count(self) -> int:
        """Return total completed words in current transcript."""
        text = self.buffer.strip()
        return len(text.split()) if text else 0

    @property
    def char_count(self) -> int:
        """Return character count of current transcript."""
        return len(self.buffer)

    def set_dwell(self, ms: int) -> None:
        self.dwell_ms = float(max(100, ms))

    def set_threshold(self, th: float) -> None:
        self.threshold = float(max(0.1, min(0.99, th)))

    def clear(self) -> None:
        """Clear transcript and reset state machine."""
        self.buffer = ""
        self._hist.clear()
        self._candidate = None
        self._active_dwell_cand = None
        self._dwell_start = None
        self._just_emitted = None
        self._last_emit_time.clear()

    def backspace(self) -> None:
        """Delete last character or word boundary from transcript buffer."""
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
        if timestamp_ms is not None:
            t = float(timestamp_ms)
        elif now_ms is not None:
            t = float(now_ms)
        else:
            t = time.monotonic() * 1000.0

        # Add to history window if confident
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
        in_cooldown = False

        if dominant is not None:
            self._candidate = dominant
            last_emitted_t = self._last_emit_time.get(dominant, 0.0)
            in_cooldown = (t - last_emitted_t) < cooldown

            if not in_cooldown:
                if dominant == self._active_dwell_cand:
                    if self._dwell_start is not None:
                        elapsed = t - self._dwell_start
                        dwell_progress = min(1.0, elapsed / needed_dwell)
                        if elapsed >= needed_dwell:
                            confirmed = dominant
                            self._dwell_start = None
                            self._active_dwell_cand = None
                            self._just_emitted = dominant
                            self._just_emitted_at = t
                            self._last_emit_time[dominant] = t
                            self._append_to_buffer(dominant)
                else:
                    self._active_dwell_cand = dominant
                    self._dwell_start = t
                    dwell_progress = 0.0
                self._last_seen_cand_t = t
            else:
                # Active in cooldown
                dwell_progress = 0.0
                self._active_dwell_cand = None
        else:
            # Allow brief 180ms grace period for momentary blur/frame drop
            if self._active_dwell_cand and (t - self._last_seen_cand_t) < 180.0:
                pass
            elif self._just_emitted and (t - self._just_emitted_at) < cooldown:
                # Keep confirmed sign visible during cooldown feedback
                self._candidate = self._just_emitted
                in_cooldown = True
            else:
                self._candidate = None
                self._active_dwell_cand = None
                self._dwell_start = None

        guard_res = {"status": "CONFIDENT", "is_ambiguous": False, "guidance": "", "pending": None}
        if self.confidence_guard:
            guard_res = self.confidence_guard.evaluate(dominant, confidence if dominant == label else (confidence * 0.8))

        return {
            "candidate": self._candidate,
            "dwell_progress": dwell_progress,
            "dwell_needed_ms": needed_dwell,
            "confirmed": confirmed,
            "in_cooldown": in_cooldown,
            "transcript": self.buffer,
            "word_count": self.word_count,
            "char_count": self.char_count,
            "status": guard_res.get("status", "IDLE"),
            "is_ambiguous": guard_res.get("is_ambiguous", False),
            "guidance": guard_res.get("guidance", ""),
            "pending_confirmation": guard_res.get("pending", None),
        }

    def confirm(self) -> Optional[str]:
        """Manually confirm currently pending candidate gesture."""
        token = None
        if self.confidence_guard:
            token = self.confidence_guard.confirm()
        if not token and self._candidate:
            token = self._candidate

        if token:
            self._append_to_buffer(token)
            self._just_emitted = token
            self._just_emitted_at = time.monotonic() * 1000.0
            self._last_emit_time[token] = self._just_emitted_at
            self._candidate = None
            self._active_dwell_cand = None
            self._dwell_start = None
        return token

    def reject(self) -> None:
        """Reject and clear ambiguous candidate gesture."""
        if self.confidence_guard:
            self.confidence_guard.reject()
        self._candidate = None
        self._active_dwell_cand = None
        self._dwell_start = None

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

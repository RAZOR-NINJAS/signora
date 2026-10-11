"""Run state and phase timing for the shadow-play game."""

import time
from typing import Any, Dict, List, Optional

PHASES = ("idle", "ready", "answer", "correct", "wrong", "finished")

DEFAULT_READY_MS = 2200
DEFAULT_ANSWER_MS = 2000
DEFAULT_HOLD_MS = 420
DEFAULT_MIN_CONF = 0.62


class GameConfig:
    """Tunables for a run, all in milliseconds unless noted."""

    def __init__(
        self,
        ready_ms: int = DEFAULT_READY_MS,
        answer_ms: int = DEFAULT_ANSWER_MS,
        hold_ms: int = DEFAULT_HOLD_MS,
        min_conf: float = DEFAULT_MIN_CONF,
        max_cacti: int = 0,
        mode: str = "asl",
    ):
        self.ready_ms = int(ready_ms)
        self.answer_ms = int(answer_ms)
        self.hold_ms = int(hold_ms)
        self.min_conf = float(min_conf)
        self.max_cacti = int(max_cacti)
        self.mode = str(mode or "asl").lower()

    def as_dict(self) -> Dict[str, Any]:
        return {
            "ready_ms": self.ready_ms,
            "answer_ms": self.answer_ms,
            "hold_ms": self.hold_ms,
            "min_conf": self.min_conf,
            "max_cacti": self.max_cacti,
            "mode": self.mode,
        }


class GameState:
    """Game loop state tracking, scoring, and hold timing."""

    def __init__(self, config: Optional[GameConfig] = None, pool: Any = None, now: Any = None):
        self.config = config or GameConfig()
        self.pool = pool
        self.clock = now or time.monotonic
        self.phase = "idle"
        self.score = 0
        self.cleared = 0
        self.streak = 0
        self.best_streak = 0
        self.target: Optional[str] = None
        self.plays: List[str] = []
        self.misses: List[str] = []
        self.wrong_attempts = 0
        self.last_seen: Optional[str] = None
        self._phase_until = 0.0
        self._held_since: Optional[float] = None
        self._held_letter: Optional[str] = None
        self._message = ""

    def _now(self) -> float:
        return self.clock()

    def _enter(self, phase: str, duration_ms: int = 0) -> None:
        self.phase = phase
        self._phase_until = self._now() + duration_ms / 1000.0
        self._held_since = None
        self._held_letter = None

    @property
    def phase_remaining_ms(self) -> int:
        if self.phase in ("idle", "wrong", "finished"):
            return 0
        return max(0, int((self._phase_until - self._now()) * 1000))

    @property
    def phase_progress(self) -> float:
        if self.phase not in ("ready", "answer"):
            return 0.0
        total = self.config.ready_ms if self.phase == "ready" else self.config.answer_ms
        if total <= 0:
            return 0.0
        return min(1.0, max(0.0, 1.0 - self.phase_remaining_ms / total))

    def start(self) -> bool:
        if self.pool is None or self.pool.empty:
            self._message = "no usable letters in the pool"
            self._enter("wrong")
            return False
        self.score = 0
        self.cleared = 0
        self.streak = 0
        self.best_streak = 0
        self.plays = []
        self.misses = []
        self.wrong_attempts = 0
        self.last_seen = None
        self._message = ""
        self._next_challenge()
        return True

    def _next_challenge(self) -> None:
        if self.config.max_cacti and self.cleared >= self.config.max_cacti:
            self._message = "you reached the end of the play"
            self._enter("finished")
            return
        self.target = self.pool.next()
        self._enter("ready", self.config.ready_ms)

    def _succeed(self) -> None:
        if self.target:
            self.plays.append(self.target)
        self.score += 10 + 5 * min(self.streak, 4)
        self.streak += 1
        self.best_streak = max(self.best_streak, self.streak)
        self.cleared += 1
        self._enter("correct", 900)

    def _time_out(self) -> None:
        if self.target:
            self.misses.append(self.target)
        self.streak = 0
        self._message = "the light faded before the shape was ready"
        self._enter("wrong")

    def observe(self, letter: Optional[str], confidence: float = 0.0) -> None:
        if self.phase not in ("ready", "answer"):
            return

        if letter and confidence < self.config.min_conf:
            self._held_since = None
            self._held_letter = None
            return

        if not letter:
            self._held_since = None
            self._held_letter = None
            return

        if letter != self._held_letter:
            self._held_letter = letter
            self._held_since = self._now()
            return

        if self._held_since is None:
            return
        if (self._now() - self._held_since) * 1000.0 < self.config.hold_ms:
            return

        if letter == self.target:
            if self.phase == "answer":
                self._succeed()
            return

        if self.phase == "answer":
            self.wrong_attempts += 1
            self.last_seen = letter
            self._held_since = None
            self._held_letter = None

    def tick(self) -> None:
        now = self._now()
        if self.phase == "ready" and now >= self._phase_until:
            self._enter("answer", self.config.answer_ms)
        elif self.phase == "answer" and now >= self._phase_until:
            self._time_out()
        elif self.phase == "correct" and now >= self._phase_until:
            self._next_challenge()

    def snapshot(self, detected: Optional[str] = None, confidence: float = 0.0) -> Dict[str, Any]:
        return {
            "phase": self.phase,
            "target": self.target,
            "score": self.score,
            "cleared": self.cleared,
            "streak": self.streak,
            "best_streak": self.best_streak,
            "plays": list(self.plays),
            "misses": list(self.misses),
            "wrong_attempts": self.wrong_attempts,
            "last_seen": self.last_seen,
            "remaining_ms": self.phase_remaining_ms,
            "progress": round(self.phase_progress, 3),
            "message": self._message,
            "detected": detected,
            "confidence": round(float(confidence), 3),
            "config": self.config.as_dict(),
        }

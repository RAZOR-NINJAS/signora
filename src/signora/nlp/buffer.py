"""Sentence Buffer for Python Desktop ISL Recognition.

Accumulates GlossTokens and fires sentence boundaries after a temporal pause (default 1.5s)
or when an explicit terminal marker is received.
"""

import time
from typing import Callable, List, Optional
from .synthesizer import GlossToken


class SentenceBuffer:
    def __init__(
        self,
        sentence_pause_s: float = 1.5,
        on_sentence_complete: Optional[Callable[[List[GlossToken]], None]] = None,
    ):
        self.sentence_pause_s = sentence_pause_s
        self.on_sentence_complete = on_sentence_complete
        self.tokens: List[GlossToken] = []
        self.last_token_time: float = 0.0

    def push(self, token: GlossToken) -> None:
        now = token.timestamp or time.monotonic()
        self.last_token_time = now

        lbl = token.label.upper().strip()
        if lbl in {"PERIOD", "SENTENCE_END", "FULL_STOP"}:
            self.tokens.append(token)
            self.fire_and_clear()
        else:
            self.tokens.append(token)

    def tick(self, now: Optional[float] = None) -> None:
        if not self.tokens:
            return
        current_time = now or time.monotonic()
        if self.last_token_time > 0 and (current_time - self.last_token_time) >= self.sentence_pause_s:
            self.fire_and_clear()

    def flush(self) -> None:
        if self.tokens:
            self.fire_and_clear()

    def peek(self) -> List[GlossToken]:
        return list(self.tokens)

    def clear(self) -> None:
        self.tokens.clear()
        self.last_token_time = 0.0

    def fire_and_clear(self) -> None:
        snapshot = list(self.tokens)
        self.tokens.clear()
        self.last_token_time = 0.0
        if snapshot and self.on_sentence_complete:
            self.on_sentence_complete(snapshot)

"""Tests for the Dino shadow-play game logic, scoring, and pool management."""

from signora.game.pool import LetterPool
from signora.game.scoring import GameConfig, GameState


def test_game_pool_cycling():
    letters = ["A", "B", "C"]
    pool = LetterPool(letters, mode="bag", rng=42)
    assert len(pool) == 3

    # Draw all 3 without duplicate in first cycle
    drawn = [pool.next(), pool.next(), pool.next()]
    assert set(drawn) == {"A", "B", "C"}

    # Refills automatically on 4th draw
    next_letter = pool.next()
    assert next_letter in {"A", "B", "C"}


def test_game_state_scoring_progression():
    cfg = GameConfig(ready_ms=100, answer_ms=300, hold_ms=50, min_conf=0.5)
    pool = LetterPool(["A", "B"], rng=0)
    state = GameState(config=cfg, pool=pool)

    # Start run
    assert state.start() is True
    assert state.phase == "ready"
    assert state.target is not None

    target = state.target
    # Advance clock past ready phase
    state.tick()
    state._phase_until = state._now() - 1.0  # simulate ready elapsed
    state.tick()
    assert state.phase == "answer"

    # Observe correct letter held long enough
    state.observe(target, confidence=0.9)
    # Simulate hold duration passed
    state._held_since = state._now() - 0.1
    state.observe(target, confidence=0.9)

    assert state.phase == "correct"
    assert state.score > 0
    assert state.cleared == 1

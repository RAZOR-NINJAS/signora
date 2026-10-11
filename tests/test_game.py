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


def test_game_isl_pool_and_hub():
    from signora.game.pool import build_pool, model_labels
    from signora.game.server import GameHub

    # Verify ISL labels returned
    isl_labels = model_labels(mode="isl")
    assert isinstance(isl_labels, list)
    assert len(isl_labels) > 0
    assert "A" in isl_labels

    # Verify ISL pool
    isl_pool = build_pool(mode="isl")
    assert isinstance(isl_pool, list)
    assert len(isl_pool) > 0

    # Test GameHub in ISL mode
    hub = GameHub(mode="isl", use_fake=True)
    assert hub.mode == "isl"
    assert hub.state.config.mode == "isl"
    assert len(hub.state.pool) > 0

    snap = hub.snapshot()
    assert snap["mode"] == "isl"
    assert "pool" in snap

    # Test switching mode dynamically
    hub.set_mode("asl")
    assert hub.mode == "asl"
    assert hub.state.config.mode == "asl"
    assert len(hub.state.pool) == 26
    assert hub.snapshot()["mode"] == "asl"

    hub.set_mode("isl")
    assert hub.mode == "isl"
    assert hub.snapshot()["mode"] == "isl"


def test_letter_reader_isl_classification():
    from signora.game.detector import LetterReader

    reader = LetterReader(source=0, use_fake=True, mode="isl")
    assert reader.mode == "isl"
    ok = reader.start()
    assert ok is True
    try:
        import time

        time.sleep(0.15)
        letter, conf, fps, stale, frames, hands = reader.reading()
        assert frames >= 0
        reader.set_mode("asl")
        assert reader.mode == "asl"
    finally:
        reader.stop()

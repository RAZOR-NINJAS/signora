"""Tests for Learning Kiosk session hub and drill mechanics."""

from signora.learn.server import LearnHub


def test_learn_hub_initialization():
    hub = LearnHub(use_fake=True)
    assert hub.mode == "drill"
    assert hub.current_target == "A"
    assert hub.score == 0
    assert hub.streak == 0
    assert len(hub.available_items) == 26


def test_learn_hub_target_switch_and_success():
    hub = LearnHub(use_fake=True)
    hub.set_target("B")
    assert hub.current_target == "B"

    res = hub.record_success("B")
    assert res["ok"] is True
    assert hub.score > 0
    assert hub.streak == 1
    assert hub.total_completed == 1


def test_learn_hub_snapshot_format():
    hub = LearnHub(use_fake=True)
    snap = hub.snapshot()
    assert "mode" in snap
    assert "target" in snap
    assert "score" in snap
    assert "streak" in snap
    assert "camera" in snap
    assert "items" in snap

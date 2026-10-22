"""Unit tests for multi-frame hand motion tracking and trajectory analysis."""

import math
import numpy as np
import pytest

from signora.core.motion import (
    DUAL_CONVERGING,
    DUAL_STATIC,
    HandMotionTracker,
    HandTrajectory,
    PATTERN_CIRCLE_CW,
    PATTERN_STATIC,
    PATTERN_SWIPE_DOWN,
    PATTERN_SWIPE_LEFT,
    PATTERN_SWIPE_RIGHT,
    PATTERN_SWIPE_UP,
    PATTERN_WAVE,
    extract_motion_features,
    match_sign_motion,
)


def _make_dummy_hand(x: float = 0.5, y: float = 0.5, z: float = 0.0):
    """Generate 21-point dummy hand landmark list centered at (x, y, z)."""
    landmarks = []
    for i in range(21):
        landmarks.append((x + i * 0.005, y + i * 0.005, z))
    return landmarks


def test_trajectory_static_hand():
    traj = HandTrajectory(maxlen=30)
    # Add stationary hand over 10 frames
    t0 = 100.0
    for i in range(10):
        hand = _make_dummy_hand(0.5, 0.5, 0.0)
        traj.append(hand, timestamp=t0 + i * 0.033)

    speed = traj.get_speed()
    assert speed < 0.05
    pattern = traj.detect_pattern()
    assert pattern == PATTERN_STATIC


def test_trajectory_swipe_right():
    traj = HandTrajectory(maxlen=30)
    t0 = 100.0
    # Move hand rapidly to the right (x increases by 0.3 over 0.3s -> 1.0 units/sec)
    for i in range(15):
        x = 0.3 + i * 0.02
        hand = _make_dummy_hand(x=x, y=0.5, z=0.0)
        traj.append(hand, timestamp=t0 + i * 0.033)

    speed = traj.get_speed(dt_window=0.3)
    assert speed > 0.30
    pattern = traj.detect_pattern()
    assert pattern == PATTERN_SWIPE_RIGHT


def test_trajectory_swipe_up():
    traj = HandTrajectory(maxlen=30)
    t0 = 100.0
    # Move hand rapidly upwards (y decreases in screen coordinates)
    for i in range(15):
        y = 0.8 - i * 0.025
        hand = _make_dummy_hand(x=0.5, y=y, z=0.0)
        traj.append(hand, timestamp=t0 + i * 0.033)

    speed = traj.get_speed(dt_window=0.3)
    assert speed > 0.30
    pattern = traj.detect_pattern()
    assert pattern == PATTERN_SWIPE_UP


def test_trajectory_wave_oscillation():
    traj = HandTrajectory(maxlen=40)
    t0 = 100.0
    # Oscillate x back and forth over 30 frames
    for i in range(30):
        x = 0.5 + 0.08 * math.sin(i * 0.6)
        hand = _make_dummy_hand(x=x, y=0.5, z=0.0)
        traj.append(hand, timestamp=t0 + i * 0.033)

    pattern = traj.detect_pattern()
    assert pattern == PATTERN_WAVE


def test_trajectory_circle():
    traj = HandTrajectory(maxlen=40)
    t0 = 100.0
    # Full circle clockwise: x = cx + r*cos(theta), y = cy + r*sin(theta)
    for i in range(30):
        theta = i * (2.0 * math.pi / 28.0)
        x = 0.5 + 0.08 * math.cos(theta)
        y = 0.5 + 0.08 * math.sin(theta)
        hand = _make_dummy_hand(x=x, y=y, z=0.0)
        traj.append(hand, timestamp=t0 + i * 0.033)

    pattern = traj.detect_pattern()
    assert "CIRCLE" in pattern


def test_dual_hand_motion_tracker():
    tracker = HandMotionTracker()

    t0 = 100.0
    # Two hands converging toward center
    for i in range(15):
        r_hand = _make_dummy_hand(x=0.8 - i * 0.02, y=0.5)
        l_hand = _make_dummy_hand(x=0.2 + i * 0.02, y=0.5)
        st = tracker.update(
            [r_hand, l_hand],
            ["Right", "Left"],
            timestamp=t0 + i * 0.033,
        )

    assert st["is_moving"] is True
    assert st["dual_motion"] == DUAL_CONVERGING

    # Test motion features extraction
    feats = extract_motion_features(tracker)
    assert isinstance(feats, np.ndarray)
    assert len(feats) == 24
    assert np.all(np.isfinite(feats))


def test_match_sign_motion():
    # Hello requires wave
    wave_state = {"pattern": PATTERN_WAVE, "dual_motion": DUAL_STATIC, "is_moving": True}
    matched, boost = match_sign_motion("HELLO", wave_state)
    assert matched is True
    assert boost > 1.0

    static_state = {"pattern": PATTERN_STATIC, "dual_motion": DUAL_STATIC, "is_moving": False}
    matched_static, boost_static = match_sign_motion("HELLO", static_state)
    assert matched_static is False
    assert boost_static < 1.0

    # Namaste requires converging
    conv_state = {"pattern": PATTERN_STATIC, "dual_motion": DUAL_CONVERGING, "is_moving": True}
    matched_namaste, boost_namaste = match_sign_motion("NAMASTE", conv_state)
    assert matched_namaste is True
    assert boost_namaste > 1.0

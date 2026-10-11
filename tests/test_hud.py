"""Unit tests for the glassmorphic HUD rendering module."""

from types import SimpleNamespace

import numpy as np

from signora.core.hud import (
    draw_cyber_hands,
    draw_glass_card,
    draw_keycap,
    draw_rounded_rectangle,
    render_flagship_hud,
)


def test_draw_rounded_rectangle_bounds():
    """Ensure rounded rectangle safely handles out-of-bounds or inverted coordinates."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)

    # Completely outside
    draw_rounded_rectangle(frame, (-100, -100), (-10, -10), (255, 255, 255))
    draw_rounded_rectangle(frame, (700, 700), (800, 800), (255, 255, 255))

    # Partially outside
    draw_rounded_rectangle(frame, (-50, 100), (100, 200), (255, 255, 255), alpha=0.5)

    # Inverted coordinates
    draw_rounded_rectangle(frame, (200, 200), (50, 50), (0, 255, 0), radius=10)

    assert frame.shape == (480, 640, 3)


def test_draw_glass_card():
    """Verify drawing a glass card modifies the target region."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)
    draw_glass_card(frame, x=50, y=50, w=200, h=100)

    # Check that region inside the card is not pure black anymore
    roi = frame[55:145, 55:245]
    assert np.any(roi > 0)


def test_draw_cyber_hands():
    """Verify dual-hand cybernetic skeletons render without exceptions."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)

    # Empty landmarks
    res = draw_cyber_hands(frame, None)
    assert res is frame

    # Synthetic 21-landmark hand
    mock_lms = [SimpleNamespace(x=0.5 + i * 0.01, y=0.5 + i * 0.01) for i in range(21)]
    mock_left_lms = [SimpleNamespace(x=0.2 + i * 0.01, y=0.3 + i * 0.01) for i in range(21)]

    # Single hand
    draw_cyber_hands(frame, [mock_lms], handedness_list=["Right"])
    assert np.any(frame > 0)

    # Dual hands
    frame.fill(0)
    draw_cyber_hands(frame, [mock_left_lms, mock_lms], handedness_list=["Left", "Right"])
    assert np.any(frame > 0)


def test_draw_keycap():
    """Verify keycap rendering produces expected width and alters pixels."""
    frame = np.zeros((100, 200, 3), dtype=np.uint8)
    width = draw_keycap(frame, x=10, y=10, key_char="Q", action_text="Quit")

    assert width > 24
    assert np.any(frame[10:30, 10:100] > 0)


def test_render_flagship_hud():
    """Test full HUD overlay rendering across multiple states."""
    frame = np.zeros((480, 640, 3), dtype=np.uint8)

    st_idle = {
        "candidate": None,
        "conf": 0.0,
        "dwell_progress": 0.0,
        "confirmed": False,
        "last_confirmed": "",
    }
    out1 = render_flagship_hud(frame.copy(), "ASL", st_idle, "HELLO", fps=30.0)
    assert out1.shape == (480, 640, 3)

    st_active = {
        "candidate": "A",
        "conf": 0.95,
        "dwell_progress": 0.65,
        "confirmed": False,
        "last_confirmed": "HELLO",
    }
    out2 = render_flagship_hud(
        frame.copy(),
        "ISL",
        st_active,
        "HELLO WORLD",
        fps=28.5,
        delegate="CPU",
        tts_speaking=True,
        last_spoken="HELLO WORLD",
    )
    assert out2.shape == (480, 640, 3)
    assert np.any(out2 > 0)

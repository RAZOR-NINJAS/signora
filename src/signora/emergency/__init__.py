"""Signora Emergency Card module."""

from signora.emergency.presets import EMERGENCY_PRESETS, EmergencyPreset
from signora.emergency.profile import EmergencyProfile
from signora.emergency.viewer import (
    render_terminal_card,
    show_gui_emergency_card,
    spawn_emergency_card_detached,
)

__all__ = [
    "EmergencyProfile",
    "EmergencyPreset",
    "EMERGENCY_PRESETS",
    "render_terminal_card",
    "show_gui_emergency_card",
    "spawn_emergency_card_detached",
]

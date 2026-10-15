"""Tests for SIGNORA Emergency Card."""

import pytest

from signora.emergency.presets import EMERGENCY_PRESETS
from signora.emergency.profile import EmergencyProfile
from signora.emergency.viewer import render_terminal_card, spawn_emergency_card_detached


class TestEmergencyCard:
    def test_presets_defined(self):
        assert "medical" in EMERGENCY_PRESETS
        assert "police" in EMERGENCY_PRESETS
        assert "fire" in EMERGENCY_PRESETS
        assert "injury" in EMERGENCY_PRESETS
        assert "assist" in EMERGENCY_PRESETS

        med = EMERGENCY_PRESETS["medical"]
        assert med.icon == "🚑"
        assert "medical" in med.tts_broadcast.lower()

    def test_profile_save_and_load(self, tmp_path):
        prof_file = tmp_path / "emergency_profile.json"
        prof = EmergencyProfile(
            name="Alice Walker",
            blood_group="B+",
            medical_conditions="Deaf / Asthma",
            allergies="Peanuts",
            primary_contact_name="Bob Walker",
            primary_contact_phone="+1-555-9876",
        )
        prof.save(prof_file)
        assert prof_file.exists()

        loaded = EmergencyProfile.load(prof_file)
        assert loaded.name == "Alice Walker"
        assert loaded.blood_group == "B+"
        assert loaded.allergies == "Peanuts"
        assert loaded.primary_contact_phone == "+1-555-9876"

    def test_render_terminal_card(self, capsys):
        prof = EmergencyProfile(name="Test Person")
        preset = EMERGENCY_PRESETS["fire"]
        render_terminal_card(preset, prof)
        out = capsys.readouterr().out
        assert "FIRE EVACUATION" in out
        assert "Test Person" in out

    def test_spawn_detached_smoke(self):
        from unittest.mock import patch
        with patch("subprocess.Popen") as mock_popen:
            spawn_emergency_card_detached("assist")
            mock_popen.assert_called_once()

"""Tests for SIGNORA Public Help Communication Cards."""

import pytest

from signora.public_help.categories import DEFAULT_HELP_CARDS, HelpCard, HelpCardManager
from signora.public_help.viewer import render_terminal_help_card, spawn_help_board_detached


class TestPublicHelp:
    def test_default_categories_and_cards(self):
        assert "Hospital" in DEFAULT_HELP_CARDS
        assert "Travel" in DEFAULT_HELP_CARDS
        assert "Public Office" in DEFAULT_HELP_CARDS
        assert "Help & Shopping" in DEFAULT_HELP_CARDS

        hosp_cards = DEFAULT_HELP_CARDS["Hospital"]
        assert len(hosp_cards) >= 4
        assert any("Deaf" in c.message for c in hosp_cards)

    def test_manager_add_and_edit_card(self, tmp_path):
        custom_file = tmp_path / "custom_cards.json"
        mgr = HelpCardManager(storage_path=custom_file)

        new_card = HelpCard(
            id="custom_1",
            category="Hospital",
            title="Allergy Warning",
            message="I am severely allergic to penicillin and latex.",
            icon="⚠️",
        )
        mgr.add_or_update_card(new_card)

        # Reload
        mgr2 = HelpCardManager(storage_path=custom_file)
        cards = mgr2.get_cards_for_category("Hospital")
        found = [c for c in cards if c.id == "custom_1"]
        assert len(found) == 1
        assert "penicillin" in found[0].message

    def test_render_terminal_help_card(self, capsys):
        card = HelpCard(
            id="t1",
            category="Travel",
            title="Platform Check",
            message="Which platform does the train arrive on?",
            icon="🚆",
        )
        render_terminal_help_card(card)
        out = capsys.readouterr().out
        assert "TRAVEL" in out
        assert "Platform Check" in out
        assert "Which platform does the train arrive on?" in out

    def test_spawn_detached_smoke(self):
        spawn_help_board_detached("Travel")

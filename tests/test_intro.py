"""Unit tests for futuristic introduction sequence and Connecting Hands animation."""

import os
from unittest.mock import patch

import pytest
from rich.console import Console

from signora.intro import (
    INTRO_SPEECH,
    INTRO_TEXT,
    animate_connecting_hands,
    display_intro,
    play_chime_async,
    play_intro_voice_async,
)


class TestIntro:
    def test_intro_text_and_manifesto(self):
        assert "Greetings, innovators!" in INTRO_TEXT
        assert "I am signora" in INTRO_TEXT
        assert "i see gestures, recognise patterns" in INTRO_TEXT
        assert "I was created with one purpose: to make communication more accessible." in INTRO_TEXT
        assert "I am SIGNORA, where hands find there voice." in INTRO_TEXT

    def test_connecting_hands_animation_smoke(self):
        console = Console(record=True, width=100)
        # In non-interactive test mode, animation executes cleanly
        animate_connecting_hands(console)
        output = console.export_text()
        assert "HUMAN GESTURE" in output
        assert "SIGNORA AI" in output
        assert "CONNECTED: WHERE HANDS FIND THEIR VOICE" in output

    def test_display_intro_smoke(self):
        console = Console(record=True, width=100)
        display_intro(console=console, speak=False, animate=False)
        output = console.export_text()
        assert "SIGNORA ARTIFICIAL INTELLIGENCE" in output
        assert "Greetings, innovators!" in output
        assert "I am signora" in output
        assert "I am SIGNORA, where hands find there voice." in output

    def test_chime_and_voice_safe_when_no_audio(self):
        with patch.dict(os.environ, {"SIGNORA_NO_AUDIO": "1", "SIGNORA_NO_TTS": "1"}):
            # Must execute without throwing exceptions or blocking
            play_chime_async()
            play_intro_voice_async()

    def test_cli_intro_flag(self):
        from signora.cli import create_parser

        parser = create_parser()
        args = parser.parse_args(["--intro"])
        assert args.intro is True

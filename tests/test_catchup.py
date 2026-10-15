"""Tests for SIGNORA Catch-Up live captions and scrollable history."""

from pathlib import Path

import pytest

from signora.catchup.history import CaptionHistory
from signora.catchup.stt import CatchUpEngine
from signora.catchup.viewer import spawn_catchup_detached


class TestCatchUp:
    def test_caption_history_add_and_search(self):
        h = CaptionHistory()
        e1 = h.add("The final exam is scheduled for Monday", speaker="Professor")
        e2 = h.add("Please submit your lab reports", speaker="TA")
        e3 = h.add("Does anyone have a question on sorting algorithms?", speaker="Professor")

        assert len(h.entries) == 3
        # Search for exam
        res = h.search("exam")
        assert len(res) == 1
        assert res[0].text == "The final exam is scheduled for Monday"

        # Search for speaker
        res_prof = h.search("Professor")
        assert len(res_prof) == 2

    def test_caption_bookmark(self):
        h = CaptionHistory()
        h.add("Note: Question 3 is on the quiz")
        assert not h.entries[0].is_bookmarked
        state = h.toggle_bookmark(0)
        assert state is True
        assert h.entries[0].is_bookmarked is True

    def test_export_formats(self, tmp_path):
        h = CaptionHistory()
        h.add("First sentence of the lecture.", speaker="Speaker 1")
        h.add("Second sentence with important details.", speaker="Speaker 2")
        h.toggle_bookmark(1)

        txt_file = tmp_path / "captions.txt"
        h.export_txt(txt_file)
        assert txt_file.exists()
        txt_content = txt_file.read_text(encoding="utf-8")
        assert "First sentence" in txt_content
        assert "[BOOKMARKED]" in txt_content

        json_file = tmp_path / "captions.json"
        h.export_json(json_file)
        assert json_file.exists()

        srt_file = tmp_path / "captions.srt"
        h.export_srt(srt_file)
        assert srt_file.exists()
        srt_content = srt_file.read_text(encoding="utf-8")
        assert "-->" in srt_content
        assert "First sentence of the lecture." in srt_content

    def test_engine_callbacks_and_pause(self):
        engine = CatchUpEngine(use_fake_mic=False)
        received = []

        def callback(entry):
            received.append(entry.text)

        engine.subscribe(callback)
        engine.add_caption("Welcome to class", speaker="Teacher")
        assert len(received) == 1
        assert received[0] == "Welcome to class"

        # Test pause
        engine.pause()
        engine.add_caption("This should be ignored when paused")
        assert len(received) == 1

        engine.resume()
        engine.add_caption("Resumed live captions")
        assert len(received) == 2

    def test_spawn_detached_smoke(self):
        spawn_catchup_detached(use_fake_mic=True)

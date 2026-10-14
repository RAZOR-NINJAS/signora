"""Tests for the Signora Alert (Hear Through Eyes) environmental sound detection system."""

import numpy as np
import pytest

from signora.alert.classifier import AlertEvent, SoundCategory, SoundClassifier
from signora.alert.dispatcher import AlertDispatcher
from signora.alert.engine import AlertEngine
from signora.cli import create_parser


class TestSoundClassifier:
    @pytest.fixture
    def classifier(self):
        c = SoundClassifier(sample_rate=16000, sensitivity="medium")
        yield c
        c.close()

    def test_silence_detection(self, classifier):
        # 16000 samples of zero
        audio = np.zeros(16000, dtype=np.float32)
        classifier.update_audio(audio)
        event, telemetry = classifier.process()
        assert event is None
        assert telemetry["rms"] < 0.001

    def test_siren_detection(self, classifier):
        # Siren has strong high-frequency energy ~3100 Hz
        t = np.linspace(0, 0.6, int(16000 * 0.6), endpoint=False, dtype=np.float32)
        siren = (0.5 * np.sin(2 * np.pi * 3100 * t)).astype(np.float32)

        classifier.update_audio(siren)
        event, telemetry = classifier.process()
        assert event is not None
        assert event.category == SoundCategory.SIREN_ALARM
        assert event.urgency == "critical"

    def test_doorbell_detection(self, classifier):
        # Doorbell chime tones (e.g. 784 Hz and 659 Hz)
        t1 = np.linspace(0, 0.3, int(16000 * 0.3), endpoint=False, dtype=np.float32)
        t2 = np.linspace(0, 0.3, int(16000 * 0.3), endpoint=False, dtype=np.float32)
        c1 = 0.4 * np.sin(2 * np.pi * 784 * t1) * np.exp(-t1 * 4)
        c2 = 0.4 * np.sin(2 * np.pi * 659 * t2) * np.exp(-t2 * 4)
        chime = np.concatenate([c1, c2]).astype(np.float32)

        classifier.update_audio(chime)
        event, telemetry = classifier.process()
        assert event is not None
        assert event.category == SoundCategory.DOORBELL
        assert event.icon == "🔔"

    def test_sensitivity_adjustment(self, classifier):
        classifier.set_sensitivity("high")
        assert classifier.thresh_mult < 1.0
        classifier.set_sensitivity("low")
        assert classifier.thresh_mult > 1.0
        classifier.set_sensitivity("medium")
        assert classifier.thresh_mult == 1.0

    def test_loud_impulse_detection(self, classifier):
        # Sudden sharp loud clap/bang impulse
        sr = 16000
        audio = np.zeros(int(sr * 0.4), dtype=np.float32)
        t_imp = np.linspace(0, 0.05, int(sr * 0.05))
        impulse = 0.85 * np.sin(2 * np.pi * 500 * t_imp) * np.exp(-t_imp * 60)
        audio[int(sr * 0.05):int(sr * 0.05) + len(impulse)] = impulse.astype(np.float32)

        classifier.update_audio(audio)
        event, telemetry = classifier.process()
        assert event is not None
        assert event.category in (SoundCategory.LOUD_SOUND, SoundCategory.DOOR_KNOCK)


class TestAlertDispatcher:
    def test_dispatch_and_cooldown(self, tmp_path):
        log_file = tmp_path / "alerts.log"
        dispatcher = AlertDispatcher(enable_desktop=False, log_path=log_file, cooldown_seconds=3.0)

        event = AlertEvent(
            category=SoundCategory.DOORBELL,
            title="DOORBELL CHIME",
            description="Front door chime detected",
            confidence=0.92,
            urgency="normal",
            icon="🔔",
            color_bgr=(52, 211, 153),
        )

        # First dispatch succeeds
        assert dispatcher.dispatch(event) is True
        assert len(dispatcher.recent_events) == 1

        # Second dispatch of same category within cooldown is suppressed
        assert dispatcher.dispatch(event) is False
        assert len(dispatcher.recent_events) == 1

        # File log written
        assert log_file.exists()
        content = log_file.read_text(encoding="utf-8")
        assert "DOORBELL CHIME" in content

    def test_test_alert(self, tmp_path):
        log_file = tmp_path / "alerts.log"
        dispatcher = AlertDispatcher(enable_desktop=False, log_path=log_file)
        event = dispatcher.test_alert(SoundCategory.DOORBELL)
        assert event is not None
        assert event.category == SoundCategory.DOORBELL
        assert len(dispatcher.recent_events) == 1

    def test_cross_category_dispatch_no_suppression(self, tmp_path):
        log_file = tmp_path / "alerts.log"
        dispatcher = AlertDispatcher(enable_desktop=False, log_path=log_file, cooldown_seconds=5.0)

        e1 = AlertEvent(
            category=SoundCategory.HUMAN_CALL,
            title="SOMEONE CALLING",
            description="Human voice calling",
            confidence=0.9,
            urgency="normal",
            icon="🗣️",
            color_bgr=(6, 182, 212),
        )
        e2 = AlertEvent(
            category=SoundCategory.DOOR_KNOCK,
            title="KNOCK AT DOOR",
            description="Impulsive knock",
            confidence=0.9,
            urgency="normal",
            icon="🚪",
            color_bgr=(245, 158, 11),
        )

        assert dispatcher.dispatch(e1) is True
        # Immediately dispatching a different category must succeed without suppression
        assert dispatcher.dispatch(e2) is True
        assert len(dispatcher.recent_events) == 2


class TestAlertEngine:
    def test_fake_mic_engine_lifecycle(self):
        engine = AlertEngine(
            sample_rate=16000,
            block_size=640,
            sensitivity="medium",
            enable_desktop=False,
            use_fake_mic=True,
        )

        assert engine.start() is True
        assert engine.running is True

        # Test simulation
        event = engine.simulate_sound("DOORBELL")
        assert event is not None
        assert event.category == SoundCategory.DOORBELL

        # Run one step
        ev, tel = engine.step()
        assert "rms" in tel

        engine.stop()
        assert not engine.running

    def test_simulate_various_sounds(self):
        engine = AlertEngine(enable_desktop=False, use_fake_mic=True)
        for snd in ["siren", "call", "knock", "loud", "baby", "bark", "glass"]:
            ev = engine.simulate_sound(snd)
            assert ev is not None, f"Failed simulation for {snd}"
        engine.stop()


class TestAlertCLI:
    def test_alert_cli_parser(self):
        parser = create_parser()
        subparsers_actions = [
            action
            for action in parser._actions
            if hasattr(action, "choices") and isinstance(action.choices, dict)
        ]
        choices = subparsers_actions[0].choices
        assert "alert" in choices

        alert_parser = choices["alert"]
        flags = [opt for act in alert_parser._actions for opt in act.option_strings]
        for expected in [
            "--daemon",
            "--bg",
            "--start",
            "--stop",
            "--status",
            "--sensitivity",
            "--no-desktop",
            "--test",
            "--headless",
            "--fake-mic",
            "--calibrate",
            "--simulate",
        ]:
            assert expected in flags, f"Flag {expected} missing in alert subcommand"


class TestAlertCalibrationAndPopup:
    def test_calibrate_microphone_fake(self):
        from signora.alert.calibrate import calibrate_microphone

        rec, metrics = calibrate_microphone(duration_seconds=0.2, use_fake=True)
        assert rec in ["high", "medium", "low"]
        assert "avg_db" in metrics
        assert "max_db" in metrics

    def test_popup_smoke(self):
        from signora.alert.popup import spawn_alert_popup_async

        # Smoke test without crashing
        spawn_alert_popup_async(
            icon="🔔",
            title="TEST DOORBELL",
            description="Unit test trigger",
            duration_seconds=0.1,
        )

"""Tests for the Signora Lip Sync & Visual Speech Activity module."""

import numpy as np
import pytest

from signora.cli import create_parser
from signora.lipsync.audio import AudioEnergyTracker, SyncState
from signora.lipsync.detector import LipDetector, LipMetrics, Viseme
from signora.lipsync.live import render_lipsync_hud, run_lipsync_live


class TestLipDetector:
    @pytest.fixture
    def detector(self):
        return LipDetector(use_fake=True)

    def test_synthetic_metrics_generation(self, detector):
        dummy_frame = np.zeros((480, 640, 3), dtype=np.uint8)
        metrics = detector.process(dummy_frame)
        assert metrics is not None
        assert isinstance(metrics, LipMetrics)
        assert 0.0 <= metrics.mar <= 1.0
        assert metrics.viseme in list(Viseme)
        assert len(metrics.landmarks_inner_px) > 0
        assert len(metrics.landmarks_outer_px) > 0

    def test_viseme_classification_rules(self, detector):
        # Open A
        v_a = detector._classify_viseme(mar=0.30, lip_width_ratio=0.75, is_speaking=True)
        assert v_a == Viseme.OPEN_A

        # Bilabial M
        v_m = detector._classify_viseme(mar=0.03, lip_width_ratio=0.75, is_speaking=False)
        assert v_m == Viseme.BILABIAL_M

        # Rounded O
        v_o = detector._classify_viseme(mar=0.15, lip_width_ratio=0.55, is_speaking=True)
        assert v_o == Viseme.ROUNDED_O

        # Spread EE
        v_ee = detector._classify_viseme(mar=0.10, lip_width_ratio=0.92, is_speaking=True)
        assert v_ee == Viseme.SPREAD_EE

        # Rest
        v_rest = detector._classify_viseme(mar=0.08, lip_width_ratio=0.75, is_speaking=False)
        assert v_rest == Viseme.REST


class TestAudioEnergyTracker:
    def test_pure_visual_sync(self):
        tracker = AudioEnergyTracker(enable_audio=False)
        score, state, lag = tracker.compute_sync(
            visual_motion_history=None,
            visual_time_history=None,
            is_visual_speaking=True,
        )
        assert state == SyncState.MOUTHING
        assert score == 1.0

        score_silent, state_silent, _ = tracker.compute_sync(
            visual_motion_history=None,
            visual_time_history=None,
            is_visual_speaking=False,
        )
        assert state_silent == SyncState.SILENT

    def test_toggle(self):
        tracker = AudioEnergyTracker(enable_audio=False)
        try:
            curr = tracker.enable_audio
            new_state = tracker.toggle()
            assert new_state != curr
        finally:
            tracker.close()


class TestLipSyncLivePipeline:
    def test_hud_rendering(self):
        frame = np.zeros((600, 960, 3), dtype=np.uint8)
        detector = LipDetector(use_fake=True)
        metrics = detector.process(frame)
        out = render_lipsync_hud(
            frame=frame,
            metrics=metrics,
            sync_score=0.92,
            sync_state=SyncState.SYNCED,
            lag_ms=15.0,
            audio_energy=0.45,
            fps=30.0,
            audio_enabled=True,
        )
        assert out.shape == (600, 960, 3)

    def test_live_loop_headless_fake(self):
        code = run_lipsync_live(fake_camera=True, headless=True, max_frames=5, enable_audio=False)
        assert code == 0


class TestLipSyncCLI:
    def test_cli_flags(self):
        parser = create_parser()
        args = parser.parse_args(["lipsync", "-s", "2", "--no-audio", "--fake-camera", "--headless"])
        assert args.subcommand == "lipsync"
        assert args.source == "2"
        assert args.no_audio is True
        assert args.fake_camera is True
        assert args.headless is True

"""Acoustic environmental sound classifier for deaf/hard-of-hearing assistive alerts."""

import collections
import time
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Tuple

import numpy as np


class SoundCategory(str, Enum):
    """Recognized environmental sound categories."""
    DOORBELL = "DOORBELL"
    DOOR_KNOCK = "DOOR_KNOCK"
    SIREN_ALARM = "SIREN_ALARM"
    HUMAN_CALL = "HUMAN_CALL"
    BABY_CRY = "BABY_CRY"
    DOG_BARK = "DOG_BARK"
    AMBIENT = "AMBIENT"


@dataclass
class AlertEvent:
    """Classified sound event notification for visual alerting."""
    category: SoundCategory
    title: str
    description: str
    confidence: float
    urgency: str       # "critical", "normal", "low"
    icon: str          # Emoji/symbol for display
    color_bgr: Tuple[int, int, int]
    timestamp: float = 0.0

    def __post_init__(self):
        if self.timestamp == 0.0:
            self.timestamp = time.time()


class SoundClassifier:
    """100% Offline, low-latency spectral acoustic event classifier."""

    def __init__(self, sample_rate: int = 16000, sensitivity: str = "medium"):
        self.sample_rate = sample_rate
        self.set_sensitivity(sensitivity)

        # Sliding audio analysis buffer (1.2 seconds ~ 19,200 samples)
        self.buffer_len = int(sample_rate * 1.2)
        self.buffer: collections.deque = collections.deque(maxlen=self.buffer_len)

        # Rolling history for transient onset detection
        self.energy_history: collections.deque = collections.deque(maxlen=40)
        self.noise_floor: float = 0.01

        # Attempt to load MediaPipe AudioClassifier if available
        self.mediapipe_classifier = None
        self._init_mediapipe_audio()

    def _init_mediapipe_audio(self) -> None:
        """Optionally initialize MediaPipe AudioClassifier if model asset is present."""
        try:
            from mediapipe.tasks.python import audio
            from signora.models.manifest import get_default_models_dir

            model_path = get_default_models_dir() / "yamnet.tflite"
            if model_path.exists():
                options = audio.AudioClassifierOptions(
                    base_options=audio.BaseOptions(model_asset_path=str(model_path)),
                    max_results=3,
                    score_threshold=0.35,
                )
                self.mediapipe_classifier = audio.AudioClassifier.create_from_options(options)
        except Exception:
            self.mediapipe_classifier = None

    def set_sensitivity(self, sensitivity: str) -> None:
        """Adjust detection thresholds."""
        sensitivity = sensitivity.lower()
        if sensitivity == "high":
            self.thresh_mult = 0.65
            self.min_confidence = 0.45
        elif sensitivity == "low":
            self.thresh_mult = 1.4
            self.min_confidence = 0.75
        else:  # medium
            self.thresh_mult = 1.0
            self.min_confidence = 0.60

    def update_audio(self, samples: np.ndarray) -> None:
        """Append incoming audio samples to rolling circular buffer."""
        self.buffer.extend(samples.flatten())

    def process(self) -> Tuple[Optional[AlertEvent], Dict[str, float]]:
        """Analyze current audio buffer and detect significant environmental events.

        Returns:
            (event, telemetry_dict)
        """
        if len(self.buffer) < int(self.sample_rate * 0.4):
            return None, {"rms": 0.0, "db": -60.0}

        audio = np.array(self.buffer, dtype=np.float32)

        # Compute RMS volume and decibels
        rms = float(np.sqrt(np.mean(audio**2)))
        db_spl = float(20.0 * np.log10(max(rms, 1e-5)) + 94.0)  # Approx dB SPL relative to mic max

        # Update adaptive noise floor
        self.noise_floor = 0.98 * self.noise_floor + 0.02 * min(rms, self.noise_floor * 2.0)
        norm_energy = float(np.clip((rms - self.noise_floor) / max(0.015, self.noise_floor * 4.0), 0.0, 1.0))
        self.energy_history.append(rms)

        telemetry = {
            "rms": rms,
            "db": db_spl,
            "noise_floor": self.noise_floor,
            "norm_energy": norm_energy,
        }

        # If audio energy is below active threshold, return ambient
        if rms < 0.012 * self.thresh_mult:
            return None, telemetry

        # 1. Spectral Analysis via FFT
        n_fft = 2048
        if len(audio) < n_fft:
            return None, telemetry

        # Take the most recent 2048 samples
        recent_chunk = audio[-n_fft:] * np.hanning(n_fft)
        fft_mag = np.abs(np.fft.rfft(recent_chunk))
        freqs = np.fft.rfftfreq(n_fft, 1.0 / self.sample_rate)

        # Spectral energy bands
        def band_energy(f_low: float, f_high: float) -> float:
            idx = (freqs >= f_low) & (freqs <= f_high)
            return float(np.sum(fft_mag[idx]**2)) if np.any(idx) else 0.0

        total_energy = float(np.sum(fft_mag**2)) + 1e-8
        e_knock = band_energy(100, 750) / total_energy
        e_voice = band_energy(300, 3200) / total_energy
        e_chime = band_energy(550, 1200) / total_energy
        e_siren = band_energy(2400, 3800) / total_energy

        telemetry["e_knock"] = e_knock
        telemetry["e_voice"] = e_voice
        telemetry["e_siren"] = e_siren
        telemetry["e_chime"] = e_chime

        # 2. Heuristic Classifiers

        # A. SIREN / FIRE ALARM (Continuous / Pulsed High-Pitch Tone ~2.8-3.4 kHz)
        if e_siren > (0.42 * self.thresh_mult) and rms > 0.04:
            # Check for dominant sharp spectral peak in 2.5 - 3.5 kHz
            siren_band = (freqs >= 2400) & (freqs <= 3800)
            peak_freq = freqs[siren_band][np.argmax(fft_mag[siren_band])]
            peak_power = np.max(fft_mag[siren_band]) / (np.mean(fft_mag) + 1e-6)

            if peak_power > 12.0:
                conf = float(min(0.99, (e_siren * 1.5) + (peak_power / 60.0)))
                if conf >= self.min_confidence:
                    return AlertEvent(
                        category=SoundCategory.SIREN_ALARM,
                        title="EMERGENCY: SIREN / FIRE ALARM",
                        description=f"High-frequency alarm tone detected ({int(peak_freq)} Hz)",
                        confidence=conf,
                        urgency="critical",
                        icon="🚨",
                        color_bgr=(0, 0, 255),  # Red
                    ), telemetry

        # B. DOORBELL (Two-tone resonant chimes: e.g. 600-850 Hz prominent peaks)
        if e_chime > (0.35 * self.thresh_mult) and rms > 0.035:
            chime_band = (freqs >= 500) & (freqs <= 1200)
            peaks_sorted = freqs[chime_band][np.argsort(fft_mag[chime_band])[-3:]]
            # Check for characteristic chime frequencies (e.g. around 650Hz, 780Hz)
            if any(600 <= f <= 850 for f in peaks_sorted):
                conf = float(min(0.96, e_chime * 1.6))
                if conf >= self.min_confidence:
                    return AlertEvent(
                        category=SoundCategory.DOORBELL,
                        title="DOORBELL CHIME DETECTED",
                        description="Harmonic chime sound at the door",
                        confidence=conf,
                        urgency="normal",
                        icon="🔔",
                        color_bgr=(52, 211, 153),  # Emerald
                    ), telemetry

        # C. DOOR KNOCK (Rhythmic acoustic impulses with sharp onset in low-mid band)
        if e_knock > (0.45 * self.thresh_mult) and rms > 0.045:
            # Count onset spikes in recent energy history
            history = list(self.energy_history)[-25:]
            if len(history) >= 10:
                diffs = np.diff(history)
                spike_count = int(np.sum(diffs > (0.025 * self.thresh_mult)))
                if 2 <= spike_count <= 6:
                    conf = float(min(0.95, 0.65 + 0.08 * spike_count))
                    if conf >= self.min_confidence:
                        return AlertEvent(
                            category=SoundCategory.DOOR_KNOCK,
                            title="KNOCK AT THE DOOR",
                            description=f"Rhythmic knocking impulses detected ({spike_count} strikes)",
                            confidence=conf,
                            urgency="normal",
                            icon="🚪",
                            color_bgr=(245, 158, 11),  # Amber
                        ), telemetry

        # D. HUMAN CALL / SHOUTING ("Calling for attention!")
        if e_voice > (0.55 * self.thresh_mult) and rms > 0.06:
            # Voice pitch harmonics in 150 - 3000 Hz
            voice_band = (freqs >= 200) & (freqs <= 3000)
            voice_peak = np.max(fft_mag[voice_band]) / (np.mean(fft_mag) + 1e-6)
            if voice_peak > 8.0:
                conf = float(min(0.94, (e_voice * 1.1) + (rms * 2.0)))
                if conf >= self.min_confidence:
                    return AlertEvent(
                        category=SoundCategory.HUMAN_CALL,
                        title="SOMEONE CALLING / SHOUTING",
                        description="Human voice calling out loudly for attention",
                        confidence=conf,
                        urgency="normal",
                        icon="🗣️",
                        color_bgr=(6, 182, 212),  # Electric Cyan
                    ), telemetry

        # E. DOG BARK (Short explosive acoustic bark burst)
        if (0.35 <= e_knock <= 0.65) and (0.30 <= e_voice <= 0.60) and rms > 0.08:
            return AlertEvent(
                category=SoundCategory.DOG_BARK,
                title="DOG BARKING DETECTED",
                description="Loud canine bark acoustic signature",
                confidence=0.78,
                urgency="low",
                icon="🐕",
                color_bgr=(251, 146, 60),  # Orange
            ), telemetry

        return None, telemetry

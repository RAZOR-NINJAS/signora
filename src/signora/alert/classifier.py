"""Acoustic environmental sound classifier for deaf/hard-of-hearing assistive alerts.

Combines Google AudioSet deep neural network (YAMNet via MediaPipe Tasks Audio)
with low-latency spectral energy band analysis for 100% offline edge sound detection.
"""

import collections
import time
from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Tuple

import numpy as np

try:
    from mediapipe.tasks.python import audio
    from mediapipe.tasks.python.components import containers
    from mediapipe.tasks.python.core import base_options
    MEDIAPIPE_AUDIO_AVAILABLE = True
except Exception:
    audio = None
    containers = None
    base_options = None
    MEDIAPIPE_AUDIO_AVAILABLE = False


class SoundCategory(str, Enum):
    """Recognized environmental sound categories."""
    DOORBELL = "DOORBELL"
    DOOR_KNOCK = "DOOR_KNOCK"
    SIREN_ALARM = "SIREN_ALARM"
    HUMAN_CALL = "HUMAN_CALL"
    BABY_CRY = "BABY_CRY"
    DOG_BARK = "DOG_BARK"
    GLASS_BREAK = "GLASS_BREAK"
    AMBIENT = "AMBIENT"


@dataclass
class AlertEvent:
    """Classified sound event notification for visual alerting."""
    category: SoundCategory
    title: str
    description: str
    confidence: float
    urgency: str       # "critical", "high", "normal", "low"
    icon: str          # Visual symbol / emoji
    color_bgr: Tuple[int, int, int]
    timestamp: float = 0.0

    def __post_init__(self):
        if self.timestamp == 0.0:
            self.timestamp = time.time()


# AudioSet keyword to SoundCategory mapping
YAMNET_CLASS_MAP = {
    # Doorbell / Chime
    "doorbell": (SoundCategory.DOORBELL, "DOORBELL / CHIME DETECTED", "🔔", "Harmonic doorbell / chime ring at door", "normal", (52, 211, 153)),
    "ding-dong": (SoundCategory.DOORBELL, "DOORBELL CHIME DETECTED", "🔔", "Ding-dong door chime acoustic signature", "normal", (52, 211, 153)),
    "chime": (SoundCategory.DOORBELL, "DOOR CHIME DETECTED", "🔔", "Resonant chime acoustic signature", "normal", (52, 211, 153)),
    "jingle bell": (SoundCategory.DOORBELL, "BELL CHIME DETECTED", "🔔", "Bell or chime ringing", "normal", (52, 211, 153)),
    "telephone bell ringing": (SoundCategory.DOORBELL, "TELEPHONE / INTERCOM RING", "📞", "Telephone or intercom bell ringing", "normal", (52, 211, 153)),
    "bicycle bell": (SoundCategory.DOORBELL, "BELL RING DETECTED", "🔔", "Bell ringing acoustic signature", "normal", (52, 211, 153)),
    "buzzer": (SoundCategory.DOORBELL, "INTERCOM / DOOR BUZZER", "🚨", "Electric door buzzer tone", "normal", (52, 211, 153)),

    # Knocks
    "knock": (SoundCategory.DOOR_KNOCK, "KNOCK AT THE DOOR", "🚪", "Impulsive knocking acoustic signature", "normal", (245, 158, 11)),
    "door": (SoundCategory.DOOR_KNOCK, "DOOR ACTIVITY / KNOCK", "🚪", "Door movement or strike detected", "normal", (245, 158, 11)),
    "tap": (SoundCategory.DOOR_KNOCK, "TAPPING / KNOCKING", "🚪", "Rhythmic tapping detected", "normal", (245, 158, 11)),
    "thump, thud": (SoundCategory.DOOR_KNOCK, "LOUD THUMP / KNOCK", "🚪", "Heavy strike or knock detected", "normal", (245, 158, 11)),

    # Alarms / Sirens
    "fire alarm": (SoundCategory.SIREN_ALARM, "EMERGENCY: FIRE ALARM", "🚨", "Fire alarm acoustic warning pattern", "critical", (0, 0, 255)),
    "smoke detector, smoke alarm": (SoundCategory.SIREN_ALARM, "EMERGENCY: SMOKE DETECTOR", "🚨", "High-frequency smoke detector beeps", "critical", (0, 0, 255)),
    "siren": (SoundCategory.SIREN_ALARM, "EMERGENCY: SIREN ALARM", "🚨", "High-frequency emergency siren", "critical", (0, 0, 255)),
    "ambulance (siren)": (SoundCategory.SIREN_ALARM, "EMERGENCY: AMBULANCE SIREN", "🚑", "Emergency vehicle siren nearby", "critical", (0, 0, 255)),
    "police car (siren)": (SoundCategory.SIREN_ALARM, "EMERGENCY: POLICE SIREN", "🚓", "Police emergency siren nearby", "critical", (0, 0, 255)),
    "fire engine, fire truck (siren)": (SoundCategory.SIREN_ALARM, "EMERGENCY: FIRE TRUCK SIREN", "🚒", "Fire vehicle siren nearby", "critical", (0, 0, 255)),
    "civil defense siren": (SoundCategory.SIREN_ALARM, "EMERGENCY: SIREN WARNING", "🚨", "Civil siren acoustic warning", "critical", (0, 0, 255)),
    "alarm": (SoundCategory.SIREN_ALARM, "LOUD ALARM DETECTED", "⏰", "Continuous acoustic alarm tone", "critical", (0, 0, 255)),
    "car alarm": (SoundCategory.SIREN_ALARM, "VEHICLE ALARM DETECTED", "🚗", "Car alarm acoustic horn pattern", "high", (0, 0, 255)),

    # Human calls
    "shout": (SoundCategory.HUMAN_CALL, "SOMEONE SHOUTING", "🗣️", "Loud shouting detected nearby", "normal", (6, 182, 212)),
    "yell": (SoundCategory.HUMAN_CALL, "SOMEONE CALLING OUT", "🗣️", "Human voice calling for attention", "normal", (6, 182, 212)),
    "children shouting": (SoundCategory.HUMAN_CALL, "CHILDREN SHOUTING", "🗣️", "Shouting or calling detected", "normal", (6, 182, 212)),
    "screaming": (SoundCategory.HUMAN_CALL, "URGENT: SCREAM / YELL", "⚠️", "Urgent vocal distress / screaming detected", "high", (6, 182, 212)),

    # Baby cry
    "baby cry, infant cry": (SoundCategory.BABY_CRY, "BABY CRYING DETECTED", "👶", "Infant crying acoustic harmonics", "high", (244, 114, 182)),
    "crying, sobbing": (SoundCategory.BABY_CRY, "CRYING DETECTED", "👶", "Vocal distress / crying detected", "high", (244, 114, 182)),

    # Dog bark
    "bark": (SoundCategory.DOG_BARK, "DOG BARKING DETECTED", "🐕", "Canine acoustic bark signature", "low", (251, 146, 60)),
    "dog": (SoundCategory.DOG_BARK, "DOG BARKING DETECTED", "🐕", "Canine acoustic sound detected", "low", (251, 146, 60)),

    # Glass shatter
    "glass": (SoundCategory.GLASS_BREAK, "GLASS BREAKING / SHATTER", "💥", "High-frequency glass shatter acoustic impulse", "critical", (239, 68, 68)),
}


class SoundClassifier:
    """Hybrid edge sound classifier combining Google YAMNet neural inference and spectral acoustics."""

    def __init__(self, sample_rate: int = 16000, sensitivity: str = "medium"):
        self.sample_rate = sample_rate
        self.set_sensitivity(sensitivity)

        # 0.975s analysis buffer for YAMNet (15,600 samples)
        self.buffer_len = int(sample_rate * 1.0)
        self.buffer: collections.deque = collections.deque(maxlen=self.buffer_len)

        # Rolling history for transient onset detection
        self.energy_history: collections.deque = collections.deque(maxlen=40)
        self.noise_floor: float = 0.012

        # Step rate limiter for YAMNet neural inference (run every ~250ms)
        self.last_yamnet_time = 0.0
        self.yamnet_interval = 0.25

        # Initialize MediaPipe AudioClassifier
        self.mediapipe_classifier = None
        self._init_mediapipe_audio()

    def _init_mediapipe_audio(self) -> None:
        """Initialize Google AudioSet neural classifier if model is present."""
        if not MEDIAPIPE_AUDIO_AVAILABLE:
            return

        try:
            from signora.models.manifest import get_default_models_dir

            model_path = get_default_models_dir() / "yamnet.tflite"
            if not model_path.exists():
                # Check repo root
                alt_path = model_path.parents[3] / "models" / "yamnet.tflite"
                if alt_path.exists():
                    model_path = alt_path

            if model_path.exists():
                options = audio.AudioClassifierOptions(
                    base_options=base_options.BaseOptions(model_asset_path=str(model_path)),
                    max_results=6,
                    score_threshold=0.15 * self.thresh_mult,
                )
                self.mediapipe_classifier = audio.AudioClassifier.create_from_options(options)
        except Exception:
            self.mediapipe_classifier = None

    def set_sensitivity(self, sensitivity: str) -> None:
        """Adjust detection thresholds."""
        sensitivity = sensitivity.lower()
        if sensitivity == "high":
            self.thresh_mult = 0.65
            self.min_confidence = 0.40
        elif sensitivity == "low":
            self.thresh_mult = 1.45
            self.min_confidence = 0.75
        else:  # medium
            self.thresh_mult = 1.0
            self.min_confidence = 0.55

    def update_audio(self, samples: np.ndarray) -> None:
        """Append incoming audio samples to rolling circular buffer."""
        self.buffer.extend(samples.flatten())

    def process(self) -> Tuple[Optional[AlertEvent], Dict[str, float]]:
        """Analyze current audio buffer and return detected sound event and telemetry."""
        if len(self.buffer) < int(self.sample_rate * 0.3):
            return None, {"rms": 0.0, "db": -60.0}

        audio_arr = np.array(self.buffer, dtype=np.float32)

        # 1. Compute RMS volume and decibels
        rms = float(np.sqrt(np.mean(audio_arr**2)))
        db_spl = float(20.0 * np.log10(max(rms, 1e-5)) + 94.0)

        # Update adaptive noise floor (slowly tracking ambient quiet background)
        self.noise_floor = 0.99 * self.noise_floor + 0.01 * min(rms, self.noise_floor * 1.8)
        norm_energy = float(np.clip((rms - self.noise_floor) / max(0.015, self.noise_floor * 3.5), 0.0, 1.0))
        self.energy_history.append(rms)

        telemetry: Dict[str, float] = {
            "rms": rms,
            "db": db_spl,
            "noise_floor": self.noise_floor,
            "norm_energy": norm_energy,
            "neural_active": 1.0 if self.mediapipe_classifier else 0.0,
        }

        # 2. Spectral Analysis via FFT for telemetry & fast-path
        n_fft = 2048
        if len(audio_arr) >= n_fft:
            recent_chunk = audio_arr[-n_fft:] * np.hanning(n_fft)
            fft_mag = np.abs(np.fft.rfft(recent_chunk))
            freqs = np.fft.rfftfreq(n_fft, 1.0 / self.sample_rate)

            def band_energy(f_low: float, f_high: float) -> float:
                idx = (freqs >= f_low) & (freqs <= f_high)
                return float(np.sum(fft_mag[idx]**2)) if np.any(idx) else 0.0

            total_energy = float(np.sum(fft_mag**2)) + 1e-8
            telemetry["e_knock"] = band_energy(100, 750) / total_energy
            telemetry["e_voice"] = band_energy(300, 3200) / total_energy
            telemetry["e_chime"] = band_energy(550, 1200) / total_energy
            telemetry["e_siren"] = band_energy(2400, 3800) / total_energy
        else:
            telemetry["e_knock"] = 0.0
            telemetry["e_voice"] = 0.0
            telemetry["e_chime"] = 0.0
            telemetry["e_siren"] = 0.0

        # If audio energy is below background gate, return ambient
        if rms < 0.010 * self.thresh_mult:
            return None, telemetry

        now = time.monotonic()

        # 3. Neural AudioSet Classifier (Google YAMNet)
        if self.mediapipe_classifier and len(audio_arr) >= int(self.sample_rate * 0.75):
            if now - self.last_yamnet_time >= self.yamnet_interval:
                self.last_yamnet_time = now
                try:
                    # YAMNet requires exactly 15,600 or 16,000 float32 samples
                    chunk_len = min(len(audio_arr), int(self.sample_rate * 0.975))
                    sample_slice = audio_arr[-chunk_len:]
                    audio_data = containers.AudioData.create_from_array(sample_slice, self.sample_rate)
                    results = self.mediapipe_classifier.classify(audio_data)

                    if results and results[0].classifications:
                        for cat in results[0].classifications[0].categories:
                            c_name = cat.category_name.lower()
                            score = float(cat.score)

                            for key, (category, title, icon, desc, urgency, color) in YAMNET_CLASS_MAP.items():
                                if key in c_name:
                                    # Boost score if energy is elevated
                                    effective_score = float(min(0.99, score + (norm_energy * 0.25)))
                                    if effective_score >= self.min_confidence:
                                        telemetry["detected_class"] = c_name
                                        telemetry["detected_score"] = effective_score
                                        return AlertEvent(
                                            category=category,
                                            title=title,
                                            description=f"{desc} (YAMNet Neural: {int(effective_score*100)}%)",
                                            confidence=effective_score,
                                            urgency=urgency,
                                            icon=icon,
                                            color_bgr=color,
                                        ), telemetry
                except Exception:
                    pass

        # 4. Low-Latency Heuristic Fast-Path (sub-50ms transient response or fallback)
        e_siren = telemetry["e_siren"]
        e_chime = telemetry["e_chime"]
        e_knock = telemetry["e_knock"]
        e_voice = telemetry["e_voice"]

        # A. SIREN / FIRE ALARM (Continuous / Pulsed High-Pitch Tone ~2.8-3.4 kHz)
        if e_siren > (0.38 * self.thresh_mult) and rms > 0.035:
            conf = float(min(0.98, e_siren * 1.5 + (norm_energy * 0.2)))
            if conf >= self.min_confidence:
                return AlertEvent(
                    category=SoundCategory.SIREN_ALARM,
                    title="EMERGENCY: SIREN / FIRE ALARM",
                    description="High-frequency alarm acoustic signature detected",
                    confidence=conf,
                    urgency="critical",
                    icon="🚨",
                    color_bgr=(0, 0, 255),
                ), telemetry

        # B. DOORBELL (Two-tone resonant chimes ~600-850 Hz)
        if e_chime > (0.32 * self.thresh_mult) and rms > 0.030:
            conf = float(min(0.96, e_chime * 1.6 + (norm_energy * 0.2)))
            if conf >= self.min_confidence:
                return AlertEvent(
                    category=SoundCategory.DOORBELL,
                    title="DOORBELL CHIME DETECTED",
                    description="Harmonic chime at the door",
                    confidence=conf,
                    urgency="normal",
                    icon="🔔",
                    color_bgr=(52, 211, 153),
                ), telemetry

        # C. DOOR KNOCK (Rhythmic acoustic impulses with sharp onset in low band)
        if e_knock > (0.40 * self.thresh_mult) and rms > 0.038:
            history = list(self.energy_history)[-25:]
            if len(history) >= 8:
                diffs = np.diff(history)
                spike_count = int(np.sum(diffs > (0.020 * self.thresh_mult)))
                if spike_count >= 2:
                    conf = float(min(0.95, 0.60 + 0.10 * spike_count))
                    if conf >= self.min_confidence:
                        return AlertEvent(
                            category=SoundCategory.DOOR_KNOCK,
                            title="KNOCK AT THE DOOR",
                            description=f"Rhythmic knocking impulses detected ({spike_count} strikes)",
                            confidence=conf,
                            urgency="normal",
                            icon="🚪",
                            color_bgr=(245, 158, 11),
                        ), telemetry

        # D. HUMAN CALL / SHOUTING
        if e_voice > (0.50 * self.thresh_mult) and rms > 0.055:
            conf = float(min(0.94, e_voice * 1.1 + (rms * 2.0)))
            if conf >= self.min_confidence:
                return AlertEvent(
                    category=SoundCategory.HUMAN_CALL,
                    title="SOMEONE CALLING / SHOUTING",
                    description="Human voice calling out loudly for attention",
                    confidence=conf,
                    urgency="normal",
                    icon="🗣️",
                    color_bgr=(6, 182, 212),
                ), telemetry

        return None, telemetry

    def close(self) -> None:
        """Release MediaPipe AudioClassifier and background resources."""
        if self.mediapipe_classifier:
            try:
                self.mediapipe_classifier.close()
            except Exception:
                pass
            self.mediapipe_classifier = None

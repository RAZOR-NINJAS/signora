"""Non-blocking microphone audio energy sampler and Audio-Visual synchronization engine."""

import collections
import threading
import time
from enum import Enum
from typing import Deque, Optional, Tuple

import numpy as np

try:
    import sounddevice as sd
    SOUNDDEVICE_AVAILABLE = True
except Exception:
    sd = None
    SOUNDDEVICE_AVAILABLE = False


class SyncState(str, Enum):
    """Audio-Visual Synchronization States."""
    SYNCED = "IN SYNC (Voice + Lips Active)"
    MOUTHING = "MOUTHING (Silent Speech / No Audio)"
    BACKGROUND_AUDIO = "BACKGROUND AUDIO (Lips Still)"
    SILENT = "SILENT (Idle)"


class AudioEnergyTracker:
    """Thread-safe real-time microphone RMS sampler and Audio-Visual correlation engine."""

    def __init__(
        self,
        sample_rate: int = 16000,
        block_size: int = 320,  # 20ms chunks
        buffer_seconds: float = 2.0,
        enable_audio: bool = True,
    ):
        self.sample_rate = sample_rate
        self.block_size = block_size
        self.enable_audio = enable_audio and SOUNDDEVICE_AVAILABLE
        self.audio_active: bool = False

        capacity = int((buffer_seconds * sample_rate) / block_size)
        self.energy_buffer: Deque[float] = collections.deque(maxlen=capacity)
        self.time_buffer: Deque[float] = collections.deque(maxlen=capacity)
        self.lock = threading.Lock()

        self.noise_floor: float = 0.015
        self.peak_energy: float = 0.05
        self.stream: Optional[Any] = None

        if self.enable_audio:
            self._start_stream()

    def _start_stream(self) -> None:
        """Initialize sounddevice InputStream in background."""
        try:
            self.stream = sd.InputStream(
                samplerate=self.sample_rate,
                blocksize=self.block_size,
                channels=1,
                dtype="float32",
                callback=self._audio_callback,
            )
            self.stream.start()
            self.audio_active = True
        except Exception as e:
            # Fall back cleanly if no audio input device is available
            self.audio_active = False
            self.stream = None

    def _audio_callback(self, indata: np.ndarray, frames: int, time_info: Any, status: Any) -> None:
        """Receive microphone buffer and compute RMS volume energy."""
        if not indata.size:
            return

        # Calculate Root-Mean-Square (RMS) amplitude
        rms = float(np.sqrt(np.mean(indata**2)))

        now = time.monotonic()
        with self.lock:
            # Adaptive noise floor
            self.noise_floor = 0.995 * self.noise_floor + 0.005 * min(rms, self.noise_floor * 1.5)
            # Normalized energy above noise floor
            norm_energy = float(np.clip((rms - self.noise_floor) / max(0.02, self.peak_energy), 0.0, 1.0))
            if norm_energy > 0.8:
                self.peak_energy = 0.95 * self.peak_energy + 0.05 * rms

            self.energy_buffer.append(norm_energy)
            self.time_buffer.append(now)

    def get_latest_energy(self) -> float:
        """Get latest normalized audio energy [0.0, 1.0]."""
        with self.lock:
            return self.energy_buffer[-1] if self.energy_buffer else 0.0

    def compute_sync(
        self,
        visual_motion_history: collections.deque,
        visual_time_history: collections.deque,
        is_visual_speaking: bool,
    ) -> Tuple[float, SyncState, float]:
        """Compute Audio-Visual lip synchronization score and status.

        Returns:
            sync_score: 0.0 to 1.0 (percentage of speech-to-lip alignment)
            state: SyncState classification
            lag_ms: estimated acoustic-visual offset in milliseconds
        """
        if not self.audio_active or not self.enable_audio:
            # Pure visual mode without mic
            if is_visual_speaking:
                return 1.0, SyncState.MOUTHING, 0.0
            return 0.0, SyncState.SILENT, 0.0

        with self.lock:
            if len(self.energy_buffer) < 10 or len(visual_motion_history) < 10:
                return 0.0, SyncState.SILENT, 0.0

            audio_energies = np.array(self.energy_buffer)
            audio_times = np.array(self.time_buffer)

        vis_energies = np.array(visual_motion_history)
        vis_times = np.array(visual_time_history)

        # Recent 0.8s evaluation window
        now = time.monotonic()
        t_start = now - 0.8

        a_mask = audio_times >= t_start
        v_mask = vis_times >= t_start

        if np.sum(a_mask) < 5 or np.sum(v_mask) < 5:
            return 0.0, SyncState.SILENT, 0.0

        a_win = audio_energies[a_mask]
        v_win = vis_energies[v_mask]

        audio_level = float(np.mean(a_win))
        vis_level = float(np.mean(v_win))

        has_audio = audio_level > 0.08
        has_lips = is_visual_speaking or (vis_level > 0.08)

        # State determination
        if has_lips and not has_audio:
            # Silent mouthing / sign language oral gesture
            return 0.0, SyncState.MOUTHING, 0.0
        elif has_audio and not has_lips:
            # Ambient audio or external speaker
            return 0.0, SyncState.BACKGROUND_AUDIO, 0.0
        elif not has_audio and not has_lips:
            return 0.0, SyncState.SILENT, 0.0

        # Both are active: Resample visual trace to audio grid for Pearson cross-correlation
        try:
            v_resampled = np.interp(audio_times[a_mask], vis_times[v_mask], v_win)

            # Normalized correlation
            a_norm = a_win - np.mean(a_win)
            v_norm = v_resampled - np.mean(v_resampled)
            denom = (np.std(a_norm) * np.std(v_norm))

            if denom > 1e-4:
                corr = float(np.mean(a_norm * v_norm) / denom)
                sync_score = float(np.clip((corr + 1.0) / 2.0, 0.0, 1.0))
            else:
                # Coincident bursts
                sync_score = 0.82

            # Compute cross-correlation lag
            xcorr = np.correlate(a_norm, v_norm, mode="full")
            shift = int(np.argmax(xcorr) - (len(v_norm) - 1))
            dt_step = (audio_times[-1] - audio_times[0]) / max(len(audio_times), 1)
            lag_ms = float(shift * dt_step * 1000.0)

            return sync_score, SyncState.SYNCED, lag_ms

        except Exception:
            return 0.85, SyncState.SYNCED, 0.0

    def toggle(self) -> bool:
        """Toggle microphone audio processing on/off."""
        self.enable_audio = not self.enable_audio
        if self.enable_audio and not self.stream:
            self._start_stream()
        elif not self.enable_audio and self.stream:
            try:
                self.stream.stop()
                self.stream.close()
            except Exception:
                pass
            self.stream = None
            self.audio_active = False
        return self.enable_audio

    def close(self) -> None:
        """Release audio stream."""
        if self.stream:
            try:
                self.stream.stop()
                self.stream.close()
            except Exception:
                pass
            self.stream = None
            self.audio_active = False

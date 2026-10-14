"""Core alert engine coordinating microphone capture, classification, and event dispatch."""

import queue
import threading
import time
from typing import Any, Dict, Optional, Tuple

import numpy as np

from signora.alert.classifier import AlertEvent, SoundCategory, SoundClassifier
from signora.alert.dispatcher import AlertDispatcher

try:
    import sounddevice as sd
    SOUNDDEVICE_AVAILABLE = True
except Exception:
    sd = None
    SOUNDDEVICE_AVAILABLE = False


class AlertEngine:
    """End-to-end sound monitoring engine."""

    def __init__(
        self,
        sample_rate: int = 16000,
        block_size: int = 640,  # 40ms audio chunks
        sensitivity: str = "medium",
        enable_desktop: bool = True,
        use_fake_mic: bool = False,
    ):
        self.sample_rate = sample_rate
        self.block_size = block_size
        self.use_fake_mic = use_fake_mic or not SOUNDDEVICE_AVAILABLE
        self.running = False

        self.classifier = SoundClassifier(sample_rate=sample_rate, sensitivity=sensitivity)
        self.dispatcher = AlertDispatcher(enable_desktop=enable_desktop)

        self.audio_queue: queue.Queue = queue.Queue(maxsize=100)
        self.stream: Optional[Any] = None
        self.active_alert: Optional[AlertEvent] = None
        self.active_alert_expiry: float = 0.0

        # Subscribe engine to keep active_alert for visual HUD
        self.dispatcher.subscribe(self._on_alert)

    def _on_alert(self, event: AlertEvent) -> None:
        """Handle new alert event and hold visual alert for 4.0s."""
        self.active_alert = event
        self.active_alert_expiry = time.time() + (6.0 if event.urgency == "critical" else 3.8)

    def start(self) -> bool:
        """Start background microphone audio streaming."""
        if self.running:
            return True

        self.running = True
        if not self.use_fake_mic and SOUNDDEVICE_AVAILABLE:
            try:
                self.stream = sd.InputStream(
                    samplerate=self.sample_rate,
                    blocksize=self.block_size,
                    channels=1,
                    dtype="float32",
                    callback=self._mic_callback,
                )
                self.stream.start()
                return True
            except Exception as e:
                print(f"[AlertEngine] Notice: Using virtual audio stream ({e})")
                self.use_fake_mic = True

        return True

    def _mic_callback(self, indata: np.ndarray, frames: int, time_info: Any, status: Any) -> None:
        """Non-blocking audio callback storing raw samples into queue."""
        if not self.running:
            return
        try:
            self.audio_queue.put_nowait(indata.copy())
        except queue.Full:
            pass

    def step(self) -> Tuple[Optional[AlertEvent], Dict[str, float]]:
        """Process pending audio chunks, run inference, and trigger alerts."""
        if self.use_fake_mic:
            # Generate mild synthetic ambient room noise
            noise = np.random.normal(0, 0.003, self.block_size).astype(np.float32)
            self.classifier.update_audio(noise)
        else:
            # Drain queue
            while not self.audio_queue.empty():
                try:
                    chunk = self.audio_queue.get_nowait()
                    self.classifier.update_audio(chunk)
                except queue.Empty:
                    break

        event, telemetry = self.classifier.process()
        if event:
            self.dispatcher.dispatch(event)

        # Clear expired visual active alert
        if self.active_alert and time.time() > self.active_alert_expiry:
            self.active_alert = None

        return event, telemetry

    def simulate_sound(self, category_str: str) -> Optional[AlertEvent]:
        """Inject synthetic sound into the pipeline for live demonstration."""
        category_str = category_str.upper()
        sr = self.sample_rate

        if "SIREN" in category_str or "ALARM" in category_str:
            t = np.linspace(0, 0.6, int(sr * 0.6))
            sig = (0.28 * np.sin(2 * np.pi * 3100 * t)).astype(np.float32)
        elif "DOORBELL" in category_str or "CHIME" in category_str:
            t1 = np.linspace(0, 0.25, int(sr * 0.25))
            t2 = np.linspace(0, 0.25, int(sr * 0.25))
            c1 = 0.22 * np.sin(2 * np.pi * 784 * t1) * np.exp(-t1 * 6)
            c2 = 0.22 * np.sin(2 * np.pi * 659 * t2) * np.exp(-t2 * 6)
            sig = np.concatenate([c1, c2]).astype(np.float32)
        elif "KNOCK" in category_str:
            sig = np.zeros(int(sr * 0.6), dtype=np.float32)
            # 3 sharp impulses
            for pos in [int(sr * 0.1), int(sr * 0.28), int(sr * 0.46)]:
                decay = np.exp(-np.linspace(0, 8, 400))
                impulse = 0.35 * np.sin(2 * np.pi * 320 * np.linspace(0, 0.025, 400)) * decay
                sig[pos:pos+400] += impulse.astype(np.float32)
        elif "CALL" in category_str or "VOICE" in category_str or "SPEECH" in category_str:
            t = np.linspace(0, 0.6, int(sr * 0.6))
            sig = (0.30 * np.sin(2 * np.pi * 400 * t) + 0.18 * np.sin(2 * np.pi * 1250 * t) + 0.12 * np.sin(2 * np.pi * 2400 * t)).astype(np.float32)
        elif "LOUD" in category_str or "CLAP" in category_str:
            sig = np.zeros(int(sr * 0.5), dtype=np.float32)
            t_imp = np.linspace(0, 0.04, int(sr * 0.04))
            impulse = 0.70 * np.sin(2 * np.pi * 500 * t_imp) * np.exp(-t_imp * 80)
            sig[int(sr * 0.08):int(sr * 0.08) + len(impulse)] = impulse.astype(np.float32)
        else:
            # Neural AudioSet classes (baby cry, dog bark, glass shatter)
            target_cat = SoundCategory.DOORBELL
            if "BABY" in category_str or "CRY" in category_str:
                target_cat = SoundCategory.BABY_CRY
            elif "BARK" in category_str or "DOG" in category_str:
                target_cat = SoundCategory.DOG_BARK
            elif "GLASS" in category_str:
                target_cat = SoundCategory.GLASS_BREAK
            return self.dispatcher.test_alert(target_cat)

        self.classifier.update_audio(sig)
        event, _ = self.classifier.process()

        if not event:
            target_cat = SoundCategory.DOORBELL
            if "CALL" in category_str or "VOICE" in category_str:
                target_cat = SoundCategory.HUMAN_CALL
            elif "SIREN" in category_str or "ALARM" in category_str:
                target_cat = SoundCategory.SIREN_ALARM
            elif "KNOCK" in category_str:
                target_cat = SoundCategory.DOOR_KNOCK
            elif "LOUD" in category_str or "CLAP" in category_str:
                target_cat = SoundCategory.LOUD_SOUND
            event = self.dispatcher.test_alert(target_cat)
            return event

        self.dispatcher.dispatch(event)
        return event

    def stop(self) -> None:
        """Release audio stream and stop processing."""
        self.running = False
        if self.stream:
            try:
                self.stream.stop()
                self.stream.close()
            except Exception:
                pass
            self.stream = None
        self.classifier.close()

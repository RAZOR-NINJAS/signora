"""Alert notification dispatcher for desktop visual notifications and cooldown management."""

import datetime
import os
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Callable, Dict, List, Optional

from signora.alert.classifier import AlertEvent, SoundCategory


def get_default_alerts_log_path() -> Path:
    """Return persistent log file path for acoustic alerts."""
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local")))
        log_dir = base / "Signora" / "logs"
    else:
        xdg = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share")))
        log_dir = xdg / "signora" / "logs"

    log_dir.mkdir(parents=True, exist_ok=True)
    return log_dir / "alerts.log"


class AlertDispatcher:
    """Dispatches notifications across desktop notification daemons with debounce and logging."""

    def __init__(
        self,
        enable_desktop: bool = True,
        log_path: Optional[Path] = None,
        cooldown_seconds: float = 1.0,
    ):
        self.enable_desktop = enable_desktop
        self.log_path = log_path or get_default_alerts_log_path()
        self.cooldown_seconds = cooldown_seconds

        self.last_fired: Dict[SoundCategory, float] = {}
        self.subscribers: List[Callable[[AlertEvent], None]] = []
        self.lock = threading.Lock()
        self.recent_events: List[AlertEvent] = []

    def subscribe(self, callback: Callable[[AlertEvent], None]) -> None:
        """Register a callback listener for alert events."""
        with self.lock:
            self.subscribers.append(callback)

    def dispatch(self, event: AlertEvent) -> bool:
        """Dispatch an alert event if cooldown conditions are satisfied."""
        now = time.time()
        with self.lock:
            # Check cooldown per category (1.0s for general sounds, 8.0s for sirens)
            last_t = self.last_fired.get(event.category, 0.0)
            cooldown = 8.0 if event.category == SoundCategory.SIREN_ALARM else self.cooldown_seconds
            if now - last_t < cooldown:
                return False

            self.last_fired[event.category] = now
            self.recent_events.append(event)
            if len(self.recent_events) > 50:
                self.recent_events.pop(0)

            # Notify subscribers (HUD, UI, WebSockets)
            for sub in list(self.subscribers):
                try:
                    sub(event)
                except Exception:
                    pass

        # Append to log file
        self._log_to_disk(event)

        # Trigger desktop visual notification
        if self.enable_desktop:
            self._send_desktop_notification(event)

        return True

    def _log_to_disk(self, event: AlertEvent) -> None:
        """Persist alert event to log file."""
        try:
            dt_str = datetime.datetime.fromtimestamp(event.timestamp).strftime("%Y-%m-%d %H:%M:%S")
            line = (
                f"[{dt_str}] {event.icon} {event.title} "
                f"(Conf: {event.confidence*100:.1f}%, Urgency: {event.urgency}) - {event.description}\n"
            )
            with open(self.log_path, "a", encoding="utf-8") as f:
                f.write(line)
        except Exception:
            pass

    def _send_desktop_notification(self, event: AlertEvent) -> None:
        """Dispatch desktop visual notification via notify-send or PowerShell toast."""
        threading.Thread(
            target=self._notify_worker, args=(event,), daemon=True
        ).start()

    def _notify_worker(self, event: AlertEvent) -> None:
        urgency_flag = "critical" if event.urgency == "critical" else "normal"
        expire_ms = "8000" if event.urgency == "critical" else "4000"
        title = f"{event.icon} {event.title}"
        body = f"{event.description} (Confidence: {int(event.confidence * 100)}%)"

        if os.name == "nt":
            # Windows PowerShell notification balloon
            ps_script = (
                f'[reflection.assembly]::loadwithpartialname("System.Windows.Forms");'
                f'$notify = new-object system.windows.forms.notifyicon;'
                f'$notify.icon = [system.drawing.systemicons]::information;'
                f'$notify.visible = $true;'
                f'$notify.showballoontip(4000, "{title}", "{body}", [system.windows.forms.tooltipicon]::Info);'
            )
            try:
                subprocess.run(
                    ["powershell", "-NoProfile", "-Command", ps_script],
                    capture_output=True,
                    timeout=5,
                )
            except Exception:
                pass
        else:
            # Linux notify-send
            if shutil.which("notify-send"):
                try:
                    cmd = [
                        "notify-send",
                        "-a", "Signora Alert",
                        "-u", urgency_flag,
                        "-t", expire_ms,
                        title,
                        body,
                    ]
                    subprocess.run(cmd, capture_output=True, timeout=5)
                except Exception:
                    pass

        # Trigger on-top floating visual strobe window
        try:
            from signora.alert.popup import spawn_alert_popup_async

            b, g, r = event.color_bgr
            color_hex = f"#{r:02x}{g:02x}{b:02x}"
            duration = 6.0 if event.urgency == "critical" else 4.2
            spawn_alert_popup_async(
                icon=event.icon,
                title=event.title,
                description=event.description,
                color_hex=color_hex,
                duration_seconds=duration,
            )
        except Exception:
            pass

    def test_alert(self, category: SoundCategory = SoundCategory.DOORBELL) -> AlertEvent:
        """Generate and dispatch a test alert for instant verification."""
        if category == SoundCategory.SIREN_ALARM:
            event = AlertEvent(
                category=SoundCategory.SIREN_ALARM,
                title="TEST ALERT: SIREN / FIRE ALARM",
                description="Simulated emergency alarm audio trigger",
                confidence=0.98,
                urgency="critical",
                icon="🚨",
                color_bgr=(0, 0, 255),
            )
        elif category == SoundCategory.HUMAN_CALL:
            event = AlertEvent(
                category=SoundCategory.HUMAN_CALL,
                title="TEST ALERT: SOMEONE CALLING",
                description="Simulated human voice shouting for attention",
                confidence=0.92,
                urgency="normal",
                icon="🗣️",
                color_bgr=(6, 182, 212),
            )
        elif category == SoundCategory.LOUD_SOUND:
            event = AlertEvent(
                category=SoundCategory.LOUD_SOUND,
                title="TEST ALERT: LOUD SOUND",
                description="Simulated sudden acoustic impulse / noise spike",
                confidence=0.90,
                urgency="high",
                icon="⚡",
                color_bgr=(234, 179, 8),
            )
        elif category == SoundCategory.DOOR_KNOCK:
            event = AlertEvent(
                category=SoundCategory.DOOR_KNOCK,
                title="TEST ALERT: DOOR KNOCK",
                description="Simulated door knock acoustic strikes",
                confidence=0.94,
                urgency="normal",
                icon="🚪",
                color_bgr=(245, 158, 11),
            )
        elif category == SoundCategory.BABY_CRY:
            event = AlertEvent(
                category=SoundCategory.BABY_CRY,
                title="TEST ALERT: BABY CRYING",
                description="Simulated infant crying acoustic distress",
                confidence=0.91,
                urgency="high",
                icon="👶",
                color_bgr=(244, 114, 182),
            )
        elif category == SoundCategory.DOG_BARK:
            event = AlertEvent(
                category=SoundCategory.DOG_BARK,
                title="TEST ALERT: DOG BARK",
                description="Simulated canine bark acoustic pattern",
                confidence=0.93,
                urgency="normal",
                icon="🐕",
                color_bgr=(251, 146, 60),
            )
        elif category == SoundCategory.GLASS_BREAK:
            event = AlertEvent(
                category=SoundCategory.GLASS_BREAK,
                title="TEST ALERT: GLASS SHATTER",
                description="Simulated glass break acoustic impulse",
                confidence=0.95,
                urgency="critical",
                icon="💥",
                color_bgr=(239, 68, 68),
            )
        else:
            event = AlertEvent(
                category=SoundCategory.DOORBELL,
                title="TEST ALERT: DOORBELL CHIME",
                description="Simulated chime at front door",
                confidence=0.95,
                urgency="normal",
                icon="🔔",
                color_bgr=(52, 211, 153),
            )

        self.dispatch(event)
        return event

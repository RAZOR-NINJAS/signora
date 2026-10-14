"""SIGNORA Alert: Hear Through Your Eyes (Assistive Environmental Sound Recognition)."""

from signora.alert.calibrate import calibrate_microphone
from signora.alert.classifier import AlertEvent, SoundCategory, SoundClassifier
from signora.alert.daemon import run_daemon_loop, start_daemon, status_daemon, stop_daemon
from signora.alert.dispatcher import AlertDispatcher
from signora.alert.engine import AlertEngine
from signora.alert.live import run_alert_live
from signora.alert.popup import show_alert_popup, spawn_alert_popup_async

__all__ = [
    "AlertEvent",
    "SoundCategory",
    "SoundClassifier",
    "AlertDispatcher",
    "AlertEngine",
    "run_alert_live",
    "start_daemon",
    "stop_daemon",
    "status_daemon",
    "run_daemon_loop",
    "calibrate_microphone",
    "show_alert_popup",
    "spawn_alert_popup_async",
]

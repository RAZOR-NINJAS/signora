"""Interactive live visual monitor for Signora Alert (Hear Through Your Eyes)."""

import os
import sys
import time
from typing import Any, Optional

import cv2
import numpy as np

from signora.alert.classifier import SoundCategory
from signora.alert.engine import AlertEngine
from signora.alert.hud import render_alert_hud

# Wayland / X11 compatibility
os.environ.setdefault("QT_QPA_PLATFORM", "xcb")


def run_alert_live(
    source: Any = None,
    sensitivity: str = "medium",
    enable_desktop: bool = True,
    headless: bool = False,
    use_fake_mic: bool = False,
    max_frames: Optional[int] = None,
) -> int:
    """Run interactive visual acoustic monitor with Glassmorphic HUD."""
    print("=" * 65)
    print("  SIGNORA ALERT -- 'Hear Through Your Eyes' Visual Sound Monitor")
    print("=" * 65)
    print(f"  Sensitivity         : {sensitivity.upper()}")
    print(f"  Desktop Alerts      : {'ENABLED' if enable_desktop else 'DISABLED'}")
    print("  Controls:")
    print("    [T]   : Trigger simulated alert (Doorbell -> Knock -> Siren)")
    print("    [S]   : Toggle sensitivity (Low -> Medium -> High)")
    print("    [D]   : Toggle desktop notifications")
    print("    [Q]   : Quit monitor")
    print("=" * 65)

    engine = AlertEngine(
        sensitivity=sensitivity,
        enable_desktop=enable_desktop,
        use_fake_mic=use_fake_mic,
    )

    if not engine.start():
        print("[Error] Could not initialize alert engine.", file=sys.stderr)
        return 1

    window_name = "Signora Alert - Hear Through Your Eyes"
    if not headless:
        cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
        cv2.resizeWindow(window_name, 960, 600)

    # Optional camera capture if requested
    cap = None
    if source is not None and str(source).lower() not in ("none", "false"):
        try:
            cam_idx = int(source) if str(source).isdigit() else source
            cap = cv2.VideoCapture(cam_idx)
            if not cap.isOpened():
                cap = None
        except Exception:
            cap = None

    test_categories = [
        SoundCategory.DOORBELL,
        SoundCategory.DOOR_KNOCK,
        SoundCategory.SIREN_ALARM,
        SoundCategory.HUMAN_CALL,
    ]
    test_idx = 0
    sens_levels = ["low", "medium", "high"]
    sens_idx = sens_levels.index(sensitivity.lower()) if sensitivity.lower() in sens_levels else 1

    frame_count = 0

    try:
        while True:
            frame_count += 1
            if max_frames and frame_count > max_frames:
                break

            # 1. Background frame: either camera feed or modern dark cyberpunk canvas
            if cap and cap.isOpened():
                ret, frame = cap.read()
                if not ret or frame is None:
                    frame = np.full((600, 960, 3), 20, dtype=np.uint8)
                else:
                    frame = cv2.flip(frame, 1)
            else:
                frame = np.full((600, 960, 3), 20, dtype=np.uint8)

            # 2. Step audio analysis
            event, telemetry = engine.step()

            # 3. Key handling
            key = cv2.waitKey(15) & 0xFF if not headless else -1
            if key in (ord("q"), ord("Q"), 27):
                break
            elif key in (ord("t"), ord("T")):
                # Fire simulated alert for visual verification
                cat = test_categories[test_idx % len(test_categories)]
                test_idx += 1
                engine.simulate_sound(cat.value)
            elif key in (ord("s"), ord("S")):
                sens_idx = (sens_idx + 1) % len(sens_levels)
                new_sens = sens_levels[sens_idx]
                engine.classifier.set_sensitivity(new_sens)
                print(f"[Alert] Sensitivity switched to: {new_sens.upper()}")
            elif key in (ord("d"), ord("D")):
                engine.dispatcher.enable_desktop = not engine.dispatcher.enable_desktop
                print(f"[Alert] Desktop notifications: {'ON' if engine.dispatcher.enable_desktop else 'OFF'}")

            # 4. Render Glassmorphic HUD
            frame = render_alert_hud(
                frame=frame,
                active_alert=engine.active_alert,
                telemetry=telemetry,
                sensitivity=sens_levels[sens_idx],
                desktop_enabled=engine.dispatcher.enable_desktop,
                recent_events=engine.dispatcher.recent_events,
            )

            if not headless:
                cv2.imshow(window_name, frame)

            if use_fake_mic and headless:
                break

    finally:
        engine.stop()
        if cap:
            cap.release()
        if not headless:
            cv2.destroyAllWindows()

    return 0

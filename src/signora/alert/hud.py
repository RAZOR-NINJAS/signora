"""Glassmorphic visual alert HUD with full-screen border strobing, decibel radar, and event cards."""

import time
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np

from signora.alert.classifier import AlertEvent, SoundCategory


def render_alert_hud(
    frame: np.ndarray,
    active_alert: Optional[AlertEvent],
    telemetry: Dict[str, float],
    sensitivity: str,
    desktop_enabled: bool,
    recent_events: List[AlertEvent],
) -> np.ndarray:
    """Render high-contrast visual alert HUD on video frame."""
    h, w = frame.shape[:2]

    # 1. Full-Screen Visual Alert Strobe / Border Pulse (High Priority for Peripheral Vision)
    if active_alert:
        color = active_alert.color_bgr
        # Oscillate border pulse at ~4 Hz
        pulse_phase = np.sin(time.time() * 12.0)
        border_thick = int(14 + 8 * pulse_phase)

        # Glowing border around entire screen
        cv2.rectangle(frame, (0, 0), (w, h), color, border_thick)

        # Large Centered Alert Card
        card_w, card_h = min(720, w - 40), 220
        cx, cy = w // 2 - card_w // 2, h // 2 - card_h // 2

        overlay = frame.copy()
        cv2.rectangle(overlay, (cx, cy), (cx + card_w, cy + card_h), (15, 23, 42), -1)
        cv2.addWeighted(overlay, 0.92, frame, 0.08, 0, frame)
        cv2.rectangle(frame, (cx, cy), (cx + card_w, cy + card_h), color, 3)

        # Sound Category Icon & Title
        title_text = f"{active_alert.icon} {active_alert.title}"
        cv2.putText(
            frame,
            title_text,
            (cx + 30, cy + 65),
            cv2.FONT_HERSHEY_DUPLEX,
            0.9,
            (255, 255, 255),
            2,
            cv2.LINE_AA,
        )

        # Description
        cv2.putText(
            frame,
            active_alert.description,
            (cx + 35, cy + 115),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (226, 232, 240),
            1,
            cv2.LINE_AA,
        )

        # Confidence & Urgency Badge
        badge_text = (
            f"Confidence: {int(active_alert.confidence * 100)}%  |  "
            f"Urgency: {active_alert.urgency.upper()}  |  "
            f"Visual Strobe Active"
        )
        cv2.putText(
            frame,
            badge_text,
            (cx + 35, cy + 165),
            cv2.FONT_HERSHEY_DUPLEX,
            0.52,
            color,
            1,
            cv2.LINE_AA,
        )

        return frame

    # 2. Ambient Monitoring State (Normal HUD)

    # Top Header Banner
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 85), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)

    # Title & Subtitle
    cv2.putText(
        frame,
        "SIGNORA ALERT: HEAR THROUGH YOUR EYES",
        (20, 32),
        cv2.FONT_HERSHEY_DUPLEX,
        0.75,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )
    neural_active = telemetry.get("neural_active", 0.0) > 0.5
    engine_label = "Neural: Google YAMNet AudioSet" if neural_active else "Spectral Acoustic FFT"
    cv2.putText(
        frame,
        f"100% Offline Assistive Monitor  |  {engine_label}  |  Sens: {sensitivity.upper()}  |  Desktop: {'ON' if desktop_enabled else 'OFF'}",
        (20, 62),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.44,
        (148, 163, 184),
        1,
        cv2.LINE_AA,
    )

    # Pulsing Live Indicator (Top Right)
    pulse_dot_color = (52, 211, 153) if int(time.time() * 2) % 2 == 0 else (16, 185, 129)
    cv2.circle(frame, (w - 140, 42), 8, pulse_dot_color, -1)
    cv2.putText(
        frame,
        "LISTENING",
        (w - 120, 48),
        cv2.FONT_HERSHEY_DUPLEX,
        0.55,
        (52, 211, 153),
        1,
        cv2.LINE_AA,
    )

    # 3. Sound Pressure Level (dB SPL) Radar & Audio Energy Meter (Center Left)
    meter_x, meter_y, meter_w, meter_h = 24, 115, 340, 20
    db = telemetry.get("db", 30.0)
    norm_energy = telemetry.get("norm_energy", 0.0)

    # Decibel meter background
    cv2.rectangle(frame, (meter_x, meter_y), (meter_x + meter_w, meter_y + meter_h), (30, 41, 59), -1)

    # Energy fill bar
    fill_w = int(meter_w * min(1.0, norm_energy))
    fill_col = (52, 211, 153) if norm_energy < 0.6 else ((245, 158, 11) if norm_energy < 0.8 else (239, 68, 68))
    if fill_w > 0:
        cv2.rectangle(frame, (meter_x, meter_y), (meter_x + fill_w, meter_y + meter_h), fill_col, -1)

    cv2.putText(
        frame,
        f"Audio Level: {db:.1f} dB SPL (Normalized Energy: {int(norm_energy * 100)}%)",
        (meter_x, meter_y - 8),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.45,
        (226, 232, 240),
        1,
        cv2.LINE_AA,
    )

    # 4. Spectral Frequency Bands Breakdown
    bands = [
        ("Knock (150-700Hz)", telemetry.get("e_knock", 0.0), (245, 158, 11)),
        ("Voice (300-3.2kHz)", telemetry.get("e_voice", 0.0), (6, 182, 212)),
        ("Chime (550-1.2kHz)", telemetry.get("e_chime", 0.0), (52, 211, 153)),
        ("Siren (2.4-3.8kHz)", telemetry.get("e_siren", 0.0), (239, 68, 68)),
    ]

    by = meter_y + 45
    for name, val, col in bands:
        cv2.putText(frame, name, (meter_x, by), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (203, 213, 225), 1, cv2.LINE_AA)
        bw = int(180 * min(1.0, val * 1.8))
        cv2.rectangle(frame, (meter_x + 160, by - 11), (meter_x + 160 + 180, by + 1), (30, 41, 59), -1)
        if bw > 0:
            cv2.rectangle(frame, (meter_x + 160, by - 11), (meter_x + 160 + bw, by + 1), col, -1)
        by += 26

    # 5. Recent Alerts Timeline Panel (Center Right)
    panel_x = max(meter_x + meter_w + 30, w - 380)
    panel_w = w - panel_x - 20
    panel_h = 240
    panel_y = 105

    overlay = frame.copy()
    cv2.rectangle(overlay, (panel_x, panel_y), (panel_x + panel_w, panel_y + panel_h), (15, 23, 42), -1)
    cv2.addWeighted(overlay, 0.85, frame, 0.15, 0, frame)
    cv2.rectangle(frame, (panel_x, panel_y), (panel_x + panel_w, panel_y + panel_h), (51, 65, 85), 1)

    cv2.putText(
        frame,
        "RECENT SOUND EVENTS",
        (panel_x + 14, panel_y + 25),
        cv2.FONT_HERSHEY_DUPLEX,
        0.52,
        (255, 255, 255),
        1,
        cv2.LINE_AA,
    )

    if not recent_events:
        cv2.putText(
            frame,
            "No events detected yet. Monitoring...",
            (panel_x + 14, panel_y + 60),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.42,
            (148, 163, 184),
            1,
            cv2.LINE_AA,
        )
    else:
        ey = panel_y + 55
        for ev in reversed(recent_events[-5:]):
            mins_ago = int(time.time() - ev.timestamp)
            time_str = f"{mins_ago}s ago" if mins_ago < 60 else f"{mins_ago//60}m ago"
            txt = f"{ev.icon} {ev.title[:22]} ({time_str})"
            cv2.putText(frame, txt, (panel_x + 14, ey), cv2.FONT_HERSHEY_SIMPLEX, 0.42, ev.color_bgr, 1, cv2.LINE_AA)
            ey += 35

    # 6. Bottom Keycaps Hotkey Bar
    cv2.rectangle(frame, (0, h - 45), (w, h), (10, 15, 30), -1)
    hotkeys = (
        "[T] Test Alert   "
        "[S] Toggle Sensitivity   "
        "[D] Toggle Desktop Notifications   "
        "[Q] Quit"
    )
    cv2.putText(
        frame,
        hotkeys,
        (20, h - 16),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.46,
        (203, 213, 225),
        1,
        cv2.LINE_AA,
    )

    return frame

"""Floating on-top visual strobe alert popup window for Deaf & Hard of Hearing individuals.

Provides an unmissable high-contrast visual flash that appears on top of all windows
when an environmental acoustic event (doorbell, knock, siren, calling) triggers in the background.
"""

import argparse
import os
import sys
import threading
import time
from typing import Optional


def show_alert_popup(
    icon: str = "🔔",
    title: str = "DOORBELL DETECTED",
    description: str = "Harmonic chime at front door",
    color_hex: str = "#34d399",
    duration_seconds: float = 4.5,
    strobe_count: int = 4,
) -> None:
    """Display an on-top floating visual strobe notification banner."""
    # Ensure DISPLAY or WAYLAND_DISPLAY is present on Linux
    if os.name != "nt" and not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return

    try:
        import tkinter as tk
    except Exception:
        return

    try:
        root = tk.Tk()
        root.title("Signora Alert")

        # Make window topmost and frameless
        root.attributes("-topmost", True)
        root.overrideredirect(True)

        # Dimensions & position (Top Center)
        w, h = 580, 130
        sw = root.winfo_screenwidth()
        x = max(0, (sw - w) // 2)
        y = 36  # Floating near top edge of display
        root.geometry(f"{w}x{h}+{x}+{y}")

        # Dark sleek background
        bg_dark = "#090d16"
        card_bg = "#0f172a"
        text_light = "#f8fafc"
        dim_text = "#94a3b8"

        root.configure(bg=color_hex)

        container = tk.Frame(root, bg=card_bg, padx=16, pady=12)
        container.pack(fill="both", expand=True, padx=4, pady=4)

        # Left: Large Icon Badge
        icon_label = tk.Label(
            container,
            text=icon,
            font=("Segoe UI Emoji", 42) if os.name == "nt" else ("Noto Color Emoji", 38),
            bg=card_bg,
            fg=text_light,
        )
        icon_label.pack(side="left", padx=(8, 16))

        # Right: Title & Description
        text_frame = tk.Frame(container, bg=card_bg)
        text_frame.pack(side="left", fill="both", expand=True, pady=4)

        title_label = tk.Label(
            text_frame,
            text=title,
            font=("Helvetica", 14, "bold"),
            bg=card_bg,
            fg=color_hex,
            anchor="w",
        )
        title_label.pack(fill="x")

        desc_label = tk.Label(
            text_frame,
            text=description,
            font=("Helvetica", 10),
            bg=card_bg,
            fg=dim_text,
            anchor="w",
        )
        desc_label.pack(fill="x", pady=(2, 0))

        hint_label = tk.Label(
            text_frame,
            text="SIGNORA Alert • Click anywhere to dismiss",
            font=("Helvetica", 8),
            bg=card_bg,
            fg="#475569",
            anchor="w",
        )
        hint_label.pack(fill="x", pady=(6, 0))

        # Dismiss on click
        def dismiss(event=None):
            try:
                root.destroy()
            except Exception:
                pass

        root.bind("<Button-1>", dismiss)
        container.bind("<Button-1>", dismiss)
        icon_label.bind("<Button-1>", dismiss)
        title_label.bind("<Button-1>", dismiss)
        desc_label.bind("<Button-1>", dismiss)

        # Visual Strobe Animation: pulse outer border between color_hex and white
        def strobe(step=0):
            if step < strobe_count * 2:
                cur_color = "#ffffff" if (step % 2 == 0) else color_hex
                try:
                    root.configure(bg=cur_color)
                    root.after(160, lambda: strobe(step + 1))
                except Exception:
                    pass
            else:
                try:
                    root.configure(bg=color_hex)
                except Exception:
                    pass

        strobe()

        # Auto-dismiss after duration_seconds
        root.after(int(duration_seconds * 1000), dismiss)

        root.mainloop()

    except Exception:
        pass


def spawn_alert_popup_async(
    icon: str,
    title: str,
    description: str,
    color_hex: str = "#34d399",
    duration_seconds: float = 4.5,
) -> None:
    """Spawn alert popup in a separate daemon thread."""
    t = threading.Thread(
        target=show_alert_popup,
        args=(icon, title, description, color_hex, duration_seconds),
        daemon=True,
    )
    t.start()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Signora Alert Visual Popup")
    parser.add_argument("--icon", default="🔔")
    parser.add_argument("--title", default="DOORBELL CHIME DETECTED")
    parser.add_argument("--desc", default="Harmonic chime sound at the door")
    parser.add_argument("--color", default="#34d399")
    parser.add_argument("--duration", type=float, default=4.0)
    args = parser.parse_args()

    show_alert_popup(
        icon=args.icon,
        title=args.title,
        description=args.desc,
        color_hex=args.color,
        duration_seconds=args.duration,
    )

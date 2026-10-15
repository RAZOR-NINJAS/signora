"""Full-screen visual Emergency Card viewer for desktop and terminal."""

import argparse
import os
import sys
import threading
from typing import Optional

from signora.emergency.presets import EMERGENCY_PRESETS, EmergencyPreset
from signora.emergency.profile import EmergencyProfile


def render_terminal_card(preset: EmergencyPreset, profile: EmergencyProfile) -> None:
    """Render full-screen emergency card to terminal using ANSI formatting."""
    border = "=" * 70
    sub_border = "-" * 70
    print("\n" + border)
    print(f"  {preset.icon}  *** {preset.title} ***  {preset.icon}".center(70))
    print(border)
    print(f"\n  HEADLINE: {preset.headline}\n")
    print(f"  DETAILS : {preset.body}\n")
    print(sub_border)
    print("  USER PROFILE & EMERGENCY CONTACT DETAILS:")
    print(sub_border)
    print(f"  • Name                : {profile.name}")
    print(f"  • Communication Note  : {profile.communication_note}")
    print(f"  • Blood Group         : {profile.blood_group}")
    print(f"  • Medical Conditions  : {profile.medical_conditions}")
    print(f"  • Allergies           : {profile.allergies}")
    print(f"  • Primary Contact     : {profile.primary_contact_name} ({profile.primary_contact_phone})")
    if profile.secondary_contact:
        print(f"  • Secondary Contact   : {profile.secondary_contact}")
    print(border + "\n")


def show_gui_emergency_card(preset_id: str = "medical", speak_on_open: bool = False) -> None:
    """Launch full-screen Tkinter emergency card window."""
    try:
        import tkinter as tk
        from tkinter import ttk
    except ImportError:
        preset = EMERGENCY_PRESETS.get(preset_id, EMERGENCY_PRESETS["medical"])
        profile = EmergencyProfile.load()
        render_terminal_card(preset, profile)
        return

    preset = EMERGENCY_PRESETS.get(preset_id, EMERGENCY_PRESETS["medical"])
    profile = EmergencyProfile.load()

    root = tk.Tk()
    root.title(f"SIGNORA EMERGENCY: {preset.title}")
    root.attributes("-topmost", True)
    root.configure(bg="#09090b")

    # Full screen or large dialog
    screen_w = root.winfo_screenwidth()
    screen_h = root.winfo_screenheight()
    win_w = min(880, int(screen_w * 0.9))
    win_h = min(720, int(screen_h * 0.88))
    x = (screen_w - win_w) // 2
    y = (screen_h - win_h) // 2
    root.geometry(f"{win_w}x{win_h}+{x}+{y}")

    # Outer border card
    container = tk.Frame(root, bg=preset.color_hex, padx=6, pady=6)
    container.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)

    inner = tk.Frame(container, bg="#09090b", padx=24, pady=20)
    inner.pack(fill=tk.BOTH, expand=True)

    # Top banner
    header = tk.Label(
        inner,
        text=f"{preset.icon}  {preset.title}  {preset.icon}",
        font=("Helvetica", 22, "bold"),
        fg="#ffffff",
        bg=preset.color_hex,
        pady=10,
    )
    header.pack(fill=tk.X, pady=(0, 16))

    # Headline
    headline_lbl = tk.Label(
        inner,
        text=preset.headline,
        font=("Helvetica", 18, "bold"),
        fg="#facc15",
        bg="#09090b",
        wraplength=win_w - 80,
        justify=tk.CENTER,
    )
    headline_lbl.pack(pady=8)

    # Body
    body_lbl = tk.Label(
        inner,
        text=preset.body,
        font=("Helvetica", 14),
        fg="#e4e4e7",
        bg="#09090b",
        wraplength=win_w - 80,
        justify=tk.CENTER,
    )
    body_lbl.pack(pady=(4, 16))

    # Profile Section Frame
    prof_box = tk.LabelFrame(
        inner,
        text="  EMERGENCY PROFILE & CONTACT DETAILS  ",
        font=("Helvetica", 12, "bold"),
        fg="#93c5fd",
        bg="#18181b",
        padx=16,
        pady=12,
    )
    prof_box.pack(fill=tk.X, pady=8)

    fields = [
        ("Full Name", profile.name),
        ("Communication", profile.communication_note),
        ("Blood Group", profile.blood_group),
        ("Medical Conditions", profile.medical_conditions),
        ("Allergies", profile.allergies),
        ("Emergency Contact", f"{profile.primary_contact_name} ({profile.primary_contact_phone})"),
    ]

    for idx, (label, val) in enumerate(fields):
        row = tk.Frame(prof_box, bg="#18181b")
        row.pack(fill=tk.X, pady=2)
        tk.Label(
            row, text=f"{label}:", font=("Helvetica", 11, "bold"), fg="#a1a1aa", bg="#18181b", width=18, anchor="w"
        ).pack(side=tk.LEFT)
        tk.Label(
            row, text=val, font=("Helvetica", 11), fg="#ffffff", bg="#18181b", anchor="w"
        ).pack(side=tk.LEFT, fill=tk.X, expand=True)

    # Actions: Speak aloud & Preset Switcher Buttons
    btn_frame = tk.Frame(inner, bg="#09090b")
    btn_frame.pack(fill=tk.X, pady=(16, 0))

    def speak_message():
        try:
            from signora.tts.piper import speak_async
            speak_async(preset.tts_broadcast)
        except Exception:
            pass

    if speak_on_open:
        root.after(300, speak_message)

    speak_btn = tk.Button(
        btn_frame,
        text="🔊 Speak Aloud to Bystanders (TTS)",
        font=("Helvetica", 13, "bold"),
        bg="#22c55e",
        fg="#ffffff",
        activebackground="#16a34a",
        command=speak_message,
        padx=16,
        pady=8,
    )
    speak_btn.pack(side=tk.LEFT, padx=6)

    def switch_preset(p_id: str):
        root.destroy()
        show_gui_emergency_card(p_id)

    # Preset shortcut buttons
    for pid, p in EMERGENCY_PRESETS.items():
        if pid == preset_id:
            continue
        b = tk.Button(
            btn_frame,
            text=f"{p.icon} {p.id.capitalize()}",
            font=("Helvetica", 10, "bold"),
            bg="#27272a",
            fg="#e4e4e7",
            command=lambda x=pid: switch_preset(x),
            padx=8,
            pady=8,
        )
        b.pack(side=tk.LEFT, padx=3)

    dismiss_btn = tk.Button(
        btn_frame,
        text="✕ Dismiss [Esc]",
        font=("Helvetica", 11),
        bg="#3f3f46",
        fg="#ffffff",
        command=root.destroy,
        padx=12,
        pady=8,
    )
    dismiss_btn.pack(side=tk.RIGHT, padx=6)

    root.bind("<Escape>", lambda e: root.destroy())
    root.mainloop()


def spawn_emergency_card_detached(preset_id: str = "medical", speak: bool = False) -> None:
    """Launch emergency card in isolated detached process to avoid main loop blocking."""
    import subprocess
    cmd = [
        sys.executable,
        "-m", "signora.emergency.viewer",
        "--preset", preset_id,
    ]
    if speak:
        cmd.append("--speak")

    kwargs = {}
    if os.name != "nt":
        kwargs["start_new_session"] = True
    subprocess.Popen(cmd, **kwargs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Signora Emergency Card Viewer")
    parser.add_argument("--preset", default="medical", choices=list(EMERGENCY_PRESETS.keys()))
    parser.add_argument("--speak", action="store_true", help="Speak announcement on open")
    parser.add_argument("--headless", action="store_true", help="Render to terminal only")
    args = parser.parse_args()

    if args.headless:
        preset = EMERGENCY_PRESETS.get(args.preset, EMERGENCY_PRESETS["medical"])
        profile = EmergencyProfile.load()
        render_terminal_card(preset, profile)
        if args.speak:
            from signora.tts.piper import speak_async
            speak_async(preset.tts_broadcast)
    else:
        show_gui_emergency_card(args.preset, speak_on_open=args.speak)

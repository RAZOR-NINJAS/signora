"""Interactive full-screen communication card viewer and editor."""

import argparse
import os
import sys
from typing import Optional

from signora.public_help.categories import HelpCard, HelpCardManager


def render_terminal_help_card(card: HelpCard) -> None:
    """Print large high-contrast communication card to terminal."""
    border = "=" * 72
    print("\n" + border)
    print(f"  {card.icon}  [{card.category.upper()}]  {card.title}  {card.icon}".center(72))
    print(border)
    print(f"\n  \"{card.message}\"\n")
    print("-" * 72)
    print("  [Tip: Show this screen directly to hospital staff, station clerks, or officers]")
    print(border + "\n")


def show_gui_help_board(initial_category: str = "Hospital") -> None:
    """Launch interactive Public Help Communication Board in Tkinter."""
    try:
        import tkinter as tk
        from tkinter import messagebox, simpledialog, ttk
    except ImportError:
        manager = HelpCardManager()
        cards = manager.get_cards_for_category(initial_category)
        if cards:
            render_terminal_help_card(cards[0])
        return

    manager = HelpCardManager()
    categories = manager.get_categories()

    root = tk.Tk()
    root.title("SIGNORA Public Help: Assistive Communication Cards")
    root.attributes("-topmost", True)
    root.configure(bg="#0f172a")

    screen_w = root.winfo_screenwidth()
    screen_h = root.winfo_screenheight()
    win_w = min(920, int(screen_w * 0.92))
    win_h = min(740, int(screen_h * 0.88))
    x = (screen_w - win_w) // 2
    y = (screen_h - win_h) // 2
    root.geometry(f"{win_w}x{win_h}+{x}+{y}")

    active_cat = tk.StringVar(value=initial_category if initial_category in categories else categories[0])
    selected_card_idx = tk.IntVar(value=0)

    # Top Category Selector Bar
    cat_frame = tk.Frame(root, bg="#1e293b", pady=10, padx=12)
    cat_frame.pack(fill=tk.X)

    tk.Label(
        cat_frame,
        text="CATEGORIES:",
        font=("Helvetica", 11, "bold"),
        fg="#94a3b8",
        bg="#1e293b",
    ).pack(side=tk.LEFT, padx=(0, 10))

    cat_buttons = {}

    def update_cards_list():
        # Clear card listbox
        card_listbox.delete(0, tk.END)
        current_cards = manager.get_cards_for_category(active_cat.get())
        for c in current_cards:
            card_listbox.insert(tk.END, f"  {c.icon}  {c.title}")
        if current_cards:
            card_listbox.select_set(0)
            show_card_details(0)

    def select_category(cat: str):
        active_cat.set(cat)
        for c, b in cat_buttons.items():
            if c == cat:
                b.configure(bg="#3b82f6", fg="#ffffff")
            else:
                b.configure(bg="#334155", fg="#cbd5e1")
        update_cards_list()

    for cat in categories:
        b = tk.Button(
            cat_frame,
            text=cat,
            font=("Helvetica", 11, "bold"),
            bg="#3b82f6" if cat == active_cat.get() else "#334155",
            fg="#ffffff" if cat == active_cat.get() else "#cbd5e1",
            command=lambda c=cat: select_category(c),
            padx=12,
            pady=6,
        )
        b.pack(side=tk.LEFT, padx=4)
        cat_buttons[cat] = b

    # Main split container: Left (Cards list) | Right (Large Display Card)
    main_split = tk.Frame(root, bg="#0f172a", padx=12, pady=12)
    main_split.pack(fill=tk.BOTH, expand=True)

    # Left Panel
    left_panel = tk.Frame(main_split, bg="#1e293b", width=280, padx=8, pady=8)
    left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))

    tk.Label(
        left_panel,
        text="Select Message Card:",
        font=("Helvetica", 11, "bold"),
        fg="#94a3b8",
        bg="#1e293b",
    ).pack(anchor="w", pady=(0, 6))

    card_listbox = tk.Listbox(
        left_panel,
        font=("Helvetica", 11),
        bg="#0f172a",
        fg="#f1f5f9",
        selectbackground="#3b82f6",
        selectforeground="#ffffff",
        activestyle="none",
        relief=tk.FLAT,
        highlightthickness=0,
    )
    card_listbox.pack(fill=tk.BOTH, expand=True)

    # Right Panel: Large Display Card
    right_panel = tk.Frame(main_split, bg="#0284c7", padx=4, pady=4)
    right_panel.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)

    card_card = tk.Frame(right_panel, bg="#0f172a", padx=20, pady=20)
    card_card.pack(fill=tk.BOTH, expand=True)

    card_title_lbl = tk.Label(
        card_card,
        text="Title",
        font=("Helvetica", 18, "bold"),
        fg="#38bdf8",
        bg="#0f172a",
        wraplength=win_w - 360,
    )
    card_title_lbl.pack(pady=(10, 16))

    # Large text message box (editable directly by user!)
    card_text = tk.Text(
        card_card,
        font=("Helvetica", 20, "bold"),
        fg="#ffffff",
        bg="#1e293b",
        wrap=tk.WORD,
        padx=18,
        pady=18,
        relief=tk.FLAT,
        highlightbackground="#38bdf8",
        highlightthickness=2,
        height=7,
    )
    card_text.pack(fill=tk.BOTH, expand=True, pady=(0, 16))

    def show_card_details(idx: int):
        current_cards = manager.get_cards_for_category(active_cat.get())
        if 0 <= idx < len(current_cards):
            selected_card_idx.set(idx)
            c = current_cards[idx]
            card_title_lbl.config(text=f"{c.icon}  {c.title}")
            card_text.delete("1.0", tk.END)
            card_text.insert(tk.END, c.message)

    def on_listbox_select(evt):
        sel = card_listbox.curselection()
        if sel:
            show_card_details(sel[0])

    card_listbox.bind("<<ListboxSelect>>", on_listbox_select)

    # Bottom Actions Bar
    actions_frame = tk.Frame(card_card, bg="#0f172a")
    actions_frame.pack(fill=tk.X)

    def speak_card():
        text_to_speak = card_text.get("1.0", tk.END).strip()
        if text_to_speak:
            try:
                from signora.tts.piper import speak_async
                speak_async(text_to_speak)
            except Exception:
                pass

    speak_btn = tk.Button(
        actions_frame,
        text="🔊 Speak Message Aloud (TTS)",
        font=("Helvetica", 12, "bold"),
        bg="#22c55e",
        fg="#ffffff",
        activebackground="#16a34a",
        command=speak_card,
        padx=14,
        pady=8,
    )
    speak_btn.pack(side=tk.LEFT, padx=6)

    def save_custom_changes():
        current_cards = manager.get_cards_for_category(active_cat.get())
        idx = selected_card_idx.get()
        if 0 <= idx < len(current_cards):
            current_cards[idx].message = card_text.get("1.0", tk.END).strip()
            manager.save()
            messagebox.showinfo("Saved", "Card message updated and saved successfully!")

    save_btn = tk.Button(
        actions_frame,
        text="💾 Save Edits",
        font=("Helvetica", 11),
        bg="#475569",
        fg="#ffffff",
        command=save_custom_changes,
        padx=12,
        pady=8,
    )
    save_btn.pack(side=tk.LEFT, padx=6)

    dismiss_btn = tk.Button(
        actions_frame,
        text="✕ Close [Esc]",
        font=("Helvetica", 11),
        bg="#334155",
        fg="#ffffff",
        command=root.destroy,
        padx=12,
        pady=8,
    )
    dismiss_btn.pack(side=tk.RIGHT, padx=6)

    root.bind("<Escape>", lambda e: root.destroy())
    update_cards_list()
    root.mainloop()


def spawn_help_board_detached(category: str = "Hospital") -> None:
    """Launch public help card board in isolated detached process."""
    import subprocess
    cmd = [
        sys.executable,
        "-m", "signora.public_help.viewer",
        "--category", category,
    ]
    kwargs = {}
    if os.name != "nt":
        kwargs["start_new_session"] = True
    subprocess.Popen(cmd, **kwargs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Signora Public Help Cards")
    parser.add_argument("--category", default="Hospital", help="Initial category")
    parser.add_argument("--headless", action="store_true", help="Print card to terminal only")
    args = parser.parse_args()

    if args.headless:
        mgr = HelpCardManager()
        cards = mgr.get_cards_for_category(args.category)
        if cards:
            render_terminal_help_card(cards[0])
    else:
        show_gui_help_board(args.category)

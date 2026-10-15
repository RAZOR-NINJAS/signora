"""Scrollable live captions history viewer for SIGNORA Catch-Up."""

import argparse
import os
import sys
import time
from pathlib import Path

from signora.catchup.history import CaptionHistory
from signora.catchup.stt import CatchUpEngine


def run_terminal_catchup(engine: CatchUpEngine, duration_seconds: float = 12.0) -> None:
    """Stream live captions to terminal with scrollable review history."""
    print("\n" + "=" * 70)
    print("  📜  SIGNORA CATCH-UP: Live Spoken Captions & Scrollable History  📜".center(70))
    print("=" * 70)
    print("  Listening for spoken information... (Ctrl+C to finish & view summary)")
    print("-" * 70)

    def on_caption(entry):
        star = " ★" if entry.is_bookmarked else ""
        print(f"  [{entry.time_str}] {entry.speaker}: {entry.text}{star}")

    engine.subscribe(on_caption)
    engine.start()

    start_t = time.time()
    try:
        while time.time() - start_t < duration_seconds:
            time.sleep(0.5)
    except KeyboardInterrupt:
        pass
    finally:
        engine.stop()

    print("-" * 70)
    print(f"  ✓ Total Spoken Captions Recorded: {len(engine.history.entries)}")
    print("=" * 70 + "\n")


def show_gui_catchup_window(use_fake_mic: bool = False) -> None:
    """Launch full Tkinter scrollable live caption history window."""
    try:
        import tkinter as tk
        from tkinter import filedialog, messagebox, ttk
    except ImportError:
        engine = CatchUpEngine(use_fake_mic=True)
        run_terminal_catchup(engine, duration_seconds=10.0)
        return

    history = CaptionHistory()
    engine = CatchUpEngine(history=history, use_fake_mic=use_fake_mic)

    root = tk.Tk()
    root.title("SIGNORA Catch-Up: Live Captions & Spoken History Review")
    root.attributes("-topmost", True)
    root.configure(bg="#09090b")

    screen_w = root.winfo_screenwidth()
    screen_h = root.winfo_screenheight()
    win_w = min(940, int(screen_w * 0.92))
    win_h = min(720, int(screen_h * 0.88))
    x = (screen_w - win_w) // 2
    y = (screen_h - win_h) // 2
    root.geometry(f"{win_w}x{win_h}+{x}+{y}")

    # Top Header
    header = tk.Frame(root, bg="#18181b", padx=16, pady=12)
    header.pack(fill=tk.X)

    tk.Label(
        header,
        text="📜 SIGNORA CATCH-UP",
        font=("Helvetica", 16, "bold"),
        fg="#38bdf8",
        bg="#18181b",
    ).pack(side=tk.LEFT)

    status_var = tk.StringVar(value="● LIVE CAPTIONS ACTIVE")
    status_lbl = tk.Label(
        header,
        textvariable=status_var,
        font=("Helvetica", 11, "bold"),
        fg="#22c55e",
        bg="#18181b",
    )
    status_lbl.pack(side=tk.LEFT, padx=16)

    # Search Bar
    search_frame = tk.Frame(root, bg="#09090b", padx=16, pady=6)
    search_frame.pack(fill=tk.X)

    tk.Label(
        search_frame, text="🔍 Search Transcript:", font=("Helvetica", 10, "bold"), fg="#a1a1aa", bg="#09090b"
    ).pack(side=tk.LEFT, padx=(0, 8))

    search_var = tk.StringVar()
    search_entry = tk.Entry(
        search_frame,
        textvariable=search_var,
        font=("Helvetica", 11),
        bg="#18181b",
        fg="#ffffff",
        relief=tk.FLAT,
        highlightbackground="#3f3f46",
        highlightthickness=1,
    )
    search_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)

    # Scrollable Captions Text Area
    text_frame = tk.Frame(root, bg="#09090b", padx=16, pady=8)
    text_frame.pack(fill=tk.BOTH, expand=True)

    scrollbar = tk.Scrollbar(text_frame)
    scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

    caption_text = tk.Text(
        text_frame,
        font=("Helvetica", 13),
        fg="#f4f4f5",
        bg="#18181b",
        wrap=tk.WORD,
        padx=14,
        pady=14,
        yscrollcommand=scrollbar.set,
        relief=tk.FLAT,
    )
    caption_text.pack(fill=tk.BOTH, expand=True)
    scrollbar.config(command=caption_text.yview)

    # Configure text tags for styling
    caption_text.tag_config("time", foreground="#71717a", font=("Helvetica", 10))
    caption_text.tag_config("speaker", foreground="#38bdf8", font=("Helvetica", 12, "bold"))
    caption_text.tag_config("body", foreground="#ffffff", font=("Helvetica", 13))
    caption_text.tag_config("bookmark", foreground="#facc15", font=("Helvetica", 11, "bold"))

    def render_captions(filter_query: str = ""):
        caption_text.delete("1.0", tk.END)
        entries = history.search(filter_query) if filter_query else history.entries
        for e in entries:
            star = " ★ [BOOKMARKED]" if e.is_bookmarked else ""
            caption_text.insert(tk.END, f"[{e.time_str}] ", "time")
            caption_text.insert(tk.END, f"{e.speaker}: ", "speaker")
            caption_text.insert(tk.END, f"{e.text} ", "body")
            if star:
                caption_text.insert(tk.END, f"{star}\n\n", "bookmark")
            else:
                caption_text.insert(tk.END, "\n\n")
        caption_text.see(tk.END)

    def on_new_caption(entry):
        root.after(0, lambda: render_captions(search_var.get()))

    engine.subscribe(on_new_caption)

    def on_search_change(*args):
        render_captions(search_var.get())

    search_var.trace_add("write", on_search_change)

    # Bottom Actions Bar
    actions = tk.Frame(root, bg="#09090b", padx=16, pady=12)
    actions.pack(fill=tk.X)

    def toggle_pause():
        if engine.paused:
            engine.resume()
            status_var.set("● LIVE CAPTIONS ACTIVE")
            status_lbl.config(fg="#22c55e")
            pause_btn.config(text="⏸ Pause Live Stream")
        else:
            engine.pause()
            status_var.set("⏸ PAUSED (REVIEWING HISTORY)")
            status_lbl.config(fg="#facc15")
            pause_btn.config(text="▶ Resume Live Stream")

    pause_btn = tk.Button(
        actions,
        text="⏸ Pause Live Stream",
        font=("Helvetica", 11, "bold"),
        bg="#3f3f46",
        fg="#ffffff",
        command=toggle_pause,
        padx=12,
        pady=6,
    )
    pause_btn.pack(side=tk.LEFT, padx=4)

    def bookmark_last():
        if history.entries:
            history.toggle_bookmark(len(history.entries) - 1)
            render_captions(search_var.get())

    book_btn = tk.Button(
        actions,
        text="★ Bookmark Recent Note",
        font=("Helvetica", 11, "bold"),
        bg="#ca8a04",
        fg="#ffffff",
        command=bookmark_last,
        padx=12,
        pady=6,
    )
    book_btn.pack(side=tk.LEFT, padx=4)

    def export_transcript():
        filename = filedialog.asksaveasfilename(
            defaultextension=".txt",
            filetypes=[("Text Transcript", "*.txt"), ("Subtitles", "*.srt"), ("JSON Data", "*.json")],
            initialfile="catchup_transcript.txt",
        )
        if filename:
            p = Path(filename)
            if p.suffix == ".srt":
                history.export_srt(p)
            elif p.suffix == ".json":
                history.export_json(p)
            else:
                history.export_txt(p)
            messagebox.showinfo("Exported", f"Successfully saved {len(history.entries)} captions to:\n{filename}")

    export_btn = tk.Button(
        actions,
        text="💾 Export Transcript",
        font=("Helvetica", 11),
        bg="#2563eb",
        fg="#ffffff",
        command=export_transcript,
        padx=12,
        pady=6,
    )
    export_btn.pack(side=tk.LEFT, padx=4)

    dismiss_btn = tk.Button(
        actions,
        text="✕ Close [Esc]",
        font=("Helvetica", 11),
        bg="#27272a",
        fg="#ffffff",
        command=root.destroy,
        padx=12,
        pady=6,
    )
    dismiss_btn.pack(side=tk.RIGHT, padx=4)

    root.bind("<Escape>", lambda e: root.destroy())

    # Pre-populate sample entries for initial review
    history.add("Welcome to the lecture. Please check your audio and display settings.", "Host", 0.98)
    history.add("Today's topic covers accessibility tools and live scrollable captions.", "Instructor", 0.96)
    history.add("Students may review any spoken points using the search bar above.", "Instructor", 0.95)
    render_captions()

    engine.start()

    def on_close():
        engine.stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()


def spawn_catchup_detached(use_fake_mic: bool = False) -> None:
    """Launch Catch-Up viewer in isolated detached process."""
    import subprocess
    cmd = [sys.executable, "-m", "signora.catchup.viewer"]
    if use_fake_mic:
        cmd.append("--fake-mic")
    kwargs = {}
    if os.name != "nt":
        kwargs["start_new_session"] = True
    subprocess.Popen(cmd, **kwargs)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SIGNORA Catch-Up Live Captions & Review")
    parser.add_argument("--fake-mic", action="store_true", help="Simulate spoken classroom audio")
    parser.add_argument("--headless", action="store_true", help="Run terminal stream only")
    args = parser.parse_args()

    if args.headless:
        eng = CatchUpEngine(use_fake_mic=True)
        run_terminal_catchup(eng, duration_seconds=6.0)
    else:
        show_gui_catchup_window(use_fake_mic=args.fake_mic)

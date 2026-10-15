"""Futuristic introduction sequence & 'Connecting Hands' startup animation for SIGNORA.

Inspired by the iconic connecting hands motif, reimagined for the future of accessibility:
Human Gesture meeting Artificial Intelligence -- 'Where hands find their voice'.
"""

import os
import sys
import threading
import time
from typing import Optional

import numpy as np
from rich import box
from rich.console import Console
from rich.panel import Panel
from rich.text import Text

try:
    import sounddevice as sd
    SOUNDDEVICE_AVAILABLE = True
except Exception:
    sd = None
    SOUNDDEVICE_AVAILABLE = False


INTRO_TEXT = (
    "Greetings, innovators!\n"
    "I am signora\n"
    "i see gestures, recognise patterns, and transform supported signs into words. "
    "I was created with one purpose: to make communication more accessible.\n\n"
    "I am SIGNORA, where hands find there voice."
)

INTRO_SPEECH = (
    "Greetings, innovators! I am Signora. "
    "I see gestures, recognise patterns, and transform supported signs into words. "
    "I was created with one purpose: to make communication more accessible. "
    "I am SIGNORA, where hands find their voice."
)

# ASCII Hand Art (Angled Human Gesture Hand & AI Neural Hand)
LEFT_HAND = [
    r"                     _.-/`) ",
    r"                   // / / ) ",
    r"                .=// / / / )",
    r"               //`/ / / / / ",
    r"              // /     `    ",
    r"             ||         /   ",
    r"              \       /     ",
    r"               `------'     ",
]

RIGHT_HAND = [
    r" (`\-._                     ",
    r" ( \ \ \\                   ",
    r"( \ \ \ \\=.                ",
    r" \ \ \ \ \`\\               ",
    r"    '     \ \\              ",
    r"   \         ||             ",
    r"    \       //              ",
    r"     `------'               ",
]


def play_chime_async() -> None:
    """Synthesize and play an ethereal, rising sci-fi harmonic chime chord in background."""
    if not SOUNDDEVICE_AVAILABLE or os.environ.get("SIGNORA_NO_AUDIO", "").lower() in ("1", "true"):
        return

    def _worker():
        try:
            sr = 22050
            # Rising harmonic chord: F#5 (740 Hz) -> A5 (880 Hz) -> C#6 (1108 Hz) -> E6 (1318 Hz)
            t1 = np.linspace(0, 0.22, int(sr * 0.22), endpoint=False, dtype=np.float32)
            t2 = np.linspace(0, 0.22, int(sr * 0.22), endpoint=False, dtype=np.float32)
            t3 = np.linspace(0, 0.35, int(sr * 0.35), endpoint=False, dtype=np.float32)
            c1 = (0.18 * np.sin(2 * np.pi * 740 * t1) * np.exp(-t1 * 7)).astype(np.float32)
            c2 = (0.22 * np.sin(2 * np.pi * 880 * t2) * np.exp(-t2 * 6)).astype(np.float32)
            c3 = (0.26 * np.sin(2 * np.pi * 1318 * t3) * np.exp(-t3 * 4)).astype(np.float32)
            chime = np.concatenate([c1, c2, c3])
            sd.play(chime, samplerate=sr)
            sd.wait()
        except Exception:
            pass

    threading.Thread(target=_worker, daemon=True, name="SignoraChime").start()


def play_intro_voice_async() -> None:
    """Asynchronously broadcast futuristic spoken introduction via Piper neural voice."""
    if os.environ.get("SIGNORA_NO_TTS", "").lower() in ("1", "true", "yes"):
        return
    try:
        from signora.tts.piper import get_tts
        tts = get_tts()
        if tts and tts.is_ready:
            tts.speak_async(INTRO_SPEECH)
    except Exception:
        pass


def animate_connecting_hands(console: Optional[Console] = None) -> None:
    """Render cinematic 'Connecting Hands' startup animation."""
    c = console or Console()
    is_interactive = sys.stdin.isatty() and os.environ.get("SIGNORA_NO_ANIMATION", "") != "1"

    gaps = [24, 16, 10, 4, 1, 0] if is_interactive else [0]
    frame_delay = 0.07

    for g in gaps:
        if is_interactive:
            c.clear()
        c.print()
        header_line = (
            "[bold cyan]       ✦ HUMAN GESTURE[/bold cyan]"
            + " " * (g + 16)
            + "[bold magenta]SIGNORA AI ✦[/bold magenta]"
        )
        c.print(header_line)

        for l, r in zip(LEFT_HAND, RIGHT_HAND):
            if g == 0 and "_.-/" in l:
                c.print(f"[bold cyan]{l}[/bold cyan][bold yellow] ⚡ [/bold yellow][bold magenta]{r}[/bold magenta]")
            else:
                spacing = " " * g
                c.print(f"[bold cyan]{l}[/bold cyan]{spacing}[bold magenta]{r}[/bold magenta]")

        if is_interactive and g > 0:
            time.sleep(frame_delay)

    # Final connection spark burst & audio chime
    play_chime_async()
    c.print(
        "\n[bold yellow]              ★ ✦ ✧ ⚡ CONNECTED: WHERE HANDS FIND THEIR VOICE ⚡ ✧ ✦ ★[/bold yellow]\n"
    )
    if is_interactive:
        time.sleep(0.4)


def display_intro(
    console: Optional[Console] = None,
    speak: bool = True,
    animate: bool = True,
    wait: bool = False,
) -> None:
    """Render full futuristic introduction sequence."""
    if os.environ.get("SIGNORA_NO_INTRO", "").lower() in ("1", "true", "yes"):
        return

    c = console or Console()

    if animate:
        animate_connecting_hands(c)

    # Trigger audio voice introduction non-blockingly
    if speak:
        play_intro_voice_async()

    header_text = Text()
    header_text.append("⚡ NEURAL INITIATION SEQUENCE // SYSTEM READY ⚡\n\n", style="bold cyan")

    body_text = Text()
    body_text.append("Greetings, innovators!\n", style="bold white")
    body_text.append("I am signora\n", style="bold cyan")
    body_text.append(
        "i see gestures, recognise patterns, and transform supported signs into words. "
        "I was created with one purpose: to make communication more accessible.\n\n",
        style="white",
    )
    body_text.append("I am SIGNORA, where hands find there voice.", style="bold magenta")

    content = Text.assemble(header_text, body_text)

    panel = Panel(
        content,
        box=box.DOUBLE,
        border_style="cyan",
        title="[bold cyan]◈ SIGNORA ARTIFICIAL INTELLIGENCE ◈[/bold cyan]",
        title_align="center",
        subtitle="[bold dim magenta]◈ 100% OFFLINE • EDGE NEURAL PERCEPTION • ZERO LATENCY ◈[/bold dim magenta]",
        subtitle_align="center",
        padding=(1, 3),
    )

    c.print(panel)
    c.print()

    if wait and speak:
        try:
            from signora.tts.piper import get_tts
            tts = get_tts()
            if tts and tts.is_ready:
                time.sleep(0.5)
                while tts.is_speaking or not tts._queue.empty():
                    time.sleep(0.2)
                time.sleep(0.5)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    display_intro(speak=True, animate=True)
    try:
        from signora.tts.piper import get_tts
        tts = get_tts()
        if tts and tts.is_ready:
            time.sleep(0.5)
            while tts.is_speaking or not tts._queue.empty():
                time.sleep(0.2)
            time.sleep(0.5)
    except KeyboardInterrupt:
        pass

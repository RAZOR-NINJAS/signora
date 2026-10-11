"""Interactive Terminal User Interface (TUI) for Signora.

Lightweight, fast (<40ms startup), and flicker-free console navigation
powered by Rich. Cross-platform compatible with Linux and Windows console hosts.
"""

import sys

from rich.console import Console
from rich.panel import Panel
from rich.prompt import Confirm, Prompt
from rich.table import Table

from signora import __version__
from signora.doctor.checks import run_all_checks
from signora.models.manifest import get_default_models_dir, load_manifest, verify_all_models
from signora.platform_compat import get_platform_info, probe_cameras

ASCII_BANNER = r"""
  ███████╗██╗ ██████╗ ███╗   ██╗ ██████╗ ██████╗  █████╗
  ██╔════╝██║██╔════╝ ████╗  ██║██╔═══██╗██╔══██╗██╔══██╗
  ███████╗██║██║  ███╗██╔██╗ ██║██║   ██║██████╔╝███████║
  ╚════██║██║██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══██║
  ███████║██║╚██████╔╝██║ ╚████║╚██████╔╝██║  ██║██║  ██║
  ╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝
"""

TAGLINE = "Real-time sign language recognition. 100% local. Nothing leaves your machine."


class SignoraTUI:
    """Console interactive dashboard."""

    def __init__(self):
        self.console = Console()
        self.models_dir = get_default_models_dir()

    def clear(self) -> None:
        """Clear screen cross-platform."""
        self.console.clear()

    def print_header(self) -> None:
        """Render stylish banner and quick system metrics."""
        self.console.print(f"[bold cyan]{ASCII_BANNER}[/bold cyan]")
        self.console.print(f"[bold white]{TAGLINE}[/bold white]\n", justify="center")

        plat = get_platform_info()
        v = sys.version_info

        info_table = Table(box=None, show_header=False, pad_edge=False, padding=(0, 2))
        info_table.add_column("Key", style="bold dim cyan")
        info_table.add_column("Val", style="bold white")
        info_table.add_column("Key2", style="bold dim cyan")
        info_table.add_column("Val2", style="bold white")

        info_table.add_row(
            "Version:",
            f"v{__version__}",
            "Platform:",
            f"{plat['system']} ({plat['machine']})",
        )
        info_table.add_row(
            "Python:",
            f"{v.major}.{v.minor}.{v.micro}",
            "Backend:",
            f"{plat['camera_backend']}",
        )

        panel = Panel(
            info_table,
            border_style="cyan",
            title="[bold cyan]System Environment[/bold cyan]",
            title_align="left",
            padding=(0, 1),
        )
        self.console.print(panel)
        self.console.print()

    def show_main_menu(self) -> str:
        """Display primary selection table and prompt for choice."""
        table = Table(
            border_style="cyan",
            title="[bold cyan]Interactive Operations & Modes[/bold cyan]",
            title_align="left",
            header_style="bold cyan",
            expand=True,
        )
        table.add_column("Option", style="bold yellow", width=8, justify="center")
        table.add_column("Mode / Utility", style="bold white", width=28)
        table.add_column("Description", style="dim")
        table.add_column("Resource Cost", style="cyan", width=14, justify="right")

        table.add_row(
            "1",
            "ASL Fingerspelling",
            "Single-handed letters (A-Z + SPACE dwell) with HUD & Piper TTS",
            "Low (CPU ~8%)",
        )
        table.add_row(
            "2",
            "ISL Recognition",
            "Indian Sign Language dual-hand alphabet & 10 static words",
            "Low (CPU ~10%)",
        )
        table.add_row(
            "3",
            "ASL Dynamic Words",
            "25-word holistic motion trajectory recognition (DTW-kNN)",
            "Med (CPU ~18%)",
        )
        table.add_row(
            "4",
            "Shadow-Play Game",
            "Browser-based gesture-controlled Dino runner (http://localhost:8000/game)",
            "Low (CPU ~12%)",
        )
        table.add_row(
            "5",
            "Learning Kiosk",
            "Interactive sign practice, drills, and timed quizzes in browser",
            "Low (CPU ~12%)",
        )
        table.add_row(
            "6",
            "System Doctor",
            "Inspect 10 hardware, neural model, audio, and network checks",
            "Instant",
        )
        table.add_row(
            "7",
            "Model Asset Manager",
            "Inspect integrity or download MediaPipe & neural model weights",
            "I/O bound",
        )
        table.add_row(
            "8", "Probe Webcams", "Enumerate available video capture hardware devices", "Instant"
        )
        table.add_row("0", "Exit", "Close Signora dashboard", "—")

        self.console.print(table)
        self.console.print()

        return Prompt.ask(
            "[bold cyan]Select an option[/bold cyan]",
            choices=["1", "2", "3", "4", "5", "6", "7", "8", "0"],
            default="1",
            console=self.console,
        )

    def run_asl_flow(self) -> None:
        self.console.print(
            "\n[bold cyan]═══ Launching ASL Fingerspelling Recognition ═══[/bold cyan]"
        )
        self.console.print(
            "[dim]Hotkeys in recognition window: [ESC/Q] Quit | [SPACE] Speak | [C] Clear | [BACKSPACE] Undo[/dim]\n"
        )
        from signora.asl.live import run_asl_live

        try:
            run_asl_live(source=0)
        except Exception as exc:
            self.console.print(f"[bold red]Error running ASL mode: {exc}[/bold red]")
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_isl_flow(self) -> None:
        self.console.print(
            "\n[bold cyan]═══ Launching Indian Sign Language (ISL) Recognition ═══[/bold cyan]"
        )
        self.console.print("[dim]Dual-handed 164D normalized spatial geometry engine.[/dim]\n")
        from signora.isl.live import run_isl_live

        try:
            run_isl_live(source=0)
        except Exception as exc:
            self.console.print(f"[bold red]Error running ISL mode: {exc}[/bold red]")
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_words_flow(self) -> None:
        self.console.print(
            "\n[bold cyan]═══ Launching ASL Dynamic Words Recognition ═══[/bold cyan]"
        )
        self.console.print("[dim]Holistic trajectory sequence classifier (DTW-kNN).[/dim]\n")
        from signora.words.live import run_live

        try:
            run_live(source=0)
        except Exception as exc:
            self.console.print(f"[bold red]Error running words mode: {exc}[/bold red]")
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_game_flow(self) -> None:
        kiosk = Confirm.ask(
            "Launch in fullscreen browser kiosk mode?", default=False, console=self.console
        )
        self.console.print(
            "\n[bold cyan]═══ Starting Shadow-Play Dino Runner Server ═══[/bold cyan]"
        )
        from signora.game.server import serve

        try:
            serve(host="127.0.0.1", port=8000, kiosk=kiosk)
        except Exception as exc:
            self.console.print(f"[bold red]Error running game mode: {exc}[/bold red]")
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_learn_flow(self) -> None:
        kiosk = Confirm.ask(
            "Launch in fullscreen browser kiosk mode?", default=False, console=self.console
        )
        self.console.print(
            "\n[bold cyan]═══ Starting Interactive Learning Kiosk Server ═══[/bold cyan]"
        )
        from signora.learn.server import serve

        try:
            serve(host="127.0.0.1", port=8000, kiosk=kiosk)
        except Exception as exc:
            self.console.print(f"[bold red]Error running learning mode: {exc}[/bold red]")
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_doctor_flow(self) -> None:
        self.console.print(
            "\n[bold cyan]═══ Running Signora Diagnostics (10 Checks) ═══[/bold cyan]\n"
        )
        checks = run_all_checks()

        table = Table(border_style="cyan")
        table.add_column("Category", style="bold white", width=14)
        table.add_column("Component", style="bold white", width=26)
        table.add_column("Status", width=10)
        table.add_column("Details", style="dim")

        for c in checks:
            status = (
                "[bold green]PASS[/bold green]"
                if c.passed and not c.warning
                else (
                    "[bold yellow]WARN[/bold yellow]" if c.warning else "[bold red]FAIL[/bold red]"
                )
            )
            table.add_row(c.category, c.name, status, c.message)

        self.console.print(table)
        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_models_flow(self) -> None:
        self.console.print("\n[bold cyan]═══ Model Asset Manager ═══[/bold cyan]")
        self.console.print(f"Directory: [bold white]{self.models_dir}[/bold white]\n")

        manifest = load_manifest()
        results = verify_all_models(self.models_dir)

        table = Table(border_style="cyan")
        table.add_column("Model Key", style="bold cyan")
        table.add_column("File", style="bold white")
        table.add_column("Integrity", width=12)
        table.add_column("Details", style="dim")

        missing_count = 0
        for k, (ok, msg) in results.items():
            info = manifest["models"][k]
            status = "[bold green]VERIFIED[/bold green]" if ok else "[bold red]MISSING[/bold red]"
            if not ok:
                missing_count += 1
            table.add_row(k, info["filename"], status, msg)

        self.console.print(table)

        if missing_count > 0:
            if Confirm.ask(
                f"\nDownload {missing_count} missing/invalid model asset(s)?",
                default=True,
                console=self.console,
            ):
                from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models

                fetch_models(DEFAULT_MANIFEST, self.models_dir)

        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def run_camera_probe_flow(self) -> None:
        self.console.print("\n[bold cyan]═══ Hardware Camera Probe ═══[/bold cyan]\n")
        cams = probe_cameras(max_probe=4)
        if not cams:
            self.console.print(
                "[bold yellow]No physical video devices detected on this system.[/bold yellow]"
            )
        else:
            table = Table(border_style="cyan")
            table.add_column("Index", style="bold yellow", width=8, justify="center")
            table.add_column("Resolution", style="bold white", width=16)
            table.add_column("Status", style="bold green", width=12)
            for c in cams:
                table.add_row(str(c["index"]), f"{c['width']}x{c['height']}", "Ready")
            self.console.print(table)

        Prompt.ask("\nPress Enter to return to main menu", console=self.console)

    def loop(self) -> int:
        """Main interaction loop."""
        while True:
            self.clear()
            self.print_header()
            choice = self.show_main_menu()

            if choice == "1":
                self.run_asl_flow()
            elif choice == "2":
                self.run_isl_flow()
            elif choice == "3":
                self.run_words_flow()
            elif choice == "4":
                self.run_game_flow()
            elif choice == "5":
                self.run_learn_flow()
            elif choice == "6":
                self.run_doctor_flow()
            elif choice == "7":
                self.run_models_flow()
            elif choice == "8":
                self.run_camera_probe_flow()
            elif choice == "0":
                self.console.print(
                    "\n[bold cyan]Thank you for using Signora. Goodbye![/bold cyan]\n"
                )
                return 0


def run_tui() -> int:
    try:
        tui = SignoraTUI()
        return tui.loop()
    except (KeyboardInterrupt, EOFError):
        print("\nExiting Signora.")
        return 0


if __name__ == "__main__":
    raise SystemExit(run_tui())

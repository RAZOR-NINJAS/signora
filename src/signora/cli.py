"""Signora command-line interface entry point.

Dispatches to ASL fingerspelling, ISL two-handed recognition, ASL dynamic words,
interactive browser game, learning kiosk, system doctor, models manager, and TUI.
"""

import argparse
import sys
from typing import List, Optional

# Ensure safe console encoding on Windows CP1252 / legacy shells
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from signora import __version__


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="signora",
        description="Signora: 100% Offline, Real-Time Sign Language Recognition & Interactive Learning Engine",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("-v", "--version", action="version", version=f"signora {__version__}")

    subparsers = parser.add_subparsers(
        dest="subcommand", title="Available Subcommands", metavar="<command>"
    )

    # 1. signora live / asl
    p_live = subparsers.add_parser(
        "live", aliases=["asl"], help="Run ASL fingerspelling recognition with real-time HUD"
    )
    p_live.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_live.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_live.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback")
    p_live.add_argument(
        "--detect-every", type=int, default=1, help="Process landmarks every N frames (default: 1)"
    )
    p_live.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 2. signora isl
    p_isl = subparsers.add_parser(
        "isl", help="Run Indian Sign Language (ISL) two-handed recognition (A-Z + 10 words)"
    )
    p_isl.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_isl.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_isl.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback")
    p_isl.add_argument(
        "--detect-every", type=int, default=1, help="Process landmarks every N frames (default: 1)"
    )
    p_isl.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 3. signora words
    p_words = subparsers.add_parser(
        "words", help="Run ASL dynamic words holistic motion recognition (DTW-kNN)"
    )
    p_words.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_words.add_argument(
        "--threshold",
        type=float,
        default=0.18,
        help="Recognition confidence threshold (default: 0.18)",
    )
    p_words.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_words.add_argument(
        "--max-frames", type=int, default=None, help="Maximum frames to process before exit"
    )

    # 4. signora game
    p_game = subparsers.add_parser(
        "game", help="Start gesture-controlled shadow-play Dino runner web server"
    )
    p_game.add_argument("--host", default="127.0.0.1", help="Bind IP address (default: 127.0.0.1)")
    p_game.add_argument("-p", "--port", type=int, default=8000, help="HTTP port (default: 8000)")
    p_game.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_game.add_argument(
        "--kiosk", action="store_true", help="Open fullscreen kiosk window in Chrome/Edge"
    )
    p_game.add_argument(
        "--no-camera", action="store_true", help="Do not initialize camera detector automatically"
    )
    p_game.add_argument(
        "-m",
        "--mode",
        choices=["asl", "isl"],
        default="asl",
        help="Sign language gesture set: 'asl' (single-handed) or 'isl' (dual-handed) (default: asl)",
    )
    p_game.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 5. signora learn
    p_learn = subparsers.add_parser(
        "learn", help="Start browser Learning Kiosk for interactive sign drills and quizzes"
    )
    p_learn.add_argument("--host", default="127.0.0.1", help="Bind IP address (default: 127.0.0.1)")
    p_learn.add_argument("-p", "--port", type=int, default=8000, help="HTTP port (default: 8000)")
    p_learn.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_learn.add_argument(
        "--kiosk", action="store_true", help="Open fullscreen kiosk window in Chrome/Edge"
    )
    p_learn.add_argument(
        "--no-camera", action="store_true", help="Do not initialize camera detector automatically"
    )
    p_learn.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 6. signora doctor
    p_doc = subparsers.add_parser(
        "doctor", help="Inspect system health, hardware, neural weights, and dependencies"
    )
    p_doc.add_argument(
        "--check-only",
        action="store_true",
        help="Silent mode returning exit code 0 on pass, 1 on fail",
    )

    # 7. signora models
    p_models = subparsers.add_parser(
        "models", help="Inspect, download, or verify neural model weights"
    )
    p_models.add_argument(
        "action",
        choices=["fetch", "verify", "list"],
        default="list",
        nargs="?",
        help="Model operation",
    )
    p_models.add_argument("-d", "--dir", default=None, help="Custom models directory")

    # 8. signora tui
    subparsers.add_parser("tui", help="Launch interactive Terminal UI dashboard")

    # 9. signora collect / studio
    p_collect = subparsers.add_parser(
        "collect",
        aliases=["studio"],
        help="Interactive ISL sample collection studio & neural trainer",
    )
    p_collect.add_argument(
        "-s", "--sign", default=None, help="Specific ISL letter/sign to collect (e.g. A-Z, SPACE)"
    )
    p_collect.add_argument(
        "-n", "--count", type=int, default=300, help="Target samples to collect automatically (default: 300)"
    )
    p_collect.add_argument(
        "--timer", type=float, default=5.0, help="Countdown timer duration before auto-capture in seconds (default: 5.0)"
    )
    p_collect.add_argument(
        "--source", default="0", help="Camera index or device path (default: 0)"
    )
    p_collect.add_argument(
        "--train", action="store_true", help="Train PyTorch MLP model on all collected ISL data"
    )
    p_collect.add_argument(
        "--headless", action="store_true", help="Run without graphical display window"
    )
    p_collect.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = create_parser()

    if argv is None:
        argv = sys.argv[1:]

    # Default to TUI if run interactively with no arguments
    if not argv:
        if sys.stdin.isatty():
            from signora.tui.app import run_tui

            return run_tui()
        parser.print_help()
        return 0

    args = parser.parse_args(argv)

    if args.subcommand in ("live", "asl"):
        from signora.asl.live import run_asl_live

        return run_asl_live(
            source=args.source,
            headless=args.headless,
            no_tts=args.no_tts,
            detect_every=args.detect_every,
            use_fake=args.fake_camera,
        )

    if args.subcommand == "isl":
        from signora.isl.live import run_isl_live

        return run_isl_live(
            source=args.source,
            headless=args.headless,
            no_tts=args.no_tts,
            detect_every=args.detect_every,
            use_fake=args.fake_camera,
        )

    if args.subcommand == "words":
        from signora.words.live import run_live

        return run_live(
            source=args.source,
            threshold=args.threshold,
            max_frames=args.max_frames,
            headless=args.headless,
        )

    if args.subcommand == "game":
        from signora.game.server import serve

        serve(
            host=args.host,
            port=args.port,
            source=args.source,
            kiosk=args.kiosk,
            camera=not args.no_camera,
            use_fake=args.fake_camera,
            mode=args.mode,
        )
        return 0

    if args.subcommand == "learn":
        from signora.learn.server import serve as serve_learn

        serve_learn(
            host=args.host,
            port=args.port,
            source=args.source,
            kiosk=args.kiosk,
            camera=not args.no_camera,
            use_fake=args.fake_camera,
        )
        return 0

    if args.subcommand == "doctor":
        from signora.doctor.checks import main as doc_main

        doc_args = ["--check-only"] if args.check_only else []
        return doc_main(doc_args)

    if args.subcommand == "models":
        from pathlib import Path

        from signora.models.manifest import get_default_models_dir, load_manifest, verify_all_models

        models_dir = Path(args.dir) if args.dir else get_default_models_dir()
        action = args.action

        if action == "list":
            manifest = load_manifest()
            print(f"Models directory: {models_dir}")
            for k, info in manifest.get("models", {}).items():
                p = models_dir / info["filename"]
                status = "INSTALLED" if p.exists() else "MISSING"
                print(
                    f"  * {k:16s} [{status:9s}] {info['filename']} ({info.get('description', '')})"
                )
            return 0

        if action == "verify":
            results = verify_all_models(models_dir)
            all_ok = True
            for k, (ok, msg) in results.items():
                status = "PASS" if ok else "FAIL"
                print(f"  [{status}] {k}: {msg}")
                if not ok:
                    all_ok = False
            return 0 if all_ok else 1

        if action == "fetch":
            from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models

            ok = fetch_models(DEFAULT_MANIFEST, models_dir)
            return 0 if ok else 1

    if args.subcommand == "tui":
        from signora.tui.app import run_tui

        return run_tui()

    if args.subcommand in ("collect", "studio"):
        from signora.isl.collect import run_isl_collector

        cam_source = args.source
        if str(cam_source).isdigit():
            cam_source = int(cam_source)

        return run_isl_collector(
            sign=args.sign,
            count=args.count,
            source=cam_source,
            timer_seconds=args.timer,
            headless=args.headless,
            use_fake=args.fake_camera,
            auto_train=args.train,
        )

    parser.print_help()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

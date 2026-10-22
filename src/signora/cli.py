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
    parser.add_argument(
        "--intro",
        action="store_true",
        help="Display futuristic introduction and 'Connecting Hands' startup animation",
    )
    parser.add_argument(
        "--no-intro",
        action="store_true",
        help="Skip startup introduction animation",
    )

    parser.add_argument(
        "--res",
        "--resolution",
        dest="resolution",
        default=None,
        help="Camera resolution preset: 720p (default), 480p, 360p, 1080p (aspect ratio strictly 16:9)",
    )

    res_parser = argparse.ArgumentParser(add_help=False)
    res_parser.add_argument(
        "--res",
        "--resolution",
        dest="resolution",
        default=argparse.SUPPRESS,
        help="Camera resolution preset: 720p (default), 480p, 360p, 1080p (aspect ratio strictly 16:9)",
    )
    res_parser.add_argument(
        "--phone",
        action="store_true",
        default=False,
        help="Use connected Android phone camera as high-speed zero-latency webcam via scrcpy",
    )
    res_parser.add_argument(
        "--facing",
        choices=["back", "front"],
        default="back",
        help="Phone camera facing direction when streaming from phone (default: back)",
    )

    subparsers = parser.add_subparsers(
        dest="subcommand", title="Available Subcommands", metavar="<command>"
    )

    # 1. signora live / asl
    p_live = subparsers.add_parser(
        "live", aliases=["asl"], parents=[res_parser], help="Run ASL fingerspelling recognition with real-time HUD"
    )
    p_live.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_live.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_live.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback")
    p_live.add_argument(
        "--detect-every", type=int, default=2, help="Process landmarks every N frames (default: 2)"
    )
    p_live.add_argument(
        "--dwell", type=int, default=None, help="Hold dwell duration in ms for sign confirmation (default: 1000)"
    )
    p_live.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 2. signora isl
    p_isl = subparsers.add_parser(
        "isl", parents=[res_parser], help="Run Indian Sign Language (ISL) two-handed recognition (A-Z + 200 static words)"
    )
    p_isl.add_argument(
        "-s", "--source", default="0", help="Camera index, video file, or IP stream URL (default: 0)"
    )
    p_isl.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_isl.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback")
    p_isl.add_argument(
        "--detect-every", type=int, default=2, help="Process landmarks every N frames (default: 2)"
    )
    p_isl.add_argument(
        "--dwell", type=int, default=None, help="Hold dwell duration in ms for sign confirmation (default: 1000)"
    )
    p_isl.add_argument(
        "--collect", action="store_true", help="Launch interactive collection studio"
    )
    p_isl.add_argument(
        "--train", action="store_true", help="Train PyTorch MLP model on collected ISL data"
    )
    p_isl.add_argument(
        "--fresh", action="store_true", help="Train clean model from scratch without injecting baseline classes"
    )
    p_isl.add_argument(
        "--body", action="store_true", help="Enable upper-body pose & spatial placement anchor tracking"
    )
    p_isl.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )
    p_isl.add_argument(
        "--gemini-key", default=None, help="Google Gemini API key for Real AI vision & LLM grammar"
    )
    p_isl.add_argument(
        "--no-topology", action="store_true", help="Disable hardcoded geometric topology engine and use pure trained neural weights only"
    )

    # 2.5 signora body / spatial
    p_body = subparsers.add_parser(
        "body",
        aliases=["spatial"],
        parents=[res_parser],
        help="Run Upper-Body Pose & Hand Spatial Recognition (static signs with body placement anchors)",
    )
    p_body.add_argument(
        "-s", "--source", default="0", help="Camera index, video file, or IP stream URL (e.g. iPhone DroidCam) (default: 0)"
    )
    p_body.add_argument("--headless", action="store_true", help="Run without graphical window")
    p_body.add_argument("--no-tts", action="store_true", help="Disable Piper audio speech feedback")
    p_body.add_argument(
        "--detect-every", type=int, default=2, help="Process landmarks every N frames (default: 2)"
    )
    p_body.add_argument(
        "--dwell", type=int, default=None, help="Hold dwell duration in ms for spatial sign confirmation (default: 1000)"
    )
    p_body.add_argument(
        "--collect", action="store_true", help="Launch interactive collection studio with body tracking"
    )
    p_body.add_argument(
        "--train", action="store_true", help="Train 220D PyTorch MLP model on collected spatial data"
    )
    p_body.add_argument(
        "--fresh", action="store_true", help="Train clean model from scratch without injecting baseline classes"
    )
    p_body.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )
    p_body.add_argument(
        "--no-topology", action="store_true", help="Disable hardcoded geometric topology engine and use pure trained neural weights only"
    )

    # 3. signora words
    p_words = subparsers.add_parser(
        "words", parents=[res_parser], help="Run ASL dynamic words holistic motion recognition (DTW-kNN)"
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
        "game", parents=[res_parser], help="Start gesture-controlled shadow-play Dino runner web server"
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
        "learn", parents=[res_parser], help="Start browser Learning Kiosk for interactive sign drills and quizzes"
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
        parents=[res_parser],
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
        "--body", action="store_true", help="Enable upper-body pose & spatial placement anchor tracking (220D features)"
    )
    p_collect.add_argument(
        "--headless", action="store_true", help="Run without graphical display window"
    )
    p_collect.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )

    # 9.5 signora train
    p_train = subparsers.add_parser(
        "train",
        help="Train or fine-tune neural network classifier on collected sign data",
    )
    p_train.add_argument(
        "--body", action="store_true", help="Train 220D Spatial Hand+Body model from data/isl_spatial/"
    )
    p_train.add_argument(
        "-d", "--data-dir", default=None, help="Custom data directory containing .npy sample files"
    )
    p_train.add_argument(
        "--epochs", type=int, default=50, help="Number of training epochs (default: 50)"
    )
    p_train.add_argument(
        "--lr", type=float, default=1e-3, help="Optimizer learning rate (default: 0.001)"
    )
    p_train.add_argument(
        "-i", "--import-file", "--import", dest="import_file", default=None, help="Import phone-exported dataset JSON file before training"
    )

    # 9.6 signora dataset
    p_dataset = subparsers.add_parser(
        "dataset",
        help="Dataset tools: import, inspect, and verify phone-recorded ISL data",
    )
    p_dataset.add_argument(
        "action",
        choices=["import", "inspect", "info"],
        default="inspect",
        nargs="?",
        help="Dataset action: import or inspect (default: inspect)",
    )
    p_dataset.add_argument(
        "file",
        nargs="?",
        default="signora_isl_dataset.json",
        help="Path to exported signora_isl_dataset.json file",
    )
    p_dataset.add_argument(
        "--train", action="store_true", default=True, help="Automatically train model after import (default: True)"
    )
    p_dataset.add_argument(
        "--no-train", dest="train", action="store_false", help="Import samples without triggering retraining"
    )
    p_dataset.add_argument(
        "--epochs", type=int, default=50, help="Number of training epochs (default: 50)"
    )

    # 10. signora alert (Hear Through Your Eyes)
    p_alert = subparsers.add_parser(
        "alert",
        aliases=["listen", "hear"],
        help="SIGNORA Alert: Hear Through Your Eyes (Doorbell, Alarms, Calls, Knocks)",
    )
    p_alert.add_argument(
        "--daemon", "--bg", dest="daemon", action="store_true", help="Spawn and run in background daemon mode"
    )
    p_alert.add_argument(
        "--start", action="store_true", help="Start background alert watcher daemon"
    )
    p_alert.add_argument(
        "--stop", action="store_true", help="Stop running background alert watcher daemon"
    )
    p_alert.add_argument(
        "--status", action="store_true", help="Inspect background daemon health and recent sound log"
    )
    p_alert.add_argument(
        "--daemon-loop", action="store_true", help=argparse.SUPPRESS
    )
    p_alert.add_argument(
        "--sensitivity",
        choices=["low", "medium", "high"],
        default="medium",
        help="Acoustic detection sensitivity (default: medium)",
    )
    p_alert.add_argument(
        "-s", "--source", default=None, help="Camera index if visual video backdrop is desired (default: None)"
    )
    p_alert.add_argument(
        "--no-desktop", action="store_true", help="Disable OS desktop visual notifications"
    )
    p_alert.add_argument(
        "--test", action="store_true", help="Fire an instant test notification and exit"
    )
    p_alert.add_argument(
        "--headless", action="store_true", help="Run without HighGUI display window"
    )
    p_alert.add_argument(
        "--fake-mic", action="store_true", help="Use synthetic microphone audio for testing"
    )
    p_alert.add_argument(
        "--calibrate", action="store_true", help="Profile room ambient acoustics and calibrate sensitivity"
    )
    p_alert.add_argument(
        "--simulate",
        choices=["doorbell", "knock", "siren", "call", "baby", "bark", "glass"],
        default=None,
        help="Simulate a specific acoustic event and trigger desktop/popup alerts",
    )

    p_lipsync = subparsers.add_parser(
        "lipsync",
        aliases=["lip", "lips"],
        parents=[res_parser],
        help="Real-time Lip Sync & Visual Speech Activity recognition",
    )
    p_lipsync.add_argument(
        "-s", "--source", default=0, help="Camera index or video file path (default: 0)"
    )
    p_lipsync.add_argument(
        "--no-audio", action="store_true", help="Disable microphone audio synchronization"
    )
    p_lipsync.add_argument(
        "--headless", action="store_true", help="Run in headless mode without window"
    )
    p_lipsync.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic animated face camera bench"
    )

    # 12. signora stream / vcam (Virtual Webcam for Zoom/Meet/Teams)
    p_stream = subparsers.add_parser(
        "stream",
        aliases=["vcam"],
        parents=[res_parser],
        help="Broadcast sign recognition as a virtual webcam for Zoom, Google Meet & Teams",
    )
    p_stream.add_argument(
        "-s", "--source", default="0", help="Camera index or video file (default: 0)"
    )
    p_stream.add_argument(
        "-m", "--mode", choices=["isl", "asl"], default="isl", help="Sign language mode (default: isl)"
    )
    p_stream.add_argument(
        "--device", default=None, help="Target virtual camera device (e.g. /dev/video10)"
    )
    p_stream.add_argument(
        "--width", type=int, default=1280, help="Output stream width (default: 1280)"
    )
    p_stream.add_argument(
        "--height", type=int, default=720, help="Output stream height (default: 720)"
    )
    p_stream.add_argument(
        "--fps", type=int, default=30, help="Output stream target FPS (default: 30)"
    )
    p_stream.add_argument(
        "--clean", action="store_true", help="Clean broadcast mode (lower-third captions only, no HUD cards)"
    )
    p_stream.add_argument(
        "--no-hands", action="store_true", help="Hide hand skeletal overlays on video stream"
    )
    p_stream.add_argument(
        "--no-tts", action="store_true", help="Disable Piper audio speech feedback"
    )
    p_stream.add_argument(
        "--headless", action="store_true", help="Run without preview window"
    )
    p_stream.add_argument(
        "--no-web", action="store_true", help="Disable local HTTP stream server"
    )
    p_stream.add_argument(
        "--port", type=int, default=8888, help="Local HTTP stream port (default: 8888)"
    )
    p_stream.add_argument(
        "--setup-v4l2", action="store_true", help="Display v4l2loopback installation & setup guide"
    )
    p_stream.add_argument(
        "--fake-camera", action="store_true", help="Use synthetic camera for testing"
    )
    p_stream.add_argument(
        "--gemini-key", default=None, help="Google Gemini API key for grammar synthesis"
    )

    # 13. signora phone (One-command zero-latency phone webcam bridge)
    p_phone = subparsers.add_parser(
        "phone",
        parents=[res_parser],
        help="One-command launcher: connects phone via ADB and streams zero-latency camera to Signora",
    )
    p_phone.add_argument(
        "mode",
        nargs="?",
        default="isl",
        choices=["isl", "body", "asl", "words"],
        help="Signora recognition mode to launch with phone camera (default: isl)",
    )
    p_phone.add_argument(
        "--no-tts", action="store_true", help="Disable Piper audio speech feedback"
    )
    p_phone.add_argument(
        "--dwell", type=int, default=None, help="Hold dwell duration in ms for sign confirmation"
    )
    p_phone.add_argument(
        "--detect-every", type=int, default=2, help="Process landmarks every N frames (default: 2)"
    )

    return parser


def main(argv: Optional[List[str]] = None) -> int:
    parser = create_parser()

    if argv is None:
        argv = sys.argv[1:]

    # Explicit --intro flag
    if "--intro" in argv:
        from signora.intro import display_intro

        display_intro(speak=True, animate=True, wait=True)
        return 0

    # Default to TUI if run interactively with no arguments
    if not argv:
        if sys.stdin.isatty():
            from signora.intro import display_intro
            display_intro(speak=True, animate=True)
            from signora.tui.app import run_tui

            return run_tui()
        parser.print_help()
        return 0

    args = parser.parse_args(argv)

    if args.intro:
        from signora.intro import display_intro
        display_intro(speak=True, animate=True)
        return 0

    if args.subcommand == "phone":
        from signora.core.phone import launch_signora_with_phone

        return launch_signora_with_phone(
            mode=args.mode,
            facing=getattr(args, "facing", "back"),
            resolution=getattr(args, "resolution", "720p"),
            no_tts=getattr(args, "no_tts", False),
            dwell_ms=getattr(args, "dwell", None),
            detect_every=getattr(args, "detect_every", 2),
            enable_body=True if args.mode in ("isl", "body") else False,
        )

    if args.subcommand in ("live", "asl"):
        if getattr(args, "phone", False):
            from signora.core.phone import launch_signora_with_phone

            return launch_signora_with_phone(
                mode="asl",
                facing=getattr(args, "facing", "back"),
                resolution=getattr(args, "resolution", "720p"),
                no_tts=args.no_tts,
                dwell_ms=getattr(args, "dwell", None),
                detect_every=args.detect_every,
            )

        from signora.asl.live import run_asl_live
        from signora.core.resolution import prompt_resolution_16_9

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

        return run_asl_live(
            source=args.source,
            headless=args.headless,
            no_tts=args.no_tts,
            detect_every=args.detect_every,
            use_fake=args.fake_camera,
            dwell_ms=getattr(args, "dwell", None),
            width=w,
            height=h,
        )

    if args.subcommand in ("body", "spatial"):
        if getattr(args, "phone", False):
            from signora.core.phone import launch_signora_with_phone

            return launch_signora_with_phone(
                mode="body",
                facing=getattr(args, "facing", "back"),
                resolution=getattr(args, "resolution", "720p"),
                no_tts=args.no_tts,
                dwell_ms=getattr(args, "dwell", None),
                detect_every=args.detect_every,
                enable_body=True,
            )

        cam_source = args.source
        if str(cam_source).isdigit():
            cam_source = int(cam_source)

        if getattr(args, "train", False):
            from signora.isl.train import DATA_SPATIAL_DIR, train_isl_model

            res = train_isl_model(
                data_dir=DATA_SPATIAL_DIR,
                in_dim=220,
                fresh=getattr(args, "fresh", False),
            )
            return 0 if res.get("success") else 1

        from signora.core.resolution import prompt_resolution_16_9

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

        if getattr(args, "collect", False):
            from signora.isl.collect import run_isl_collector

            return run_isl_collector(
                source=cam_source,
                headless=args.headless,
                use_fake=args.fake_camera,
                enable_body=True,
                width=w,
                height=h,
            )

        from signora.body.live import run_body_live

        return run_body_live(
            source=args.source,
            headless=args.headless,
            no_tts=args.no_tts,
            detect_every=args.detect_every,
            use_fake=args.fake_camera,
            dwell_ms=getattr(args, "dwell", None),
            width=w,
            height=h,
            enable_topology=not getattr(args, "no_topology", False),
        )

    if args.subcommand == "isl":
        if getattr(args, "phone", False):
            from signora.core.phone import launch_signora_with_phone

            return launch_signora_with_phone(
                mode="isl",
                facing=getattr(args, "facing", "back"),
                resolution=getattr(args, "resolution", "720p"),
                no_tts=args.no_tts,
                dwell_ms=getattr(args, "dwell", None),
                detect_every=args.detect_every,
                enable_body=getattr(args, "body", False),
                gemini_key=getattr(args, "gemini_key", None),
            )

        cam_source = args.source
        if str(cam_source).isdigit():
            cam_source = int(cam_source)

        if getattr(args, "train", False):
            from signora.isl.train import DATA_DIR, DATA_SPATIAL_DIR, train_isl_model

            target_dir = DATA_SPATIAL_DIR if getattr(args, "body", False) else DATA_DIR
            res = train_isl_model(
                data_dir=target_dir,
                in_dim=220 if getattr(args, "body", False) else 164,
                fresh=getattr(args, "fresh", False),
            )
            return 0 if res.get("success") else 1

        from signora.core.resolution import prompt_resolution_16_9

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

        if getattr(args, "collect", False):
            from signora.isl.collect import run_isl_collector

            return run_isl_collector(
                source=cam_source,
                headless=args.headless,
                use_fake=args.fake_camera,
                enable_body=getattr(args, "body", False),
                width=w,
                height=h,
            )

        if getattr(args, "body", False):
            from signora.body.live import run_body_live

            return run_body_live(
                source=args.source,
                headless=args.headless,
                no_tts=args.no_tts,
                detect_every=args.detect_every,
                use_fake=args.fake_camera,
                dwell_ms=getattr(args, "dwell", None),
                width=w,
                height=h,
                enable_topology=not getattr(args, "no_topology", False),
            )

        from signora.isl.live import run_isl_live

        return run_isl_live(
            source=args.source,
            headless=args.headless,
            no_tts=args.no_tts,
            detect_every=args.detect_every,
            use_fake=args.fake_camera,
            dwell_ms=getattr(args, "dwell", None),
            gemini_key=getattr(args, "gemini_key", None),
            width=w,
            height=h,
            enable_topology=not getattr(args, "no_topology", False),
        )

    if args.subcommand == "words":
        from signora.core.resolution import prompt_resolution_16_9
        from signora.words.live import run_live

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

        return run_live(
            source=args.source,
            threshold=args.threshold,
            max_frames=args.max_frames,
            headless=args.headless,
            width=w,
            height=h,
        )

    if args.subcommand == "game":
        w, h = 1280, 720
        if not args.no_camera:
            from signora.core.resolution import prompt_resolution_16_9

            w, h, _ = prompt_resolution_16_9(
                default_choice="720p",
                headless=False,
                cli_arg=getattr(args, "resolution", None),
            )

        from signora.game.server import serve

        serve(
            host=args.host,
            port=args.port,
            source=args.source,
            kiosk=args.kiosk,
            camera=not args.no_camera,
            use_fake=args.fake_camera,
            mode=args.mode,
            width=w,
            height=h,
        )
        return 0

    if args.subcommand == "learn":
        w, h = 1280, 720
        if not args.no_camera:
            from signora.core.resolution import prompt_resolution_16_9

            w, h, _ = prompt_resolution_16_9(
                default_choice="720p",
                headless=False,
                cli_arg=getattr(args, "resolution", None),
            )

        from signora.learn.server import serve as serve_learn

        serve_learn(
            host=args.host,
            port=args.port,
            source=args.source,
            kiosk=args.kiosk,
            camera=not args.no_camera,
            use_fake=args.fake_camera,
            width=w,
            height=h,
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
        if getattr(args, "train", False) and getattr(args, "sign", None) is None:
            print("[Studio] Directly invoking ISL model training on all data...")
            from signora.isl.train import DATA_DIR, DATA_SPATIAL_DIR, train_isl_model

            target_dir = DATA_SPATIAL_DIR if getattr(args, "body", False) else DATA_DIR
            res = train_isl_model(
                data_dir=target_dir,
                in_dim=220 if getattr(args, "body", False) else 164,
            )
            return 0 if res.get("success") else 1

        from signora.core.resolution import prompt_resolution_16_9

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

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
            enable_body=getattr(args, "body", False),
            width=w,
            height=h,
        )

    if args.subcommand == "train":
        from pathlib import Path
        from signora.isl.train import DATA_DIR, DATA_SPATIAL_DIR, train_isl_model

        if getattr(args, "import_file", None):
            from signora.isl.import_dataset import import_phone_dataset
            res = import_phone_dataset(
                json_path=Path(args.import_file),
                epochs=args.epochs,
                auto_train=True,
            )
            return 0 if res.get("success") else 1

        if getattr(args, "data_dir", None):
            target_dir = Path(args.data_dir)
            in_dim = 220 if getattr(args, "body", False) else None
        else:
            target_dir = DATA_SPATIAL_DIR if getattr(args, "body", False) else DATA_DIR
            in_dim = 220 if getattr(args, "body", False) else 164

        res = train_isl_model(
            data_dir=target_dir,
            epochs=args.epochs,
            lr=args.lr,
            in_dim=in_dim,
        )
        return 0 if res.get("success") else 1

    if args.subcommand == "dataset":
        from pathlib import Path
        from signora.isl.import_dataset import import_phone_dataset, inspect_dataset_file

        file_path = Path(args.file)
        if args.action in ("inspect", "info"):
            meta = inspect_dataset_file(file_path)
            print("=" * 60)
            print(f"  SIGNORA Dataset Inspector: {file_path.name}")
            print("=" * 60)
            print(f"Device:            {meta.get('device', 'Android')}")
            print(f"Feature Dimension: {meta.get('feature_dim')}D")
            print(f"Total Classes:     {meta.get('total_classes')}")
            print(f"Total Samples:     {meta.get('total_samples')}")
            print("-" * 60)
            print("Classes:")
            for cls_name, count in sorted(meta.get("classes", {}).items()):
                print(f"  * {cls_name:16s} : {count:4d} samples")
            print("=" * 60)
            return 0

        if args.action == "import":
            res = import_phone_dataset(
                json_path=file_path,
                auto_train=args.train,
                epochs=args.epochs,
            )
            return 0 if res.get("success") else 1

    if args.subcommand in ("alert", "listen", "hear"):
        from signora.alert import (
            AlertDispatcher,
            run_alert_live,
            run_daemon_loop,
            start_daemon,
            status_daemon,
            stop_daemon,
        )

        enable_desktop = not args.no_desktop

        if args.test:
            dispatcher = AlertDispatcher(enable_desktop=True)
            dispatcher.test_alert()
            print("✓ Dispatched test visual desktop alert: 🔔 DOORBELL")
            return 0

        if args.calibrate:
            from signora.alert import calibrate_microphone

            calibrate_microphone(use_fake=args.fake_mic)
            return 0

        if args.simulate:
            from signora.alert import AlertEngine

            engine = AlertEngine(enable_desktop=enable_desktop, use_fake_mic=True)
            ev = engine.simulate_sound(args.simulate)
            if ev:
                print(f"✓ Successfully simulated sound: {ev.icon} {ev.title}")
            else:
                print(f"✓ Dispatched simulated acoustic test: {args.simulate.upper()}")
            return 0

        if args.stop:
            return stop_daemon()

        if args.status:
            return status_daemon()

        if args.daemon or args.start:
            return start_daemon(
                sensitivity=args.sensitivity,
                enable_desktop=enable_desktop,
            )

        if args.daemon_loop:
            return run_daemon_loop(
                sensitivity=args.sensitivity,
                enable_desktop=enable_desktop,
                use_fake_mic=args.fake_mic,
            )

        return run_alert_live(
            source=args.source,
            sensitivity=args.sensitivity,
            enable_desktop=enable_desktop,
            headless=args.headless,
            use_fake_mic=args.fake_mic,
        )

    if args.subcommand in ("lipsync", "lip", "lips"):
        from signora.core.resolution import prompt_resolution_16_9
        from signora.lipsync import run_lipsync_live

        w, h, _ = prompt_resolution_16_9(
            default_choice="720p",
            headless=args.headless,
            cli_arg=getattr(args, "resolution", None),
        )

        return run_lipsync_live(
            source=args.source,
            enable_audio=not args.no_audio,
            headless=args.headless,
            fake_camera=args.fake_camera,
            width=w,
            height=h,
        )

    if args.subcommand in ("stream", "vcam"):
        from signora.stream import get_v4l2_setup_instructions, run_virtual_stream

        if args.setup_v4l2:
            print(get_v4l2_setup_instructions())
            return 0

        w, h = getattr(args, "width", 1280), getattr(args, "height", 720)
        cli_res = getattr(args, "resolution", None)
        if cli_res or (w == 1280 and h == 720):
            from signora.core.resolution import prompt_resolution_16_9

            w, h, _ = prompt_resolution_16_9(
                default_choice="720p",
                headless=args.headless,
                cli_arg=cli_res,
            )

        source = int(args.source) if str(args.source).isdigit() else args.source

        return run_virtual_stream(
            source=source,
            mode=args.mode,
            device=args.device,
            width=w,
            height=h,
            fps=args.fps,
            clean_mode=args.clean,
            show_hands=not args.no_hands,
            no_tts=args.no_tts,
            headless=args.headless,
            enable_web=not args.no_web,
            web_port=args.port,
            use_fake=args.fake_camera,
            gemini_key=args.gemini_key,
        )

    parser.print_help()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

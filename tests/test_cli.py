"""Tests for the Signora CLI parser and subcommand dispatch."""

import pytest

from signora.cli import create_parser


def test_cli_version():
    parser = create_parser()
    with pytest.raises(SystemExit) as exc:
        parser.parse_args(["-v"])
    assert exc.value.code == 0


def test_cli_subcommands_present():
    parser = create_parser()
    expected_cmds = {
        "live",
        "asl",
        "isl",
        "body",
        "spatial",
        "words",
        "game",
        "learn",
        "doctor",
        "models",
        "tui",
        "collect",
        "studio",
        "alert",
        "lipsync",
        "train",
    }

    subparsers_actions = [
        action
        for action in parser._actions
        if hasattr(action, "choices") and isinstance(action.choices, dict)
    ]
    assert subparsers_actions
    choices = subparsers_actions[0].choices
    for cmd in expected_cmds:
        assert cmd in choices, f"Expected subcommand '{cmd}' missing from parser."


def test_cli_live_args():
    parser = create_parser()
    args = parser.parse_args(
        [
            "live",
            "--source",
            "test.mp4",
            "--headless",
            "--no-tts",
            "--detect-every",
            "2",
            "--fake-camera",
        ]
    )
    assert args.subcommand == "live"
    assert args.source == "test.mp4"
    assert args.headless is True
    assert args.no_tts is True
    assert args.detect_every == 2
    assert args.fake_camera is True


def test_cli_isl_args():
    parser = create_parser()
    args = parser.parse_args(
        ["isl", "--source", "1", "--headless", "--no-tts", "--detect-every", "3", "--fake-camera", "--body"]
    )
    assert args.subcommand == "isl"
    assert args.source == "1"
    assert args.headless is True
    assert args.no_tts is True
    assert args.detect_every == 3
    assert args.fake_camera is True
    assert args.body is True


def test_cli_body_args():
    parser = create_parser()
    args = parser.parse_args(
        ["body", "--source", "http://192.168.1.15:4747/video", "--headless", "--no-tts", "--detect-every", "2", "--fake-camera"]
    )
    assert args.subcommand == "body"
    assert args.source == "http://192.168.1.15:4747/video"
    assert args.headless is True
    assert args.no_tts is True
    assert args.detect_every == 2
    assert args.fake_camera is True


def test_cli_words_args():
    parser = create_parser()
    args = parser.parse_args(["words", "--threshold", "0.25", "--headless", "--max-frames", "100"])
    assert args.subcommand == "words"
    assert args.threshold == 0.25
    assert args.headless is True
    assert args.max_frames == 100


def test_cli_game_args():
    parser = create_parser()
    args = parser.parse_args(
        [
            "game",
            "--host",
            "0.0.0.0",
            "-p",
            "8080",
            "--kiosk",
            "--no-camera",
            "--fake-camera",
            "--mode",
            "isl",
        ]
    )
    assert args.subcommand == "game"
    assert args.host == "0.0.0.0"
    assert args.port == 8080
    assert args.kiosk is True
    assert args.no_camera is True
    assert args.fake_camera is True
    assert args.mode == "isl"


def test_cli_learn_args():
    parser = create_parser()
    args = parser.parse_args(
        ["learn", "--host", "127.0.0.1", "-p", "9000", "--kiosk", "--no-camera", "--fake-camera"]
    )
    assert args.subcommand == "learn"
    assert args.host == "127.0.0.1"
    assert args.port == 9000
    assert args.kiosk is True
    assert args.no_camera is True
    assert args.fake_camera is True


def test_cli_doctor_args():
    parser = create_parser()
    args = parser.parse_args(["doctor", "--check-only"])
    assert args.subcommand == "doctor"
    assert args.check_only is True


def test_cli_models_args():
    parser = create_parser()
    args = parser.parse_args(["models", "verify", "-d", "/tmp/models"])
    assert args.subcommand == "models"
    assert args.action == "verify"
    assert args.dir == "/tmp/models"


def test_cli_collect_args():
    parser = create_parser()
    args = parser.parse_args(
        ["collect", "-s", "D", "-n", "300", "--timer", "5.0", "--train", "--headless", "--fake-camera"]
    )
    assert args.subcommand == "collect"
    assert args.sign == "D"
    assert args.count == 300
    assert args.timer == 5.0
    assert args.train is True
    assert args.headless is True
    assert args.fake_camera is True


def test_tui_initialization_and_header():
    from signora.tui.app import SignoraTUI

    tui = SignoraTUI()
    tui.print_header()
    assert tui.models_dir.exists()


def test_cli_alert_args():
    parser = create_parser()
    args = parser.parse_args(
        ["alert", "--daemon", "--sensitivity", "high", "--no-desktop", "--fake-mic", "--headless"]
    )
    assert args.subcommand == "alert"
    assert args.daemon is True
    assert args.sensitivity == "high"
    assert args.no_desktop is True
    assert args.fake_mic is True
    assert args.headless is True


def test_cli_train_subcommand():
    parser = create_parser()
    args = parser.parse_args(["train", "--body", "--epochs", "15", "--lr", "0.002", "-d", "/tmp/spatial_data"])
    assert args.subcommand == "train"
    assert args.body is True
    assert args.epochs == 15
    assert args.lr == 0.002
    assert args.data_dir == "/tmp/spatial_data"


def test_cli_body_collect_train_subcommand():
    parser = create_parser()
    args_collect = parser.parse_args(["body", "--collect", "--source", "1", "--headless", "--fake-camera"])
    assert args_collect.subcommand == "body"
    assert args_collect.collect is True

    args_train = parser.parse_args(["body", "--train"])
    assert args_train.subcommand == "body"
    assert args_train.train is True


def test_cli_dwell_configuration():
    parser = create_parser()
    args_live = parser.parse_args(["live", "--dwell", "1200"])
    assert args_live.dwell == 1200

    args_isl = parser.parse_args(["isl", "--dwell", "1400"])
    assert args_isl.dwell == 1400

    args_body = parser.parse_args(["body", "--dwell", "1500"])
    assert args_body.dwell == 1500


def test_cli_collect_body_flag():
    parser = create_parser()
    args = parser.parse_args(["collect", "--body", "-s", "FOOD", "-n", "100", "--fake-camera"])
    assert args.subcommand == "collect"
    assert args.body is True
    assert args.sign == "FOOD"
    assert args.count == 100


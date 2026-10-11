"""Test ensuring all documented subcommands and flags exist in the CLI parser."""

from signora.cli import create_parser


def test_documented_subcommands_exist():
    parser = create_parser()
    subparsers_actions = [
        action
        for action in parser._actions
        if hasattr(action, "choices") and isinstance(action.choices, dict)
    ]
    assert subparsers_actions
    choices = subparsers_actions[0].choices

    documented_commands = [
        "live",
        "isl",
        "words",
        "game",
        "learn",
        "doctor",
        "models",
        "tui",
    ]

    for cmd in documented_commands:
        assert cmd in choices, f"Documented subcommand '{cmd}' is missing from the CLI!"


def test_documented_flags_exist():
    parser = create_parser()
    subparsers_actions = [
        action
        for action in parser._actions
        if hasattr(action, "choices") and isinstance(action.choices, dict)
    ]
    subparser_dict = subparsers_actions[0].choices

    # Check live flags
    live_parser = subparser_dict["live"]
    live_flags = [opt for act in live_parser._actions for opt in act.option_strings]
    for flag in ["--source", "-s", "--headless", "--no-tts", "--detect-every"]:
        assert flag in live_flags, f"Missing flag '{flag}' on 'signora live'"

    # Check isl flags
    isl_parser = subparser_dict["isl"]
    isl_flags = [opt for act in isl_parser._actions for opt in act.option_strings]
    for flag in ["--source", "-s", "--headless", "--no-tts", "--detect-every"]:
        assert flag in isl_flags, f"Missing flag '{flag}' on 'signora isl'"

    # Check game flags
    game_parser = subparser_dict["game"]
    game_flags = [opt for act in game_parser._actions for opt in act.option_strings]
    for flag in ["--host", "--port", "-p", "--kiosk", "--no-camera"]:
        assert flag in game_flags, f"Missing flag '{flag}' on 'signora game'"

    # Check learn flags
    learn_parser = subparser_dict["learn"]
    learn_flags = [opt for act in learn_parser._actions for opt in act.option_strings]
    for flag in ["--host", "--port", "-p", "--kiosk", "--no-camera"]:
        assert flag in learn_flags, f"Missing flag '{flag}' on 'signora learn'"

    # Check doctor flags
    doc_parser = subparser_dict["doctor"]
    doc_flags = [opt for act in doc_parser._actions for opt in act.option_strings]
    assert "--check-only" in doc_flags

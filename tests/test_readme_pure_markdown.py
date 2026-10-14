"""Ensure README.md and documentation are strictly 100% pure Markdown without HTML."""

import re
from pathlib import Path


def test_readme_contains_zero_html():
    readme_path = Path(__file__).resolve().parent.parent / "README.md"
    assert readme_path.exists(), "README.md must exist at repo root"

    content = readme_path.read_text(encoding="utf-8")

    # Match any opening/closing HTML tags like <div, <table, <picture, <br, etc.
    html_pattern = re.compile(r"<\s*/?\s*[a-zA-Z]+(?:\s+[^>]*)?>")
    matches = html_pattern.findall(content)

    assert not matches, f"README.md contains forbidden HTML tags: {matches}"


def test_readme_mentions_all_core_modes():
    readme_path = Path(__file__).resolve().parent.parent / "README.md"
    content = readme_path.read_text(encoding="utf-8")

    assert "signora live" in content
    assert "signora isl" in content
    assert "signora words" in content
    assert "signora game" in content
    assert "signora learn" in content
    assert "signora doctor" in content
    assert "signora tui" in content
    assert "signora alert" in content

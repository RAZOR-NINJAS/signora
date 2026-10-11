"""Tests for the 10 system diagnostic checks."""

from signora.doctor.checks import (
    check_network_port,
    check_operating_system,
    check_python_environment,
    run_all_checks,
)


def test_doctor_python_environment():
    chk = check_python_environment()
    assert chk.passed is True
    assert "3.11" in chk.message or "3.12" in chk.message


def test_doctor_operating_system():
    chk = check_operating_system()
    assert chk.passed is True


def test_doctor_network_port():
    chk = check_network_port()
    assert chk.passed is True


def test_doctor_all_checks_count():
    checks = run_all_checks()
    assert len(checks) == 10, f"Expected exactly 10 checks, got {len(checks)}"
    # Critical failures should be 0 on configured system
    critical_failures = [c for c in checks if not c.passed and not c.warning]
    assert len(critical_failures) == 0, f"Failures: {[c.name for c in critical_failures]}"

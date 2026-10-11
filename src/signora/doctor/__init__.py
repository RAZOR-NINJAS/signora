"""System diagnostics and self-test utility module."""

from .checks import DoctorCheck, main, run_all_checks

__all__ = ["DoctorCheck", "main", "run_all_checks"]

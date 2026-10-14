"""Background daemon manager for Signora Alert (Hear Through Your Eyes)."""

import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

from signora.alert.dispatcher import get_default_alerts_log_path
from signora.alert.engine import AlertEngine


def get_default_pid_path() -> Path:
    """Return PID file path for the background alert daemon."""
    if os.name == "nt":
        base = Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local")))
        d = base / "Signora"
    else:
        xdg = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share")))
        d = xdg / "signora"

    d.mkdir(parents=True, exist_ok=True)
    return d / "alert_daemon.pid"


def is_pid_running(pid: int) -> bool:
    """Check if process with given PID is actively running."""
    if pid <= 0:
        return False
    if os.name == "nt":
        try:
            out = subprocess.check_output(["tasklist", "/FI", f"PID eq {pid}"], text=True)
            return str(pid) in out
        except Exception:
            return False
    else:
        try:
            os.kill(pid, 0)
            return True
        except (OSError, ProcessLookupError):
            return False


def start_daemon(
    sensitivity: str = "medium",
    enable_desktop: bool = True,
    pid_path: Optional[Path] = None,
    log_path: Optional[Path] = None,
) -> int:
    """Spawn the alert monitor daemon in background."""
    pid_file = pid_path or get_default_pid_path()
    log_file = log_path or get_default_alerts_log_path()

    if pid_file.exists():
        try:
            existing_pid = int(pid_file.read_text().strip())
            if is_pid_running(existing_pid):
                print(f"[Signora Alert Daemon] Already running (PID: {existing_pid}).")
                print(f"Log output: {log_file}")
                return 0
        except Exception:
            pass

    # Command to spawn detached daemon process
    python_exe = sys.executable
    cmd = [
        python_exe,
        "-m", "signora.cli",
        "alert",
        "--daemon-loop",
        "--sensitivity", sensitivity,
    ]
    if not enable_desktop:
        cmd.append("--no-desktop")

    print("=" * 65)
    print("  SIGNORA ALERT -- Starting Background Acoustic Watcher Daemon")
    print("=" * 65)
    print(f"  Mode         : Background Daemon ('Hear Through Your Eyes')")
    print(f"  Sensitivity  : {sensitivity.upper()}")
    print(f"  Log File     : {log_file}")
    print(f"  PID File     : {pid_file}")
    print("=" * 65)

    with open(log_file, "a") as f_out:
        proc = subprocess.Popen(
            cmd,
            stdout=f_out,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
        )

    pid_file.write_text(str(proc.pid))
    time.sleep(0.5)

    if proc.poll() is None:
        print(f"✓ Daemon successfully started in background (PID: {proc.pid})")
        print("\nMonitoring for:")
        print("  • 🔔 Doorbell chimes & door knocks")
        print("  • 🚨 Fire alarms & sirens")
        print("  • 🗣️ People calling out / shouting for attention")
        print("  • 🐕 Dog barking & critical sounds\n")
        print("To check status:   signora alert --status")
        print("To stop daemon:    signora alert --stop")
        return 0
    else:
        print(f"[Error] Daemon exited unexpectedly with code {proc.returncode}")
        return 1


def stop_daemon(pid_path: Optional[Path] = None) -> int:
    """Stop running background daemon."""
    pid_file = pid_path or get_default_pid_path()
    if not pid_file.exists():
        print("[Signora Alert Daemon] No active daemon PID file found.")
        return 0

    try:
        pid = int(pid_file.read_text().strip())
    except Exception:
        print("[Signora Alert Daemon] Corrupted PID file removed.")
        pid_file.unlink(missing_ok=True)
        return 0

    if not is_pid_running(pid):
        print(f"[Signora Alert Daemon] Process {pid} is not running.")
        pid_file.unlink(missing_ok=True)
        return 0

    print(f"[Signora Alert Daemon] Stopping background daemon (PID: {pid})...")
    try:
        if os.name == "nt":
            subprocess.run(["taskkill", "/F", "/PID", str(pid)], capture_output=True)
        else:
            os.kill(pid, signal.SIGTERM)
            for _ in range(20):
                time.sleep(0.1)
                if not is_pid_running(pid):
                    break
            if is_pid_running(pid):
                os.kill(pid, signal.SIGKILL)
    except Exception as e:
        print(f"[Warning] Signal error: {e}")

    pid_file.unlink(missing_ok=True)
    print("✓ Daemon stopped successfully.")
    return 0


def status_daemon(
    pid_path: Optional[Path] = None, log_path: Optional[Path] = None
) -> int:
    """Inspect background daemon health and display recent alert log."""
    pid_file = pid_path or get_default_pid_path()
    log_file = log_path or get_default_alerts_log_path()

    print("=" * 65)
    print("  SIGNORA ALERT DAEMON STATUS")
    print("=" * 65)

    if not pid_file.exists():
        print("  Status: [INACTIVE / STOPPED]")
        print("  Start with: signora alert --start")
    else:
        try:
            pid = int(pid_file.read_text().strip())
            if is_pid_running(pid):
                print(f"  Status       : [ACTIVE / RUNNING] (PID: {pid})")
                print(f"  PID File     : {pid_file}")
                print(f"  Log File     : {log_file}")
            else:
                print(f"  Status       : [DEAD / NOT RUNNING] (Stale PID: {pid})")
                pid_file.unlink(missing_ok=True)
        except Exception:
            print("  Status: [CORRUPTED PID FILE]")

    print("\nRecent Alert Log Events (last 10 entries):")
    print("-" * 65)
    if log_file.exists():
        try:
            lines = log_file.read_text(encoding="utf-8").strip().splitlines()
            if lines:
                for line in lines[-10:]:
                    print(f"  {line}")
            else:
                print("  (Log file is empty)")
        except Exception as e:
            print(f"  (Could not read log file: {e})")
    else:
        print("  (No log file created yet)")
    print("=" * 65)
    return 0


def run_daemon_loop(
    sensitivity: str = "medium",
    enable_desktop: bool = True,
    use_fake_mic: bool = False,
) -> int:
    """Internal loop executed inside the detached background daemon process."""
    engine = AlertEngine(
        sensitivity=sensitivity,
        enable_desktop=enable_desktop,
        use_fake_mic=use_fake_mic,
    )

    if not engine.start():
        return 1

    running = True

    def _sig_handler(signum, frame):
        nonlocal running
        running = False

    signal.signal(signal.SIGINT, _sig_handler)
    signal.signal(signal.SIGTERM, _sig_handler)

    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Signora Alert background watcher active.")

    try:
        while running:
            engine.step()
            time.sleep(0.04)  # ~25 Hz checking cadence (near-zero CPU load)
    finally:
        engine.stop()
        print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Signora Alert background watcher stopped.")

    return 0

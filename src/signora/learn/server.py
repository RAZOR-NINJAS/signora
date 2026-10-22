"""Browser-based Sign Language Learning Kiosk server.

Provides interactive sign practice, timed quizzes, and drills with live camera verification.
Deliberately stdlib-only: zero third-party web framework dependencies.
"""

import json
import mimetypes
import random
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

from signora.asl.config import LETTERS
from signora.platform_compat import launch_kiosk_browser

STATIC_DIR = Path(__file__).resolve().parent / "static"


class LearnHub:
    """Manages learning session drills, quiz rounds, and detection feedback."""

    def __init__(
        self,
        source: Any = 0,
        use_fake: bool = False,
        width: int = 1280,
        height: int = 720,
    ):
        self.source = source
        self.use_fake = use_fake
        self.width = width
        self.height = height
        self.reader = None
        self._lock = threading.RLock()

        self.mode = "drill"  # "drill" or "quiz"
        self.category = "asl_alphabet"
        self.available_items = list(LETTERS)
        self.current_target: Optional[str] = "A"
        self.score = 0
        self.streak = 0
        self.total_completed = 0
        self.quiz_timer_start: Optional[float] = None
        self.quiz_time_limit: int = 15  # seconds per quiz item
        self.history: List[Dict[str, Any]] = []

    def start_detector(self) -> tuple[bool, str]:
        if self.reader is not None:
            return True, "already running"
        from signora.game.detector import LetterReader

        reader = LetterReader(
            source=self.source,
            hold_ms=500,
            min_conf=0.55,
            use_fake=self.use_fake,
            width=self.width,
            height=self.height,
        )
        try:
            ok = reader.start()
        except Exception as exc:
            return False, str(exc)
        if not ok:
            err = reader.error()
            return False, str(err) if err else "camera failed to initialize"
        self.reader = reader
        return True, "camera running"

    def stop_detector(self) -> None:
        if self.reader is not None:
            self.reader.stop()
            self.reader = None

    def set_target(self, target: str) -> None:
        with self._lock:
            if target.upper() in self.available_items:
                self.current_target = target.upper()

    def next_random_target(self) -> str:
        with self._lock:
            pool = [c for c in self.available_items if c != self.current_target]
            self.current_target = random.choice(pool) if pool else self.available_items[0]
            if self.mode == "quiz":
                self.quiz_timer_start = time.monotonic()
            return self.current_target

    def record_success(self, detected: str) -> Dict[str, Any]:
        with self._lock:
            if detected == self.current_target:
                self.score += 10 + 2 * min(self.streak, 5)
                self.streak += 1
                self.total_completed += 1
                prev_target = self.current_target
                self.history.append(
                    {
                        "target": prev_target,
                        "result": "correct",
                        "timestamp": time.time(),
                    }
                )
                next_t = self.next_random_target()
                return {
                    "ok": True,
                    "prev": prev_target,
                    "next": next_t,
                    "score": self.score,
                    "streak": self.streak,
                }
            return {"ok": False, "error": "target mismatch"}

    def snapshot(self) -> Dict[str, Any]:
        letter, conf, fps, stale, frames, hands = (
            self.reader.reading() if self.reader else (None, 0.0, 0.0, 0.0, 0, 0)
        )
        with self._lock:
            quiz_remaining = 0
            if self.mode == "quiz" and self.quiz_timer_start:
                elapsed = time.monotonic() - self.quiz_timer_start
                quiz_remaining = max(0, int(self.quiz_time_limit - elapsed))
                if quiz_remaining == 0:
                    self.streak = 0
                    self.history.append(
                        {
                            "target": self.current_target,
                            "result": "timeout",
                            "timestamp": time.time(),
                        }
                    )
                    self.next_random_target()

            is_correct = bool(
                letter and self.current_target and letter == self.current_target and conf >= 0.55
            )
            return {
                "mode": self.mode,
                "category": self.category,
                "target": self.current_target,
                "detected": letter,
                "confidence": round(conf, 3),
                "is_correct": is_correct,
                "score": self.score,
                "streak": self.streak,
                "total_completed": self.total_completed,
                "quiz_remaining": quiz_remaining,
                "camera": {
                    "on": self.reader is not None,
                    "fps": round(fps, 1),
                    "stale_s": round(stale, 2),
                    "hands": hands,
                },
                "items": self.available_items,
            }


def make_learn_handler(hub: LearnHub, stop_event: threading.Event):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            pass

        def _send(self, code: int, body: bytes = b"", ctype: str = "application/json"):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if body:
                self.wfile.write(body)

        def _json(self, obj: Any, code: int = 200):
            self._send(code, json.dumps(obj).encode("utf-8"))

        def do_GET(self):
            url = urlparse(self.path)
            route = url.path

            if route in ("/", "/learn"):
                return self._serve_static("index.html")
            if route.startswith("/static/"):
                return self._serve_static(route[len("/static/") :])
            if route == "/api/state":
                return self._json(hub.snapshot())
            if route == "/api/preview.jpg":
                return self._serve_preview()
            return self._json({"error": "not found"}, 404)

        def do_POST(self):
            url = urlparse(self.path)
            length = int(self.headers.get("Content-Length") or 0)
            payload = {}
            if length:
                try:
                    payload = json.loads(self.rfile.read(length) or b"{}")
                except json.JSONDecodeError:
                    return self._json({"error": "bad json"}, 400)

            if url.path == "/api/camera":
                if payload.get("on"):
                    ok, msg = hub.start_detector()
                else:
                    hub.stop_detector()
                    ok, msg = True, "camera off"
                return self._json({"ok": ok, "message": msg})

            if url.path == "/api/set_target":
                target = payload.get("target", "A")
                hub.set_target(target)
                return self._json({"ok": True, "target": hub.current_target})

            if url.path == "/api/next":
                nxt = hub.next_random_target()
                return self._json({"ok": True, "target": nxt})

            if url.path == "/api/success":
                detected = payload.get("detected", "")
                res = hub.record_success(detected)
                return self._json(res)

            if url.path == "/api/mode":
                mode = payload.get("mode", "drill")
                if mode in ("drill", "quiz"):
                    hub.mode = mode
                    if mode == "quiz":
                        hub.quiz_timer_start = time.monotonic()
                return self._json({"ok": True, "mode": hub.mode})

            return self._json({"error": "not found"}, 404)

        def _serve_preview(self):
            if hub.reader is None:
                return self._json({"error": "camera off"}, 503)
            jpeg, _seq = hub.reader.preview()
            if not jpeg:
                return self._json({"error": "no frame yet"}, 503)
            self._send(200, jpeg, "image/jpeg")

        def _serve_static(self, name: str):
            safe = Path(name)
            if safe.is_absolute() or ".." in safe.parts:
                return self._json({"error": "bad path"}, 400)
            path = STATIC_DIR / safe
            if not path.is_file():
                return self._json({"error": "not found"}, 404)
            ctype, _ = mimetypes.guess_type(str(path))
            if ctype is None:
                ctype = "application/octet-stream"
            if ctype.startswith("text/") or "javascript" in ctype:
                ctype += "; charset=utf-8"
            self._send(200, path.read_bytes(), ctype)

    return Handler


def serve(
    host: str = "127.0.0.1",
    port: int = 8000,
    source: Any = 0,
    kiosk: bool = False,
    camera: bool = True,
    use_fake: bool = False,
    width: int = 1280,
    height: int = 720,
) -> None:
    """Start the Learning Kiosk server."""
    hub = LearnHub(
        source=source,
        use_fake=use_fake,
        width=width,
        height=height,
    )
    stop_event = threading.Event()

    print("[signora learn] Interactive Learning Kiosk starting...")
    if camera:
        ok, msg = hub.start_detector()
        print(f"  Camera status: {msg}")

    server = ThreadingHTTPServer((host, port), make_learn_handler(hub, stop_event))
    url = f"http://{host}:{port}/"
    print(f"  Learning Kiosk URL: {url}")
    print("  Press Ctrl+C to terminate.")

    kiosk_proc = None
    if kiosk:
        kiosk_proc = launch_kiosk_browser(url, kiosk=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[signora learn] Stopping server...")
    finally:
        stop_event.set()
        hub.stop_detector()
        if kiosk_proc is not None:
            try:
                kiosk_proc.terminate()
            except Exception:
                pass
        server.shutdown()
        server.server_close()

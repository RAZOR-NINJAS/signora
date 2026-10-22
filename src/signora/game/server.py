"""Lightweight standard-library HTTP server for the shadow-play game."""

import json
import mimetypes
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

from signora.game.pool import LetterPool, build_pool
from signora.game.scoring import GameConfig, GameState
from signora.platform_compat import launch_kiosk_browser

STATIC_DIR = Path(__file__).resolve().parent / "static"


class GameHub:
    """Owns run state, camera detector thread, and 60Hz animation ticker."""

    def __init__(
        self,
        source: Any = 0,
        config: Optional[GameConfig] = None,
        include: Optional[List[str]] = None,
        exclude: Optional[List[str]] = None,
        seed: Optional[int] = None,
        use_fake: bool = False,
        mode: str = "asl",
        width: int = 1280,
        height: int = 720,
    ):
        self.mode = str(mode or "asl").lower()
        self.include = include
        self.exclude = exclude
        self.seed = seed
        self.source = source
        self.use_fake = use_fake
        self.width = width
        self.height = height
        self._lock = threading.Lock()
        self.reader = None

        cfg = config or GameConfig(mode=self.mode)
        cfg.mode = self.mode
        pool = LetterPool(
            build_pool(include=include, exclude=exclude, mode=self.mode),
            rng=seed,
        )
        self.state = GameState(cfg, pool)

    def set_mode(self, new_mode: str) -> None:
        """Switch between ASL and ISL gesture modes dynamically."""
        new_mode = str(new_mode or "asl").lower()
        if new_mode not in ("asl", "isl"):
            return
        with self._lock:
            if new_mode == self.mode:
                return
            self.mode = new_mode
            self.state.config.mode = new_mode
            self.state.pool = LetterPool(
                build_pool(include=self.include, exclude=self.exclude, mode=new_mode),
                rng=self.seed,
            )
            if self.reader is not None:
                self.reader.set_mode(new_mode)

    def start_detector(self) -> tuple[bool, str]:
        if self.reader is not None:
            return True, "already running"
        from signora.game.detector import LetterReader

        reader = LetterReader(
            source=self.source,
            hold_ms=self.state.config.hold_ms,
            use_fake=self.use_fake,
            mode=self.mode,
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

    def start_run(self) -> Dict[str, Any]:
        if self.state.pool.empty:
            return {"ok": False, "error": "no usable letters in the pool"}
        with self._lock:
            self.state.start()
        return {"ok": True, "pool": self.state.pool.letters}

    def snapshot(self) -> Dict[str, Any]:
        letter, conf, fps, stale, frames, hands = (
            self.reader.reading() if self.reader else (None, 0.0, 0.0, 0.0, 0, 0)
        )
        with self._lock:
            snap = self.state.snapshot(detected=letter, confidence=conf)
        snap["camera"] = {
            "on": self.reader is not None,
            "fps": round(fps, 1),
            "stale_s": round(stale, 2),
            "frames": frames,
            "hands_seen": hands,
        }
        snap["mode"] = self.mode
        snap["pool_size"] = len(self.state.pool)
        snap["pool"] = self.state.pool.letters
        return snap

    def tick_loop(self, stop_event: threading.Event, hz: int = 60) -> None:
        period = 1.0 / hz
        while not stop_event.is_set():
            letter, conf = (None, 0.0)
            if self.reader is not None:
                letter, conf = self.reader.reading()[:2]
            with self._lock:
                if letter is not None and conf:
                    self.state.observe(letter, conf)
                elif letter is None and self.state.phase in ("ready", "answer"):
                    self.state.observe(None, 0.0)
                self.state.tick()
            stop_event.wait(period)


def make_handler(hub: GameHub, stop_event: threading.Event):
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

            if route in ("/", "/game"):
                return self._serve_static("index.html")
            if route.startswith("/static/"):
                return self._serve_static(route[len("/static/") :])
            if route == "/api/state":
                return self._json(hub.snapshot())
            if route == "/api/preview.jpg":
                return self._serve_preview()
            if route == "/api/pool":
                return self._json({"pool": hub.state.pool.letters})
            if route == "/api/config":
                return self._json(hub.state.config.as_dict())
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

            if url.path == "/api/start":
                return self._json(hub.start_run())
            if url.path == "/api/camera":
                if payload.get("on"):
                    ok, msg = hub.start_detector()
                else:
                    hub.stop_detector()
                    ok, msg = True, "camera off"
                return self._json({"ok": ok, "message": msg})
            if url.path == "/api/settings":
                if "mode" in payload:
                    m = str(payload["mode"]).lower()
                    if m in ("asl", "isl"):
                        hub.set_mode(m)
                cfg = hub.state.config
                for key in ("ready_ms", "answer_ms", "hold_ms", "max_cacti"):
                    if key in payload:
                        try:
                            setattr(cfg, key, int(payload[key]))
                        except (TypeError, ValueError):
                            return self._json({"error": f"bad {key}"}, 400)
                if "min_conf" in payload:
                    try:
                        cfg.min_conf = float(payload["min_conf"])
                    except (TypeError, ValueError):
                        return self._json({"error": "bad min_conf"}, 400)
                return self._json(cfg.as_dict())
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
    config: Optional[GameConfig] = None,
    include: Optional[List[str]] = None,
    exclude: Optional[List[str]] = None,
    camera: bool = True,
    kiosk: bool = False,
    use_fake: bool = False,
    mode: str = "asl",
    width: int = 1280,
    height: int = 720,
) -> None:
    """Start the Dino gesture game server."""
    hub = GameHub(
        source=source,
        config=config,
        include=include,
        exclude=exclude,
        use_fake=use_fake,
        mode=mode,
        width=width,
        height=height,
    )
    stop_event = threading.Event()

    print("[signora game] Shadow-play Dino runner initializing...")
    print(
        f"  Sign language mode: {hub.mode.upper()} ({'Dual-handed' if hub.mode == 'isl' else 'Single-handed'})"
    )
    print(f"  Letters in pool: {len(hub.state.pool)} ({' '.join(hub.state.pool.letters)})")
    print(
        f"  Timing: ready {hub.state.config.ready_ms}ms, answer window {hub.state.config.answer_ms}ms"
    )

    if camera:
        ok, msg = hub.start_detector()
        print(f"  Camera status: {msg}")
        if not ok:
            print(
                "  Notice: camera did not start automatically. You can toggle it in the browser UI."
            )

    server = ThreadingHTTPServer((host, port), make_handler(hub, stop_event))
    clock = threading.Thread(target=hub.tick_loop, args=(stop_event,), daemon=True)
    clock.start()

    url = f"http://{host}:{port}/game"
    print(f"  Serving on: {url}")
    print("  Press Ctrl+C to terminate server.")

    kiosk_proc = None
    if kiosk:
        kiosk_proc = launch_kiosk_browser(url, kiosk=True)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[signora game] Shutting down...")
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

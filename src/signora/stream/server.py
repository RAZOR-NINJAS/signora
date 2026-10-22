"""Low-latency local HTTP MJPEG video streaming server.

Allows viewing the Signora stream in any web browser, OBS Studio (Browser Source),
or video tools over localhost without requiring root privileges or v4l2loopback.
"""

import http.server
import json
import socketserver
import threading
import time
from typing import Any, Dict, Optional

import cv2
import numpy as np


class StreamingOutput:
    """Thread-safe frame buffer for MJPEG streaming."""

    def __init__(self) -> None:
        self.frame: Optional[bytes] = None
        self.condition = threading.Condition()
        self.status: Dict[str, Any] = {
            "active_sign": "",
            "dwell": 0.0,
            "sentence": "",
            "speaking": False,
            "fps": 0.0,
        }

    def update_frame(self, bgr_frame: np.ndarray, quality: int = 85) -> None:
        """Encode and buffer a new BGR frame."""
        success, encoded = cv2.imencode(".jpg", bgr_frame, [int(cv2.IMWRITE_JPEG_QUALITY), quality])
        if success:
            with self.condition:
                self.frame = encoded.tobytes()
                self.condition.notify_all()

    def update_status(self, **kwargs: Any) -> None:
        with self.condition:
            self.status.update(kwargs)


class StreamingHandler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler for MJPEG video and status API."""

    output: Optional[StreamingOutput] = None

    def log_message(self, format: str, *args: Any) -> None:
        # Suppress noisy HTTP access logs
        pass

    def do_GET(self) -> None:
        if self.path == "/" or self.path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            html = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Signora Virtual Stream</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #090d16;
      color: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 24px;
    }
    .container {
      max-width: 960px;
      width: 100%;
      background: #0f172a;
      border: 1px solid #1e293b;
      border-radius: 20px;
      overflow: hidden;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
    }
    .header {
      padding: 18px 24px;
      background: #1e293b;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .title {
      font-weight: 800;
      font-size: 1.1rem;
      letter-spacing: 0.05em;
      color: #38bdf8;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .badge {
      display: inline-block;
      width: 10px;
      height: 10px;
      background: #10b981;
      border-radius: 50%;
      box-shadow: 0 0 10px #10b981;
    }
    .video-viewport {
      width: 100%;
      background: #000;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .video-viewport img {
      width: 100%;
      height: auto;
      max-height: 70vh;
      object-fit: contain;
      display: block;
    }
    .footer {
      padding: 18px 24px;
      background: #0f172a;
      font-size: 0.88rem;
      color: #94a3b8;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-top: 1px solid #1e293b;
    }
    .code {
      background: #1e293b;
      padding: 4px 8px;
      border-radius: 6px;
      color: #38bdf8;
      font-family: monospace;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div class="title"><span class="badge"></span> SIGNORA VIRTUAL STREAM</div>
      <div style="font-size: 0.85rem; color: #94a3b8;">Real-Time Captioning Feed</div>
    </div>
    <div class="video-viewport">
      <img src="/video" alt="Signora Live Stream">
    </div>
    <div class="footer">
      <div>Source URL: <span class="code">http://localhost:8888/video</span> (Use in OBS Browser Source)</div>
      <div style="color: #10b981;">Online & Active</div>
    </div>
  </div>
</body>
</html>"""
            self.wfile.write(html.encode("utf-8"))

        elif self.path == "/video":
            if self.output is None:
                self.send_error(503, "Stream output not available")
                return

            self.send_response(200)
            self.send_header(
                "Content-Type", "multipart/x-mixed-replace; boundary=frame"
            )
            self.send_header("Cache-Control", "no-cache, private")
            self.send_header("Pragma", "no-cache")
            self.end_headers()

            try:
                while True:
                    with self.output.condition:
                        self.output.condition.wait()
                        frame_bytes = self.output.frame

                    if frame_bytes:
                        self.wfile.write(b"--frame\r\n")
                        self.send_header("Content-Type", "image/jpeg")
                        self.send_header("Content-Length", str(len(frame_bytes)))
                        self.end_headers()
                        self.wfile.write(frame_bytes)
                        self.wfile.write(b"\r\n")
            except (ConnectionResetError, BrokenPipeError):
                pass

        elif self.path == "/api/status":
            if self.output is None:
                self.send_error(503, "Status not available")
                return

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            with self.output.condition:
                data = json.dumps(self.output.status)
            self.wfile.write(data.encode("utf-8"))

        else:
            self.send_error(404, "Not Found")


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


class WebStreamServer:
    """Manages background HTTP MJPEG server."""

    def __init__(self, host: str = "127.0.0.1", port: int = 8888) -> None:
        self.host = host
        self.port = port
        self.output = StreamingOutput()
        self.server: Optional[ThreadedHTTPServer] = None
        self.thread: Optional[threading.Thread] = None
        self._running = False

    def start(self) -> bool:
        """Start the background streaming server."""
        try:
            StreamingHandler.output = self.output
            self.server = ThreadedHTTPServer((self.host, self.port), StreamingHandler)
            self._running = True
            self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
            self.thread.start()
            return True
        except Exception as e:
            print(f"[StreamServer] Failed to bind HTTP server on {self.host}:{self.port} - {e}")
            return False

    def push_frame(self, frame: np.ndarray) -> None:
        """Push a newly rendered frame to web clients."""
        if self._running:
            self.output.update_frame(frame)

    def push_status(self, **kwargs: Any) -> None:
        """Push status metadata."""
        if self._running:
            self.output.update_status(**kwargs)

    def stop(self) -> None:
        """Stop the streaming server."""
        self._running = False
        if self.server:
            try:
                self.server.shutdown()
                self.server.server_close()
            except Exception:
                pass

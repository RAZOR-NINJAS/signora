"""Background hand classification worker for the shadow-play game."""

import threading
import time
from typing import Any, Optional, Tuple

import cv2
import numpy as np

from signora.asl.model import Predictor
from signora.core.features import landmark_features, normalize_hand
from signora.core.hands import HandPipeline
from signora.core.hud import draw_cyber_hands

PREVIEW_WIDTH = 320


class LetterReader:
    """Classifies ASL or ISL hand shapes on a background thread for browser game polling."""

    def __init__(
        self,
        source: Any = 0,
        predictor: Optional[Any] = None,
        hold_ms: int = 420,
        min_conf: float = 0.0,
        use_fake: bool = False,
        mode: str = "asl",
        width: int = 1280,
        height: int = 720,
    ):
        self.source = source
        self.hold_ms = int(hold_ms)
        self.min_conf = float(min_conf)
        self.use_fake = use_fake
        self.mode = str(mode or "asl").lower()
        self.width = width
        self.height = height
        self._predictor = predictor
        self._lock = threading.Lock()
        self._letter: Optional[str] = None
        self._conf = 0.0
        self._since = 0.0
        self._last_frame_at = 0.0
        self._frames = 0
        self._hands_seen = 0
        self._fps = 0.0
        self._error: Optional[Exception] = None
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._pipeline: Optional[HandPipeline] = None
        self._preview: Optional[bytes] = None
        self._preview_at = 0.0
        self._preview_seq = 0

    def reading(self) -> Tuple[Optional[str], float, float, float, int, int]:
        """(letter, confidence, fps, stale_seconds, frames, hands_seen)."""
        with self._lock:
            letter, conf = self._letter, self._conf
            last = self._last_frame_at
            fps = self._fps
            frames, hands = self._frames, self._hands_seen
        stale = (time.monotonic() - last) if last else 0.0
        return letter, conf, fps, stale, frames, hands

    def error(self) -> Optional[Exception]:
        return self._error

    def preview(self) -> Tuple[Optional[bytes], int]:
        """(jpeg_bytes, sequence) for browser live preview."""
        with self._lock:
            return self._preview, self._preview_seq

    def start(self) -> bool:
        if self._predictor is None:
            if self.mode == "isl":
                try:
                    from signora.isl.model import Predictor as ISLPredictor

                    self._predictor = ISLPredictor()
                except FileNotFoundError:
                    from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models
                    from signora.models.manifest import get_default_models_dir

                    fetch_models(DEFAULT_MANIFEST, get_default_models_dir())
                    from signora.isl.model import Predictor as ISLPredictor

                    self._predictor = ISLPredictor()
            else:
                try:
                    self._predictor = Predictor()
                except FileNotFoundError:
                    from scripts.fetch_models import DEFAULT_MANIFEST, fetch_models
                    from signora.models.manifest import get_default_models_dir

                    fetch_models(DEFAULT_MANIFEST, get_default_models_dir())
                    self._predictor = Predictor()

        num_hands = 2 if self.mode == "isl" else 1
        self._pipeline = HandPipeline(
            source=self.source,
            num_hands=num_hands,
            use_fake=self.use_fake,
            width=self.width,
            height=self.height,
        )
        self._pipeline.started.wait(timeout=15)
        if self._pipeline.error:
            self._error = self._pipeline.error
            self._pipeline.close()
            self._pipeline = None
            return False

        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()
        return True

    def _loop(self) -> None:
        window_start = time.monotonic()
        window_frames = 0
        while not self._stop.is_set():
            if self._pipeline is None:
                break
            try:
                frame, landmarks, handedness = self._pipeline.read(timeout=2.0)
            except Exception as exc:
                self._error = exc
                return

            if frame is None:
                continue

            now = time.monotonic()
            window_frames += 1
            if now - window_start >= 1.0:
                fps = window_frames / (now - window_start)
                with self._lock:
                    self._fps = fps
                window_start, window_frames = now, 0

            if landmarks is not None:
                draw_cyber_hands(frame, landmarks, handedness)
            self._encode_preview(frame, now)

            if landmarks is None:
                with self._lock:
                    self._letter = None
                    self._conf = 0.0
                    self._since = 0.0
                    self._last_frame_at = now
                    self._frames += 1
                continue

            letter, conf = self._classify(landmarks, handedness)
            with self._lock:
                self._frames += 1
                self._hands_seen += 1
                self._last_frame_at = now
                if letter != self._letter:
                    self._letter = letter
                    self._conf = conf
                    self._since = now
                else:
                    self._conf = 0.7 * self._conf + 0.3 * conf
                    self._since = self._since or now

    def _encode_preview(self, frame: np.ndarray, now: float) -> None:
        if now - self._preview_at < 0.08:
            return
        self._preview_at = now
        try:
            h, w = frame.shape[:2]
            if w > PREVIEW_WIDTH:
                scale = PREVIEW_WIDTH / w
                small = cv2.resize(
                    frame,
                    (PREVIEW_WIDTH, max(1, int(h * scale))),
                    interpolation=cv2.INTER_AREA,
                )
            else:
                small = frame
            ok, buf = cv2.imencode(".jpg", small, [cv2.IMWRITE_JPEG_QUALITY, 72])
            if not ok:
                return
            with self._lock:
                self._preview = buf.tobytes()
                self._preview_seq += 1
        except Exception:
            pass

    def _classify(self, landmarks: Any, handedness: Any) -> Tuple[Optional[str], float]:
        if self._predictor is None:
            return None, 0.0
        try:
            if self.mode == "isl":
                from signora.isl.features import extract_features

                lms_list = (
                    landmarks
                    if isinstance(landmarks, list) and isinstance(landmarks[0], (list, tuple))
                    else [landmarks]
                )
                hnd_list = handedness if isinstance(handedness, list) else [handedness]
                feats = extract_features(lms_list, hnd_list)
                probs = self._predictor.probs(feats)
            else:
                lms = (
                    landmarks[0]
                    if (
                        isinstance(landmarks, list)
                        and landmarks
                        and isinstance(landmarks[0], (list, tuple))
                    )
                    else landmarks
                )
                hnd = handedness[0] if (isinstance(handedness, list) and handedness) else handedness
                pts = normalize_hand([(lm.x, lm.y, lm.z) for lm in lms], hnd)
                feats = landmark_features(pts)
                probs = self._predictor.probs(feats)
        except Exception:
            return None, 0.0
        i = int(np.argmax(probs))
        return self._predictor.labels[i], float(probs[i])

    def set_mode(self, new_mode: str) -> None:
        """Switch between ASL and ISL recognition dynamically."""
        new_mode = str(new_mode or "asl").lower()
        if new_mode == self.mode:
            return
        was_running = self._thread is not None and self._thread.is_alive()
        self.stop()
        self.mode = new_mode
        self._predictor = None
        self._letter = None
        self._conf = 0.0
        self._stop.clear()
        if was_running:
            self.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=3.0)
        if self._pipeline is not None:
            try:
                self._pipeline.close()
            except Exception:
                pass
            self._pipeline = None

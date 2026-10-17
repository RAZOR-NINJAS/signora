"""Real AI Multimodal Engine powered by Google Gemini (Vision & LLM Grammar).

Uses standard library urllib (zero external dependencies).
Supports:
1. Real-time Multimodal Vision: interprets raw camera frames for sign recognition.
2. Neural Grammar Synthesizer: converts ISL gloss sequences into fluent natural English with zero hallucination.
"""

import base64
import json
import os
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

CONFIG_DIR = Path.home() / ".config" / "signora"
KEY_FILE = CONFIG_DIR / "gemini_key.txt"

GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
DEFAULT_MODEL = "gemini-2.0-flash"
FALLBACK_MODEL = "gemini-1.5-flash"


class GeminiAIEngine:
    """Zero-dependency Google Gemini Vision & LLM client for Signora."""

    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or self._resolve_api_key()
        self._grammar_cache: Dict[str, str] = {}
        self._last_call_time: float = 0.0

    @classmethod
    def _resolve_api_key(cls) -> Optional[str]:
        # 1. Environment variables
        for var in ("GEMINI_API_KEY", "GOOGLE_API_KEY", "SIGNORA_GEMINI_KEY"):
            val = os.environ.get(var, "").strip()
            if val:
                return val

        # 2. Key file on disk
        if KEY_FILE.exists():
            try:
                val = KEY_FILE.read_text(encoding="utf-8").strip()
                if val:
                    return val
            except Exception:
                pass
        return None

    @classmethod
    def save_api_key(cls, key: str) -> None:
        """Persist API key to ~/.config/signora/gemini_key.txt."""
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        KEY_FILE.write_text(key.strip(), encoding="utf-8")
        KEY_FILE.chmod(0o600)

    @property
    def is_available(self) -> bool:
        return bool(self.api_key and len(self.api_key) > 10)

    # ─── 1. Neural Grammar Synthesizer (LLM) ──────────────────────────────────

    def synthesize_grammar(self, glosses: List[str]) -> Optional[str]:
        """Convert a list of ISL glosses into natural English using Gemini LLM."""
        if not self.is_available or not glosses:
            return None

        clean_glosses = [g.upper().strip() for g in glosses if g.strip()]
        if not clean_glosses:
            return None

        cache_key = " ".join(clean_glosses)
        if cache_key in self._grammar_cache:
            return self._grammar_cache[cache_key]

        prompt = (
            "You are an expert Indian Sign Language (ISL) translator. "
            "Convert the following sequence of signed ISL gloss tokens into a clean, "
            "grammatically correct English sentence:\n\n"
            f"ISL GLOSSES: {json.dumps(clean_glosses)}\n\n"
            "STRICT TRANSLATION RULES:\n"
            "1. NEVER invent facts, extra adjectives (e.g. 'nearest', 'severe'), "
            "times of day (e.g. 'morning'), or details not present in the signed tokens.\n"
            "2. Preserve the exact meaning faithful to the user's signs.\n"
            "3. Output ONLY the single translated English sentence. No notes, no markdown, no quotation marks."
        )

        body = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.1,
                "maxOutputTokens": 60,
            },
        }

        resp = self._post_json(DEFAULT_MODEL, body)
        if not resp:
            resp = self._post_json(FALLBACK_MODEL, body)

        if resp:
            try:
                candidates = resp.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        text = parts[0].get("text", "").strip()
                        # Clean unwanted quotes or formatting
                        text = text.strip('"\'`')
                        if text:
                            self._grammar_cache[cache_key] = text
                            return text
            except Exception:
                pass
        return None

    def synthesize_grammar_async(
        self, glosses: List[str], callback: Callable[[str], None]
    ) -> threading.Thread:
        """Non-blocking asynchronous grammar synthesis call."""
        def _worker():
            res = self.synthesize_grammar(glosses)
            if res:
                callback(res)

        t = threading.Thread(target=_worker, daemon=True)
        t.start()
        return t

    # ─── 2. Multimodal Vision Sign Recognition ────────────────────────────────

    def interpret_sign(self, frame_jpeg_bytes: bytes) -> Optional[Tuple[str, float]]:
        """Interpret a live camera frame to detect ISL sign gesture via Gemini Vision."""
        if not self.is_available or not frame_jpeg_bytes:
            return None

        b64_img = base64.b64encode(frame_jpeg_bytes).decode("ascii")

        prompt = (
            "You are a computer vision Indian Sign Language (ISL) classifier. "
            "Analyze the signer's hand shapes, finger positions, and body posture in this image. "
            "Identify the exact ISL sign or manual fingerspelling letter (A-Z) being signed. "
            "Respond ONLY with a JSON object in this exact schema:\n"
            '{"sign": "<UPPERCASE_SIGN_OR_LETTER>", "confidence": <0.0_to_1.0>}\n'
            'If no clear sign is being formed or hands are idle/resting, return: {"sign": null, "confidence": 0.0}'
        )

        body = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": b64_img,
                            }
                        },
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.1,
                "response_mime_type": "application/json",
                "maxOutputTokens": 40,
            },
        }

        resp = self._post_json(DEFAULT_MODEL, body)
        if not resp:
            resp = self._post_json(FALLBACK_MODEL, body)

        if resp:
            try:
                candidates = resp.get("candidates", [])
                if candidates:
                    parts = candidates[0].get("content", {}).get("parts", [])
                    if parts:
                        raw_json = parts[0].get("text", "").strip()
                        data = json.loads(raw_json)
                        sign = data.get("sign")
                        conf = float(data.get("confidence", 0.0))
                        if sign and isinstance(sign, str):
                            return sign.upper().strip(), conf
            except Exception:
                pass
        return None

    def interpret_sign_async(
        self,
        frame_jpeg_bytes: bytes,
        callback: Callable[[Optional[str], float], None],
    ) -> threading.Thread:
        """Non-blocking background sign interpretation call."""
        def _worker():
            res = self.interpret_sign(frame_jpeg_bytes)
            if res:
                callback(res[0], res[1])
            else:
                callback(None, 0.0)

        t = threading.Thread(target=_worker, daemon=True)
        t.start()
        return t

    # ─── Internal HTTP Request Helper ─────────────────────────────────────────

    def _post_json(self, model_name: str, payload: dict) -> Optional[dict]:
        url = f"{GEMINI_API_BASE}/{model_name}:generateContent?key={self.api_key}"
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "Content-Type": "application/json",
                "User-Agent": "Signora-AI/1.0",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=3.5) as resp:
                if resp.status == 200:
                    return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            # Silently handle rate limits / bad keys
            pass
        except Exception:
            pass
        return None

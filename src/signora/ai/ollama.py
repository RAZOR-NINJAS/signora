"""Local Offline LLM Engine powered by Ollama (Gemma, Llama, Qwen).

Uses standard library urllib (zero external dependencies).
Queries local Ollama daemon at http://localhost:11434.
"""

import json
import threading
import urllib.error
import urllib.request
from typing import Callable, Dict, List, Optional

OLLAMA_BASE_URL = "http://localhost:11434"
DEFAULT_OLLAMA_MODEL = "llama3"


class OllamaAIEngine:
    """Local offline LLM client for Signora using Ollama."""

    def __init__(self, model_name: str = DEFAULT_OLLAMA_MODEL, host: str = OLLAMA_BASE_URL):
        self.model_name = model_name
        self.host = host.rstrip("/")
        self._grammar_cache: Dict[str, str] = {}

    def is_available(self) -> bool:
        """Check if local Ollama daemon is reachable."""
        try:
            req = urllib.request.Request(f"{self.host}/api/tags", headers={"User-Agent": "Signora-AI/1.0"})
            with urllib.request.urlopen(req, timeout=0.8) as resp:
                return resp.status == 200
        except Exception:
            return False

    def synthesize_grammar(self, glosses: List[str]) -> Optional[str]:
        """Convert a list of ISL glosses into natural English using local Ollama model."""
        clean_glosses = [g.upper().strip() for g in glosses if g.strip()]
        if not clean_glosses:
            return None

        cache_key = " ".join(clean_glosses)
        if cache_key in self._grammar_cache:
            return self._grammar_cache[cache_key]

        prompt = (
            "You are an Indian Sign Language (ISL) translator. "
            "Convert the following sequence of signed ISL gloss tokens into a natural, "
            "grammatically correct English sentence without adding extra information or adjectives.\n\n"
            f"ISL GLOSSES: {json.dumps(clean_glosses)}\n\n"
            "Output ONLY the translated English sentence. No quotes, no explanations."
        )

        body = {
            "model": self.model_name,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": 0.1, "num_predict": 40},
        }

        try:
            req = urllib.request.Request(
                f"{self.host}/api/generate",
                data=json.dumps(body).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=3.0) as resp:
                if resp.status == 200:
                    data = json.loads(resp.read().decode("utf-8"))
                    text = data.get("response", "").strip().strip('"\'`')
                    if text:
                        self._grammar_cache[cache_key] = text
                        return text
        except Exception:
            pass
        return None

    def synthesize_grammar_async(
        self, glosses: List[str], callback: Callable[[str], None]
    ) -> threading.Thread:
        def _worker():
            res = self.synthesize_grammar(glosses)
            if res:
                callback(res)

        t = threading.Thread(target=_worker, daemon=True)
        t.start()
        return t

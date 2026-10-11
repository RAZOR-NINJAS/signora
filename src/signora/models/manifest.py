"""Model manifest loader, SHA-256 verifier, and model path resolver for Signora."""

import hashlib
import json
import os
import shutil
import urllib.request
from pathlib import Path
from typing import Callable, Dict, Optional, Tuple

_PROJECT_ROOT = Path(__file__).resolve().parents[3]
_MANIFEST_PATH = Path(__file__).resolve().parent / "models.json"


def get_default_models_dir() -> Path:
    """Return local model storage directory."""
    env_dir = os.environ.get("SIGNORA_MODELS_DIR")
    if env_dir:
        return Path(env_dir)

    # 1. Check local project models directory if present
    project_models = _PROJECT_ROOT / "models"
    if project_models.exists():
        return project_models

    # 2. Cross-platform user data directory
    if os.name == "nt":
        appdata = os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData" / "Local"))
        return Path(appdata) / "Signora" / "models"
    else:
        xdg_data = os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share"))
        return Path(xdg_data) / "signora" / "models"


def load_manifest() -> Dict:
    """Load models manifest JSON."""
    if not _MANIFEST_PATH.exists():
        raise FileNotFoundError(f"Models manifest not found at {_MANIFEST_PATH}")
    with _MANIFEST_PATH.open("r", encoding="utf-8") as f:
        return json.load(f)


def compute_sha256(filepath: Path) -> str:
    """Compute SHA-256 hash of a file."""
    h = hashlib.sha256()
    with filepath.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def get_model_path(model_key: str, models_dir: Optional[Path] = None) -> Path:
    """Resolve local path for a given model key."""
    manifest = load_manifest()
    if model_key not in manifest.get("models", {}):
        raise KeyError(f"Unknown model key: {model_key}")
    info = manifest["models"][model_key]
    filename = info["filename"]

    base = models_dir or get_default_models_dir()
    # Check direct path
    direct = base / filename
    if direct.exists():
        return direct
    # Check subdir
    subdir = base / info.get("subdir", "") / filename
    if subdir.exists():
        return subdir
    # Fallback to direct path
    return direct


def verify_model(model_key: str, models_dir: Optional[Path] = None) -> Tuple[bool, str]:
    """Verify integrity of a single model by checking file existence and SHA-256."""
    manifest = load_manifest()
    if model_key not in manifest.get("models", {}):
        return False, f"Model key '{model_key}' not in manifest."
    info = manifest["models"][model_key]
    path = get_model_path(model_key, models_dir)
    if not path.exists():
        return False, f"File {path.name} does not exist at {path.parent}."
    expected_sha = info["sha256"]
    actual_sha = compute_sha256(path)
    if actual_sha.lower() != expected_sha.lower():
        return (
            False,
            f"SHA-256 mismatch for {path.name}. Expected {expected_sha}, got {actual_sha}.",
        )
    return True, f"{path.name} verified successfully."


def verify_all_models(models_dir: Optional[Path] = None) -> Dict[str, Tuple[bool, str]]:
    """Verify all models declared in manifest."""
    manifest = load_manifest()
    results = {}
    for key in manifest.get("models", {}):
        results[key] = verify_model(key, models_dir)
    return results


def download_file(
    url: str,
    dest: Path,
    expected_sha256: Optional[str] = None,
    progress_cb: Optional[Callable[[int, int], None]] = None,
) -> bool:
    """Download url to dest with optional progress callback and SHA-256 validation."""
    if dest.exists() and dest.stat().st_size > 0:
        if expected_sha256 and compute_sha256(dest).lower() == expected_sha256.lower():
            if progress_cb:
                size = dest.stat().st_size
                progress_cb(size, size)
            return True

    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Signora-Installer/1.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            total = int(resp.headers.get("content-length", 0))
            downloaded = 0
            chunk_size = 65536
            with tmp.open("wb") as out:
                while True:
                    chunk = resp.read(chunk_size)
                    if not chunk:
                        break
                    out.write(chunk)
                    downloaded += len(chunk)
                    if progress_cb and total > 0:
                        progress_cb(downloaded, total)

        if expected_sha256:
            actual = compute_sha256(tmp)
            if actual.lower() != expected_sha256.lower():
                tmp.unlink(missing_ok=True)
                raise ValueError(
                    f"Checksum mismatch for {dest.name}. Expected {expected_sha256}, got {actual}."
                )

        shutil.move(str(tmp), str(dest))
        return True
    finally:
        if tmp.exists():
            tmp.unlink(missing_ok=True)


def fetch_model(
    model_key: str,
    models_dir: Optional[Path] = None,
    progress_cb: Optional[Callable[[int, int], None]] = None,
) -> bool:
    """Download a model using manifest URLs."""
    manifest = load_manifest()
    if model_key not in manifest.get("models", {}):
        raise KeyError(f"Unknown model key: {model_key}")
    info = manifest["models"][model_key]
    dest = get_model_path(model_key, models_dir)
    sha = info["sha256"]

    for url in info.get("urls", []):
        try:
            ok = download_file(url, dest, expected_sha256=sha, progress_cb=progress_cb)
            if ok:
                return True
        except Exception:
            continue
    return False

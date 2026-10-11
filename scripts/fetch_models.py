#!/usr/bin/env python3
"""Standalone model downloader and checksum verifier for Signora.

Downloads MediaPipe, Piper TTS, and trained sign recognition weights
with SHA-256 validation, resume support, and mirror failover.
Can be executed standalone without external package dependencies.
"""

import argparse
import hashlib
import json
import sys
import time
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional

# Locate models.json relative to repository layout
REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = REPO_ROOT / "src" / "signora" / "models" / "models.json"


def compute_sha256(filepath: Path) -> str:
    h = hashlib.sha256()
    with filepath.open("rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def format_bytes(bytes_count: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if bytes_count < 1024:
            return f"{bytes_count:.1f} {unit}"
        bytes_count /= 1024
    return f"{bytes_count:.1f} TB"


def download_file_with_progress(
    url: str, dest: Path, expected_sha: str, expected_size: int
) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")

    existing_bytes = tmp.stat().st_size if tmp.exists() else 0
    headers = {"User-Agent": "Signora-FetchModels/1.0"}
    if existing_bytes > 0:
        headers["Range"] = f"bytes={existing_bytes}-"

    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status = resp.status if hasattr(resp, "status") else 200
            if status == 206:
                mode = "ab"
                downloaded = existing_bytes
            else:
                mode = "wb"
                downloaded = 0

            total_size = expected_size or int(resp.headers.get("content-length", 0)) + downloaded
            start_time = time.monotonic()
            last_print = start_time

            with tmp.open(mode) as f:
                while chunk := resp.read(65536):
                    f.write(chunk)
                    downloaded += len(chunk)
                    now = time.monotonic()
                    if now - last_print >= 0.25:
                        pct = (downloaded / total_size * 100) if total_size else 0
                        speed = downloaded / (now - start_time) if (now - start_time) > 0 else 0
                        sys.stdout.write(
                            f"\r    [{pct:5.1f}%] {format_bytes(downloaded)} / {format_bytes(total_size)} "
                            f"({format_bytes(int(speed))}/s)   "
                        )
                        sys.stdout.flush()
                        last_print = now

            pct = 100.0 if total_size else 0
            sys.stdout.write(
                f"\r    [{pct:5.1f}%] {format_bytes(downloaded)} / {format_bytes(total_size)} Complete!    \n"
            )
            sys.stdout.flush()

    except Exception as exc:
        print(f"\n    [Error] Download failed from {url}: {exc}")
        return False

    # Checksum validation
    actual_sha = compute_sha256(tmp)
    if actual_sha.lower() != expected_sha.lower():
        print(f"    [Error] SHA-256 mismatch for {dest.name}!")
        print(f"      Expected: {expected_sha}")
        print(f"      Got:      {actual_sha}")
        tmp.unlink(missing_ok=True)
        return False

    tmp.replace(dest)
    return True


def fetch_models(
    manifest_path: Path,
    target_dir: Path,
    verify_only: bool = False,
    selected_keys: Optional[List[str]] = None,
) -> bool:
    if not manifest_path.exists():
        print(f"[fetch_models] Manifest file not found: {manifest_path}", file=sys.stderr)
        return False

    with manifest_path.open("r", encoding="utf-8") as f:
        manifest = json.load(f)

    models: Dict[str, Dict] = manifest.get("models", {})
    keys_to_process = selected_keys if selected_keys else list(models.keys())

    print("\n=== Signora Model Manager ===")
    print(f"Target Directory: {target_dir}")
    print(f"Manifest:         {manifest_path}\n")

    all_passed = True

    for key in keys_to_process:
        if key not in models:
            print(f"[!] Unknown model key: '{key}' (skipping)")
            continue

        item = models[key]
        filename = item["filename"]
        subdir = item.get("subdir", "")
        dest = target_dir / filename
        # also check subdir if present
        if not dest.exists() and subdir:
            sub_dest = target_dir / subdir / filename
            if sub_dest.exists():
                dest = sub_dest

        expected_sha = item["sha256"]
        expected_size = item.get("size_bytes", 0)
        desc = item.get("description", key)

        print(f"• [{key}] {filename} ({format_bytes(expected_size)})")
        print(f"  Description: {desc}")

        if dest.exists() and dest.stat().st_size > 0:
            actual_sha = compute_sha256(dest)
            if actual_sha.lower() == expected_sha.lower():
                print("  ✓ Present & verified (SHA-256 match)")
                continue
            else:
                print("  ⚠ Existing file corrupt or outdated. Checksum mismatch.")

        if verify_only:
            print("  ✗ MISSING or corrupt (verification mode)")
            all_passed = False
            continue

        # Attempt downloads from mirror URLs
        urls = item.get("urls", [])
        success = False
        for url in urls:
            print(f"  Downloading from: {url}")
            if download_file_with_progress(url, dest, expected_sha, expected_size):
                print(f"  ✓ Successfully downloaded and verified {filename}")
                success = True
                break
            else:
                print("  Trying next mirror...")

        if not success:
            print(f"  ✗ Failed to download {filename} from all mirrors.")
            all_passed = False

    print("\n" + ("=" * 40))
    if all_passed:
        print("✓ All requested models verified.")
    else:
        print("✗ One or more models failed verification.")
    print("=" * 40 + "\n")

    return all_passed


def main() -> int:
    parser = argparse.ArgumentParser(description="Signora Model Asset Downloader and Verifier")
    parser.add_argument(
        "--target-dir",
        "-t",
        type=Path,
        default=REPO_ROOT / "models",
        help="Directory to save downloaded model weights (default: ./models)",
    )
    parser.add_argument(
        "--manifest",
        "-m",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="Path to models.json manifest",
    )
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="Verify existing files without downloading",
    )
    parser.add_argument(
        "--model",
        "-k",
        action="append",
        dest="models",
        help="Specific model key to fetch (repeatable, e.g. -k hand_landmarker)",
    )

    args = parser.parse_args()
    ok = fetch_models(
        manifest_path=args.manifest,
        target_dir=args.target_dir,
        verify_only=args.verify_only,
        selected_keys=args.models,
    )
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

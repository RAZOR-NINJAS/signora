#!/usr/bin/env python3
"""Train / Fine-tune the Indian Sign Language (ISL) MLP Classifier.

Incorporates newly recorded sign samples from `data/isl/`
into the model alongside existing baseline classes,
updates `models/isl_mlp.pt`, and exports mobile weights for Android.

Usage:
    # Train after collecting samples in data/isl/<SIGN>.npy:
    python scripts/train_isl.py

    # Custom options:
    python scripts/train_isl.py --epochs 60 --lr 0.001
"""

import argparse
import os
import sys
from pathlib import Path

# Auto-relaunch inside project .venv if run with system python lacking dependencies
ROOT_DIR = Path(__file__).resolve().parent.parent
_VENV_PYTHON = ROOT_DIR / ".venv" / "bin" / "python"
if _VENV_PYTHON.exists() and sys.executable != str(_VENV_PYTHON):
    try:
        import torch
    except ImportError:
        os.execv(str(_VENV_PYTHON), [str(_VENV_PYTHON)] + sys.argv)

if str(ROOT_DIR / "src") not in sys.path:
    sys.path.insert(0, str(ROOT_DIR / "src"))
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from signora.isl.train import DATA_DIR, load_dataset, synthesize_baseline_samples, train_isl_model


def main():
    parser = argparse.ArgumentParser(description="Train ISL MLP with newly collected samples")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR, help="Directory containing .npy sample files")
    parser.add_argument("--epochs", type=int, default=50, help="Training epochs (default: 50)")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate (default: 0.001)")
    parser.add_argument("--batch-size", type=int, default=32, help="Batch size (default: 32)")
    args = parser.parse_args()

    res = train_isl_model(data_dir=args.data_dir, epochs=args.epochs, lr=args.lr, batch_size=args.batch_size)
    return 0 if res.get("success") else 1


if __name__ == "__main__":
    raise SystemExit(main())

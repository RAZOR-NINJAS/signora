#!/usr/bin/env python3
"""Train / Fine-tune the Indian Sign Language (ISL) MLP Classifier.

Incorporates newly recorded sign samples (such as Letter 'D') from `data/isl/`
into the model alongside existing baseline classes (A, B, C, SPACE),
updates `models/isl_mlp.pt`, and exports mobile weights for Android.

Usage:
    # Train after collecting samples in data/isl/D.npy:
    python scripts/train_isl.py

    # Custom options:
    python scripts/train_isl.py --epochs 60 --lr 0.001
"""

import argparse
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

from signora.isl.model import get_torch_model
from signora.models.manifest import get_model_path

ROOT_DIR = Path(__file__).resolve().parent.parent
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))
DATA_DIR = ROOT_DIR / "data" / "isl"
MODELS_DIR = ROOT_DIR / "models"


def synthesize_baseline_samples(
    model: nn.Module,
    labels: List[str],
    mean: torch.Tensor,
    std: torch.Tensor,
    samples_per_class: int = 120,
) -> Dict[str, np.ndarray]:
    """Synthesize high-confidence exemplar features from existing checkpoint to prevent catastrophic forgetting."""
    print("⚡ Preserving baseline knowledge for existing classes:", labels)
    model.eval()
    exemplars: Dict[str, np.ndarray] = {}

    for idx, label in enumerate(labels):
        x_norm = nn.Parameter(torch.randn(samples_per_class, 164) * 0.15)
        optimizer = torch.optim.Adam([x_norm], lr=0.08)

        for _ in range(80):
            optimizer.zero_grad()
            out = model(x_norm)
            loss = -out[:, idx].mean() + 0.015 * (x_norm**2).mean()
            loss.backward()
            optimizer.step()

        with torch.no_grad():
            x_raw = (x_norm * std + mean).cpu().numpy().astype(np.float32)
            exemplars[label] = x_raw

    return exemplars


def load_dataset(data_dir: Path, baseline_ckpt_path: Path) -> Tuple[np.ndarray, np.ndarray, List[str]]:
    """Assemble complete training dataset combining existing and newly recorded classes."""
    dataset_dict: Dict[str, np.ndarray] = {}

    # 1. Load baseline checkpoint to preserve prior classes
    if baseline_ckpt_path.exists():
        ckpt = torch.load(baseline_ckpt_path, map_location="cpu", weights_only=False)
        base_labels = ckpt["labels"]
        base_model = get_torch_model(in_dim=164, n_classes=len(base_labels))
        base_model.load_state_dict(ckpt["state_dict"])
        base_mean = torch.tensor(ckpt["mean"], dtype=torch.float32)
        base_std = torch.tensor(ckpt["std"], dtype=torch.float32)

        baseline_data = synthesize_baseline_samples(base_model, base_labels, base_mean, base_std)
        dataset_dict.update(baseline_data)
    else:
        print("[Warning] No previous baseline checkpoint found. Training from scratch.")

    # 2. Load newly recorded user samples from data/isl/*.npy
    new_files = list(data_dir.glob("*.npy"))
    if not new_files:
        print(f"[Error] No sample files found in {data_dir}.", file=sys.stderr)
        print("Please record samples first using:")
        print("    python scripts/collect_isl.py --sign D --count 20", file=sys.stderr)
        sys.exit(1)

    for npy_file in new_files:
        sign_label = npy_file.stem.upper()
        arr = np.load(npy_file).astype(np.float32)
        if arr.ndim == 1:
            arr = arr.reshape(1, -1)
        if arr.shape[1] != 164:
            print(f"[Warning] Skipping {npy_file.name}: invalid feature dim {arr.shape[1]} (expected 164)")
            continue

        print(f"✓ Loaded user sign '{sign_label}': {len(arr)} samples from {npy_file.name}")
        # Overwrite or augment class
        dataset_dict[sign_label] = arr

    all_labels = sorted(list(dataset_dict.keys()))
    print(f"\nTarget Model Vocabulary ({len(all_labels)} classes): {all_labels}")

    X_list = []
    y_list = []

    for label_idx, label in enumerate(all_labels):
        samples = dataset_dict[label]
        # Data augmentation: jitter noise to improve generalization
        noisy_samples = []
        for _ in range(max(1, 100 // len(samples))):
            noise = np.random.normal(0, 0.015, size=samples.shape).astype(np.float32)
            noisy_samples.append(samples + noise)

        combined = np.vstack([samples] + noisy_samples)
        X_list.append(combined)
        y_list.append(np.full(len(combined), label_idx, dtype=np.int64))

    X = np.vstack(X_list).astype(np.float32)
    y = np.concatenate(y_list).astype(np.int64)

    return X, y, all_labels


def train_isl_model(
    data_dir: Path = DATA_DIR,
    epochs: int = 50,
    lr: float = 1e-3,
    batch_size: int = 32,
) -> None:
    print("=" * 65)
    print("  SIGNORA -- ISL Neural Network Trainer (with Letter 'D')")
    print("=" * 65)

    ckpt_path = get_model_path("isl_signs")
    if not ckpt_path.exists():
        ckpt_path = MODELS_DIR / "isl_mlp.pt"

    X, y, labels = load_dataset(data_dir, ckpt_path)

    # Compute dataset mean and std for normalization
    mean = np.mean(X, axis=0)
    std = np.std(X, axis=0)
    std = np.where(std < 1e-4, 1.0, std).astype(np.float32)

    X_norm = (X - mean) / std

    # Train / Validation Split (80 / 20)
    indices = np.random.permutation(len(X_norm))
    split = int(0.8 * len(X_norm))
    train_idx, val_idx = indices[:split], indices[split:]

    X_train, y_train = torch.tensor(X_norm[train_idx]), torch.tensor(y[train_idx])
    X_val, y_val = torch.tensor(X_norm[val_idx]), torch.tensor(y[val_idx])

    train_loader = DataLoader(TensorDataset(X_train, y_train), batch_size=batch_size, shuffle=True)

    n_classes = len(labels)
    model = get_torch_model(in_dim=164, n_classes=n_classes)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)

    print(f"\n⚡ Training PyTorchISLMLP for {epochs} epochs on {len(X_train)} samples...")
    best_acc = 0.0
    best_state = None

    for epoch in range(1, epochs + 1):
        model.train()
        total_loss = 0.0
        for bx, by in train_loader:
            optimizer.zero_grad()
            out = model(bx)
            loss = criterion(out, by)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * len(bx)
        scheduler.step()

        # Validation
        model.eval()
        with torch.no_grad():
            v_out = model(X_val)
            preds = torch.argmax(v_out, dim=-1)
            acc = float((preds == y_val).float().mean().item())

        if acc > best_acc or best_state is None:
            best_acc = acc
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}

        if epoch % 10 == 0 or epoch == epochs:
            avg_loss = total_loss / len(X_train)
            print(f"  Epoch [{epoch:02d}/{epochs:02d}]  Loss: {avg_loss:.4f}  Val Acc: {acc * 100:.1f}%")

    print(f"\n✓ Best Validation Accuracy: {best_acc * 100:.2f}%")

    # Backup previous model
    if ckpt_path.exists():
        backup_path = ckpt_path.with_suffix(".pt.bak")
        shutil.copyfile(ckpt_path, backup_path)
        print(f"✓ Backed up prior checkpoint -> {backup_path.name}")

    # Save new checkpoint
    new_ckpt = {
        "state_dict": best_state,
        "labels": labels,
        "in_dim": 164,
        "n_classes": n_classes,
        "mean": mean.astype(np.float32),
        "std": std.astype(np.float32),
        "temperature": 0.05,
        "meta": {
            "val_acc": best_acc,
            "n_samples": len(X),
            "epochs": epochs,
        },
    }

    ckpt_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(new_ckpt, ckpt_path)
    print(f"🎉 Saved updated ISL model checkpoint -> {ckpt_path}")

    # Automatically export for Android APK
    print("\n⚡ Exporting mobile assets for Android Unspoken app...")
    from scripts.export_mobile_models import export_model_to_json

    mobile_out = ROOT_DIR / "android" / "app" / "src" / "main" / "assets" / "models" / "isl_model.json"
    export_model_to_json("isl_signs", "isl", mobile_out)

    print("\n" + "=" * 65)
    print("✅ TRAINING COMPLETE!")
    print(f"Recognized classes: {labels}")
    print("Test it live now in the terminal:")
    print("    signora isl")
    print("Or recompile the Android APK:")
    print("    ./android/setup.sh")
    print("=" * 65)


def main():
    parser = argparse.ArgumentParser(description="Train ISL MLP with newly collected samples")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR, help="Directory containing .npy sample files")
    parser.add_argument("--epochs", type=int, default=50, help="Training epochs (default: 50)")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate (default: 0.001)")
    parser.add_argument("--batch-size", type=int, default=32, help="Batch size (default: 32)")
    args = parser.parse_args()

    train_isl_model(data_dir=args.data_dir, epochs=args.epochs, lr=args.lr, batch_size=args.batch_size)


if __name__ == "__main__":
    main()

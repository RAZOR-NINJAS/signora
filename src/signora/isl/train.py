"""Train / Fine-tune the Indian Sign Language (ISL) MLP Classifier.

Incorporates newly recorded sign samples from data/isl/
alongside existing baseline classes to prevent catastrophic forgetting,
updates models/isl_mlp.pt, and exports mobile weights for Android.
"""

import json
import shutil
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
from torch.optim.lr_scheduler import CosineAnnealingLR

from signora.isl.model import get_torch_model
from signora.models.export import export_model_to_json
from signora.models.manifest import (
    _MANIFEST_PATH,
    compute_sha256,
    get_default_models_dir,
    get_model_path,
)

ROOT_DIR = Path(__file__).resolve().parents[3]
DATA_DIR = ROOT_DIR / "data" / "isl"
DATA_SPATIAL_DIR = ROOT_DIR / "data" / "isl_spatial"
MODELS_DIR = get_default_models_dir()


def synthesize_baseline_samples(
    model: nn.Module,
    labels: List[str],
    mean: torch.Tensor,
    std: torch.Tensor,
    in_dim: int = 164,
    samples_per_class: int = 120,
) -> Dict[str, np.ndarray]:
    """Synthesize high-confidence exemplar features from existing checkpoint to prevent catastrophic forgetting."""
    print("⚡ Preserving baseline knowledge for existing classes:", labels)
    model.eval()
    exemplars: Dict[str, np.ndarray] = {}

    for idx, label in enumerate(labels):
        x_norm = nn.Parameter(torch.randn(samples_per_class, in_dim) * 0.15)
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


def load_dataset(
    data_dir: Path,
    baseline_ckpt_path: Path,
    expected_dim: Optional[int] = None,
    fresh: bool = False,
) -> Tuple[np.ndarray, np.ndarray, List[str], int]:
    """Assemble complete training dataset combining existing and newly recorded classes."""
    dataset_dict: Dict[str, np.ndarray] = {}

    # Inspect newly recorded user samples to detect dimensionality if not specified
    new_files = list(data_dir.glob("*.npy")) if data_dir.exists() else []

    target_in_dim = expected_dim
    if target_in_dim is None:
        if new_files:
            try:
                sample_peek = np.load(new_files[0])
                target_in_dim = sample_peek.shape[1] if sample_peek.ndim > 1 else sample_peek.shape[0]
            except Exception:
                target_in_dim = 220 if "spatial" in str(data_dir) else 164
        else:
            target_in_dim = 220 if "spatial" in str(data_dir) else 164

    # 1. Load baseline checkpoint to preserve prior classes if dimensions match and not fresh
    if baseline_ckpt_path.exists() and not fresh:
        ckpt = torch.load(baseline_ckpt_path, map_location="cpu", weights_only=False)
        base_in_dim = ckpt.get("in_dim", 164)
        if base_in_dim == target_in_dim:
            base_labels = ckpt["labels"]
            base_model = get_torch_model(in_dim=target_in_dim, n_classes=len(base_labels))
            base_model.load_state_dict(ckpt["state_dict"])
            base_mean = torch.tensor(ckpt["mean"], dtype=torch.float32)
            base_std = torch.tensor(ckpt["std"], dtype=torch.float32)

            baseline_data = synthesize_baseline_samples(
                base_model, base_labels, base_mean, base_std, in_dim=target_in_dim
            )
            dataset_dict.update(baseline_data)
        else:
            print(
                f"[Info] Baseline checkpoint has dimension {base_in_dim}D, but current dataset requires {target_in_dim}D. Training fresh {target_in_dim}D model."
            )
    else:
        print("[Info] Training clean model from scratch on user-provided data only.")

    # 2. Load newly recorded user samples from data_dir/*.npy
    if not new_files and not dataset_dict:
        raise FileNotFoundError(f"No sample files found in {data_dir} and no baseline checkpoint found.")

    for npy_file in new_files:
        sign_label = npy_file.stem.upper()
        arr = np.load(npy_file).astype(np.float32)
        if arr.ndim == 1:
            arr = arr.reshape(1, -1)
        if arr.shape[1] != target_in_dim:
            print(
                f"[Warning] Skipping {npy_file.name}: invalid feature dim {arr.shape[1]} (expected {target_in_dim})"
            )
            continue

        print(f"✓ Loaded user sign '{sign_label}': {len(arr)} samples from {npy_file.name}")
        dataset_dict[sign_label] = arr

    if not dataset_dict:
        raise ValueError(f"No valid samples with feature dim {target_in_dim} found in {data_dir}.")

    all_labels = sorted(list(dataset_dict.keys()))
    print(f"\nTarget Model Vocabulary ({len(all_labels)} classes, {target_in_dim}D): {all_labels}")

    X_list = []
    y_list = []

    for label_idx, label in enumerate(all_labels):
        samples = dataset_dict[label]
        # Data augmentation: jitter noise to improve generalization only if class is small (< 100 samples)
        noisy_samples = []
        if len(samples) < 100:
            target_reps = min(5, max(1, 100 // max(1, len(samples))))
            for _ in range(target_reps):
                noise = np.random.normal(0, 0.015, size=samples.shape).astype(np.float32)
                noisy_samples.append(samples + noise)

        combined = np.vstack([samples] + noisy_samples) if noisy_samples else samples
        X_list.append(combined)
        y_list.append(np.full(len(combined), label_idx, dtype=np.int64))

    X = np.vstack(X_list).astype(np.float32)
    y = np.concatenate(y_list).astype(np.int64)

    return X, y, all_labels, target_in_dim


def train_isl_model(
    data_dir: Path = DATA_DIR,
    epochs: int = 50,
    lr: float = 1e-3,
    batch_size: int = 32,
    ckpt_path: Optional[Path] = None,
    in_dim: Optional[int] = None,
    fresh: bool = False,
    export_mobile: bool = False,
) -> Dict[str, Any]:
    """Train or fine-tune the ISL MLP model on data_dir and update model checkpoint."""
    print("=" * 65)
    mode_str = "Spatial Hand+Body (220D)" if (in_dim == 220 or "spatial" in str(data_dir)) else "ISL Hand (164D)"
    print(f"  SIGNORA -- {mode_str} Neural Network Trainer")
    print("=" * 65)

    if ckpt_path is None:
        if in_dim == 220 or "spatial" in str(data_dir):
            ckpt_path = MODELS_DIR / "isl_spatial_mlp.pt"
        else:
            try:
                ckpt_path = get_model_path("isl_signs")
            except Exception:
                ckpt_path = MODELS_DIR / "isl_mlp.pt"
            if not ckpt_path.exists():
                ckpt_path = MODELS_DIR / "isl_mlp.pt"

    X, y, labels, actual_dim = load_dataset(data_dir, ckpt_path, expected_dim=in_dim, fresh=fresh)
    if actual_dim == 220 and ckpt_path.name == "isl_mlp.pt":
        ckpt_path = MODELS_DIR / "isl_spatial_mlp.pt"

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

    eff_batch_size = 128 if (batch_size == 32 and len(X_train) >= 2000) else batch_size
    train_loader = DataLoader(TensorDataset(X_train, y_train), batch_size=eff_batch_size, shuffle=True)

    n_classes = len(labels)
    model = get_torch_model(in_dim=actual_dim, n_classes=n_classes)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.05)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=epochs, eta_min=lr * 0.05)

    best_acc = 0.0
    best_state = None

    print(f"\nTraining on {len(X_train)} samples, validating on {len(X_val)} samples across {epochs} epochs ({actual_dim}D)...")

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

        model.eval()
        with torch.no_grad():
            v_out = model(X_val)
            preds = torch.argmax(v_out, dim=-1)
            acc = float((preds == y_val).float().mean().item())

        if acc > best_acc or best_state is None:
            best_acc = acc
            best_state = {k: v.cpu().clone() for k, v in model.state_dict().items()}

        if epoch % 5 == 0 or epoch == epochs or epoch == 1:
            avg_loss = total_loss / len(X_train)
            print(f"  Epoch [{epoch:02d}/{epochs:02d}]  Loss: {avg_loss:.4f}  Val Acc: {acc * 100:.1f}%", flush=True)

    print(f"\n✓ Best Validation Accuracy: {best_acc * 100:.2f}%", flush=True)

    # Temperature calibration: find T that minimises NLL on the validation split
    model.load_state_dict(best_state)
    model.eval()
    with torch.no_grad():
        val_logits = model(X_val)  # (N, C)

    best_nll = float("inf")
    calibrated_temperature = 1.0
    for t_cand in [0.3, 0.5, 0.7, 1.0, 1.3, 1.5, 2.0]:
        scaled = val_logits / t_cand
        log_probs = scaled - torch.logsumexp(scaled, dim=-1, keepdim=True)
        nll = -log_probs[torch.arange(len(y_val)), y_val].mean().item()
        if nll < best_nll:
            best_nll = nll
            calibrated_temperature = t_cand
    print(f"✓ Calibrated temperature: {calibrated_temperature} (NLL={best_nll:.4f})")

    # Backup previous model
    if ckpt_path.exists():
        backup_path = ckpt_path.with_suffix(".pt.bak")
        shutil.copyfile(ckpt_path, backup_path)
        print(f"✓ Backed up prior checkpoint -> {backup_path.name}")

    # Save new checkpoint
    new_ckpt = {
        "state_dict": best_state,
        "labels": labels,
        "in_dim": actual_dim,
        "n_classes": n_classes,
        "mean": mean.astype(np.float32),
        "std": std.astype(np.float32),
        "temperature": calibrated_temperature,
        "meta": {
            "val_acc": best_acc,
            "n_samples": len(X),
            "epochs": epochs,
        },
    }

    ckpt_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(new_ckpt, ckpt_path)
    print(f"🎉 Saved updated ISL model checkpoint ({actual_dim}D) -> {ckpt_path}")

    # Synchronize models.json manifest hash for standard 164D isl_signs
    if actual_dim == 164:
        try:
            if _MANIFEST_PATH.exists():
                with open(_MANIFEST_PATH, "r", encoding="utf-8") as mf:
                    mdata = json.load(mf)
                if "isl_signs" in mdata.get("models", {}):
                    mdata["models"]["isl_signs"]["sha256"] = compute_sha256(ckpt_path)
                    mdata["models"]["isl_signs"]["size_bytes"] = ckpt_path.stat().st_size
                    with open(_MANIFEST_PATH, "w", encoding="utf-8") as mf:
                        json.dump(mdata, mf, indent=2)
                    print(f"✓ Updated manifest SHA-256 in {_MANIFEST_PATH.name}")
        except Exception as me:
            print(f"[Warning] Could not update models.json manifest: {me}")

    # Automatically export for Android APK
    is_production_ckpt = (ckpt_path.parent.resolve() == MODELS_DIR.resolve())
    if (is_production_ckpt or export_mobile) and actual_dim == 220:
        mobile_out = ROOT_DIR / "android" / "app" / "src" / "main" / "assets" / "models" / "isl_model.json"
        if mobile_out.parent.exists() or (ROOT_DIR / "android").exists():
            try:
                mobile_out.parent.mkdir(parents=True, exist_ok=True)
                print(f"\n⚡ Exporting mobile assets ({actual_dim}D) for Android Signora app -> {mobile_out}...")
                export_model_to_json(ckpt_path, "isl", mobile_out)
            except Exception as e:
                print(f"[Warning] Mobile asset export skipped: {e}")

    # Also save a copy in models/ directory if production
    if is_production_ckpt:
        try:
            models_json_out = MODELS_DIR / ("isl_spatial_model.json" if actual_dim == 220 else "isl_mobile_model.json")
            export_model_to_json(ckpt_path, "isl", models_json_out)
        except Exception:
            pass

    print("\n" + "=" * 65)
    print("✅ TRAINING COMPLETE!")
    print(f"Recognized classes ({actual_dim}D): {labels}")
    print("Test it live now in the terminal:")
    if actual_dim == 220:
        print("    signora body")
    else:
        print("    signora isl")
    print("=" * 65)

    return {
        "success": True,
        "val_acc": float(best_acc),
        "labels": labels,
        "n_samples": int(len(X)),
        "epochs": epochs,
        "ckpt_path": str(ckpt_path),
        "in_dim": actual_dim,
    }

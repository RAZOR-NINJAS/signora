"""Export Signora PyTorch neural models and MediaPipe assets for Native Android."""

import json
import shutil
import sys
from pathlib import Path

import torch

from signora.models.manifest import get_model_path


def export_model_to_json(model_key: str, mode_name: str, out_path: Path) -> None:
    """Export PyTorch MLP state_dict and normalization vectors to mobile JSON format."""
    p = get_model_path(model_key)
    if not p.exists():
        print(
            f"[Error] Model file {p} not found. Run 'signora models fetch' first.", file=sys.stderr
        )
        return

    ckpt = torch.load(p, map_location="cpu", weights_only=False)
    st = ckpt["state_dict"]
    labels = ckpt["labels"]
    mean = ckpt["mean"]
    std = ckpt["std"]
    temperature = float(ckpt.get("temperature", 1.0) or 1.0)

    # Convert tensors to nested python lists
    bundle = {
        "format": "signora-mobile-v1",
        "mode": mode_name,
        "in_dim": len(mean),
        "num_classes": len(labels),
        "labels": labels,
        "mean": mean.tolist(),
        "std": std.tolist(),
        "temperature": temperature,
        "net_0_weight": st["net.0.weight"].numpy().tolist(),  # shape [hidden1, in_dim]
        "net_0_bias": st["net.0.bias"].numpy().tolist(),  # shape [hidden1]
        "net_1_weight": st["net.1.weight"].numpy().tolist(),  # LayerNorm weight
        "net_1_bias": st["net.1.bias"].numpy().tolist(),  # LayerNorm bias
        "net_4_weight": st["net.4.weight"].numpy().tolist(),  # shape [hidden2, hidden1]
        "net_4_bias": st["net.4.bias"].numpy().tolist(),  # shape [hidden2]
        "net_5_weight": st["net.5.weight"].numpy().tolist(),  # LayerNorm weight
        "net_5_bias": st["net.5.bias"].numpy().tolist(),  # LayerNorm bias
        "net_8_weight": st["net.8.weight"].numpy().tolist(),  # shape [num_classes, hidden2]
        "net_8_bias": st["net.8.bias"].numpy().tolist(),  # shape [num_classes]
    }

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(bundle, f)

    kb = out_path.stat().st_size / 1024
    print(f"✓ Exported {mode_name.upper()} mobile bundle -> {out_path} ({kb:.1f} KB)")


def copy_mediapipe_landmarker(out_path: Path) -> None:
    """Copy MediaPipe HandLandmarker Tasks API model to Android assets."""
    p = get_model_path("hand_landmarker")
    if not p.exists():
        print(
            f"[Error] HandLandmarker task {p} not found. Run 'signora models fetch' first.",
            file=sys.stderr,
        )
        return

    out_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(p, out_path)
    mb = out_path.stat().st_size / (1024 * 1024)
    print(f"✓ Copied MediaPipe HandLandmarker -> {out_path} ({mb:.1f} MB)")

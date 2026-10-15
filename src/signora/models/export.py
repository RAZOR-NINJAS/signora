"""Export Signora PyTorch neural models and MediaPipe assets for Native Android."""

import json
import shutil
import sys
from pathlib import Path

import torch

from signora.models.manifest import get_model_path


from typing import Any, Union


def export_model_to_json(model_source: Union[str, Path], mode_name: str, out_path: Path) -> None:
    """Export PyTorch MLP state_dict and normalization vectors to mobile JSON format."""
    if isinstance(model_source, Path):
        p = model_source
    elif isinstance(model_source, str) and (Path(model_source).exists() or "/" in model_source or "\\" in model_source or model_source.endswith(".pt")):
        p = Path(model_source)
    else:
        p = get_model_path(str(model_source))

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

    def to_py(val):
        if hasattr(val, "detach"):
            val = val.detach()
        if hasattr(val, "cpu"):
            val = val.cpu()
        if hasattr(val, "numpy"):
            val = val.numpy()
        if hasattr(val, "tolist"):
            return val.tolist()
        return list(val)

    # Convert tensors to nested python lists
    bundle = {
        "format": "signora-mobile-v1",
        "mode": mode_name,
        "in_dim": len(mean),
        "num_classes": len(labels),
        "labels": labels,
        "mean": to_py(mean),
        "std": to_py(std),
        "temperature": temperature,
        "net_0_weight": to_py(st["net.0.weight"]),  # shape [hidden1, in_dim]
        "net_0_bias": to_py(st["net.0.bias"]),  # shape [hidden1]
        "net_1_weight": to_py(st["net.1.weight"]),  # LayerNorm weight
        "net_1_bias": to_py(st["net.1.bias"]),  # LayerNorm bias
        "net_4_weight": to_py(st["net.4.weight"]),  # shape [hidden2, hidden1]
        "net_4_bias": to_py(st["net.4.bias"]),  # shape [hidden2]
        "net_5_weight": to_py(st["net.5.weight"]),  # LayerNorm weight
        "net_5_bias": to_py(st["net.5.bias"]),  # LayerNorm bias
        "net_8_weight": to_py(st["net.8.weight"]),  # shape [num_classes, hidden2]
        "net_8_bias": to_py(st["net.8.bias"]),  # shape [num_classes]
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

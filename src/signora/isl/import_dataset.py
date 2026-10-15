"""Dataset Ingestion & Bridge for Phone-Trained ISL Data.

Imports 220D spatial feature samples exported from the Signora Android App
(via TrainingDatasetManager / Studio screen) into the project's training directory,
merges them with existing dataset files, and triggers PyTorch retraining to update
the pre-trained model for both Python and the Android APK.
"""

import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from signora.isl.train import DATA_DIR, DATA_SPATIAL_DIR, train_isl_model


def inspect_dataset_file(json_path: Path) -> Dict[str, Any]:
    """Inspect exported JSON dataset metadata without importing."""
    if not json_path.exists():
        raise FileNotFoundError(f"Dataset file not found: {json_path}")

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if not isinstance(data, dict):
        raise ValueError("Invalid dataset JSON: root must be an object")

    fmt = data.get("format", "unknown")
    classes_dict = data.get("classes", {})
    if not classes_dict and "format" not in data:
        # Fallback: direct { "SIGN": [[...]] } map
        classes_dict = {k: v for k, v in data.items() if isinstance(v, list)}

    class_counts: Dict[str, int] = {}
    sample_dim = 0
    total_samples = 0

    for cls_name, samples in classes_dict.items():
        if isinstance(samples, list) and len(samples) > 0:
            count = len(samples)
            class_counts[cls_name.upper()] = count
            total_samples += count
            if sample_dim == 0 and len(samples[0]) > 0:
                sample_dim = len(samples[0])

    return {
        "path": str(json_path),
        "format": fmt,
        "version": data.get("version", 1),
        "device": data.get("device", "Unknown"),
        "feature_dim": sample_dim or data.get("feature_dim", 220),
        "total_classes": len(class_counts),
        "total_samples": total_samples,
        "classes": class_counts,
    }


def import_phone_dataset(
    json_path: Path,
    target_dir: Optional[Path] = None,
    append: bool = True,
    auto_train: bool = True,
    epochs: int = 50,
) -> Dict[str, Any]:
    """Parse phone-recorded dataset JSON, save .npy arrays, and optionally retrain model."""
    meta = inspect_dataset_file(json_path)
    feature_dim = meta["feature_dim"]

    if target_dir is None:
        target_dir = DATA_SPATIAL_DIR if feature_dim == 220 else DATA_DIR

    target_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 65)
    print("  SIGNORA -- Phone ISL Dataset Ingestion Pipeline")
    print("=" * 65)
    print(f"📦 Source File: {json_path.name}")
    print(f"📱 Source Device: {meta.get('device', 'Android')}")
    print(f"📐 Feature Dimension: {feature_dim}D")
    print(f"🏷️  Classes Found: {meta['total_classes']}")
    print(f"📊 Total Samples: {meta['total_samples']}")
    print(f"📂 Target Directory: {target_dir}")
    print("-" * 65)

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    classes_dict = data.get("classes", {})
    if not classes_dict and "format" not in data:
        classes_dict = {k: v for k, v in data.items() if isinstance(v, list)}

    imported_classes: List[str] = []
    sample_stats: Dict[str, int] = {}

    for cls_name, raw_samples in classes_dict.items():
        if not raw_samples:
            continue
        clean_name = cls_name.upper().strip()
        arr = np.array(raw_samples, dtype=np.float32)
        if arr.ndim == 1:
            arr = arr.reshape(1, -1)

        if arr.shape[1] != feature_dim:
            print(f"⚠️  Skipping '{clean_name}': dimension {arr.shape[1]} != expected {feature_dim}")
            continue

        npy_path = target_dir / f"{clean_name}.npy"
        if append and npy_path.exists():
            try:
                existing = np.load(npy_path).astype(np.float32)
                if existing.ndim == 1:
                    existing = existing.reshape(1, -1)
                if existing.shape[1] == arr.shape[1]:
                    # Combine and remove exact duplicates
                    combined = np.vstack([existing, arr])
                    # Unique along axis 0
                    _, unique_indices = np.unique(np.round(combined, decimals=5), axis=0, return_index=True)
                    unique_indices.sort()
                    arr = combined[unique_indices]
            except Exception as e:
                print(f"[Warning] Could not merge with existing {npy_path.name}: {e}")

        np.save(npy_path, arr)
        imported_classes.append(clean_name)
        sample_stats[clean_name] = len(arr)
        print(f"  ✓ {clean_name:12s} : {len(arr)} samples -> {npy_path.name}")

    print("-" * 65)
    print(f"🎉 Successfully ingested {len(imported_classes)} classes into {target_dir.name}/")

    train_result = None
    if auto_train:
        print("\n🚀 Initiating neural network retraining with newly ingested phone dataset...")
        train_result = train_isl_model(
            data_dir=target_dir,
            epochs=epochs,
            in_dim=feature_dim,
        )

    return {
        "success": True,
        "feature_dim": feature_dim,
        "imported_classes": imported_classes,
        "sample_stats": sample_stats,
        "target_dir": str(target_dir),
        "train_result": train_result,
    }

---
title: "Command: signora train"
tags:
  - cmd/training
aliases:
  - signora train
  - train
---

# `signora train`

> [!INFO] Summary
> **Neural Network Trainer**. Trains or fine-tunes local PyTorch Multilayer Perceptron (MLP) classification models using collected `.npy` coordinate samples, updates class label registries, and exports weights for both desktop Python and Android embedded runtime.

---

## 1. Syntax

```bash
signora train [OPTIONS]
```

---

## 2. Common Usage Examples

### Train Standard Hand Model (50 Epochs)
```bash
# Trains 126D hand topology MLP from data/isl_raw/
signora train --epochs 50
```

### Train Whole-Body Spatial 220D Model
```bash
# Trains 220D Spatial Hand+Pose MLP from data/isl_spatial/
signora train --body --epochs 60 --lr 0.001
```

### Import Mobile Dataset JSON & Train
```bash
# Ingests signora_isl_dataset.json recorded on Android phone, merges samples, and trains
signora train -i signora_isl_dataset.json
```

### Custom Learning Rate & Epochs
```bash
signora train --epochs 100 --lr 0.0005
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `--body` | Flag | `False` | Train 220D Spatial Hand+Pose model instead of hand-only model |
| `--epochs` | Integer | `50` | Number of training epochs |
| `--lr` | Float | `0.001` | AdamW optimizer learning rate |
| `-d`, `--data-dir` | Path | `None` | Custom directory containing `.npy` training samples |
| `-i`, `--import-file` | Path | `None` | Path to exported mobile `signora_isl_dataset.json` to import prior to training |

---

## 4. Architecture & Weight Export

When training completes, the trainer outputs:
1. **PyTorch Checkpoint**: `src/signora/models/isl_model.pth`
2. **Normalized Manifest JSON**: `src/signora/models/models.json`
3. **Android Client Asset**: `android/app/src/main/assets/models/isl_model.json`

This ensures that any newly trained sign immediately functions identically on desktop and mobile.

---

## 5. Related Commands
- [[signora collect]] — Collect training data frames.
- [[signora dataset]] — Inspect and import dataset bundles.
- [[signora isl]] — Test trained models in live camera loop.

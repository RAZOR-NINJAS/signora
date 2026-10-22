---
title: "Command: signora dataset"
tags:
  - cmd/training
aliases:
  - signora dataset
  - dataset
---

# `signora dataset`

> [!INFO] Summary
> **Dataset Ingestion & Inspection Utility**. Parses mobile-recorded dataset JSON files exported from the Android Signora Train Studio, verifies schema integrity, extracts sample frames into `.npy` format, and optionally triggers model training.

---

## 1. Syntax

```bash
signora dataset [ACTION] [FILE] [OPTIONS]
```

Where `[ACTION]` can be:
- `inspect` (default) — Display sample distribution, sign counts, and validation checks.
- `import` — Ingest samples into local training directories.
- `info` — Summarize metadata and timestamps.

---

## 2. Common Usage Examples

### Inspect an Exported Mobile Dataset
```bash
signora dataset inspect signora_isl_dataset.json
```

### Import Dataset and Auto-Retrain
```bash
# Ingests samples and immediately executes PyTorch MLP training
signora dataset import signora_isl_dataset.json
```

### Import Samples Without Training
```bash
signora dataset import signora_isl_dataset.json --no-train
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `action` | Positional | `inspect` | Action: `inspect`, `import`, `info` |
| `file` | Positional | `signora_isl_dataset.json` | Path to JSON dataset file |
| `--train` | Flag | `True` | Automatically train model after import |
| `--no-train` | Flag | `False` | Import samples without triggering retraining |
| `--epochs` | Integer | `50` | Number of training epochs if `--train` is active |

---

## 4. End-to-End Mobile Data Workflow
1. In the Android App, navigate to **Train Studio**.
2. Select or create an ISL sign label (e.g. `HELLO`).
3. Record 200–300 frames of clean hand gestures.
4. Tap **Export Dataset** $\to$ saves `signora_isl_dataset.json` to phone storage.
5. Pull file via ADB:
   ```bash
   adb pull /sdcard/Download/signora_isl_dataset.json .
   ```
6. Ingest and retrain:
   ```bash
   signora dataset import signora_isl_dataset.json
   ```

---

## 5. Related Commands
- [[signora train]] — PyTorch neural model training.
- [[signora collect]] — Desktop sample collection studio.
- [[adb commands]] — Transfer files to/from Android phone.

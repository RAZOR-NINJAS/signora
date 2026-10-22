---
title: "Command: signora models"
tags:
  - cmd/tools
aliases:
  - signora models
  - models
---

# `signora models`

> [!INFO] Summary
> **Neural Model Weights & Asset Manager**. Inspects, verifies checksums, and fetches pre-trained neural network weights, MediaPipe task bundles, and Piper TTS acoustic models according to the project manifest.

---

## 1. Syntax

```bash
signora models [ACTION] [OPTIONS]
```

Where `[ACTION]` can be:
- `list` (default) — Display status, file sizes, and paths of all local model files.
- `fetch` — Download any missing weights or task assets.
- `verify` — Compute SHA-256 hashes and verify against manifest signatures.

---

## 2. Common Usage Examples

### List Installed Models & Status
```bash
signora models list
# or simply
signora models
```

### Download All Required Model Weights
```bash
signora models fetch
```

### Verify Integrity & Checksums
```bash
signora models verify
```

### Custom Model Storage Directory
```bash
signora models fetch -d /opt/signora/models
```

---

## 3. Managed Assets
- `asl_model.pth` / `asl_model.json`: 63D ASL fingerspelling classifier.
- `isl_model.pth` / `isl_model.json`: 126D ISL dual-handed alphabet & vocabulary classifier.
- `isl_spatial.pth`: 220D Whole-Body spatial placement classifier.
- `hand_landmarker.task`: MediaPipe 21-point 3D hand tracking bundle.
- `pose_landmarker.task`: MediaPipe 33-point whole-body pose bundle.
- `en_US-lessac-medium.onnx`: Offline Piper neural TTS voice.

---

## 4. Related Commands
- [[signora doctor]] — System health and environment check.
- [[signora train]] — Train custom models and update weights.

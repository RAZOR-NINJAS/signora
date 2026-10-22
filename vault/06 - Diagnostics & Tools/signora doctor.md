---
title: "Command: signora doctor"
tags:
  - cmd/tools
aliases:
  - signora doctor
  - doctor
---

# `signora doctor`

> [!INFO] Summary
> **System Health & Diagnostic Engine**. Inspects host hardware, acceleration backends (CUDA, ROCm, MPS, CPU), camera index availability, neural model weight integrity, Piper TTS voice models, and required tooling (`adb`, `scrcpy`, `v4l2loopback`).

---

## 1. Syntax

```bash
signora doctor [OPTIONS]
```

---

## 2. Common Usage Examples

### Run Full System Health Check
```bash
signora doctor
```

### Automated CI / Script Check
```bash
# Silent mode returning exit code 0 on all passes, 1 on any failure
signora doctor --check-only
```

---

## 3. Diagnostics Performed

1. **Python Environment**: Version check ($\ge 3.9$), architecture validation.
2. **Hardware Acceleration**: PyTorch backend detection (NVIDIA CUDA, AMD ROCm, Apple Silicon MPS, or AVX2 CPU).
3. **Camera Device Enumeration**: Scans `/dev/video*` nodes and tests OpenCV read latencies.
4. **Phone Webcam Dependencies**: Checks if `adb` and `scrcpy` binaries are installed and accessible in `$PATH`.
5. **V4L2 Loopback**: Verifies kernel module status for virtual camera and phone streaming.
6. **Model Manifest Integrity**: Verifies SHA-256 hashes of local neural network weights and MediaPipe task files.
7. **Speech Synthesis**: Checks for Piper C++ runtime and `en_US-lessac-medium.onnx` voice model.

---

## 4. Related Commands
- [[signora models]] — Fetch and verify neural model weights.
- [[signora phone]] — Phone webcam bridge diagnostics.
- [[signora tui]] — Interactive terminal dashboard.

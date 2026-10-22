---
title: "Command: signora collect"
tags:
  - cmd/training
aliases:
  - signora collect
  - signora studio
  - collect
  - studio
---

# `signora collect`

> [!INFO] Summary
> **Interactive Sample Collection Studio**. Launches an interactive studio to record training samples for any sign language letter or word with visual countdown timers, live hand landmarks verification, and optional automatic post-collection PyTorch training.

---

## 1. Syntax

```bash
signora collect [OPTIONS]
# or
signora studio [OPTIONS]
```

---

## 2. Common Usage Examples

### Collect 300 Samples for Letter 'A'
```bash
# Starts 5-second countdown timer, then records 300 frames of landmark coordinates
signora collect -s A
```

### Quick Collection with 3-Second Timer
```bash
signora collect -s B -n 500 --timer 3.0
```

### Collect with Whole-Body 220D Spatial Anchors
```bash
# Records both hands and 33-point body pose for spatial signs (e.g. HELLO, DOCTOR)
signora collect -s HELLO --body
```

### Stream from Phone Camera via scrcpy
```bash
signora collect -s EMERGENCY --phone
```

### Automatically Train Model Immediately After Collection
```bash
# Gathers 300 samples and automatically triggers PyTorch MLP training
signora collect -s WATER --train
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--sign` | String | `None` | Specific sign/letter to collect (e.g. `A`-`Z`, `SPACE`, `HELLO`) |
| `-n`, `--count` | Integer | `300` | Number of landmark sample frames to record |
| `--timer` | Float | `5.0` | Countdown duration in seconds before capture starts |
| `--body` | Flag | `False` | Enable 220D whole-body pose landmarks alongside hands |
| `--train` | Flag | `False` | Automatically retrain PyTorch MLP model after recording finishes |
| `--source` | String / Int | `0` | Camera index, device node, or video file path |
| `--phone` | Flag | `False` | Use connected phone camera via scrcpy |
| `--headless` | Flag | `False` | Run in headless mode without window |

---

## 4. Interactive Studio Workflow
1. Run `signora collect -s <SIGN_NAME>`.
2. A cyberpunk HUD appears showing a 5-second countdown bar.
3. Align your hands into the target sign posture.
4. Once the timer hits zero, the studio captures consecutive valid frames at 30–60 FPS.
5. Coordinates are normalized and saved as `.npy` feature files inside `data/isl_raw/` or `data/isl_spatial/`.

---

## 5. Related Commands
- [[signora train]] — Train PyTorch neural network on collected data.
- [[signora dataset]] — Inspect and import dataset packages.
- [[signora isl]] — Test new signs live in recognition mode.

---
title: "Command: signora body"
tags:
  - cmd/core
  - cmd/vision
aliases:
  - signora body
  - signora spatial
  - body
  - spatial
---

# `signora body`

> [!INFO] Summary
> Runs **Upper-Body Pose & Hand Spatial Recognition**. Simultaneously tracks 33 whole-body pose landmarks alongside hands to disambiguate signs characterized by anatomical placement anchors (`FOREHEAD`, `TEMPLE`, `CHIN`, `THROAT`, `CHEST`, `SHOULDER`).

---

## 1. Syntax

```bash
signora body [OPTIONS]
# or
signora spatial [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Body Spatial Recognition
```bash
signora body
```

### High-Speed Phone Camera via scrcpy
```bash
# Connect phone via USB cable and stream camera
signora body --phone
```

### Connect to Network IP Stream or DroidCam
```bash
signora body -s "http://192.168.1.100:8080/video"
```

### Collect Custom Spatial Training Data
```bash
# Launch interactive collection studio with 220D body pose features
signora body --collect
```

### Train Spatial PyTorch MLP Model
```bash
# Train 220D PyTorch neural network from data/isl_spatial/
signora body --train
```

### Fluid Timing Control
```bash
# 250ms dwell hold
signora body --dwell 250
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--source` | String / Int | `0` | Camera index, video device node, IP stream URL, or video file |
| `--phone` | Flag | `False` | Stream video from connected Android phone via scrcpy |
| `--facing` | `back` \| `front` | `back` | Camera facing direction when using `--phone` |
| `--res`, `--resolution` | String | `720p` | Resolution preset: `720p`, `480p`, `360p`, `1080p` |
| `--dwell` | Integer | `250` | Hold dwell duration in milliseconds |
| `--collect` | Flag | `False` | Launch interactive sample collection studio with body tracking |
| `--train` | Flag | `False` | Train 220D PyTorch model on collected spatial datasets |
| `--no-tts` | Flag | `False` | Disable Piper neural text-to-speech feedback |
| `--detect-every` | Integer | `2` | Landmark detection interval in frames |
| `--headless` | Flag | `False` | Run in headless mode without display window |
| `--fake-camera` | Flag | `False` | Use synthetic test camera for automated tests |

---

## 4. Anatomical Zones Tracked

- `FOREHEAD`: Nose, brow, forehead center (*THINK*, *SMART*, *REMEMBER*)
- `TEMPLE`: Left/Right temporal bone (*HELLO*, *TIRED*)
- `CHIN`: Mouth, jaw (*TELL*, *EAT*, *WATER*)
- `THROAT`: Suprasternal notch (*THIRSTY*, *DOCTOR*)
- `CHEST`: Sternum midpoint between shoulders (*NAMASTE*, *FEEL*, *PLEASE*)
- `SHOULDER`: Left/Right acromion (*RESPONSIBILITY*)
- `OPEN_SPACE`: Neutral gesture zone in front of torso (Standard alphabet)

---

## 5. Related Commands
- [[signora isl]] — Dual-handed ISL recognition without full body tracking.
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.
- [[signora collect]] — Sample collection studio.
- [[signora train]] — PyTorch neural model training.

---
title: "Command: signora words"
tags:
  - cmd/core
  - cmd/vision
aliases:
  - signora words
  - words
---

# `signora words`

> [!INFO] Summary
> Runs **Dynamic Words Motion Recognition**. Uses Dynamic Time Warping (DTW) with a k-Nearest Neighbors classifier to track multi-frame hand motion trajectories over time.

---

## 1. Syntax

```bash
signora words [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Motion Recognition
```bash
signora words
```

### High-Speed Phone Camera via scrcpy
```bash
signora words --phone
```

### Adjust Confidence Threshold
```bash
# Set stricter threshold for motion verification
signora words --threshold 0.25
```

### Process Video File
```bash
# Run dynamic gesture analysis over an offline video clip
signora words -s sample.mp4
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--source` | String / Int | `0` | Camera index or video file path |
| `--phone` | Flag | `False` | Stream video from connected Android phone via scrcpy |
| `--facing` | `back` \| `front` | `back` | Camera facing direction when using `--phone` |
| `--res`, `--resolution` | String | `720p` | Resolution preset: `720p`, `480p`, `360p`, `1080p` |
| `--threshold` | Float | `0.18` | Dynamic gesture classification distance threshold |
| `--max-frames` | Integer | `None` | Maximum frames to process before auto-exit |
| `--headless` | Flag | `False` | Run in headless mode without window |

---

## 4. Supported Gestures
- `HELLO`: Lateral outward wave
- `THANK YOU`: Forward projection from chin
- `PLEASE`: Circular motion over chest
- `GOODBYE`: Side wave
- `SORRY`: Fist circling on chest

---

## 5. Related Commands
- [[signora isl]] — Static dual-handed recognition.
- [[signora body]] — Upper-body anatomical placement recognition.
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.

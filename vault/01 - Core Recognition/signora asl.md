---
title: "Command: signora asl"
tags:
  - cmd/core
  - cmd/vision
aliases:
  - signora asl
  - signora live
  - asl
  - live
---

# `signora asl`

> [!INFO] Summary
> Runs real-time **American Sign Language (ASL)** single-handed fingerspelling recognition (letters A through Z and SPACE) with cyberpunk glassmorphic HUD and confusable pair hysteresis filtering.

---

## 1. Syntax

```bash
signora asl [OPTIONS]
# or
signora live [OPTIONS]
```

---

## 2. Common Usage Examples

### Standard Fingerspelling
```bash
signora asl
```

### Rapid Fingerspelling Dwell Override
```bash
# 180ms hold dwell for experienced, rapid fingerspellers
signora asl --dwell 180
```

### Stream from Phone Camera
```bash
signora asl --phone
```

### High-Resolution 1080p Stream
```bash
signora asl --res 1080p
```

### Headless Pipeline
```bash
signora asl --headless
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--source` | String / Int | `0` | Camera index, device path, or video file path |
| `--phone` | Flag | `False` | Stream video from connected Android phone via scrcpy |
| `--facing` | `back` \| `front` | `back` | Camera facing direction when using `--phone` |
| `--res`, `--resolution` | String | `720p` | Resolution preset: `720p`, `480p`, `360p`, `1080p` |
| `--dwell` | Integer | `250` | Hold dwell duration in milliseconds |
| `--no-tts` | Flag | `False` | Disable Piper neural audio voice output |
| `--detect-every` | Integer | `2` | Landmark detection interval in frames |
| `--headless` | Flag | `False` | Run without preview window |
| `--fake-camera` | Flag | `False` | Use synthetic test camera for automated tests |

---

## 4. Confusable Letter Pairs Filtered
Signora applies hysteresis voting to prevent visual oscillation between:
- `M` vs `N`
- `U` vs `V`
- `R` vs `U`
- `A` vs `S`
- `E` vs `S`
- `K` vs `P`

---

## 5. Related Commands
- [[signora isl]] — Dual-handed Indian Sign Language recognition.
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.
- [[signora stream]] — Broadcast ASL as virtual webcam to Zoom/Meet.

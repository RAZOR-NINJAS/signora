---
title: "Command: signora lipsync"
tags:
  - cmd/audio
  - cmd/vision
aliases:
  - signora lipsync
  - signora lip
  - signora lips
  - lipsync
---

# `signora lipsync`

> [!INFO] Summary
> **Visual Lip Reading & Speech Activity Detection**. Tracks facial lip geometry and phoneme aperture shapes to determine visual speech activity and synchronize spoken phrases with lip motion.

---

## 1. Syntax

```bash
signora lipsync [OPTIONS]
# or
signora lip [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Lip Tracking
```bash
signora lipsync
```

### Visual-Only Without Microphone Audio
```bash
# Pure visual lip motion aperture detection
signora lipsync --no-audio
```

### Use Phone Camera via scrcpy
```bash
signora lipsync --phone
```

### Automated Benchmark Testing
```bash
signora lipsync --fake-camera
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--source` | String / Int | `0` | Camera index or video file path |
| `--phone` | Flag | `False` | Use connected phone camera via scrcpy |
| `--no-audio` | Flag | `False` | Disable microphone audio synchronization |
| `--headless` | Flag | `False` | Run in headless mode without graphical window |
| `--fake-camera` | Flag | `False` | Use synthetic animated face bench for testing |

---

## 4. Related Commands
- [[signora alert]] — Acoustic environment sound alert monitor.
- [[signora isl]] — Dual-handed sign recognition.

---
title: "Command: signora learn"
tags:
  - cmd/interactive
aliases:
  - signora learn
  - learn
---

# `signora learn`

> [!INFO] Summary
> **Interactive Learning Kiosk**. Starts a browser-based educational dashboard designed for interactive sign language drills, real-time posture alignment quizzes, and visual flashcards with instant AI scoring.

---

## 1. Syntax

```bash
signora learn [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Learning Kiosk
```bash
# Starts web server on http://127.0.0.1:8000
signora learn
```

### Fullscreen Exhibition Kiosk
```bash
signora learn --kiosk
```

### Stream from Phone Camera via scrcpy
```bash
signora learn --phone
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `--host` | String | `127.0.0.1` | Bind IP address |
| `-p`, `--port` | Integer | `8000` | HTTP port |
| `-s`, `--source` | String / Int | `0` | Camera index or video file |
| `--phone` | Flag | `False` | Use connected Android phone camera via scrcpy |
| `--kiosk` | Flag | `False` | Open fullscreen kiosk browser window |
| `--no-camera` | Flag | `False` | Run web server without camera feed |

---

## 4. Related Commands
- [[signora game]] — Gesture-controlled shadow Dino runner.
- [[signora isl]] — Dual-handed ISL recognition.

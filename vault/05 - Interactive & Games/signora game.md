---
title: "Command: signora game"
tags:
  - cmd/interactive
aliases:
  - signora game
  - game
---

# `signora game`

> [!INFO] Summary
> **Gesture-Controlled Shadow Dino Game**. Starts a local HTTP and WebSockets game server running an interactive Chromium Dino Runner where game actions (Jump, Duck, Shield) are controlled in real time by physical hand sign gestures.

---

## 1. Syntax

```bash
signora game [OPTIONS]
```

---

## 2. Common Usage Examples

### Default ASL Shadow Dino Game
```bash
# Starts game on http://127.0.0.1:8000
signora game
```

### Fullscreen Kiosk Mode (Exhibition / Demo)
```bash
# Automatically launches Chrome or Edge in fullscreen kiosk window
signora game --kiosk
```

### Dual-Handed ISL Mode
```bash
# Use dual-handed Indian Sign Language gestures to control the Dino
signora game --mode isl
```

### Stream from Phone Camera via scrcpy
```bash
signora game --phone
```

### Custom Port
```bash
signora game -p 9000
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `--host` | String | `127.0.0.1` | Bind IP address |
| `-p`, `--port` | Integer | `8000` | HTTP port |
| `-m`, `--mode` | `asl` \| `isl` | `asl` | Gesture control scheme (`asl` single-hand or `isl` dual-hand) |
| `-s`, `--source` | String / Int | `0` | Camera index, device path, or video file |
| `--phone` | Flag | `False` | Use connected Android phone camera via scrcpy |
| `--kiosk` | Flag | `False` | Open fullscreen kiosk browser window |
| `--no-camera` | Flag | `False` | Run game server without initializing local camera detector |
| `--fake-camera` | Flag | `False` | Use synthetic camera for testing |

---

## 4. Gesture Controls
- **JUMP**: Open palm raised high
- **DUCK**: Fist or closed fingers held low
- **SHIELD**: Crossed hands or flat horizontal palm

---

## 5. Related Commands
- [[signora learn]] — Interactive sign drills and flashcard kiosk.
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.

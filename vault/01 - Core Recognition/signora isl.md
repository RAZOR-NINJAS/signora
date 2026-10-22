---
title: "Command: signora isl"
tags:
  - cmd/core
  - cmd/vision
aliases:
  - signora isl
  - isl
---

# `signora isl`

> [!INFO] Summary
> Launches real-time **Indian Sign Language (ISL)** dual-handed recognition with live cyberpunk glassmorphic HUD, sub-millisecond neural forward pass, and fluid continuous timing.

---

## 1. Syntax

```bash
signora isl [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Live Recognition
```bash
# Launch ISL on default webcam (device 0)
signora isl
```

### Fluid Continuous Timing (Fast Signing)
```bash
# Set dwell to 250ms (or down to 140ms with kinetic apex acceleration)
signora isl --dwell 250
```

### High-Speed Phone Camera via scrcpy
```bash
# Stream camera directly from connected Android phone over USB
signora isl --phone
```

### Enable Upper-Body Spatial Placement Anchors
```bash
# Track whole-body pose to disambiguate head/chest/throat signs
signora isl --body
```

### Custom Camera Resolution (Strict 16:9 Aspect Ratio)
```bash
# Options: 720p (default), 480p, 360p, 1080p
signora isl --res 720p
signora isl -s 1 --res 1080p
```

### Headless Server Mode (No GUI Window)
```bash
# Run headless and print confirmed transcripts directly to stdout
signora isl --headless
```

### Silent Mode (No Audio TTS)
```bash
# Disable Piper neural audio voice output
signora isl --no-tts
```

### Tier-2 LLM Grammar Synthesis
```bash
# Pass Gemini API key for natural conversational English reconstruction
signora isl --gemini-key "AIzaSy..."
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-s`, `--source` | String / Int | `0` | Camera index (`0`, `1`), device node (`/dev/video2`), or video file path |
| `--phone` | Flag | `False` | Stream video from connected Android phone via low-latency scrcpy sink |
| `--facing` | `back` \| `front` | `back` | Camera facing direction when using `--phone` |
| `--res`, `--resolution` | String | `720p` | Resolution preset: `720p`, `480p`, `360p`, `1080p` |
| `--dwell` | Integer | `250` | Hold dwell duration in milliseconds before confirming sign |
| `--body` | Flag | `False` | Enable 33-point MediaPipe upper-body pose & spatial anchor tracking |
| `--no-tts` | Flag | `False` | Disable Piper neural text-to-speech feedback |
| `--detect-every` | Integer | `2` | Run heavy hand detector every N frames (tracks on intermediate frames) |
| `--headless` | Flag | `False` | Run without HighGUI graphical preview window |
| `--fake-camera` | Flag | `False` | Use synthetic test camera for automated test benches |
| `--gemini-key` | String | `None` | Google Gemini API key for Tier-2 LLM grammar reconstruction |

---

## 4. Key Bindings (Live GUI Window)

| Key | Action |
|---|---|
| `q` or `ESC` | Exit application cleanly |
| `c` | Clear current transcript buffer |
| `BACKSPACE` | Delete last committed character or word |
| `SPACE` | Insert manual space token |
| `b` | Toggle whole-body spatial anchor overlay on/off |
| `f` | Toggle fullscreen mode |
| `m` | Toggle audio mute on/off |

---

## 5. Related Commands
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.
- [[signora body]] — Upper-body anatomical placement anchor recognition.
- [[signora collect]] — Collect custom training samples for ISL signs.
- [[signora train]] — Train PyTorch neural network on collected data.
- [[signora stream]] — Broadcast ISL stream as virtual webcam to Zoom/Meet.

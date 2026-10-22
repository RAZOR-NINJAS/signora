---
title: "Command: signora stream"
tags:
  - cmd/hardware
  - cmd/camera
aliases:
  - signora stream
  - signora vcam
  - stream
  - vcam
---

# `signora stream`

> [!INFO] Summary
> **Virtual Webcam Broadcaster**. Pipes the camera feed with real-time sign language captions, live transcript lower-thirds, and optional skeletal hand overlays directly into a virtual webcam device (`/dev/video10`) selectable in Zoom, Google Meet, Microsoft Teams, Discord, and OBS Studio.

---

## 1. Syntax

```bash
signora stream [OPTIONS]
# or
signora vcam [OPTIONS]
```

---

## 2. Common Usage Examples

### Default Virtual Webcam Stream (ISL Mode)
```bash
signora stream
```

### ASL Virtual Webcam Stream
```bash
signora stream -m asl
```

### Clean Broadcast Mode (Captions Only, No HUD Cards)
```bash
# Ideal for professional calls and webinars
signora stream --clean
```

### Clean Broadcast Without Skeletal Hand Lines
```bash
signora stream --clean --no-hands
```

### Stream from Connected Phone Camera via scrcpy
```bash
signora stream --phone
```

### Display v4l2loopback Setup Guide
```bash
signora stream --setup-v4l2
```

### Custom Resolution & Frame Rate
```bash
signora stream --width 1920 --height 1080 --fps 60
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `-m`, `--mode` | `isl` \| `asl` | `isl` | Sign language recognition mode |
| `-s`, `--source` | String / Int | `0` | Input camera index or device node |
| `--phone` | Flag | `False` | Use connected Android phone camera via scrcpy |
| `--clean` | Flag | `False` | Lower-third broadcast captions only (hides glassmorphic cards) |
| `--no-hands` | Flag | `False` | Suppress skeletal hand overlay on broadcast output |
| `--device` | String | Auto | Virtual V4L2 device node (e.g. `/dev/video10`) |
| `--width` | Integer | `1280` | Output stream width |
| `--height` | Integer | `720` | Output stream height |
| `--fps` | Integer | `30` | Output stream target FPS |
| `--no-tts` | Flag | `False` | Disable Piper neural text-to-speech audio |
| `--port` | Integer | `8888` | Local HTTP preview stream port |
| `--setup-v4l2` | Flag | `False` | Print v4l2loopback kernel module installation instructions |

---

## 4. Setup v4l2loopback on Linux

To enable the virtual camera device:
```bash
# Install kernel module package
sudo apt install -y v4l2loopback-dkms

# Load module creating /dev/video10 with exclusive caps for Chrome/Zoom
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="Signora Virtual Cam" exclusive_caps=1
```

Once loaded, select **"Signora Virtual Cam"** as your camera in Zoom, Meet, or Teams settings.

---

## 5. Related Commands
- [[signora isl]] — ISL dual-handed recognition.
- [[signora phone]] — Stream zero-latency phone camera via scrcpy.
- [[signora asl]] — Single-handed ASL recognition.

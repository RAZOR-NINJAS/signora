---
title: "Command: signora alert"
tags:
  - cmd/audio
aliases:
  - signora alert
  - signora listen
  - signora hear
  - alert
---

# `signora alert`

> [!INFO] Summary
> **Hear Through Your Eyes**. Real-time acoustic environment classifier that detects critical non-speech environmental sounds (doorbells, fire alarms/sirens, knocks, baby crying, dog barking, breaking glass) and renders instant high-contrast visual alerts and desktop notifications for Deaf and hard-of-hearing users.

---

## 1. Syntax

```bash
signora alert [OPTIONS]
# or
signora listen [OPTIONS]
```

---

## 2. Common Usage Examples

### Interactive Visual Sound Monitor
```bash
# Launch interactive HUD displaying audio waveform, decibel meter, and detected sounds
signora alert
```

### Start Background Daemon Watcher
```bash
# Spawns background process monitoring acoustics while you work in other apps
signora alert --start
```

### Inspect Background Daemon Status
```bash
signora alert --status
```

### Stop Running Background Daemon
```bash
signora alert --stop
```

### Profile Room Acoustics & Calibrate
```bash
# Measures ambient room background noise floor to set dynamic threshold
signora alert --calibrate
```

### Immediate Notification Test
```bash
# Fire an instant notification to verify OS notification system
signora alert --test
```

### Simulate Specific Acoustic Events
```bash
signora alert --simulate doorbell
signora alert --simulate siren
signora alert --simulate knock
signora alert --simulate baby
signora alert --simulate glass
```

### High Acoustic Sensitivity
```bash
signora alert --sensitivity high
```

---

## 3. Options & Flags

| Flag | Type | Default | Description |
|---|---|---|---|
| `--start` | Flag | `False` | Start background daemon watcher |
| `--stop` | Flag | `False` | Stop running background daemon watcher |
| `--status` | Flag | `False` | Inspect daemon status and recent sound log |
| `--sensitivity` | `low` \| `medium` \| `high` | `medium` | Acoustic detection threshold |
| `--calibrate` | Flag | `False` | Profile ambient room acoustics and calibrate noise floor |
| `--test` | Flag | `False` | Fire test desktop alert notification and exit |
| `--simulate` | Choice | `None` | Simulate event: `doorbell`, `knock`, `siren`, `call`, `baby`, `bark`, `glass` |
| `--no-desktop` | Flag | `False` | Disable OS desktop visual notifications |
| `-s`, `--source` | String / Int | `None` | Optional camera index if camera backdrop is desired |
| `--fake-mic` | Flag | `False` | Use synthetic microphone audio for automated testing |

---

## 4. Supported Acoustic Classes
- **DOORBELL**: High-priority chime alert
- **EMERGENCY SIREN / FIRE ALARM**: Flashing red HUD and alert sound banner
- **KNOCK ON DOOR**: Transient percussive signature
- **BABY CRYING**: Harmonic distress pitch pattern
- **DOG BARKING**: Repetitive acoustic burst
- **GLASS BREAKING**: High-frequency shatter signature
- **PHONE CALL / RINGTONE**: Sustained cadence chime

---

## 5. Related Commands
- [[signora isl]] — Dual-handed ISL recognition.
- [[signora lipsync]] — Visual speech and lip reading.

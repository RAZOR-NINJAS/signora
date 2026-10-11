# Signora System Architecture & Engineering Specifications

This document outlines the system architecture, mathematical representations, feature engineering pipelines, and classification backends powering **Signora**.

```
┌─────────────────┐       ┌────────────────────────┐       ┌───────────────────────┐
│  Video Capture  │ ────> │  Spatial Landmark      │ ────> │  Geometric Feature    │
│ (DirectShow /   │       │  Estimation            │       │  Extraction           │
│  V4L2 640x480)  │       │ (MediaPipe Tasks API)  │       │ (74D / 164D / 153D)   │
└─────────────────┘       └────────────────────────┘       └───────────────────────┘
                                                                       │
                                                                       ▼
┌─────────────────┐       ┌────────────────────────┐       ┌───────────────────────┐
│ Speech Output   │ <──── │ Temporal Dwell & State │ <──── │ Classification Engine │
│ (Piper TTS      │       │ Confirmation           │       │ (PyTorch MLP / NumPy  │
│  60ms latency)  │       │ (Debounce Machine)     │       │  FastMLP / DTW-kNN)   │
└─────────────────┘       └────────────────────────┘       └───────────────────────┘
```

---

## 1. Vision & Landmark Estimation

Signora utilizes Google MediaPipe's HandLandmarker Tasks API (`hand_landmarker.task`) configured in `RunningMode.VIDEO`.
- **Single-Hand Tracking (ASL):** Extracts 21 3D hand coordinates $(x, y, z)$.
- **Dual-Hand Tracking (ISL):** Simultaneously extracts both left and right hand keypoints (42 3D landmarks) with explicit handedness labels (`Left`, `Right`).
- **Holistic Tracking (ASL Words):** Combines 9 upper-body pose landmarks (shoulders, elbows, wrists, hips, nose) with both 21-point hand skeletons to capture full upper-body gesture trajectories.

---

## 2. Feature Engineering

To achieve zero calibration across different hands, camera angles, and body sizes, raw pixel coordinates are transformed into canonical, scale-invariant geometric feature spaces.

### 2.1 ASL Single-Hand 74D Feature Vector
For a single hand with 21 landmarks:
1. **Translation Invariance:** The wrist landmark ($W = p_0$) is subtracted from all coordinates, pinning the wrist to origin $(0, 0, 0)$.
2. **Scale Invariance:** All coordinates are divided by the palm span $S = \|p_9 - p_0\|_2$ (Euclidean distance between wrist and middle MCP).
3. **Lateral Invariance:** For left hands, the $x$-coordinates are reflected across the sagittal plane ($x \leftarrow -x$).
4. **Coordinate Block (63D):** 21 normalized coordinates $\times 3$.
5. **Tip Extension Ratios (5D):** Distance from each fingertip to its MCP joint divided by palm span.
6. **Hand Geometry (3D):**
   - Thumb-to-index pinch distance $\|p_4 - p_8\|_2 / S$.
   - Mean inter-finger spread ratio between adjacent fingertips.
   - Mean fingertip depth offset along the $z$-axis.
7. **Palm Surface Normal (3D):** Unit cross product of wrist-to-index vector and wrist-to-pinky vector:
   $$\hat{n} = \frac{(p_5 - p_0) \times (p_{17} - p_0)}{\|(p_5 - p_0) \times (p_{17} - p_0)\|_2}$$
   Total vector length: $63 + 5 + 3 + 3 = 74\text{ dimensions}$.

### 2.2 ISL Dual-Hand 164D Feature Vector
Indian Sign Language relies on dual-hand interactions where one hand frequently contacts or orbits the other:
1. **Left Hand 74D Descriptor:** Normalized single-hand geometry.
2. **Right Hand 74D Descriptor:** Normalized single-hand geometry.
3. **Inter-Hand Spatial Relations (14D):**
   - Relative wrist displacement vector: $(\vec{W}_{\text{right}} - \vec{W}_{\text{left}}) / S_{\text{avg}}$.
   - Euclidean inter-wrist distance.
   - 5 contact distances: dominant index fingertip to each base hand fingertip.
   - Dominant index tip to base wrist distance.
   - Dominant thumb tip to base index tip distance.
   - Minimum pair distance across all 25 fingertip combinations.
   - Palm-to-palm surface normal dot product $\hat{n}_{\text{left}} \cdot \hat{n}_{\text{right}}$ (quantifies coplanarity).
   - Relative wrist vertical elevation offset.
4. **Presence Indicators (2D):** Binary flags $[H_{\text{left}}, H_{\text{right}}]$ indicating hand visibility.
   Total vector length: $74 + 74 + 14 + 2 = 164\text{ dimensions}$.

### 2.3 ASL Dynamic Words 153D Holistic Trajectory
Words involving physical motion (such as `HELLO`, `THANKYOU`, `EAT`) are recorded across 30-frame temporal windows:
- 9 body landmarks + 42 dual-hand landmarks = 51 landmarks $\times 3 = 153\text{ dimensions}$.
- Coordinates are normalized relative to shoulder midpoint and inter-shoulder width.

---

## 3. Classifier Backends

| Engine | Target Mode | Implementation | Latency (AMD A6 CPU) | Memory Footprint |
| :--- | :--- | :--- | :--- | :--- |
| **PyTorch MLP** | ASL Fingerspelling | `Linear(74, 192) -> LayerNorm -> GELU -> Linear(192, 112) -> LayerNorm -> GELU -> Linear(112, 27)` | 0.82 ms | 35 MB |
| **NumPy FastMLP** | ISL Two-Handed | Pure NumPy matrix multiplication, closed-form GELU, and LayerNorm | 0.43 ms | 8 MB |
| **FastDTW-kNN** | ASL Dynamic Words | Dynamic Time Warping with Euclidean local cost and Sakoe-Chiba band | 14.2 ms | 22 MB |

---

## 4. Debounce & Gesture Confirmation State Machine

To prevent transcript jitter from transitional hand shapes, Signora implements an internal temporal debounce engine (`signora.core.debounce.DebounceEngine`):
- **Continuous Sliding Window:** Maintains a 420 ms rolling buffer of recent predictions.
- **Dwell Confirmation:** Requires candidate gesture confidence to stay $\ge 0.70$ for at least 800 ms (or 1000 ms in ISL) before committing to transcript.
- **Repeat Cooldown:** A 900 ms refractory period prevents repeated letters unless the hand is visibly relaxed or moved.
- **In-Air Space Sign:** Dedicated 800 ms hold filter for thumb-to-palm gesture to insert spaces without touching the keyboard.

---

## 5. Offline Neural Speech Synthesis

- Engine: **Piper Neural TTS** running the `en_US-lessac-medium` ONNX checkpoint.
- Execution: Non-blocking asynchronous daemon thread with in-memory letter audio caching.
- Audio Device Backend: `sounddevice` with WASAPI / DirectSound on Windows and ALSA / PulseAudio / PipeWire on Linux.

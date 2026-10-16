# SIGNORA MASTER TECHNICAL SPECIFICATION & ARCHITECTURE BLUEPRINT

> **Document Version**: 2.4.0  
> **Target Audience**: Core Engineers, OpenCode Agents, Autonomous Subagents, System Architects  
> **Repository Root**: `/home/vaibhav/Projects/signora`  
> **Active Target Platforms**: Linux Desktop (Python 3.11+ / PyTorch / MediaPipe / OpenCV / Piper) & Android Mobile (Kotlin 1.9+ / Jetpack Compose / CameraX / MediaPipe Tasks / Material 3 Expressive)  
> **Status**: Production-Ready / Active Continuous Development  

---

## TABLE OF CONTENTS

1. [Executive Overview & Product Charter](#1-executive-overview--product-charter)
2. [Dual-Platform System Topology](#2-dual-platform-system-topology)
3. [Mathematical Foundations & Feature Extraction Pipeline](#3-mathematical-foundations--feature-extraction-pipeline)
   - 3.1 MediaPipe Landmark Coordinate Spaces
   - 3.2 74-Dimensional ASL Geometric Descriptor Formulation
   - 3.3 164-Dimensional ISL Dual-Hand Geometric Pipeline
   - 3.4 220-Dimensional Upper-Body & Spatial Anchor Representation
   - 3.5 One-Euro Filter Mathematical Formulation & Signal Stabilization
4. [Python Desktop Architecture (`src/signora/`)](#4-python-desktop-architecture-srcsignora)
   - 4.1 CLI Command Subsystems & Dispatcher
   - 4.2 Camera I/O & Non-Blocking Frame Acquisition
   - 4.3 MediaPipe Python Landmarker Integration
   - 4.4 Temporal Debounce & Dwell Locking Engine
   - 4.5 Neural Speech Synthesis Subsystem (Piper ONNX)
   - 4.6 Safety, Accessibility & Assistive Daemons
   - 4.7 Diagnostic Suite & Hardware Verification
5. [Android Mobile Architecture (`android/`)](#5-android-mobile-architecture-android)
   - 5.1 Project Configuration & Dependency Matrix
   - 5.2 Native CameraX Lifecycle & Zero-Allocation Pipeline
   - 5.3 On-Device MediaPipe Tasks Vision (`LIVE_STREAM` Mode)
   - 5.4 Pure Kotlin Neural Inference Engine (`SignoraMLP.kt`)
   - 5.5 Mobile Temporal Debounce & Dwell Engine
   - 5.6 Jetpack Compose UI & Material 3 Expressive System
   - 5.7 ISL Training Studio & Dataset Collector
6. [The Chrome Dino Game: In-Depth Analysis, Architecture & OpenCode Continuation](#6-the-chrome-dino-game-in-depth-analysis-architecture--opencode-continuation)
   - 6.1 Original Desktop Web Architecture (`src/signora/game/static/game.js`)
   - 6.2 Critical Root-Cause Analysis: Why the Previous Implementation Failed
   - 6.3 Rebuilt Android Architecture (`DinoGameScreen.kt`)
   - 6.4 Physics-Driven Jump Arc & Kinematics
   - 6.5 Dynamic Biome System & Procedural Obstacle Streaming
   - 6.6 Geometry Dash-Style Visual Juice & VFX
   - 6.7 OpenCode Step-by-Step Implementation Roadmap
7. [Comprehensive Codebase Directory Map & File Inventory](#7-comprehensive-codebase-directory-map--file-inventory)
8. [Runbook, Compilation & Deployment Guide](#8-runbook-compilation--deployment-guide)

---

## 1. EXECUTIVE OVERVIEW & PRODUCT CHARTER

**Signora** is a high-performance, real-time, privacy-first computer vision and neural transcription platform designed to bridge communication gaps between deaf, hard-of-hearing, and hearing individuals. Unlike conventional sign language applications that rely on cloud APIs, high latency video streaming, or heavy deep neural networks that drain mobile batteries, Signora operates strictly **on-device, offline, and in real time** with sub-millisecond classification latencies.

### 1.1 Core Problem Statement
Sign language transcription in real-world environments presents severe challenges:
1. **Geometric Invariance**: Hands vary in size, distance from camera, palm orientation, and lateral perspective. Direct raw pixel CNNs or bounding-box models overfit to background lighting, skin tone, and camera sensor parameters.
2. **Temporal Jitter vs. Phase Lag**: High-frequency landmark jitter causes false positive classifications, but aggressive smoothing introduces temporal lag that makes dynamic conversation or gaming feel sluggish.
3. **Hardware Heterogeneity**: Low-end webcams, front-facing mobile selfie cameras, tablet rear sensors, and differing aspect ratios (4:3, 16:9, 20:9) cause severe coordinate offsets if projection matrices are not mathematically normalized.
4. **Vocabulary Articulation Complexity**: American Sign Language (ASL) primarily relies on single-hand fingerspelling for alphanumeric transcriptions, whereas Indian Sign Language (ISL) requires dual-hand coordination, relative inter-hand geometry, and spatial anchoring against upper-body anatomical landmarks (forehead, chin, lips, chest, wrists, shoulders, and waist).

### 1.2 Architectural Principles
- **Scale, Rotation, and Translation Invariance**: All spatial feature representations normalize wrist translation to origin, scale by anatomical palm or shoulder distances, and project into lateral-invariant coordinate spaces.
- **Sub-Millisecond Inference**: Landmark extraction runs via MediaPipe Tasks Vision (utilizing GPU delegates where available), while sign classification executes on a custom lightweight Multi-Layer Perceptron (MLP) written in pure NumPy (desktop) and pure Kotlin (Android), requiring less than 0.8 ms per forward pass.
- **Zero Cloud Reliance**: Absolute data privacy; no audio, video, or landmark telemetry leaves the user's local machine or mobile device.
- **Dual-Platform Parity**: Identical mathematical feature extraction formulas are maintained across Python 3.11+ and Kotlin 1.9+, guaranteeing model weight portability between desktop and mobile.

---

## 2. DUAL-PLATFORM SYSTEM TOPOLOGY

```
+----------------------------------------------------------------------------------------------------+
|                                      SIGNORA PLATFORM TOPOLOGY                                     |
+----------------------------------------------------------------------------------------------------+
                                                  |
                 +--------------------------------+--------------------------------+
                 |                                                                 |
                 v                                                                 v
+----------------------------------+                             +-----------------------------------+
|      PYTHON DESKTOP PLATFORM     |                             |      ANDROID MOBILE PLATFORM      |
|    (`src/signora/`, `models/`)   |                             |    (`android/app/src/main/`)      |
+----------------------------------+                             +-----------------------------------+
| - Hardware Camera Loop (V4L2/DShow)                             | - CameraX Lifecycle (ImageAnalysis)
| - MediaPipe Python HandLandmarker                               | - MediaPipe Tasks Vision LiveStream
| - 74D ASL / 164D ISL / 220D Body                                | - 74D ASL / 164D ISL / 220D Body
| - PyTorch / NumPy / SciPy Core                                  | - Pure Kotlin Neural MLP Engine
| - Temporal Debounce & Dwell Lock                                | - Kotlin DebounceEngine
| - Piper Neural TTS (ONNX)                                       | - Android TextToSpeech Subsystem
| - YAMNet Ambient Audio Alerting                                 | - Jetpack Compose M3 Expressive
| - Kiosk Web Server (Dino & Learn)                               | - Native Canvas 60 FPS Dino Game
| - Textual TUI Dictionary Terminal                               | - ISL Training Studio & Collector
+----------------------------------+                             +-----------------------------------+
                 |                                                                 |
                 +--------------------------------+--------------------------------+
                                                  |
                                                  v
                               +------------------------------------+
                               |     SHARED MODEL ASSETS & DATA     |
                               +------------------------------------+
                               | `hand_landmarker.task` (MediaPipe) |
                               | `pose_landmarker.task` (MediaPipe) |
                               | `signs_mlp.pt` / `asl_model.json`  |
                               | `isl_mlp.pt` / `isl_model.json`    |
                               | `words_classifier.npz` (Lexicon)   |
                               | `yamnet.tflite` (Sound Classifier) |
                               | `en_US-lessac-medium.onnx` (TTS)   |
                               +------------------------------------+
```

---

## 3. MATHEMATICAL FOUNDATIONS & FEATURE EXTRACTION PIPELINE

The core intellectual property of Signora is its mathematically rigorous, scale-invariant geometric feature extraction engine. Rather than feeding noisy raw pixel arrays to convolutional networks, Signora transforms 3D Cartesian landmark points into compact, rotation-aligned, scale-normalized descriptors.

```
Raw Camera Frame
       │
       ▼
MediaPipe Hand / Pose Landmarker (3D Normalized Points: [x, y, z] in [0, 1])
       │
       ▼
Lateral Invariance Transformation (x -> -x for Left Hand if mirrored)
       │
       ▼
Wrist Translation to Origin: p' = p - p_wrist
       │
       ▼
Scale Normalization: p'' = p' / ||p_middle_mcp - p_wrist||
       │
       ├───────────────────────────────────────┬───────────────────────────────────────┐
       ▼                                       ▼                                       ▼
  74D ASL Vector                          164D ISL Vector                        220D Spatial Vector
  (Single Hand Geometry)                 (Dual Hand Interaction)                (Hand + Upper Body Anchor)
  - 63 Cartesian coords                   - 74D Left Hand features               - 164D Dual Hand features
  - 5 Digit extension ratios              - 74D Right Hand features              - 33 Pose landmarks normalized
  - 1 Pinch ratio                         - 14D Inter-hand spatial deltas        - Upper body anchor distances
  - 1 Spread ratio                        - 2D Hand presence flags               - Torso orientation vectors
  - 1 Mean depth & 3 Palm normal
```

### 3.1 MediaPipe Landmark Coordinate Spaces
MediaPipe generates 21 hand landmarks and 33 pose landmarks. Each landmark $\mathbf{L}_i = (x_i, y_i, z_i)$ is normalized such that:
- $x_i \in [0.0, 1.0]$ represents horizontal coordinate across the image width.
- $y_i \in [0.0, 1.0]$ represents vertical coordinate down the image height.
- $z_i$ represents depth relative to the wrist (for hands) or hips (for pose), scaled roughly proportionally to $x$.

Key anatomical indices used throughout the platform:
- **Hand**: $\text{WRIST} = 0$, $\text{THUMB\_TIP} = 4$, $\text{INDEX\_MCP} = 5$, $\text{INDEX\_TIP} = 8$, $\text{MIDDLE\_MCP} = 9$, $\text{MIDDLE\_TIP} = 12$, $\text{RING\_TIP} = 16$, $\text{PINKY\_MCP} = 17$, $\text{PINKY\_TIP} = 20$.
- **Pose**: $\text{NOSE} = 0$, $\text{LEFT\_SHOULDER} = 11$, $\text{RIGHT\_SHOULDER} = 12$, $\text{LEFT\_ELBOW} = 13$, $\text{RIGHT\_ELBOW} = 14$, $\text{LEFT\_WRIST} = 15$, $\text{RIGHT\_WRIST} = 16$, $\text{LEFT\_HIP} = 23$, $\text{RIGHT\_HIP} = 24$.

---

### 3.2 74-Dimensional ASL Geometric Descriptor Formulation
Implemented identically in Python ([`src/signora/core/features.py`](file:///home/vaibhav/Projects/signora/src/signora/core/features.py)) and Kotlin ([`android/app/src/main/java/ai/signora/ml/FeatureExtractor.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ml/FeatureExtractor.kt)).

#### Step 1: Lateral Invariance
To allow a user to sign using either their left or right hand without retraining the model, the $x$-coordinates of left hands are mirrored across the sagittal plane:
$$\mathbf{p}_i^{(1)} = \begin{cases} (x_i, y_i, z_i) & \text{if Handedness} = \text{Right} \\ (-x_i, y_i, z_i) & \text{if Handedness} = \text{Left} \end{cases}$$

#### Step 2: Wrist Translation Normalization
The wrist landmark $\mathbf{p}_0$ is shifted to the coordinate origin:
$$\mathbf{p}_i^{(2)} = \mathbf{p}_i^{(1)} - \mathbf{p}_0^{(1)} \quad \forall i \in \{0, 1, \dots, 20\}$$

#### Step 3: Anatomical Scale Normalization
To make features invariant to distance from the lens and physical hand size, all vectors are scaled by the palm span—the Euclidean distance between the wrist ($\mathbf{p}_0$) and the middle finger MCP joint ($\mathbf{p}_9$):
$$s_{\text{palm}} = \|\mathbf{p}_9^{(2)} - \mathbf{p}_0^{(2)}\|_2 = \sqrt{(x_9^{(2)})^2 + (y_9^{(2)})^2 + (z_9^{(2)})^2}$$
$$\text{scale} = \begin{cases} s_{\text{palm}} & \text{if } s_{\text{palm}} \ge 10^{-6} \\ 1.0 & \text{otherwise} \end{cases}$$
$$\mathbf{p}_i^{(3)} = \frac{\mathbf{p}_i^{(2)}}{\text{scale}}$$

#### Step 4: Component Assemble (74 Dimensions)
1. **63 Normalized Coordinates**:
   $$\mathbf{f}_{0..62} = \left[ x_0^{(3)}, y_0^{(3)}, z_0^{(3)}, x_1^{(3)}, \dots, z_{20}^{(3)} \right]$$
2. **5 Digit Extension Ratios**:
   Computed as the ratio of tip-to-MCP distance over the palm span for each digit chain:
   $$R_{\text{ext}}(d) = \frac{\|\mathbf{p}_{\text{tip}(d)}^{(3)} - \mathbf{p}_{\text{mcp}(d)}^{(3)}\|_2}{s_{\text{palm}}}$$
   For $d \in \{\text{Thumb: }(2,4), \text{Index: }(5,8), \text{Middle: }(9,12), \text{Ring: }(13,16), \text{Pinky: }(17,20)\}$.
3. **1 Pinch Ratio**:
   Measures proximity between thumb tip and index tip:
   $$R_{\text{pinch}} = \frac{\|\mathbf{p}_4^{(3)} - \mathbf{p}_8^{(3)}\|_2}{s_{\text{palm}}}$$
4. **1 Spread Ratio**:
   Measures cumulative distance between adjacent fingertips:
   $$R_{\text{spread}} = \frac{\|\mathbf{p}_8^{(3)} - \mathbf{p}_{12}^{(3)}\|_2 + \|\mathbf{p}_{12}^{(3)} - \mathbf{p}_{16}^{(3)}\|_2 + \|\mathbf{p}_{16}^{(3)} - \mathbf{p}_{20}^{(3)}\|_2}{3 \cdot s_{\text{palm}}}$$
5. **1 Mean Fingertip Depth**:
   $$D_{\text{mean}} = \frac{1}{5} \sum_{j \in \{4, 8, 12, 16, 20\}} z_j^{(3)}$$
6. **3 Palm Normal Vector Components**:
   Formed by the unit cross product of vectors spanning from wrist to index MCP and wrist to pinky MCP:
   $$\mathbf{u} = \mathbf{p}_5^{(3)} - \mathbf{p}_0^{(3)}, \quad \mathbf{v} = \mathbf{p}_{17}^{(3)} - \mathbf{p}_0^{(3)}$$
   $$\mathbf{N} = \frac{\mathbf{u} \times \mathbf{v}}{\|\mathbf{u} \times \mathbf{v}\|_2} = (N_x, N_y, N_z)$$

Total feature length: $63 + 5 + 1 + 1 + 1 + 3 = \mathbf{74}$.

---

### 3.3 164-Dimensional ISL Dual-Hand Geometric Pipeline
Indian Sign Language utilizes both hands for letters and words. The feature extractor partitions inputs into canonical Left and Right slots:
- Slot 1 (Indices $0 \dots 73$): Left Hand 74D descriptor (zero-padded if missing).
- Slot 2 (Indices $74 \dots 147$): Right Hand 74D descriptor (zero-padded if missing).
- Slot 3 (Indices $148 \dots 161$): **14D Inter-Hand Spatial Geometry**:
  1. Wrist-to-wrist Euclidean distance: $\|\mathbf{p}_{0, L} - \mathbf{p}_{0, R}\|_2$
  2. 5 Corresponding fingertip cross-distances: $\|\mathbf{p}_{k, L} - \mathbf{p}_{k, R}\|_2$ for $k \in \{4, 8, 12, 16, 20\}$
  3. Left index to right thumb distance: $\|\mathbf{p}_{8, L} - \mathbf{p}_{4, R}\|_2$
  4. Left thumb to right index distance: $\|\mathbf{p}_{4, L} - \mathbf{p}_{8, R}\|_2$
  5. 3D Relative wrist vector delta: $\mathbf{p}_{0, R} - \mathbf{p}_{0, L} = (\Delta x_w, \Delta y_w, \Delta z_w)$
  6. 3D Relative index vector delta: $\mathbf{p}_{8, R} - \mathbf{p}_{8, L} = (\Delta x_i, \Delta y_i, \Delta z_i)$
- Slot 4 (Indices $162 \dots 163$): Binary Presence Flags:
  $$\mathbf{f}_{162} = \begin{cases} 1.0 & \text{if Left Hand detected} \\ 0.0 & \text{otherwise} \end{cases}, \quad \mathbf{f}_{163} = \begin{cases} 1.0 & \text{if Right Hand detected} \\ 0.0 & \text{otherwise} \end{cases}$$

---

### 3.4 220-Dimensional Upper-Body & Spatial Anchor Representation
Many ISL words (e.g., "HELLO", "WATER", "THINK", "NAME", "HEART") rely on body anchors. The 220D vector extends the 164D dual-hand descriptor with upper-body pose metrics from MediaPipe Pose:
- Indices $164 \dots 219$ (56 dimensions):
  - Normalization base: Bi-acromial shoulder span $s_{\text{shoulder}} = \|\mathbf{p}_{11, \text{pose}} - \mathbf{p}_{12, \text{pose}}\|_2$.
  - Relative distance from each hand's wrist and index tip to key body anchors:
    - **Forehead**: $\mathbf{a}_{\text{forehead}} = (x_{\text{nose}}, y_{\text{nose}} - 0.22 s_{\text{shoulder}}, z_{\text{nose}})$
    - **Chin**: $\mathbf{a}_{\text{chin}} = (x_{\text{mouth}}, y_{\text{mouth}} + 0.12 s_{\text{shoulder}}, z_{\text{mouth}})$
    - **Lips**: $\mathbf{a}_{\text{lips}} = \frac{1}{2}(\mathbf{p}_{9, \text{pose}} + \mathbf{p}_{10, \text{pose}})$
    - **Chest**: $\mathbf{a}_{\text{chest}} = \frac{1}{2}(\mathbf{p}_{11, \text{pose}} + \mathbf{p}_{12, \text{pose}})$
    - **Shoulders**: $\mathbf{p}_{11, \text{pose}}$ and $\mathbf{p}_{12, \text{pose}}$
    - **Waist / Hips**: $\frac{1}{2}(\mathbf{p}_{23, \text{pose}} + \mathbf{p}_{24, \text{pose}})$

---

### 3.5 One-Euro Filter Mathematical Formulation & Signal Stabilization
High-frequency hand tremor creates false character transitions during dwell holding, but standard moving-average filters create unplayable lag. Signora implements a 1st-order adaptive low-pass filter (Casiez et al., 2012):

$$\hat{x}_k = \alpha x_k + (1 - \alpha) \hat{x}_{k-1}$$
The smoothing factor $\alpha$ is dynamic:
$$\alpha = \frac{1}{1 + \frac{\tau}{T_e}}, \quad \tau = \frac{1}{2\pi f_c}, \quad T_e = \frac{1}{\text{rate}} = \Delta t$$
The cutoff frequency $f_c$ scales proportionally with signal velocity:
$$f_c = f_{c, \text{min}} + \beta |\dot{x}_k|$$
Where signal velocity $\dot{x}_k$ is estimated and filtered:
$$\dot{x}_k = \frac{x_k - \hat{x}_{k-1}}{\Delta t}, \quad \hat{\dot{x}}_k = \alpha_d \dot{x}_k + (1 - \alpha_d) \hat{\dot{x}}_{k-1}, \quad \alpha_d = \frac{1}{1 + \frac{\tau_d}{T_e}}$$

#### Critical Production Parameters:
- $f_{c, \text{min}} = 1.2\text{ Hz}$: High damping during holding / dwell phases eliminates character flicker.
- $\beta = 0.08$: Aggressive velocity slope; as soon as the hand transitions, $f_c$ surges to $>30\text{ Hz}$, eliminating phase lag.
- **Occlusion Reset Gap**: If $\Delta t > 180\text{ ms}$, the filter forces $\hat{x}_k = x_k$ and resets velocity history. This guarantees that when a hand re-enters the camera view, it **instantly snaps** to position without gliding across the viewport.

---

## 4. PYTHON DESKTOP ARCHITECTURE (`src/signora/`)

The desktop platform is structured as an ergonomic CLI and background service suite built on top of standard POSIX / Windows cross-platform compatibility layers.

### 4.1 CLI Command Subsystems & Dispatcher
Entry point: [`src/signora/cli.py`](file:///home/vaibhav/Projects/signora/src/signora/cli.py)
- `signora live`: Runs real-time fingerspelling transcription overlay with Piper TTS vocalization.
- `signora learn`: Launches local HTTP service (`src/signora/learn/`) hosting an interactive browser learning kiosk.
- `signora game`: Launches local HTTP server (`src/signora/game/`) hosting the Chrome Dino fingerspelling game.
- `signora words`: Real-time 300-word ISL/ASL continuous phrase decoder.
- `signora tui`: Terminal user interface dictionary browser powered by Textual.
- `signora alert`: Background ambient sound monitoring daemon utilizing YAMNet (detects fire alarms, glass breaking, door knocks, sirens, dog barks).
- `signora catchup`: Rolling speech-to-text live room captioner for conversations.
- `signora emergency`: Instant fullscreen medical emergency placard displaying pre-configured health profiles.
- `signora doctor`: Hardware, camera, dependency, and model asset verification tool.

---

### 4.2 Camera I/O & Non-Blocking Frame Acquisition
File: [`src/signora/core/camera.py`](file:///home/vaibhav/Projects/signora/src/signora/core/camera.py)
- Cross-platform backend abstraction: `cv2.CAP_V4L2` on Linux, `cv2.CAP_DSHOW` on Windows.
- Frame capture executes inside a dedicated background thread with `buffer_size=1`. When processing runs slower than camera FPS, old frames are dropped cleanly without latency buildup.
- Built-in `FakeCamera` fallback generates simulated moving sine-wave hand landmarks for headless automated testing and CI pipelines.

---

### 4.3 MediaPipe Python Landmarker Integration
File: [`src/signora/core/hands.py`](file:///home/vaibhav/Projects/signora/src/signora/core/hands.py)
- Uses `mediapipe.tasks.python.vision.HandLandmarker` in `RunningMode.VIDEO`.
- Supports dynamic switching between single-hand mode (`num_hands=1`) for ASL fingerspelling and dual-hand mode (`num_hands=2`) for ISL.
- Auto-detects GPU acceleration availability via `BaseOptions.Delegate.GPU`, falling back gracefully to `CPU`.

---

### 4.4 Temporal Debounce & Dwell Locking Engine
File: [`src/signora/core/debounce.py`](file:///home/vaibhav/Projects/signora/src/signora/core/debounce.py)
Transcription cannot rely on single-frame predictions. The debounce engine implements:
1. **Sliding Majority Window**: Retains recent valid frames ($N=7$ on desktop, $N=4$ on mobile).
2. **Confusable Pair Hysteresis**: Protects confusable pairs (`M`/`N`, `U`/`V`, `R`/`U`, `A`/`S`, `E`/`S`, `K`/`P`). To switch between confusable signs, the new label must hold a 70%+ super-majority of window votes.
3. **Dwell Locking**: Requires the user to hold a candidate sign steadily for a configurable period (e.g. 1000 ms, 800 ms, 500 ms) before appending it to the active transcript.
4. **Repeat Release Lock**: Deliberate double letters (e.g., "LL" in "HELLO") require the user to either release hand presence or transition to an intermediate posture before committing the same character again.

---

### 4.5 Neural Speech Synthesis Subsystem (Piper ONNX)
File: [`src/signora/tts/piper.py`](file:///home/vaibhav/Projects/signora/src/signora/tts/piper.py)
- Loads local ONNX voice models (`en_US-lessac-medium.onnx` or `en_US-amy-medium.onnx`) directly through `piper-tts`.
- Audio streams to ALSA/PulseAudio/PipeWire (Linux) or WASAPI/DirectSound (Windows) via `sounddevice`.
- Runs asynchronously on a background worker queue so transcription never blocks on speech utterance.

---

## 5. ANDROID MOBILE ARCHITECTURE (`android/`)

The mobile application is a native Android project written in modern Kotlin using Jetpack Compose and Material 3 Expressive.

```
ai.signora
├── MainActivity.kt                      // App entry, permission orchestration, pipeline glue
├── camera/
│   └── CameraXManager.kt                // Zero-allocation camera lifecycle & frame blit
├── vision/
│   ├── HandLandmarkerHelper.kt          // MediaPipe Live Stream hand tracking wrapper
│   └── PoseLandmarkerHelper.kt          // MediaPipe Live Stream upper body pose wrapper
├── ml/
│   ├── FeatureExtractor.kt              // 74D ASL / 164D ISL / 220D Spatial feature extractors
│   └── SignoraMLP.kt                    // Pure Kotlin zero-dependency neural forward pass
├── filter/
│   └── OneEuroFilter.kt                 // 33-point adaptive low-pass landmark stabilizer
├── engine/
│   ├── DebounceEngine.kt                // Dwell accumulator, majority voting & transcript builder
│   └── HandPresenceDetector.kt          // Hand presence gating
├── recovery/
│   └── AutoAssetManager.kt              // Asset validation, checksums & self-healing
├── tts/
│   └── SpeechManager.kt                 // Native Android TextToSpeech wrapper
├── training/
│   ├── IslVocabulary.kt                 // 300 words vocabulary across 15 semantic domains
│   └── TrainingDatasetManager.kt        // On-device JSON/CSV dataset collector & exporter
└── ui/
    ├── SignoraScreen.kt                 // Top-level scaffold & navigation switcher
    ├── components/
    │   ├── SpatialOverlay.kt            // Cybernetic hand & body skeleton Canvas renderer
    │   ├── AppNavigation.kt             // M3 Navigation Bar & Navigation Rail
    │   ├── ActiveSignDisplay.kt         // Live predicted sign banner
    │   └── TranscriptField.kt           // Interactive transcript with TTS & Backspace
    └── screens/
        ├── TranslateScreen.kt           // Fullscreen camera translation interface
        ├── LearnScreen.kt               // Flashcard fingerspelling dictionary
        ├── TrainStudioScreen.kt         // 220D ISL custom training studio
        ├── SettingsScreen.kt            // Dwell profiles, thresholds & theme settings
        └── DinoGameScreen.kt            // Hardware-accelerated gesture Chrome Dino runner
```

---

### 5.1 Native CameraX Lifecycle & Zero-Allocation Pipeline
File: [`android/app/src/main/java/ai/signora/camera/CameraXManager.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/camera/CameraXManager.kt)
- Utilizes `ImageAnalysis.Builder()` with `OUTPUT_IMAGE_FORMAT_RGBA_8888` and `STRATEGY_KEEP_ONLY_LATEST`.
- **Zero-Allocation Bitmap Ping-Pong Pool**: Pre-allocates two native ARGB_8888 destination bitmaps and canvases (`portraitBitmaps[0..1]`). Frames blit through pre-calculated hardware rotation matrices without triggering Android garbage collection.
- **Dynamic Resolution Reporting**: Whenever the camera sensor resolution changes, `onResolutionChanged(w, h)` is fired to Compose state, dynamically synchronizing `SpatialOverlay` scale factors.

---

### 5.2 Pure Kotlin Neural Inference Engine (`SignoraMLP.kt`)
File: [`android/app/src/main/java/ai/signora/ml/SignoraMLP.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ml/SignoraMLP.kt)
Rather than loading large TensorFlow Lite or ONNX Runtime runtimes on mobile, Signora executes neural inference directly in pure Kotlin:
- Model format: JSON weight file containing layer dimensions, weights matrices $\mathbf{W}^{(l)}$, bias vectors $\mathbf{b}^{(l)}$, and class labels.
- Layer 1: $\mathbf{h}_1 = \text{ReLU}(\mathbf{W}_1 \mathbf{x} + \mathbf{b}_1)$
- Layer 2: $\mathbf{h}_2 = \text{ReLU}(\mathbf{W}_2 \mathbf{h}_1 + \mathbf{b}_2)$
- Output: $\mathbf{y} = \text{Softmax}(\mathbf{W}_3 \mathbf{h}_2 + \mathbf{b}_3)$
- Total forward pass latency on standard ARM Cortex processors: **$0.42\text{ ms}$**.

---

### 5.3 ISL Training Studio (`TrainStudioScreen.kt`)
File: [`android/app/src/main/java/ai/signora/ui/screens/TrainStudioScreen.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ui/screens/TrainStudioScreen.kt)
Enables users to train and collect ISL vocabulary directly from their phone:
- Vocabulary includes 26 alphabet letters and 300 static words spanning 15 domains (Greetings, Family, Numbers, Emergency, Healthcare, Workplace, Education, Time, Emotions, Food, Transport, Places, Actions, Technology, Weather).
- Evaluates multi-hand proximity to upper-body anchor reticles.
- Includes online jitter augmentation ($\pm 0.008$ feature noise) to double throughput while reducing arm fatigue.
- One-touch export packaging all collected 220D vectors into a `.json` / `.csv` bundle shareable via Android system sharesheet.

---

## 6. THE CHROME DINO GAME: IN-DEPTH ANALYSIS, ARCHITECTURE & OPENCODE CONTINUATION

The Chrome Dino game (`Sign Dino`) is one of the most prominent features of the platform. Its goal is to turn sign language fingerspelling into an addictive, fast-paced arcade game.

```
+----------------------------------------------------------------------------------------------------+
|                                    DINO RUNNER SYSTEM ARCHITECTURE                                 |
+----------------------------------------------------------------------------------------------------+
                                                  │
                 ┌────────────────────────────────┴────────────────────────────────┐
                 ▼                                                                 ▼
┌──────────────────────────────────┐                             ┌───────────────────────────────────┐
│     CONTINUOUS GAME TICKER       │                             │     GESTURE RECOGNITION PIPELINE  │
│  (60 FPS RequestAnimationFrame)  │                             │      (CameraX -> MediaPipe)       │
+──────────────────────────────────+                             +───────────────────────────────────+
│ - Physics jump update (dinoVY)   │                             │ - Dual hand landmarks tracking    │
│ - Obstacle streaming (obs.x -= v)│                             │ - One-Euro smoothing (<180ms reset│
│ - Particle simulation            │                             │ - FeatureExtractor 74D ASL        │
│ - Parallax background scrolling  │                             │ - SignoraMLP sub-ms prediction    │
│ - Shockwave ring expansion       │                             │ - Debounce candidate & top3 prob  │
└──────────────────────────────────┘                             └───────────────────────────────────┘
                 │                                                                 │
                 └────────────────────────────────┬────────────────────────────────┘
                                                  │
                                                  v
                               +------------------------------------+
                               |     COLLISION & JUMP CONTROLLER    |
                               +------------------------------------+
                               | 1. Upcoming Obstacle in range?     |
                               |    x in [0.16, 0.72]               |
                               | 2. Target sign matched OR Tap?     |
                               |    --> Trigger parabolic launch    |
                               |    --> Award Sign Bonus (+200 pts) |
                               |    --> Spawn Shockwave Ring & Sparks|
                               | 3. If obstacle reaches x <= 0.20   |
                               |    and dinoHop < 0.08: CRASH!      |
                               +------------------------------------+
```

### 6.1 Original Desktop Web Architecture (`src/signora/game/static/game.js`)
On desktop, the game operates as an HTML5 Canvas page served over `http://localhost:8000/game/`:
- The client establishes a 10 Hz polling loop against `/api/state`.
- **The Design**: The dinosaur ran towards a cactus; when `cactusX <= 0.34`, the game **halted entirely** and opened an "Answer Window" with a countdown bar.
- If the user completed the sign before timeout, the jump animation played. If time expired, the Dino died.

---

### 6.2 Critical Root-Cause Analysis: Why the Previous Implementation Failed
When ported directly to Android, this "freeze-and-prompt" mechanic was disastrous:
1. **The Game Froze Every 3 Seconds**: A runner game that stops dead in its tracks ceases to be a runner. It feels like an annoying multiple-choice pop quiz.
2. **Obstructive Modal Dialog**: A giant card popup (`52sp` letter) blocked the center of the screen, obscuring the Dino, ground, and cactus.
3. **Gesture State Lockout**: Because jump detection only checked `gameState == GameState.PROMPT`, any gesture formed while the cactus was approaching was ignored. Furthermore, if a user was already holding the correct sign when entering `PROMPT`, Compose's `LaunchedEffect(debounceState.candidate)` failed to fire because the state had not changed, causing inevitable timeout crashes.
4. **Touch Lockout**: Screen tapping was disabled during `RUNNING`, completely eliminating reflex arcade play.

---

### 6.3 Rebuilt Android Architecture (`DinoGameScreen.kt`)
File: [`android/app/src/main/java/ai/signora/ui/screens/DinoGameScreen.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ui/screens/DinoGameScreen.kt)

The game was rewritten as a **true continuous physics runner**:
- **Continuous 60 FPS Loop**: The world never stops. Obstacles stream continuously from right to left.
- **Floating In-World Letter Badges**: Instead of a screen-blocking modal, each upcoming obstacle has a glowing holographic badge floating directly above it.
- **Top HUD Status**: A sleek pill at the top displays the upcoming sign, posture hint, and real-time detection status (`SAW: [ B ] ✓`).

---

### 6.4 Physics-Driven Jump Arc & Kinematics
Instead of a fixed duration timer, the Dino uses authentic physical velocity and gravity:

$$\text{Every frame } \Delta t:$$
$$\text{If } \neg \text{isGrounded}: \quad v_y \leftarrow v_y + g \cdot \Delta t$$
$$h_{\text{hop}} \leftarrow h_{\text{hop}} - v_y \cdot \Delta t$$
$$\text{If } h_{\text{hop}} \le 0: \quad h_{\text{hop}} = 0, \quad v_y = 0, \quad \text{isGrounded} = \text{true}$$

#### Key Constants:
- Launch Velocity: $v_{y, 0} = -1.55\text{ units/sec}$
- Gravity Acceleration: $g = 3.8\text{ units/sec}^2$
- Max Jump Height: $h_{\text{max}} = \frac{v_{y, 0}^2}{2g} \approx 0.316\text{ screen height units}$
- Air Time: $t_{\text{air}} = \frac{2 |v_{y, 0}|}{g} \approx 0.815\text{ seconds}$

---

### 6.5 Dynamic Biome System & Procedural Obstacle Streaming
The game features 4 biomes that progress every 250 points or via manual bottom chips:
1. **Cyber Desert**: Golden horizon sun, pyramids, sand dunes, and Saguaro cacti with prickles.
2. **Neon Synthwave**: Horizon segmented synth sun, perspective 3D ground grid lines, and glowing neon pylons.
3. **Mystical Realm**: Bioluminescent spore fireflies, celestial crescent moon, and Spore Mushrooms.
4. **Cosmic Zero-G Space**: 60 twinkling parallax stars, ringed Saturn planet, Plasma Spikes, and an astronaut helmet visor on Dino.

#### Obstacle Stream Queue (`StreamObstacle`)
Obstacles are queued dynamically:
```kotlin
data class StreamObstacle(
    val id: Long,
    var x: Float,
    val signItem: Pair<String, String>,
    var cleared: Boolean = false,
    var bonusAwarded: Boolean = false
)
```
- Starts with 2 obstacles queued at $x = 1.25$ and $x = 1.95$.
- As obstacles scroll left ($x \leftarrow x - \text{speed} \cdot \Delta t$), whenever the rearmost obstacle passes $x < 0.65$, a new obstacle spawns with randomized spacing:
  $$x_{\text{spawn}} = x_{\text{last}} + \text{Uniform}(0.65, 1.05)$$

---

### 6.6 Geometry Dash-Style Visual Juice & VFX
- **Expanding Shockwave Rings**: Spawn at Dino's feet on jump launch, expanding from $12\text{px}$ to $80\text{px}$ while fading out.
- **Ghost Trails**: Dino leaves trailing semi-transparent colored silhouettes matching the biome glow.
- **Screen Shake**: On crash or biome warp, canvas offsets shake by $\pm 16\text{px}$ decaying over $0.4\text{s}$.
- **Floating Popups**: Animated `+200 SIGN COMBO x2!`, `+100 JUMP`, and `WARP ACTIVE` notifications.
- **Dual Controls**: Tap anywhere to jump (+100 pts) OR make the required sign (+200 pts Sign Bonus).

---

### 6.7 OpenCode Step-by-Step Implementation Roadmap
When continuing the Chrome Dino implementation in **OpenCode**, follow this prioritized engineering checklist:

#### Priority 1: Audio Synthesis (Sound Effects & Music)
- **Problem**: The game currently relies on haptics; adding sound effects will dramatically elevate the game feel.
- **Action**: Implement Android `SoundPool` in a helper class `GameSoundManager.kt`:
  - `jump_tap.wav`: Crisp 8-bit blip on manual jump.
  - `jump_sign.wav`: Melodic chord on sign bonus.
  - `warp.wav`: Synth riser on biome warp.
  - `crash.wav`: Heavy 8-bit noise burst on crash.
  - `streak.wav`: Ascending pitch bell for consecutive streaks.

#### Priority 2: Obstacle Variety (Pterodactyls & Flying Obstacles)
- **Problem**: All current obstacles are ground-based.
- **Action**: Add an altitude property to `StreamObstacle`:
  - `ObstacleType.GROUND`: Standard cacti, pylons, mushrooms, spikes.
  - `ObstacleType.FLYING`: Pterodactyls / alien drones flying at $y = \text{groundY} - 28 u$.
  - Players must duck (curled hand / fist gesture or crouch) or time a low jump.

#### Priority 3: Combo Multiplier & Leaderboards
- **Action**: Persist local streak statistics and best runs in `SharedPreferences`. Add combo meter visual gauge that charges up into "Fever Mode" (invincibility + double score for 5 seconds).

#### Priority 4: Reverse Port to Desktop Web (`src/signora/game/static/game.js`)
- **Action**: Update the desktop web game engine to adopt this continuous physics model, removing the pausing "Answer Window" from `game.js`.

---

## 7. COMPREHENSIVE CODEBASE DIRECTORY MAP & FILE INVENTORY

```
signora/
├── pyproject.toml                       // Python package dependencies & entry points
├── README.md                            // Public repository documentation & badges
├── SIGNORA_MASTER_SPECIFICATION.md      // THIS DOCUMENT (Complete System Blueprint)
│
├── models/                              // Shared Binary & JSON Model Assets
│   ├── hand_landmarker.task             // MediaPipe 21-point 3D hand tracking model (Bundle)
│   ├── pose_landmarker.task             // MediaPipe 33-point 3D upper-body pose model
│   ├── signs_mlp.pt                     // PyTorch weights for ASL 74D classifier
│   ├── asl_model.json                   // Mobile-exported JSON weights for ASL MLP
│   ├── isl_mlp.pt                       // PyTorch weights for ISL 164D classifier
│   ├── isl_model.json                   // Mobile-exported JSON weights for ISL MLP
│   ├── isl_spatial_mlp.pt               // PyTorch weights for ISL 220D spatial anchor model
│   ├── isl_spatial_model.json           // Mobile-exported JSON weights for 220D model
│   ├── words_classifier.npz             // NumPy weights for 300-word lexicon classifier
│   ├── yamnet.tflite                    // TFLite model for ambient environmental audio alerting
│   ├── en_US-lessac-medium.onnx         // Piper neural TTS voice model (Lessac)
│   ├── en_US-lessac-medium.onnx.json    // Piper voice configuration & phoneme dictionary
│   └── models.json                      // Manifest file with asset URLs, versions & SHA256 hashes
│
├── scripts/                             // Developer & Automation Scripts
│   ├── fetch_models.py                  // Auto-downloads missing model assets from CDN
│   ├── export_mobile_models.py          // Exports PyTorch .pt weights to mobile JSON format
│   ├── train_isl.py                     // Trains/fine-tunes ISL MLP with newly collected samples
│   └── collect_isl.py                   // Desktop CLI sample collector for training
│
├── src/signora/                         // Python Desktop Core Source Code
│   ├── __init__.py                      // Package initialization & version string
│   ├── cli.py                           // Main click/argparse CLI dispatcher
│   ├── intro.py                         // Interactive terminal welcome wizard
│   ├── platform_compat.py               // Cross-platform OS helpers (Linux/macOS/Windows)
│   │
│   ├── core/                            // Vision & Tracking Primitives
│   │   ├── camera.py                    // Threaded camera capture (V4L2/DirectShow)
│   │   ├── fake_camera.py               // Synthetic landmark generator for testing
│   │   ├── hands.py                     // MediaPipe HandLandmarker wrapper
│   │   ├── features.py                  // 74D ASL geometric feature extraction
│   │   ├── debounce.py                  // Majority voting, hysteresis & dwell engine
│   │   ├── hud.py                       // OpenCV visual skeleton & telemetry overlay
│   │   └── spatial.py                   // 3D vector math & anchor distance utilities
│   │
│   ├── asl/                             // ASL Fingerspelling Engine
│   │   ├── config.py                    // 26-letter alphabet, thresholds & paths
│   │   ├── model.py                     // PyTorch & NumPy ASL MLP implementations
│   │   └── live.py                      // Full live transcription overlay loop
│   │
│   ├── isl/                             // ISL Dual-Hand & Spatial Engine
│   │   ├── config.py                    // ISL vocabulary, letters & body anchors
│   │   ├── features.py                  // 164D dual-hand & 220D body feature extractor
│   │   ├── model.py                     // ISL PyTorch & NumPy classification models
│   │   ├── spatial.py                   // Upper body spatial reticle detector
│   │   ├── collect.py                   // Sample dataset recording logic
│   │   ├── train.py                     // Dataset loader & PyTorch training loop
│   │   ├── live.py                      // Real-time live ISL translator
│   │   └── import_dataset.py            // Ingests external ISL datasets
│   │
│   ├── words/                           // Continuous Word Recognition Engine
│   │   ├── config.py                    // 300 words lexicon & categories
│   │   ├── classifier.py                // Multi-class continuous word model
│   │   ├── dataset.py                   // Dynamic time warping (DTW) feature buffers
│   │   ├── normalize.py                 // Sequence normalization utilities
│   │   └── live.py                      // Live continuous phrase recognizer
│   │
│   ├── tts/                             // Neural Text-to-Speech Subsystem
│   │   └── piper.py                     // Piper ONNX local voice synthesis runner
│   │
│   ├── game/                            // Desktop Chrome Dino Runner (Web Kiosk)
│   │   ├── server.py                    // Threaded HTTP server hosting /game/
│   │   ├── pool.py                      // Letter pool generator with seed support
│   │   ├── scoring.py                   // Score, streak & game state container
│   │   ├── detector.py                  // Background camera gesture detection thread
│   │   └── static/                      // Web Frontend Assets
│   │       ├── index.html               // Canvas game stage & HUD layout
│   │       ├── style.css                // Retro pixel & arcade styling
│   │       └── game.js                  // 60 FPS HTML5 Canvas game loop
│   │
│   ├── learn/                           // Interactive Learning Kiosk
│   │   ├── server.py                    // HTTP server hosting /learn/
│   │   └── static/                      // Educational flashcards & reference cards
│   │
│   ├── alert/                           // Ambient Environmental Sound Alerting
│   │   ├── engine.py                    // Audio stream capture & feature buffering
│   │   ├── classifier.py                // YAMNet inference engine
│   │   ├── calibrate.py                 // Ambient noise floor calibration
│   │   ├── dispatcher.py                // Notification dispatcher (Desktop/Sound)
│   │   ├── popup.py                     // Visual warning banner
│   │   ├── hud.py                       // Sound monitor overlay
│   │   ├── live.py                      // Real-time sound monitoring CLI loop
│   │   └── daemon.py                    // Systemd background service daemon
│   │
│   ├── catchup/                         // Speech-to-Text Conversation Transcriber
│   │   ├── stt.py                       // Local speech recognition engine
│   │   ├── history.py                   // Rolling transcript buffer
│   │   └── viewer.py                    // Terminal / GUI catchup viewer
│   │
│   ├── emergency/                       // Emergency Medical Placard System
│   │   ├── profile.py                   // Medical profile schema & JSON loader
│   │   ├── presets.py                   // Emergency situation presets (ICE, Allergy)
│   │   └── viewer.py                    // High-contrast emergency card viewer
│   │
│   ├── public_help/                     // Public Interaction Phrases
│   │   ├── categories.py                // Transit, Retail, Dining, Medical categories
│   │   └── viewer.py                    // Fast card display for public communication
│   │
│   ├── doctor/                          // Diagnostic & Self-Test Suite
│   │   └── checks.py                    // Verifies camera, CPU/GPU, TTS, audio, models
│   │
│   ├── lipsync/                         // Visual Mouth Shape Analysis
│   │   ├── detector.py                  // Lip contour & aspect ratio tracker
│   │   ├── audio.py                     // Audio energy correlation
│   │   └── live.py                      // Real-time lip reading assistance
│   │
│   ├── body/                            // Full Body Pose Tracking
│   │   └── live.py                      // MediaPipe Pose live visualization
│   │
│   └── tui/                             // Textual Terminal User Interface
│       ├── app.py                       // Terminal dictionary application
│       └── dictionary.py                // Sign language dictionary lookups
│
├── tests/                               // Comprehensive Pytest Test Suite
│   ├── test_asl.py                      // Verifies ASL classification accuracy
│   ├── test_isl.py                      // Verifies ISL 164D/220D descriptors
│   ├── test_core_features.py            // Verifies 74D mathematical invariance
│   ├── test_core_camera.py              // Tests camera capture & fake fallback
│   ├── test_core_debounce.py            // Tests debounce hysteresis & dwell
│   ├── test_game.py                     // Tests desktop game pool & scoring
│   ├── test_tts.py                      // Tests Piper TTS initialization
│   ├── test_doctor.py                   // Tests system doctor diagnostic checks
│   ├── test_models_manifest.py          // Verifies asset checksums & manifest
│   ├── test_words.py                    // Tests 300-word lexicon classifier
│   └── ... (25+ test suites)
│
└── android/                             // Native Android Mobile Codebase
    ├── build.gradle.kts                 // Top-level build script
    ├── settings.gradle.kts              // Module definitions & plugin repositories
    ├── gradle.properties                // JVM memory & AndroidX settings
    ├── Signora.apk                      // Compiled 80 MB production debug APK
    │
    └── app/
        ├── build.gradle.kts             // Dependencies (Compose, CameraX, MediaPipe)
        ├── proguard-rules.pro           // Proguard rules preserving model classes
        └── src/main/
            ├── AndroidManifest.xml      // Permissions (Camera, Audio, Torch)
            ├── assets/                  // Bundled local models
            │   ├── hand_landmarker.task
            │   ├── pose_landmarker.task
            │   ├── asl_model.json
            │   └── isl_model.json
            │
            └── java/ai/signora/
                ├── MainActivity.kt      // Pipeline coordinator & activity entry
                ├── camera/
                │   └── CameraXManager.kt // Zero-allocation CameraX controller
                ├── vision/
                │   ├── HandLandmarkerHelper.kt // MediaPipe hand live stream
                │   └── PoseLandmarkerHelper.kt // MediaPipe pose live stream
                ├── ml/
                │   ├── FeatureExtractor.kt    // 74D/164D/220D extraction
                │   └── SignoraMLP.kt          // Pure Kotlin neural inference
                ├── filter/
                │   └── OneEuroFilter.kt       // Landmark stabilization
                ├── engine/
                │   ├── DebounceEngine.kt      // Temporal majority voting
                │   └── HandPresenceDetector.kt// Presence gating
                ├── profiler/
                │   └── TrackingProfiler.kt    // Millisecond stage latency profiler
                ├── recovery/
                │   └── AutoAssetManager.kt    // Asset validation & self-healing
                ├── tts/
                │   └── SpeechManager.kt       // Android TTS integration
                ├── training/
                │   ├── IslVocabulary.kt       // 300 words lexicon definition
                │   └── TrainingDatasetManager.kt // On-device dataset collector
                └── ui/
                    ├── Theme.kt               // M3 Color schemes & typography
                    ├── SignoraScreen.kt       // Root navigation scaffold
                    ├── components/
                    │   ├── SpatialOverlay.kt  // Hand/body skeletal canvas overlay
                    │   ├── AppNavigation.kt   // Navigation bar & rail
                    │   ├── AppTopBar.kt       // Action bar with camera toggles
                    │   ├── ActiveSignDisplay.kt // Detected letter/word HUD
                    │   ├── TranscriptField.kt // Transcript display with actions
                    │   ├── ModeSelector.kt    // ASL / ISL mode selector chips
                    │   ├── ActionButtonRow.kt // Space, Backspace, Clear buttons
                    │   └── FloatingCameraToolbar.kt // Torch, Pause, Flip overlay
                    └── screens/
                        ├── TranslateScreen.kt // Fullscreen live translation
                        ├── LearnScreen.kt     // Fingerspelling flashcard studio
                        ├── TrainStudioScreen.kt // 220D ISL custom training studio
                        ├── SettingsScreen.kt  // Configuration & threshold sliders
                        └── DinoGameScreen.kt  // Complete continuous physics runner
```

---

## 8. RUNBOOK, COMPILATION & DEPLOYMENT GUIDE

### 8.1 Python Desktop Setup & Execution
```bash
# 1. Clone repository & enter workspace
cd /home/vaibhav/Projects/signora

# 2. Activate virtual environment
source .venv/bin/activate

# 3. Verify hardware and dependencies
signora doctor

# 4. Download required models if missing
python scripts/fetch_models.py

# 5. Launch Live ASL Transcription Overlay
signora live

# 6. Launch Desktop Chrome Dino Game
signora game --source 0

# 7. Run full automated test suite
pytest -v
```

---

### 8.2 Android Compilation & Device Deployment
```bash
# 1. Navigate to android directory
cd /home/vaibhav/Projects/signora/android

# 2. Verify connected Android device via ADB
adb devices

# 3. Compile Kotlin source code
./gradlew compileDebugKotlin

# 4. Assemble production debug APK
./gradlew assembleDebug

# 5. Copy to root release artifact
cp app/build/outputs/apk/debug/app-debug.apk Signora.apk

# 6. Install to connected device
adb -s <DEVICE_ID> install -r Signora.apk

# 7. Launch application on device
adb -s <DEVICE_ID> shell monkey -p ai.signora.debug -c android.intent.category.LAUNCHER 1

# 8. Monitor real-time logs
adb -s <DEVICE_ID> logcat | grep -i "ai.signora"
```

---

## 9. CONCLUSION & OPENCODE HANDOFF DIRECTIVE

This specification contains every mathematical formula, architectural contract, data structure, and implementation detail of the Signora ecosystem. When continuing development in **OpenCode**:
1. Keep the **continuous runner philosophy**: never freeze the gameplay loop for user input.
2. Maintain mathematical equivalence between Python and Kotlin feature extraction pipelines.
3. Preserve the One-Euro filter gap reset threshold ($180\text{ ms}$) to prevent landmark flying artifacts.
4. Refer to **Section 6.7** for the immediate priority roadmap (Audio Synthesis with `SoundPool`, flying obstacles, and desktop web port).


---

## 10. TECHNICAL DEEP-DIVE APPENDIX

### 10.1 Exhaustive Mathematical Derivations & Pseudocode

#### Complete 74D ASL Feature Vector Map
The following table provides the exact index, mathematical definition, semantic meaning, and range for every single dimension of the 74D ASL descriptor:

| Index Range | Name | Mathematical Formula | Physical Significance | Expected Range |
| :--- | :--- | :--- | :--- | :--- |
| `0..2` | Normalized Wrist $\mathbf{p}_0$ | $(x_0 - x_0)/s, (y_0 - y_0)/s, (z_0 - z_0)/s = (0, 0, 0)$ | Origin anchor point | Always $(0, 0, 0)$ |
| `3..5` | Normalized Thumb CMC $\mathbf{p}_1$ | $(\pm x_1 - x_0)/s, (y_1 - y_0)/s, (z_1 - z_0)/s$ | Base of thumb | $[-1.5, 1.5]$ |
| `6..8` | Normalized Thumb MCP $\mathbf{p}_2$ | $(\pm x_2 - x_0)/s, (y_2 - y_0)/s, (z_2 - z_0)/s$ | Thumb lower knuckle | $[-2.0, 2.0]$ |
| `9..11` | Normalized Thumb IP $\mathbf{p}_3$ | $(\pm x_3 - x_0)/s, (y_3 - y_0)/s, (z_3 - z_0)/s$ | Thumb middle joint | $[-2.5, 2.5]$ |
| `12..14` | Normalized Thumb Tip $\mathbf{p}_4$ | $(\pm x_4 - x_0)/s, (y_4 - y_0)/s, (z_4 - z_0)/s$ | Thumb extremity | $[-3.0, 3.0]$ |
| `15..17` | Normalized Index MCP $\mathbf{p}_5$ | $(\pm x_5 - x_0)/s, (y_5 - y_0)/s, (z_5 - z_0)/s$ | Index knuckle | $[-1.5, 1.5]$ |
| `18..20` | Normalized Index PIP $\mathbf{p}_6$ | $(\pm x_6 - x_0)/s, (y_6 - y_0)/s, (z_6 - z_0)/s$ | Index first joint | $[-2.2, 2.2]$ |
| `21..23` | Normalized Index DIP $\mathbf{p}_7$ | $(\pm x_7 - x_0)/s, (y_7 - y_0)/s, (z_7 - z_0)/s$ | Index second joint | $[-2.8, 2.8]$ |
| `24..26` | Normalized Index Tip $\mathbf{p}_8$ | $(\pm x_8 - x_0)/s, (y_8 - y_0)/s, (z_8 - z_0)/s$ | Index extremity | $[-3.5, 3.5]$ |
| `27..29` | Normalized Middle MCP $\mathbf{p}_9$ | $(\pm x_9 - x_0)/s, (y_9 - y_0)/s, (z_9 - z_0)/s$ | Middle knuckle (Palm scale unit) | Magnitude $\equiv 1.0$ |
| `30..32` | Normalized Middle PIP $\mathbf{p}_{10}$ | $(\pm x_{10} - x_0)/s, (y_{10} - y_0)/s, (z_{10} - z_0)/s$ | Middle first joint | $[-2.2, 2.2]$ |
| `33..35` | Normalized Middle DIP $\mathbf{p}_{11}$ | $(\pm x_{11} - x_0)/s, (y_{11} - y_0)/s, (z_{11} - z_0)/s$ | Middle second joint | $[-2.8, 2.8]$ |
| `36..38` | Normalized Middle Tip $\mathbf{p}_{12}$ | $(\pm x_{12} - x_0)/s, (y_{12} - y_0)/s, (z_{12} - z_0)/s$ | Middle extremity | $[-3.8, 3.8]$ |
| `39..41` | Normalized Ring MCP $\mathbf{p}_{13}$ | $(\pm x_{13} - x_0)/s, (y_{13} - y_0)/s, (z_{13} - z_0)/s$ | Ring knuckle | $[-1.5, 1.5]$ |
| `42..44` | Normalized Ring PIP $\mathbf{p}_{14}$ | $(\pm x_{14} - x_0)/s, (y_{14} - y_0)/s, (z_{14} - z_0)/s$ | Ring first joint | $[-2.2, 2.2]$ |
| `45..47` | Normalized Ring DIP $\mathbf{p}_{15}$ | $(\pm x_{15} - x_0)/s, (y_{15} - y_0)/s, (z_{15} - z_0)/s$ | Ring second joint | $[-2.8, 2.8]$ |
| `48..50` | Normalized Ring Tip $\mathbf{p}_{16}$ | $(\pm x_{16} - x_0)/s, (y_{16} - y_0)/s, (z_{16} - z_0)/s$ | Ring extremity | $[-3.5, 3.5]$ |
| `51..53` | Normalized Pinky MCP $\mathbf{p}_{17}$ | $(\pm x_{17} - x_0)/s, (y_{17} - y_0)/s, (z_{17} - z_0)/s$ | Pinky knuckle | $[-1.5, 1.5]$ |
| `54..56` | Normalized Pinky PIP $\mathbf{p}_{18}$ | $(\pm x_{18} - x_0)/s, (y_{18} - y_0)/s, (z_{18} - z_0)/s$ | Pinky first joint | $[-2.0, 2.0]$ |
| `57..59` | Normalized Pinky DIP $\mathbf{p}_{19}$ | $(\pm x_{19} - x_0)/s, (y_{19} - y_0)/s, (z_{19} - z_0)/s$ | Pinky second joint | $[-2.5, 2.5]$ |
| `60..62` | Normalized Pinky Tip $\mathbf{p}_{20}$ | $(\pm x_{20} - x_0)/s, (y_{20} - y_0)/s, (z_{20} - z_0)/s$ | Pinky extremity | $[-3.0, 3.0]$ |
| `63` | Thumb Extension Ratio | $\|\mathbf{p}_4 - \mathbf{p}_2\|_2 / s$ | Thumb open vs folded | $[0.2, 1.8]$ |
| `64` | Index Extension Ratio | $\|\mathbf{p}_8 - \mathbf{p}_5\|_2 / s$ | Index straight vs curled | $[0.2, 2.2]$ |
| `65` | Middle Extension Ratio | $\|\mathbf{p}_{12} - \mathbf{p}_9\|_2 / s$ | Middle straight vs curled | $[0.2, 2.4]$ |
| `66` | Ring Extension Ratio | $\|\mathbf{p}_{16} - \mathbf{p}_{13}\|_2 / s$ | Ring straight vs curled | $[0.2, 2.2]$ |
| `67` | Pinky Extension Ratio | $\|\mathbf{p}_{20} - \mathbf{p}_{17}\|_2 / s$ | Pinky straight vs curled | $[0.2, 1.9]$ |
| `68` | Pinch Ratio | $\|\mathbf{p}_4 - \mathbf{p}_8\|_2 / s$ | Proximity of thumb & index | $[0.0, 2.5]$ |
| `69` | Spread Ratio | $(\|\mathbf{p}_8-\mathbf{p}_{12}\|+\|\mathbf{p}_{12}-\mathbf{p}_{16}\|+\|\mathbf{p}_{16}-\mathbf{p}_{20}\|)/(3s)$ | Finger splay angle | $[0.1, 1.5]$ |
| `70` | Mean Fingertip Depth | $\frac{1}{5} \sum_{j \in \{4,8,12,16,20\}} z_j$ | Plane tilt towards camera | $[-1.5, 1.5]$ |
| `71` | Palm Normal $N_x$ | $(\mathbf{u} \times \mathbf{v})_x / \|\mathbf{u} \times \mathbf{v}\|$ | Palm facing left/right | $[-1.0, 1.0]$ |
| `72` | Palm Normal $N_y$ | $(\mathbf{u} \times \mathbf{v})_y / \|\mathbf{u} \times \mathbf{v}\|$ | Palm facing up/down | $[-1.0, 1.0]$ |
| `73` | Palm Normal $N_z$ | $(\mathbf{u} \times \mathbf{v})_z / \|\mathbf{u} \times \mathbf{v}\|$ | Palm facing camera vs away | $[-1.0, 1.0]$ |

---

### 10.2 Complete Neural Network Architecture & Weight JSON Schema

The pure Kotlin inference engine [`SignoraMLP.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ml/SignoraMLP.kt) loads neural weights directly from asset JSON files without requiring external runtimes. The exact schema specification is defined below:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "SignoraMLPModelWeights",
  "type": "object",
  "required": ["input_dim", "hidden_dims", "output_dim", "layers", "classes"],
  "properties": {
    "input_dim": {
      "type": "integer",
      "description": "74 for ASL, 164 for ISL dual-hand, 220 for ISL spatial"
    },
    "hidden_dims": {
      "type": "array",
      "items": { "type": "integer" },
      "description": "Dimensions of intermediate dense layers, e.g. [128, 64]"
    },
    "output_dim": {
      "type": "integer",
      "description": "Total number of classification classes, e.g. 26 for alphabet, 326 for ISL"
    },
    "classes": {
      "type": "array",
      "items": { "type": "string" },
      "description": "String labels matching output Softmax indices: ['A', 'B', ...]"
    },
    "layers": {
      "type": "array",
      "description": "Sequential dense layers containing flattened row-major weight matrices and biases",
      "items": {
        "type": "object",
        "required": ["in_features", "out_features", "weights", "biases"],
        "properties": {
          "in_features": { "type": "integer" },
          "out_features": { "type": "integer" },
          "weights": {
            "type": "array",
            "items": { "type": "number" },
            "description": "Flattened matrix of size in_features * out_features (row-major)"
          },
          "biases": {
            "type": "array",
            "items": { "type": "number" },
            "description": "Vector of size out_features"
          }
        }
      }
    }
  }
}
```

#### Neural Forward Pass Implementation (`SignoraMLP.kt`):
```kotlin
package ai.signora.ml

import ai.signora.models.RecognitionResult
import org.json.JSONObject
import kotlin.math.exp

class SignoraMLP(
    val inDim: Int,
    val hiddenDims: IntArray,
    val outDim: Int,
    val weights: Array<FloatArray>,
    val biases: Array<FloatArray>,
    val labels: List<String>
) {
    fun predict(features: FloatArray): RecognitionResult? {
        if (features.size != inDim) return null

        var current = features

        // Intermediate Hidden Layers (Dense + ReLU)
        for (l in 0 until weights.size - 1) {
            val w = weights[l]
            val b = biases[l]
            val outSize = b.size
            val inSize = current.size
            val next = FloatArray(outSize)

            var wIdx = 0
            for (i in 0 until outSize) {
                var sum = b[i]
                for (j in 0 until inSize) {
                    sum += current[j] * w[wIdx++]
                }
                next[i] = if (sum > 0f) sum else 0f // ReLU activation
            }
            current = next
        }

        // Final Output Layer (Dense + Softmax)
        val finalL = weights.size - 1
        val finalW = weights[finalL]
        val finalB = biases[finalL]
        val logits = FloatArray(outDim)

        var wIdx = 0
        var maxLogit = -Float.MAX_VALUE
        for (i in 0 until outDim) {
            var sum = finalB[i]
            for (j in 0 until current.size) {
                sum += current[j] * finalW[wIdx++]
            }
            logits[i] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        // Numerical Stable Softmax
        var sumExp = 0f
        val probs = FloatArray(outDim)
        for (i in 0 until outDim) {
            val ep = exp(logits[i] - maxLogit)
            probs[i] = ep
            sumExp += ep
        }
        val invSum = if (sumExp > 1e-6f) 1.0f / sumExp else 1.0f
        for (i in 0 until outDim) {
            probs[i] *= invSum
        }

        // Top-1 and Top-2 Search
        var top1Idx = 0
        var top1Val = -1f
        var top2Val = -1f
        for (i in 0 until outDim) {
            val v = probs[i]
            if (v > top1Val) {
                top2Val = top1Val
                top1Val = v
                top1Idx = i
            } else if (v > top2Val) {
                top2Val = v
            }
        }

        val topLabel = if (top1Idx < labels.size) labels[top1Idx] else "UNKNOWN"
        val margin = top1Val - top2Val

        return RecognitionResult(
            label = topLabel,
            confidence = top1Val,
            margin = margin
        )
    }

    companion object {
        fun fromJson(jsonString: String): SignoraMLP {
            val obj = JSONObject(jsonString)
            val inDim = obj.getInt("input_dim")
            val outDim = obj.getInt("output_dim")

            val hDimsArr = obj.getJSONArray("hidden_dims")
            val hiddenDims = IntArray(hDimsArr.length()) { hDimsArr.getInt(it) }

            val labelsArr = obj.getJSONArray("classes")
            val labels = List(labelsArr.length()) { labelsArr.getString(it) }

            val layersArr = obj.getJSONArray("layers")
            val numLayers = layersArr.length()
            val weights = Array(numLayers) { l ->
                val lObj = layersArr.getJSONObject(l)
                val wArr = lObj.getJSONArray("weights")
                FloatArray(wArr.length()) { wArr.getDouble(it).toFloat() }
            }
            val biases = Array(numLayers) { l ->
                val lObj = layersArr.getJSONObject(l)
                val bArr = lObj.getJSONArray("biases")
                FloatArray(bArr.length()) { bArr.getDouble(it).toFloat() }
            }

            return SignoraMLP(inDim, hiddenDims, outDim, weights, biases, labels)
        }
    }
}
```

---

### 10.3 Complete ISL 300-Word Lexicon Catalog

The complete catalog defined in [`android/app/src/main/java/ai/signora/training/IslVocabulary.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/training/IslVocabulary.kt) organizes 300 static words across 15 semantic domains:

```
Category 1: GREETINGS & BASICS (20 Words)
- HELLO (Forehead salute wave, 1 hand, Anchor: FOREHEAD)
- NAMASTE (Palms joined at chest, 2 hands, Anchor: CHEST)
- GOOD_MORNING (Sun rising from flat horizontal palm, 2 hands, Anchor: CHEST)
- GOOD_NIGHT (Arm setting downward over wrist, 2 hands, Anchor: CHEST)
- THANK_YOU (Hand touching chin and extending forward, 1 hand, Anchor: CHIN)
- PLEASE (Open palm rubbing chest in circular motion, 1 hand, Anchor: CHEST)
- WELCOME (Open arms gathering inward to chest, 2 hands, Anchor: CHEST)
- SORRY (Fist rubbing circular motion on chest, 1 hand, Anchor: CHEST)
- YES (Closed fist nodding up and down, 1 hand, Anchor: OPEN_SPACE)
- NO (Index and middle snapping onto thumb, 1 hand, Anchor: OPEN_SPACE)
- FINE (Thumb resting against chest, 4 fingers splayed, 1 hand, Anchor: CHEST)
- BYE (Hand waving sideways, 1 hand, Anchor: OPEN_SPACE)
- HOW_ARE_YOU (Curved palms turning outward, 2 hands, Anchor: CHEST)
- NICE_TO_MEET (Two index fingers meeting tip-to-tip, 2 hands, Anchor: CHEST)
- SEE_YOU (Index pointing to eye then forward, 1 hand, Anchor: FOREHEAD)
- GOOD_AFTERNOON (Hand at 12 o'clock touching horizon, 2 hands, Anchor: CHEST)
- GOOD_EVENING (Cupped hands lowering together, 2 hands, Anchor: CHEST)
- EXCUSE_ME (Fingertips brushing flat palm, 2 hands, Anchor: OPEN_SPACE)
- CONGRATULATIONS (Clapping fists or waving raised palms, 2 hands, Anchor: CHEST)
- ALL_THE_BEST (Thumbs up thrusting forward, 1 hand, Anchor: OPEN_SPACE)

Category 2: FAMILY & RELATIONSHIPS (20 Words)
- FATHER (Thumb touching right side of forehead, 1 hand, Anchor: FOREHEAD)
- MOTHER (Thumb touching chin or cheek, 1 hand, Anchor: CHIN)
- BROTHER (Index finger rubbing nose/jaw then pointing forward, 2 hands, Anchor: CHIN)
- SISTER (Index touching cheek then matching parallel, 2 hands, Anchor: CHIN)
- BABY (Rocking crossed forearms, 2 hands, Anchor: CHEST)
- SON (Forehead salute transforming into baby cradle, 2 hands, Anchor: FOREHEAD)
- DAUGHTER (Chin touch transforming into baby cradle, 2 hands, Anchor: CHIN)
- GRANDFATHER (Father sign stepping forward in 2 hops, 1 hand, Anchor: FOREHEAD)
- GRANDMOTHER (Mother sign stepping forward in 2 hops, 1 hand, Anchor: CHIN)
- UNCLE (U-hand twisting near right temple, 1 hand, Anchor: TEMPLE)
- AUNT (A-hand twisting near jaw/chin, 1 hand, Anchor: CHIN)
- FRIEND (Interlocking hooked index fingers, 2 hands, Anchor: CHEST)
- FAMILY (F-hands describing circular meeting boundary, 2 hands, Anchor: CHEST)
- HUSBAND (C-hand from forehead meeting flat palm, 2 hands, Anchor: FOREHEAD)
- WIFE (C-hand from chin meeting flat palm, 2 hands, Anchor: CHIN)
- MAN (Thumb and fingers stroking imaginary beard, 1 hand, Anchor: CHIN)
- WOMAN (Index finger tracing jawline, 1 hand, Anchor: CHIN)
- CHILD (Flat palm patting downward at waist height, 1 hand, Anchor: WAIST)
- BOY (Cap brim touch at forehead, 1 hand, Anchor: FOREHEAD)
- GIRL (Thumb tracing jawline down to chin, 1 hand, Anchor: CHIN)

Category 3: NUMBERS & TIME (20 Words)
- ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
- TIME (Index finger tapping wrist watch location, 2 hands, Anchor: WRIST)
- TODAY (Both Y-hands pulsing downward twice, 2 hands, Anchor: WAIST)
- TOMORROW (A-hand thumb rolling forward from cheek, 1 hand, Anchor: CHIN)
- YESTERDAY (A-hand thumb rolling backward to ear, 1 hand, Anchor: CHIN)
- NOW (Bent-hands pulsing downward at waist, 2 hands, Anchor: WAIST)
- LATER (L-hand index snapping downward forward, 1 hand, Anchor: OPEN_SPACE)
- MORNING (Flat arm rising under opposite elbow, 2 hands, Anchor: CHEST)
- NIGHT (Curved palm draped over opposite wrist, 2 hands, Anchor: WRIST)
- CLOCK (Circling index finger then tapping wrist, 2 hands, Anchor: WRIST)

Category 4: EMERGENCY & SAFETY (20 Words)
- HELP (Closed fist resting on flat palm lifting upward, 2 hands, Anchor: CHEST)
- POLICE (C-hand pinned over left chest badge, 1 hand, Anchor: CHEST)
- FIRE (Fingers wiggling upward like dancing flames, 2 hands, Anchor: CHEST)
- AMBULANCE (Flashing red siren light twist above head, 1 hand, Anchor: FOREHEAD)
- DANGER (Fingers drumming rapidly on back of hand, 2 hands, Anchor: CHEST)
- DOCTOR (Three fingers tapping pulse on inner wrist, 2 hands, Anchor: WRIST)
- HOSPITAL (H-cross traced on upper shoulder/arm, 1 hand, Anchor: SHOULDER)
- MEDICINE (Middle finger grinding inside open palm, 2 hands, Anchor: CHEST)
- ACCIDENT (Two fists crashing knuckles together, 2 hands, Anchor: CHEST)
- EMERGENCY (E-hand shaking rapidly with alarm, 1 hand, Anchor: CHEST)
- PAIN (Index fingers twisting towards each other, 2 hands, Anchor: CHEST)
- SICK (Middle finger touching forehead and stomach, 1 hand, Anchor: FOREHEAD)
- BLOOD (Hand wiping downward from mouth shaking fingers, 1 hand, Anchor: MOUTH_LIPS)
- OXYGEN (Cupped hand over mouth and nose inhaling, 1 hand, Anchor: MOUTH_LIPS)
- STOP (Open hand chopping vertically into flat palm, 2 hands, Anchor: CHEST)
- RUN (Hooked index fingers pulling forward rapidly, 2 hands, Anchor: CHEST)
- SAFE (Crossed wrists breaking outward into open hands, 2 hands, Anchor: CHEST)
- CALL (Y-hand held to ear like telephone receiver, 1 hand, Anchor: TEMPLE)
- INJURY (Index finger pointing to affected anatomical site, 1 hand, Anchor: OPEN_SPACE)
- ESCAPE (Index finger slipping between opposite middle/index, 2 hands, Anchor: CHEST)

Categories 5 to 15: HEALTHCARE, WORKPLACE, EDUCATION, FOOD, TRANSPORT, EMOTIONS, WEATHER, ACTIONS, PLACES, TECHNOLOGY, LIFE
(Full remaining 220 items follow the exact same schema with explicit expected hand counts, target anatomical anchors, and posture tips, fully listed in `IslVocabulary.kt`).
```

---

### 10.4 Chrome Dino Sound Synthesis Architecture (`GameSoundManager.kt`)

For **OpenCode**, here is the production-ready implementation of `GameSoundManager.kt` using Android's low-latency `AudioTrack` synthesizer to generate crisp, authentic 8-bit retro arcade audio directly without requiring external asset files:

```kotlin
package ai.signora.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Procedural 8-bit retro synthesizer for Sign Dino Runner.
 * Synthesizes square and sine waves directly in memory for zero disk I/O latency.
 */
class GameSoundManager {

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    private val audioTrack: AudioTrack by lazy {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply { play() }
    }

    /**
     * Plays a snappy 8-bit jump blip (ascending pitch from 320 Hz to 680 Hz).
     */
    fun playJump() = scope.launch {
        val durationMs = 110
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples.toFloat()
            val freq = 320.0 + progress * 360.0 // Pitch slide up
            val phase = (2.0 * Math.PI * freq * i) / sampleRate
            // Square wave with envelope decay
            val square = if (sin(phase) > 0) 1.0 else -1.0
            val env = 1.0 - progress
            buffer[i] = (square * env * 14000).toInt().toShort()
        }
        audioTrack.write(buffer, 0, buffer.size)
    }

    /**
     * Plays a high-reward harmonic chime on correct sign bonus (+200 pts).
     */
    fun playSignBonus() = scope.launch {
        val durationMs = 180
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples.toFloat()
            // Arpeggio chord: 523 Hz (C5) transitioning to 659 Hz (E5) and 784 Hz (G5)
            val freq = when {
                progress < 0.33f -> 523.25
                progress < 0.66f -> 659.25
                else -> 783.99
            }
            val phase = (2.0 * Math.PI * freq * i) / sampleRate
            val sine = sin(phase)
            val env = 1.0 - (progress * 0.7)
            buffer[i] = (sine * env * 18000).toInt().toShort()
        }
        audioTrack.write(buffer, 0, buffer.size)
    }

    /**
     * Plays a cosmic Geometry Dash warp portal whoosh.
     */
    fun playWarp() = scope.launch {
        val durationMs = 350
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples.toFloat()
            // Frequency modulation riser
            val freq = 200.0 + Math.pow(progress.toDouble(), 2.0) * 800.0
            val phase = (2.0 * Math.PI * freq * i) / sampleRate
            val noise = (Math.random() - 0.5) * 0.25
            val wave = sin(phase) * 0.75 + noise
            val env = sin(progress * Math.PI)
            buffer[i] = (wave * env * 16000).toInt().toShort()
        }
        audioTrack.write(buffer, 0, buffer.size)
    }

    /**
     * Plays a deep crunchy impact sound on obstacle collision.
     */
    fun playCrash() = scope.launch {
        val durationMs = 280
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples.toFloat()
            val whiteNoise = (Math.random() - 0.5) * 2.0
            val subBass = sin((2.0 * Math.PI * 65.0 * i) / sampleRate) * 0.5
            val env = Math.pow((1.0 - progress), 1.8)
            buffer[i] = ((whiteNoise * 0.7 + subBass) * env * 22000).toInt().toShort()
        }
        audioTrack.write(buffer, 0, buffer.size)
    }

    fun release() {
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

---

### 10.5 Reverse Port: Standalone Continuous HTML5 Web Canvas Engine (`src/signora/game/static/game.js`)

Below is the complete reference implementation for OpenCode to upgrade the desktop browser kiosk version of Chrome Dino into the exact same continuous physics engine that never halts:

```javascript
/* Sign Dino 2.0 - Continuous Arcade Physics Runner
 * Zero-pause gameplay: Dino never halts for prompts.
 * Real velocity-and-gravity kinematics, streaming obstacles, and Geometry Dash particle FX.
 */
(() => {
  "use strict";

  const stage = document.getElementById("stage");
  const ctx = stage.getContext("2d");
  const camLetter = document.getElementById("cam-letter");

  let canvasW = 800;
  let canvasH = 400;

  // Kinematic Physics State
  let dinoY = 0;          // Offset from ground (px)
  let dinoVY = 0;         // Vertical velocity (px/s)
  let isGrounded = true;
  let speed = 280;        // Ground speed (px/s)
  let score = 0;
  let highScore = 0;
  let streak = 0;
  let gameState = "IDLE"; // "IDLE", "RUNNING", "GAME_OVER"

  const GRAVITY = 1800;    // px/s^2
  const JUMP_VELOCITY = -740; // px/s launch

  // Obstacle Queue
  let obstacles = [];
  let particles = [];
  let shockwaves = [];
  let ghostTrails = [];

  const BIOMES = [
    { title: "CYBER DESERT", sky: ["#fffbeb", "#fde68a"], ground: "#f59e0b", line: "#78350f", obs: "#15803d" },
    { title: "NEON SYNTH", sky: ["#0f0c29", "#302b63"], ground: "#1a102f", line: "#06b6d4", obs: "#f43f5e" },
    { title: "MYSTICAL", sky: ["#064e3b", "#022c22"], ground: "#065f46", line: "#34d399", obs: "#a855f7" },
    { title: "COSMIC", sky: ["#030712", "#1e1b4b"], ground: "#111827", line: "#818cf8", obs: "#38bdf8" }
  ];
  let biomeIdx = 0;

  function doJump(isSignBonus = false) {
    if (!isGrounded || gameState !== "RUNNING") return;
    isGrounded = false;
    dinoVY = JUMP_VELOCITY;

    // Shockwave ring
    shockwaves.push({ x: canvasW * 0.15, radius: 10, maxRadius: 70, alpha: 1.0 });

    // Spark burst
    for (let i = 0; i < 16; i++) {
      particles.push({
        x: canvasW * 0.15,
        y: canvasH * 0.75,
        vx: (Math.random() - 0.5) * 300,
        vy: -(Math.random() * 350 + 100),
        alpha: 1.0,
        color: isSignBonus ? "#fbbf24" : BIOMES[biomeIdx].obs
      });
    }

    if (isSignBonus) {
      streak++;
      score += 200 + (streak * 20);
    } else {
      score += 100;
    }
    if (score > highScore) highScore = score;
  }

  function spawnObstacle() {
    const letters = ["A", "B", "C", "D", "E", "F", "L", "O", "V", "W", "Y"];
    const lastX = obstacles.length > 0 ? obstacles[obstacles.length - 1].x : canvasW;
    const nextX = Math.max(lastX + 320 + Math.random() * 200, canvasW + 100);
    obstacles.push({
      x: nextX,
      letter: letters[Math.floor(Math.random() * letters.length)],
      cleared: false
    });
  }

  function resetGame() {
    score = 0;
    streak = 0;
    speed = 280;
    dinoY = 0;
    dinoVY = 0;
    isGrounded = true;
    obstacles = [];
    particles = [];
    shockwaves = [];
    ghostTrails = [];
    spawnObstacle();
    spawnObstacle();
    gameState = "RUNNING";
  }

  // Input Handlers (Tap/Click/Spacebar anywhere)
  window.addEventListener("keydown", (e) => {
    if (e.code === "Space" || e.code === "ArrowUp") {
      if (gameState === "RUNNING") doJump(false);
      else resetGame();
    }
  });
  stage.addEventListener("pointerdown", () => {
    if (gameState === "RUNNING") doJump(false);
    else resetGame();
  });

  // Main 60 FPS Game Loop
  let lastTime = performance.now();
  function gameLoop(now) {
    const dt = Math.min((now - lastTime) / 1000, 0.05);
    lastTime = now;

    if (gameState === "RUNNING") {
      // 1. Physics update
      if (!isGrounded) {
        dinoVY += GRAVITY * dt;
        dinoY -= dinoVY * dt;
        if (dinoY <= 0) {
          dinoY = 0;
          dinoVY = 0;
          isGrounded = true;
        }
      }

      // 2. Obstacles update
      for (let i = obstacles.length - 1; i >= 0; i--) {
        const obs = obstacles[i];
        obs.x -= speed * dt;

        // Collision Check (Dino at x = 0.15 * canvasW)
        const dinoX = canvasW * 0.15;
        if (Math.abs(obs.x - dinoX) < 24 && !obs.cleared) {
          if (dinoY > 35) {
            // Cleared!
            obs.cleared = true;
          } else {
            // Crash!
            gameState = "GAME_OVER";
          }
        }

        if (obs.x < -60) {
          obstacles.splice(i, 1);
        }
      }

      if (obstacles.length < 2) {
        spawnObstacle();
      }

      // Check upcoming obstacle for gesture match
      const upcoming = obstacles.find(o => o.x > canvasW * 0.12);
      if (upcoming && Math.abs(upcoming.x - canvasW * 0.15) < 260) {
        // Read detected letter from camera HUD element
        const currentSaw = (camLetter.innerText || "").trim().toUpperCase();
        if (currentSaw === upcoming.letter && isGrounded && !upcoming.cleared) {
          upcoming.cleared = true;
          doJump(true);
        }
      }

      speed = Math.min(280 + score * 0.08, 600);
      biomeIdx = Math.floor(score / 250) % BIOMES.length;
    }

    render(ctx);
    requestAnimationFrame(gameLoop);
  }

  function render(c) {
    const biome = BIOMES[biomeIdx];
    const groundY = canvasH * 0.75;

    // Sky
    c.fillStyle = biome.sky[0];
    c.fillRect(0, 0, canvasW, groundY);

    // Ground
    c.fillStyle = biome.ground;
    c.fillRect(0, groundY, canvasW, canvasH - groundY);
    c.strokeStyle = biome.line;
    c.lineWidth = 3;
    c.beginPath();
    c.moveTo(0, groundY);
    c.lineTo(canvasW, groundY);
    c.stroke();

    // Shockwaves
    for (let sw of shockwaves) {
      c.strokeStyle = `rgba(6, 182, 212, ${sw.alpha})`;
      c.lineWidth = 3;
      c.beginPath();
      c.arc(sw.x, groundY, sw.radius, 0, Math.PI * 2);
      c.stroke();
      sw.radius += 180 * 0.016;
      sw.alpha -= 1.6 * 0.016;
    }
    shockwaves = shockwaves.filter(s => s.alpha > 0);

    // Obstacles & Hologram Badges
    for (let obs of obstacles) {
      c.fillStyle = obs.cleared ? "rgba(75, 85, 99, 0.4)" : biome.obs;
      c.fillRect(obs.x, groundY - 45, 24, 45);

      // In-world Floating Sign Badge
      c.fillStyle = "#0f172a";
      c.beginPath();
      c.arc(obs.x + 12, groundY - 65, 14, 0, Math.PI * 2);
      c.fill();
      c.strokeStyle = obs.cleared ? "#34d399" : biome.obs;
      c.lineWidth = 2;
      c.stroke();

      c.fillStyle = "#ffffff";
      c.font = "bold 14px monospace";
      c.textAlign = "center";
      c.fillText(obs.letter, obs.x + 12, groundY - 60);
    }

    // Dino
    const dinoX = canvasW * 0.15;
    const curY = groundY - 44 - dinoY;
    c.fillStyle = "#334155";
    c.fillRect(dinoX, curY, 36, 44);

    // HUD Text
    c.fillStyle = "#1e293b";
    c.font = "bold 16px monospace";
    c.textAlign = "left";
    c.fillText(`SCORE: ${score.toString().padStart(5, '0')}  HI: ${highScore.toString().padStart(5, '0')}`, 20, 30);
    c.fillText(`BIOME: ${biome.title}`, 20, 52);
    if (streak > 1) {
      c.fillStyle = "#f59e0b";
      c.fillText(`STREAK: ${streak}x COMBO`, 20, 74);
    }
  }

  requestAnimationFrame(gameLoop);
})();
```

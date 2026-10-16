# OpenCode Autonomous Mission: Chrome Dino Arcade Overhaul

> **Allocated Time**: Up to 2 Hours  
> **Resource Constraints**: None ("No specs problem" — high performance, 60/120 FPS, maximum visual juice, pure Kotlin audio synthesis, rich particles).  
> **Target Project**: `ai.signora` Android App (`/home/vaibhav/Projects/signora/android`)  
> **Primary File**: [`android/app/src/main/java/ai/signora/ui/screens/DinoGameScreen.kt`](file:///home/vaibhav/Projects/signora/android/app/src/main/java/ai/signora/ui/screens/DinoGameScreen.kt)  
> **Master Specification**: [`SIGNORA_MASTER_SPECIFICATION.md`](file:///home/vaibhav/Projects/signora/SIGNORA_MASTER_SPECIFICATION.md) (refer to Section 10 for game mechanics and AudioTrack code).  
> **Target Device**: Connected phone `RZCX40C1HSV` (verified via `adb devices`).

---

## 1. Executive Mission Summary

The current Chrome Dino Runner in Signora needs to be transformed into an exhilarating, arcade-quality, hyper-responsive endless runner that seamlessly combines high-framerate gameplay with Indian Sign Language (ISL) gesture recognition.

You have full authority to refactor, expand, and introduce new helper classes in `android/app/src/main/java/ai/signora/` (such as `ai.signora.game.audio.*`, `ai.signora.game.entities.*`, etc.).

---

## 2. Core Pillars of the Overhaul

### 1. Procedural 8-Bit / Synth Audio Engine (`GameAudioTrack`)
*Zero external MP3/WAV assets required — synthesize high-speed 16-bit PCM buffers directly in Kotlin via `android.media.AudioTrack`!*
- **Jump Blip**: Frequency sweep 320 Hz $\to$ 880 Hz with instant decay.
- **Duck / Whoosh**: Fast descending tonal filter 420 Hz $\to$ 160 Hz.
- **Score Chime / Milestone**: Staccato two-tone chord (E6 $\to$ B6) with resonant bell sustain.
- **ISL Sign Translation Bonus**: Bright sparkle arpeggio (C-E-G-C).
- **Fever / Overdrive Mode**: Upbeat procedural 130 BPM retro bassline / arpeggio loop while active.
- **Crash / Impact**: Shaped white-noise burst layered with a punchy 80 Hz kick.

### 2. Flying Obstacles & Ducking Mechanics
- **Ground Obstacles**:
  - Cyber-Cacti (single, dual, triple clustered with neon thorns).
  - Magma Crags & Spikes (in Mystical & Desert Biomes).
- **Aerial Obstacles (Pterodactyls / Space Drones)**:
  - Spawn at high and medium altitudes.
  - High flyer: flies overhead, safe if running or ducking.
  - Mid flyer: requires the player to **DUCK** or do a very low precise hop!
- **Ducking Controls**:
  - Touch: Swipe down anywhere or hold lower half of screen.
  - Gesture: Hand closed / low wrist / downward fist detected via MediaPipe landmarks.
  - Visual: Dino flattens into a sleek horizontal sprint posture.

### 3. Dynamic Biomes & Parallax Starfields / Landscapes
- Four rich, procedurally rendered biomes transitioning every 800-1000 points:
  1. **Neon Cyber Desert**: Amber sands, distant pyramids, geometric wireframe sun.
  2. **Synthwave Outrun**: Purple grid floor, neon palms, retro horizon sun.
  3. **Mystical Floating Realm**: Teal floating islands, glowing runes, ambient fairy motes.
  4. **Cosmic Zero-G Space**: Parallax nebula starfield, shooting stars, asteroid belts.
- Smooth interpolated gradient transitions between biomes so the shift feels like warping through hyperspace.

### 4. Fever / Flow Combo State
- Collecting sign bonuses or dodging 5 consecutive obstacles without taking damage charges the **Overdrive Meter**.
- Upon 100% charge, triggers **FEVER MODE** for 8 seconds:
  - Dino emits a multi-color rainbow chromatic trail.
  - Speed accelerates by 1.3x.
  - Invincibility: Dino smashes through obstacles, shattering them into physics-driven particle shards.
  - Double score multiplier ($2\times \to 4\times$).

### 5. ISL Gesture Sign Interactivity
- Signs appear as floating holographic glyphs above upcoming obstacles.
- If the player signs the prompt (or taps the quick-action trigger):
  - Emits a radiant shockwave that clears the path ahead!
  - Awards a +250 "Sign Master" score popup with floating text.
  - Restores flow meter.

### 6. Juice, Polish & Haptic Feedback
- Screen shake on near-misses and crash.
- Dust kickback particles on every ground contact.
- Smooth acceleration curve ($v(t) = v_0 + \alpha \sqrt{t}$).
- Floating damage/score numbers (`+100`, `COMBO x3`).
- Clean Game Over overlay with instant restart button and high score persistence (using `SharedPreferences`).

---

## 3. Verification & Deployment Pipeline

Once you finish implementing the enhancements:
1. Build the Android app:
   ```bash
   cd /home/vaibhav/Projects/signora/android
   ./gradlew assembleDebug
   ```
2. Verify zero compilation errors.
3. Install onto the connected device `RZCX40C1HSV`:
   ```bash
   adb -s RZCX40C1HSV install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Start the app activity:
   ```bash
   adb -s RZCX40C1HSV shell am start -n ai.signora.debug/ai.signora.MainActivity
   ```
5. Check logcat for any crashes or regressions:
   ```bash
   adb -s RZCX40C1HSV logcat -d -s Signora DinoGame AndroidRuntime | tail -n 50
   ```

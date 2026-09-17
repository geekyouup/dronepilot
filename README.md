# DronePilot 🚁

**DronePilot** is a high-performance 3D First-Person View (FPV) Drone Simulator for Android. Built with native Kotlin, Android Jetpack Compose, and Google Filament for realistic real-time 3D rendering and physics simulation.

---

## Features

- **Realistic Flight Physics**:
  - 6-DoF Rigid Body dynamics with quaternion orientation tracking.
  - Aerodynamic drag, gravity, ground collision reaction, and propeller thrust dynamics.
  - Sub-stepped physics integration (8 substeps at 240–500 Hz) for smooth, high-fidelity response.
- **Flight Modes**:
  - **Assisted (Angle/Self-Leveling Mode)**: Closed-loop PID control stabilizes pitch and roll to commanded stick angles, auto-leveling when sticks are centered.
  - **Acro Mode**: Direct angular rate PID controller ($720^\circ/\text{s}$ roll/pitch, $540^\circ/\text{s}$ yaw) with Air-Mode for zero-throttle acrobatic maneuvers.
- **FPV Camera & Adjustable Tilt**:
  - Authentic FPV camera perspective.
  - Dynamic camera up-tilt ($0^\circ$ to $45^\circ$, default $15^\circ$) allowing forward flight without staring at the ground.
  - Quick-cycle tilt button in the top HUD (`TILT: 0°/15°/25°/35°/45°`) and continuous slider in Control Settings.
  - Artificial Horizon pitch ladder dynamically compensated for optical tilt angle.
- **On-Screen HUD & Virtual Joysticks**:
  - Low-latency dual virtual joysticks (Throttle/Yaw and Pitch/Roll) with deadzones, dynamic centers, and spring return.
  - Real-time telemetry: altitude, speed, pitch/roll, throttle percentage, flight mode, and gate timer.
  - Interactive flight gate racing tracking with checkpoint audio/visual feedback.
- **Audio Feedback**:
  - Dynamic drone motor audio synthesis modulated by throttle and motor load.

---

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Android Jetpack Compose & Material 3
- **Graphics Engine**: Google Filament (PBR engine for mobile)
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34 (Android 14)
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## Project Structure

```
app/src/main/java/com/droid/dronepilot/
├── audio/
│   └── DroneSoundEngine.kt       # Dynamic motor tone audio synthesis
├── physics/
│   ├── FlightController.kt      # Dual-loop PID controller & flight modes
│   ├── GateTracker.kt           # Racing gate collision & lap timer
│   ├── QuadcopterPhysics.kt     # 6-DoF rigid body simulation & ground reaction
│   ├── Quaternion.kt            # 3D rotational math
│   └── Vector3.kt               # 3D vector arithmetic
├── renderer/
│   ├── DroneRenderer.kt         # Filament 3D scene, materials, lighting & camera
│   ├── MeshFactory.kt           # Procedural 3D drone & gate geometry generator
│   └── ShaderUtils.kt           # Custom shader utilities
└── ui/
    ├── FlightHud.kt             # Compose HUD overlays, dials, & horizon ladder
    ├── MainActivity.kt          # Main activity & Filament SurfaceView integration
    └── VirtualJoystick.kt       # Multi-touch dual joystick implementation
```

---

## Building & Running

### Prerequisites
- Android SDK with API 34 and Build Tools 34.0.0
- JDK 17+

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run Unit Tests
```bash
./gradlew test
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License
Apache 2.0 License

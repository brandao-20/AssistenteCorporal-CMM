# Exercise Form Assistant

An Android application that uses real-time pose detection and device orientation sensing to analyse squat movement and provide immediate exercise feedback.

Built with Kotlin, CameraX and Google ML Kit, the application tracks the user's body from a lateral view, estimates knee movement, monitors squat depth and counts valid repetitions while helping the user maintain an appropriate camera setup.

## Features

### Real-Time Pose Detection

The application processes camera frames continuously using CameraX and Google ML Kit Pose Detection.

It tracks body landmarks required for lateral squat analysis and determines which side of the body provides the most reliable measurements.

The analysis pipeline includes:

- real-time pose detection
- left/right body-side selection
- stable side tracking across frames
- landmark validation
- human-pose validation
- temporary detection-loss tolerance
- false-positive filtering

### Squat Movement Analysis

The exercise engine estimates the squat state from detected body landmarks.

It calculates:

- knee angle
- relative hip displacement
- movement depth
- visible body side
- current exercise stage

The movement is classified into stages such as:

```text
Ready
Descending
Bottom
Rising
```

A repetition is only counted after the user reaches the configured bottom position and returns above the upper threshold.

### Stable Repetition Counting

The repetition counter uses temporal validation rather than relying on a single frame.

The analysis includes:

- multiple stable frames before accepting a pose
- multiple frames before confirming the bottom position
- minimum time between repetitions
- smoothed knee-angle measurements
- smoothed depth estimation
- standing-position baseline tracking

These checks reduce unstable counts caused by noisy pose estimates or short-lived detections.

### Body-Side Stabilisation

The application automatically selects the most suitable visible side of the body for analysis.

To avoid rapidly switching between left and right landmarks, a side must remain consistently preferable across multiple frames before the active side changes.

### Human-Pose Validation

Exercise analysis is suspended when a sufficiently reliable human pose cannot be detected.

The application distinguishes between:

```text
Waiting for person
Stabilising pose
Ready
Descending
Bottom position
Rising
Repetition completed
```

This reduces false exercise feedback from incomplete poses or non-human objects.

### Device Orientation

The Android Rotation Vector sensor monitors the orientation of the phone during exercise analysis.

The application calculates the device roll angle and warns the user when the phone is excessively tilted.

A deviation of approximately ±12° is accepted before an orientation warning is shown.

### Front and Rear Camera Support

Users can switch between:

- rear camera
- front camera

The selected camera is persisted locally and restored on future sessions.

### Adjustable Exercise Thresholds

Squat depth requirements can be configured using three presets:

| Mode | Bottom threshold | Standing threshold |
| --- | ---: | ---: |
| Permissive | 120° | 155° |
| Normal | 110° | 160° |
| Strict | 100° | 165° |

The thresholds can also be adjusted manually in 5° increments.

### Persistent Preferences

The application stores user configuration locally, including:

- selected camera
- bottom threshold
- standing threshold

### Real-Time Feedback

During analysis, the interface displays:

- repetition count
- current movement stage
- detected body side
- knee angle
- squat depth
- device orientation
- exercise feedback
- pose-analysis status

### Repetition Feedback

When a valid repetition is completed, the application provides immediate feedback through:

- haptic vibration
- counter animation
- visual state highlighting

## Tech Stack

### Mobile

- Kotlin
- Android SDK
- Android Studio
- XML layouts
- ViewBinding

### Computer Vision

- Google ML Kit Pose Detection

### Camera

- CameraX
  - Preview
  - ImageAnalysis
  - Camera2 integration

### Device Sensors

- Android Rotation Vector Sensor
- SensorManager

### Testing

- JUnit
- AndroidX Test
- Espresso

## Architecture

```text
┌─────────────────────────────┐
│       CameraX Preview       │
│                             │
│ Preview + ImageAnalysis     │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│      ML Kit Pose Detector   │
│                             │
│    Body landmark detection  │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│        Pose Analyzer        │
│                             │
│ Frame processing control    │
│ Camera orientation handling │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│  Exercise Feedback Engine   │
│                             │
│ Pose validation             │
│ Side selection              │
│ Knee-angle estimation       │
│ Depth estimation            │
│ Movement state tracking     │
│ Repetition counting         │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│       Analysis UI           │
│                             │
│ Angle · Depth · Reps        │
│ Stage · Feedback · Side     │
└─────────────────────────────┘

               +

┌─────────────────────────────┐
│ Rotation Vector Sensor      │
│                             │
│ Device roll estimation      │
│ Orientation feedback        │
└─────────────────────────────┘
```

## Analysis Pipeline

Each camera frame follows this processing flow:

```text
Camera frame
    │
    ▼
ML Kit Pose Detection
    │
    ▼
Landmark Validation
    │
    ▼
Body-Side Selection
    │
    ▼
Pose Stabilisation
    │
    ▼
Knee Angle + Hip Displacement
    │
    ▼
Temporal Smoothing
    │
    ▼
Movement State Machine
    │
    ▼
Repetition Validation
    │
    ▼
Real-Time UI Feedback
```

Only one camera frame is processed at a time. Incoming frames are skipped while the current ML Kit inference is still running, preventing overlapping pose-analysis operations.

## Movement State Machine

The repetition logic follows a simple movement state machine:

```text
READY
  │
  ▼
DESCENDING
  │
  ▼
DOWN
  │
  ▼
RISING
  │
  ▼
REP COMPLETED
  │
  └──────────► READY
```

A valid repetition requires:

1. a stable human pose
2. sufficient downward hip displacement
3. knee angle reaching the configured bottom threshold
4. stable confirmation of the bottom position
5. knee angle returning above the standing threshold

This approach avoids counting movement from knee angle alone.

## Project Structure

```text
exercise-form-assistant/
├── app/
│   └── src/main/
│       ├── java/com/example/assistentecorporal/
│       │   ├── MainActivity.kt
│       │   ├── AnalysisActivity.kt
│       │   ├── PoseAnalyzer.kt
│       │   ├── ExerciseFeedbackEngine.kt
│       │   ├── DeviceOrientationHelper.kt
│       │   ├── GuidanceOverlayView.kt
│       │   └── AppPreferences.kt
│       └── res/
│           ├── drawable/
│           ├── layout/
│           ├── mipmap/
│           └── values/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Getting Started

### Requirements

- Android Studio
- JDK 17
- Android SDK 34
- Android device or compatible emulator
- camera access

The application supports Android API level 23 and above.

## Installation

Clone the repository:

```bash
git clone https://github.com/brandao-20/exercise-form-assistant.git
cd exercise-form-assistant
```

Open the project in Android Studio and allow Gradle to synchronise the dependencies.

Alternatively, build from the command line.

macOS / Linux:

```bash
./gradlew assembleDebug
```

Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Running the Application

1. Connect an Android device or start a compatible emulator.
2. Build and install the application through Android Studio.
3. Grant camera permission when requested.
4. Position the device so the full body is visible from the side.
5. Keep the phone approximately upright.
6. Select a sensitivity preset or configure the angle thresholds manually.
7. Start performing squats while monitoring the real-time feedback.

A physical Android device is recommended for the complete experience because the application uses both the camera and the device's Rotation Vector sensor.

## Camera Processing

CameraX provides two simultaneous use cases:

```text
Preview
ImageAnalysis
```

`ImageAnalysis` uses:

```text
STRATEGY_KEEP_ONLY_LATEST
```

to prioritise recent frames during real-time analysis.

The pose detector runs in ML Kit's streaming mode for continuous camera processing.

## Pose Analysis

The feedback engine uses key body landmarks including the:

- shoulder
- hip
- knee
- ankle

These landmarks are used to evaluate the lateral body pose and calculate the knee angle required for squat-state estimation.

The application also tracks hip displacement relative to an estimated standing baseline, preventing knee-angle changes alone from being interpreted as a complete squat.

## Device Orientation

The Rotation Vector sensor is converted into an orientation matrix using Android's `SensorManager`.

The resulting roll angle is used to indicate whether the phone is appropriately aligned for analysis.

If the device does not provide a Rotation Vector sensor, pose analysis can continue without orientation validation.

## Privacy

Pose analysis is performed directly on the device.

The project does not require a remote backend or cloud-based user account for exercise analysis.

Camera frames are processed for pose estimation and are not intentionally persisted by the application.

## Limitations

- The current exercise analysis is designed specifically for squats observed from a lateral view.
- Pose-estimation accuracy depends on lighting, camera position, distance and body visibility.
- Loose clothing, occlusion or incomplete body framing may reduce landmark quality.
- The movement thresholds are heuristic and configurable rather than medically validated biomechanical criteria.
- The application is an experimental exercise-assistance tool and does not replace professional coaching, medical advice or biomechanical assessment.

## Purpose

This project explores the combination of:

- mobile computer vision
- real-time image analysis
- human pose estimation
- device sensors
- movement-state modelling
- temporal filtering
- interactive exercise feedback

It was originally developed in an academic mobile and multisensory computing context and is presented here as a portfolio project focused on Android development and applied computer vision.

# ChicaneX 🏁

**AI Rally Copilot with deep route knowledge** — an Android app that analyzes GPS routes and delivers rally-style pace notes via text-to-speech audio.

## Features

### 🗺️ Route Feature Extraction

ChicaneX analyzes GPX route files and automatically detects:

| Feature | Description |
|---------|-------------|
| **Curves** | Direction (Left/Right), severity (1–6 rally scale), estimated radius |
| **Hairpins** | Tight 180° turns (≥140° angle) |
| **Chicanes** | Quick left-right or right-left combinations |
| **S-Bends** | Series of linked alternating curves |
| **Straights** | Straight sections with distance in meters |
| **Crests** | Hilltops where the road drops away |
| **Dips** | Compressions where the road dips and rises |
| **Climbs** | Sustained uphill sections with gradient % |
| **Descents** | Sustained downhill sections with gradient % |
| **Tightening curves** | Curves that get sharper through the turn |
| **Opening curves** | Curves that widen through the turn |
| **Over-crest curves** | Curves that coincide with a hilltop |

### 🎤 Rally Audio Output

- **Text-to-Speech** with rally co-driver characteristics
- Crisp, clear pronunciation at adjustable speech rate
- Priority-based queuing — critical notes (hairpins, caution) interrupt normal playback
- Speech rate and pitch automatically adjust per note type
- Numbers spoken as words for clarity ("three" not "3")
- "CAUTION!" emphasis for dangerous sections
- Natural rally cadence with appropriate pauses

### 📝 Pace Note Generation

Generates authentic rally pace notes using standard co-driver terminology:

```
Start
Left 3 long, 80, Right 5 into Hairpin Left over crest
Straight 200
Chicane Right Left, 120, Left 4 tightens
Crest big, steep Descent 300
Finish
```

### 📱 Android App

- Load GPX route files from device storage
- View route analysis: statistics, feature list, pace notes
- Simulated playback with adjustable speed
- Seek to any position in the route
- Dark rally-themed UI

## Architecture

```
com.chicanex/
├── model/           # Data classes (GpsPoint, RouteFeature, PaceNote, AnalyzedRoute)
├── route/           # Route analysis engine & pace note generator
├── audio/           # TTS audio engine & pace note player
├── gpx/             # GPX file parser
└── ui/              # Android Activity
```

### Key Components

- **`RouteAnalyzer`** — Processes GPS waypoints to detect curves, elevation changes, and compound features using bearing analysis, curvature estimation, and elevation profiling.
- **`PaceNoteGenerator`** — Converts detected route features into rally-style pace note text with proper terminology, distance calls, and "into" linking.
- **`RallyAudioEngine`** — Android TTS wrapper with rally co-driver speech characteristics, priority queuing, and per-note pitch/rate adjustment.
- **`RallyPaceNotePlayer`** — Synchronizes pace note playback with position (GPS or simulated), handling call-ahead timing.
- **`GpxParser`** — Parses GPX 1.0/1.1 files extracting track points with elevation data.

## Severity Scale

The curve severity follows the standard rally scale:

| Grade | Description | Approx. Radius | Approx. Angle |
|-------|------------|-----------------|---------------|
| 1 | Very fast, gentle | > 120 m | < 20° |
| 2 | Fast | 70–120 m | 20–40° |
| 3 | Medium | 40–70 m | 40–70° |
| 4 | Slow-medium | 25–40 m | 70–100° |
| 5 | Slow | 12–25 m | 100–140° |
| 6 | Very slow/tight | < 12 m | > 140° |

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin 1.9+

### Build
```bash
./gradlew assembleDebug
```

### Test
```bash
./gradlew test
```

### Usage
1. Install the app on an Android device
2. Tap "Load GPX Route" and select a GPX file
3. Review the detected features and statistics
4. Tap "Play" to start simulated pace note playback
5. Use the seek bar to jump to any point in the route

## GPX Format

ChicaneX accepts standard GPX files with track points:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <name>Monte Carlo SS1</name>
    <trkseg>
      <trkpt lat="48.123" lon="11.456">
        <ele>520</ele>
      </trkpt>
      <!-- more track points -->
    </trkseg>
  </trk>
</gpx>
```

Elevation data (`<ele>`) is optional but enables detection of crests, dips, climbs, and descents.

## License

This project is open source.


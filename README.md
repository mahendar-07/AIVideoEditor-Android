# AI Video Editor for Android

A standalone native Android video editor, separate from iCut. The product direction is a CapCut-style manual editor with AI-assisted editing and AI video creation, built incrementally with an offline-first foundation and optional online services.

## Current implementation

- Kotlin + Jetpack Compose dark editor interface
- Android document picker for importing video files
- Media3 / ExoPlayer video preview and playback controls
- Project model with video, audio, text and overlay tracks
- Non-destructive clip metadata: source in/out points and timeline position
- Timeline operations: split, trim start, trim end, move and delete
- Automatic project JSON save/restore in app-private storage
- Multi-source video playlist preview with sequential playback across clips
- Per-clip source trim ranges applied to preview playback
- Timeline-wide seeking across the combined video sequence
- Move-left / move-right clip ordering with contiguous timeline repositioning
- Basic timeline rows and clip selection

## Architecture

- **UI:** Jetpack Compose
- **Project model and timeline engine:** Kotlin
- **Preview:** AndroidX Media3 / ExoPlayer
- **Persistence:** versioned JSON project files in app-private storage
- **Export:** planned Media3 Transformer / Android MediaCodec pipeline
- **AI:** planned offline speech recognition and optional online generation

## Current limitations

- The primary video track plays as a sequential playlist. The current editor does not yet support overlapping video layers, transitions, or gaps between clips.
- Audio, text, caption and overlay tracks are currently structural placeholders.
- Undo/redo, transitions, effects, filters, keyframes and waveform editing are not implemented yet.
- Export is not implemented yet. The Export control currently explains this limitation; it does not produce an MP4.
- Project data is saved locally on the device. Imported media remains in its original location and must remain accessible to restore the project.

## Next milestones

1. Add drag-and-drop clip rearrangement, timeline zoom and frame-accurate controls.
2. Add undo/redo and robust project recovery.
3. Implement audio tracks, mixing, text overlays and captions.
4. Implement MP4 rendering/export with progress and cancellation.
5. Add effects, transitions and AI-assisted editing.

## Build

Open the repository in Android Studio with JDK 17 and Android SDK 36, sync Gradle, and run the app. GitHub Actions builds a debug APK on pushes to main.

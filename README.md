# AI Video Editor for Android

A standalone native Android video editor, separate from iCut. Product direction: full manual editing plus AI-assisted editing and AI creation, using offline-first recognition with optional online AI services.

## Current foundation
- Kotlin + Jetpack Compose Android application
- CapCut-inspired dark mobile editor interface
- Import video from device storage
- Media3 preview playback and play/pause
- Timeline segment selection
- Split a selected segment at the playhead
- Set selected segment start/end at the playhead (trim boundaries)
- Delete a selected segment
- Playback position scrubbing

## Important editing-engine note
Split, trim-boundary and delete operations currently update the in-memory edit timeline and selected segment state. They do not yet create a rendered output file or stitch multiple segments into a final video. Project persistence, multi-source clip assembly, audio/text tracks and export are subsequent milestones.

## Roadmap
1. Stabilize timeline and clip editing; add multi-clip project model and undo/redo.
2. Add audio tracks, text overlays, captions and synchronization.
3. Add render/export pipeline and project save/restore.
4. Add offline speech recognition (Whisper evaluation), word-timed captions and AI-assisted editing.
5. Add optional online AI video generation and advanced effects.

## Build locally
Open this repository in Android Studio, install JDK 17 and Android SDK 36, sync Gradle, and run the app configuration.

## APK
Open GitHub Actions, select the latest successful Android APK workflow run, and download the AIVideoEditor-debug-apk artifact.

# AI Video Editor for Android

A standalone native Android video editor, separate from iCut. Product direction: full manual editing plus AI-assisted editing and AI creation, using offline-first recognition with optional online AI services.

## Architecture foundation
- **UI:** Kotlin + Jetpack Compose
- **Project model:** non-destructive project, tracks and clip metadata
- **Timeline engine:** pure operations for split, trim-start, trim-end, move and delete
- **Project storage:** versioned JSON project files in app-private storage
- **Preview:** AndroidX Media3 / ExoPlayer
- **Export:** planned Media3 Transformer / Android MediaCodec pipeline
- **AI:** planned offline speech recognition and optional online generation

## Current app capabilities
- Import a video from device storage
- Preview playback and play/pause
- Scrub playback position
- Basic in-memory timeline segment selection, split, trim boundaries and delete

## New project-engine foundation
The new `EditorProject.kt` defines:
- Projects with canvas dimensions and frame rate
- Video, audio, text and overlay track types
- Clips with source in/out points and timeline positions
- Validation for project/track/clip consistency
- Non-destructive timeline operations
- JSON save/load using app-private storage

Unit tests cover timeline split, trim, delete, invalid split and project validation.

## Important current limitation
The existing editor screen has not yet been migrated to use `EditorProject` and `ProjectStore`. Its visible timeline still uses a temporary in-memory segment state. The new project engine is the architectural foundation; integration into the screen, multi-source clip playback, persistent project UI and actual export are subsequent tasks. Timeline metadata operations do not yet render a new output video.

## Next milestones
1. Connect the editor screen to EditorProject and ProjectStore.
2. Build multi-track timeline UI and multi-source playback.
3. Add undo/redo and project save/restore controls.
4. Add audio tracks, text overlays and captions.
5. Implement final rendering/export.
6. Add offline speech recognition and AI-assisted editing.

## Build
Open the repository in Android Studio with JDK 17 and Android SDK 36, sync Gradle, and run the app.

GitHub Actions builds a debug APK on pushes to `main`.

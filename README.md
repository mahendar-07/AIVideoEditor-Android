# AI Video Editor for Android

A standalone native Android video editor project, separate from iCut.

## Phase 1
- Kotlin and Jetpack Compose application shell
- Dark editor home screen
- Android video picker
- Media3 ExoPlayer video preview
- GitHub Actions workflow builds a debug APK on every push to `main`

## Build locally
1. Open this repository in Android Studio.
2. Install JDK 17 and Android SDK 36.
3. Sync Gradle and run the `app` configuration.

## Download an APK
Open the repository's **Actions** tab, select the latest successful **Android APK** workflow run, and download the `AIVideoEditor-debug-apk` artifact.

To publish a permanent APK under **Releases**, create and push a version tag such as `v0.1.0`. The workflow will build the APK and attach it to the release.

## Current limitations
This is an early Phase 1 foundation, not a CapCut replacement. Multi-track timeline editing, trim/split export, captions, audio mixing, effects, and AI editing are planned follow-up work.

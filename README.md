# AI Video Editor for Android

A standalone native Android video editor project, separate from iCut.

## Current build: CapCut-inspired editor workspace
- Dark, mobile-first editing screen with top actions and export entry point
- Video import from device storage
- Media3 video preview and play/pause controls
- Timeline-style workspace with video, audio, and text track placeholders
- Bottom editing categories: Edit, Audio, Text, Captions, Overlay, Effects, Filters, Import
- Contextual tool strip for each category

## Build locally
1. Open this repository in Android Studio.
2. Install JDK 17 and Android SDK 36.
3. Sync Gradle and run the `app` configuration.

## Download an APK
Open the repository's **Actions** tab, select the latest successful **Android APK** workflow run, and download the `AIVideoEditor-debug-apk` artifact.

To publish a permanent APK under **Releases**, create and push a version tag such as `v0.1.0`. The workflow will build the APK and attach it to the release.

## Not implemented yet
The current version is the first UI and preview foundation. Timeline editing operations (real split/trim/move), audio mixing, text rendering, auto captions, effects, transitions, AI video generation, and final export are not functional yet. These will be built in stages after the editor shell is stable.

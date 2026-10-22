# Lizmo (Android) • v0.2.0

> **Dynamic-Island style music overlay**: Now-playing track info from any music app (Spotify, YouTube Music, Apple Music, etc.), synced lyrics via LRCLIB, interactive scrubbable seek bar, swipe gestures, and 8 dynamic themes.

---

### 📲 Direct Download

[![Download Lizmo APK](https://img.shields.io/badge/Download-Lizmo.apk-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)
[![Latest Release](https://img.shields.io/github/v/release/yudhajeetManna/android-widget-music?style=for-the-badge&color=7c5cff)](https://github.com/yudhajeetManna/android-widget-music/releases/latest)

* ⬇️ **[Download Latest Lizmo.apk (Direct Release Link)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)**
* 📦 **[Download Lizmo.apk (Direct Repository Link)](https://github.com/yudhajeetManna/android-widget-music/raw/main/Lizmo.apk)**
* 🏷️ **[Browse All Releases](https://github.com/yudhajeetManna/android-widget-music/releases)**

---

## ✨ What's New in v0.2.0

* 🎵 **Interactive Scrubbable Seek Bar:** Tap or drag along the progress bar to scrub/seek directly through the playing song.
* ⏱️ **Track Time Indicator:** Real-time elapsed time and total duration (`01:23 / 03:45`) displayed in the expanded island.
* 👆 **Swipe Gestures:**
  * **Swipe Right:** Skip to next track.
  * **Swipe Left:** Return to previous track.
  * **Double Tap:** Play / Pause.
  * **Tap Album Art or Long Press:** Instantly launch the active music player (Spotify, YouTube Music, etc.).
* 📳 **Haptic Feedback:** Tactile vibration responses when toggling playback, skipping tracks, or changing themes (can be toggled in settings).
* 📱 **Notch / Camera Position Selector:** Cycle between `6 dp` (Flush), `12 dp` (Punch Hole), `18 dp` (Camera Cutout), and `24 dp` (Deep Notch) to align perfectly with your screen's front camera cutout.
* 🎨 **New Themes (8 Total):** Added `Cyberpunk`, `Emerald`, and `Amber` alongside `Glass`, `Neon`, `Minimal`, `Sunset`, and `Mono`.
* 👁️ **Island Preview Demo:** Tap *"Preview Island"* in the app to test and customize the floating island without needing music playing.

---

## 🚀 First Run & Setup

1. **Install `Lizmo.apk`** on your Android device (Android 8.0+ / SDK 26+).
2. **Open Lizmo**:
   - **Grant Notification Access**: Tap to enable Lizmo so it can read active media sessions.  
     *(If sideloaded and greyed out on Android 13+: go to **Settings > Apps > Lizmo > ⋮ (3-dots menu) > Allow restricted settings**, then return and turn it on).*
   - **Grant Overlay Permission**: Tap to allow drawing the dynamic island floating over other apps.
3. **Play Music**: Start playing music in Spotify, YouTube Music, Apple Music, or any media player.
   - Tap the island to expand/collapse into mini-player.
   - Drag the progress bar to seek.
   - Swipe left or right to skip tracks.
   - Tap the theme button to cycle themes.

---

## 🛠️ Build the APK (GitHub Actions)

1. Any push to `main` triggers the `build-apk` workflow.
2. The workflow compiles the APK using Gradle 8.7 & Java 17 and creates an automatic GitHub Release.
3. Download the new version from [Releases](https://github.com/yudhajeetManna/android-widget-music/releases).

## Local Build

Requires JDK 17 + Android SDK (API 34):
```bash
./gradlew assembleDebug
```

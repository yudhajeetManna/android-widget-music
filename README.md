# Lizmo (Android)

> **Dynamic-Island style music overlay**: Now-playing track info from any music app (Spotify, YouTube Music, etc.), synced lyrics via LRCLIB, and 5 dynamic themes.

---

### 📲 Direct Download

[![Download Lizmo APK](https://img.shields.io/badge/Download-Lizmo.apk-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)
[![Latest Release](https://img.shields.io/github/v/release/yudhajeetManna/android-widget-music?style=for-the-badge&color=7c5cff)](https://github.com/yudhajeetManna/android-widget-music/releases/latest)

* ⬇️ **[Download Latest Lizmo.apk (Direct Release Link)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)**
* 📦 **[Download Lizmo.apk (Direct Repository Link)](https://github.com/yudhajeetManna/android-widget-music/raw/main/Lizmo.apk)**
* 🏷️ **[Browse All Releases](https://github.com/yudhajeetManna/android-widget-music/releases)**

---

## First Run & Setup

1. **Install the APK** on your Android device (Android 8.0+ / SDK 26+).
2. **Open Lizmo**:
   - **Grant Notification Access**: Tap to enable Lizmo so it can read active media sessions.  
     *(If sideloaded and greyed out on Android 13+: go to **Settings > Apps > Lizmo > ⋮ (3-dots menu) > Allow restricted settings**, then return and turn it on).*
   - **Grant Overlay Permission**: Tap to allow drawing the dynamic island floating over other apps.
3. **Play Music**: Start playing music in Spotify, YouTube Music, Apple Music, or any media player.
   - Tap the island to expand/collapse.
   - Tap the theme button to cycle themes (*Glass*, *Neon*, *Minimal*, *Sunset*, *Mono*).

---

## Build the APK (GitHub Actions)

1. Any push to `main` triggers the `build-apk` workflow.
2. The workflow builds the APK and automatically creates a new GitHub Release with `Lizmo.apk`.
3. Download the new version from [Releases](https://github.com/yudhajeetManna/android-widget-music/releases).

## Local Build

Requires JDK 17 + Android SDK (API 34):
```bash
gradle assembleDebug
```

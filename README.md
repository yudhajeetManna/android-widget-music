# 🏝️ Lizmo (Android)

<div align="center">

**Dynamic Island Floating Music Overlay with Synchronized Karaoke Lyrics, Real-Time Translation, and Hands-Free Voice Control for Android.**

[![Release](https://img.shields.io/badge/Release-v0.4.0%20(Signed)-blueviolet?style=for-the-badge&logo=android)](https://github.com/yudhajeetManna/android-widget-music/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Build Status](https://img.shields.io/badge/Build-Automated%20CI%2FCD-success?style=for-the-badge&logo=githubactions)](https://github.com/yudhajeetManna/android-widget-music/actions)
[![Security](https://img.shields.io/badge/Security-Signed%20Release%20Key-blue?style=for-the-badge)](#-why-is-it-safe)
[![Privacy](https://img.shields.io/badge/Privacy-Zero%20Telemetry-blue?style=for-the-badge)](#-privacy--permissions)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

[**📥 Download Latest Signed APK**](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk) • [**✨ Features**](#-features) • [**🚀 Installation**](#-quick-installation-guide) • [**🗣️ Voice Commands**](#-voice-commands-cheat-sheet) • [**🛠️ Troubleshooting**](#%EF%B8%8F-troubleshooting)

</div>

---

## ⚡ Overview

**Lizmo** brings an interactive, iOS-style **Dynamic Island** music overlay to any modern Android phone. It floats seamlessly over your apps, tracks whatever music you're playing, displays animated equalizers and real-time synchronized karaoke lyrics, translates lyrics on the fly, and lets you command your music hands-free with a customizable wake word.

```
       ┌────────────────────────────────────────────────────────┐
       │   [ ♫ ]  Kesariya - Arijit Singh         |||||||       │  <-- Compact Pill
       └────────────────────────────────────────────────────────┘
                                 ▼ (Tap to Expand)
       ┌────────────────────────────────────────────────────────┐
       │  [Album Art]  Kesariya                                 │
       │               Arijit Singh • Brahmāstra                │
       │                                                        │
       │  "Kesariya tera ishq hai piya..."   [EN / ORIG]       │  <-- Synced Karaoke
       │  "Your love is saffron-colored, my beloved..."         │  <-- Live English
       │                                                        │
       │  01:24 ━━━━━━━━━━━━━━━━●━━━━━━━━━━━━━━━━━ 04:28        │  <-- Seek Bar
       │             ⏮        ⏯        ⏭         🔍            │  <-- Media Controls
       └────────────────────────────────────────────────────────┘
```

---

## 🛡️ Why is it Safe?

| Security Check | Status | Details |
| :--- | :--- | :--- |
| **Signed with Release Key** | ✅ Verified | Signed with official Lizmo keystore — not a debug build |
| **Play Protect Verified** | ✅ Clean | Zero malware / spyware / adware |
| **Zero Telemetry** | ✅ 100% | No trackers, no ad networks, no data harvesting |
| **Open Source** | ✅ Public | Full source code inspectable on GitHub |

---

## 📥 Download & Quick Start

> [!TIP]
> **No compilation or Android Studio required!** GitHub Actions automatically builds clean, signed release APK binaries upon every push.

### 1. Download
Get the latest signed build directly from GitHub Releases:
* **[👉 Download Lizmo.apk (Latest v0.4.0 Signed Release)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)**

### 2. Install & Grant Permissions
1. Open the downloaded `Lizmo.apk` and tap **Install** (allow *Install from Unknown Sources* if prompted by your browser).
2. Launch **Lizmo** from your app drawer.
3. Tap **Grant notification access** and enable **Lizmo** (allows Lizmo to detect song title, artist, album art, and playback state).
4. Tap **Grant overlay permission** and allow **Display over other apps** (allows the floating island pill to display).
5. Start playing music in Spotify, YouTube Music, Apple Music, or any media app. The island will pop up automatically!

> [!IMPORTANT]
> ### ⚠️ Android 13 / 14 / 15 "Restricted Setting" Workaround
> If Android greys out the notification listener toggle with the message *"For your security, this setting is currently unavailable"*:
> 1. Go to your phone's **Settings** > **Apps** > **See all apps** > **Lizmo**.
> 2. Tap the **three-dots menu (⋮)** in the top-right corner.
> 3. Tap **"Allow restricted settings"** and verify with your PIN/fingerprint.
> 4. Return to the Lizmo app and toggle notification access on!

---

## ✨ Features

### 🏝️ Adaptive Floating Island
- **Two Reactive States**: Floats as an unobtrusive compact pill; tap once to spring-expand into full playback controls, album art, and seek progress.
- **Fluid Spring Animations**: Smooth physics-based spring transitions with custom Bezier easing and overshoot dynamics.
- **Universal Audio Tracker**: Hooks into Android's native `MediaSessionManager` and `NotificationListenerService`. Works instantly with:
  - 🟢 Spotify
  - 🔴 YouTube Music
  - 🍎 Apple Music
  - 🟠 SoundCloud
  - 🎶 JioSaavn / Gaana / Wynk
  - 📻 Local audio players (Poweramp, VLC, Musicolet, Retro Music)

### 🎤 Synchronized Karaoke & Translated Lyrics
- **Multi-Source Lyric Pipeline**: Automatic timed lyric discovery using **LRCLIB** with **NetEase** fallback and intelligent duration alignment.
- **Live English Translation**: One-tap toggle (`EN / ORIG`) to view real-time translated lyrics powered by Google Translate web engine.
- **Smart Language Detection**: Automatically distinguishes Romanized / non-English tracks (Hindi, Punjabi, Korean, Japanese, Spanish, etc.) with a manual language override selector.
- **Local Caching**: Synced and translated lyrics are cached on-device to minimize network bandwidth.

### 🎨 5 Designer Themes
Switch aesthetics on the fly by tapping the theme chip on the expanded island or in the settings:
| Theme | Appearance | Description |
| :--- | :--- | :--- |
| **Glass** | Translucent Frosted Dark | Minimalist blur with subtle border accent |
| **Neon** | Deep Cyberpunk Purple | Glowing neon stroke with luminous text |
| **Minimal**| Pure Pitch Black | 100% OLED true black for maximum power savings |
| **Sunset** | Radiant Gradient | Warm pink-to-purple vibrant gradient |
| **Mono** | Clean Daylight Light | High-contrast clean monochrome for bright ambient light |

### 🗣️ Voice Control & Custom Wake Word
- **Hands-Free Control**: Change tracks, pause, adjust volume, or launch any song using natural voice speech.
- **Custom Wake Word Engine**: Default wake word is `lizmo` (with phonetic tolerance for `lismo`, `limo`, `lizma`). Customize to your favorite name.
- **Instant Trigger**: Long-press the floating pill or the search icon to trigger voice recognition immediately without saying the wake word.
- **Built-in Microphone Diagnostic**: Dedicated test tool in settings that visualizes what the phone heard, wake word matching status, and speech error diagnostics.

---

## 🗣️ Voice Commands Cheat Sheet

Say your wake word followed by any of these phrases, or long-press the island to speak directly:

| Command Type | What to Say | Action |
| :--- | :--- | :--- |
| **Play / Pause** | *"lizmo pause"*, *"hold on"*, *"lizmo resume"*, *"play the music"* | Toggles playback state |
| **Track Navigation** | *"lizmo next"*, *"skip this track"*, *"lizmo back"*, *"previous song"* | Changes track |
| **Volume Adjustment** | *"lizmo volume up"*, *"turn it up"*, *"louder"*, *"volume down"*, *"quieter"* | Steps volume up or down |
| **Precise Volume** | *"lizmo set volume to 50%"*, *"volume 80"*, *"volume max"* | Sets absolute media volume |
| **Mute / Unmute** | *"lizmo mute"*, *"lizmo unmute"* | Mutes or restores audio |
| **Play Specific Song** | *"lizmo play Kesariya"*, *"play Starboy by The Weeknd"*, *"play Diljit"* | Searches iTunes catalog and launches track in your music player |

---

## 🛠️ Troubleshooting

<details>
<summary><b>1. The Island does not appear when music starts</b></summary>

- Verify that **Notification Access** is ON in Lizmo.
- Verify that **Display over other apps** (Overlay) permission is granted.
- Ensure your music app displays an active playback notification with playback controls in the Android status bar.
- If your phone aggressively kills background apps (e.g., Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Samsung OneUI):
  - In Lizmo, tap **Battery settings** and disable battery optimization for Lizmo.
  - Enable **Autostart** in system settings if available.
</details>

<details>
<summary><b>2. Voice commands / Wake word not responding</b></summary>

- Tap **Test microphone** on the Lizmo main screen.
- Ensure Google Speech Services / Google App is installed and enabled as the default speech recognition engine.
- If the test shows that your phone mishears your accent or pronunciation (e.g., hearing "lesmo" instead of "lizmo"), add your spelling to the **Wake word** input separated by commas (e.g., `lizmo, lesmo, lismo`).
- Always-on listening requires the microphone foreground service. If you prefer to save battery, keep always-listening OFF and simply **long-press the island** to speak.
</details>

<details>
<summary><b>3. Lyrics say "No lyrics found" or timing is slightly off</b></summary>

- Some obscure or freshly released tracks may not yet exist on LRCLIB or NetEase databases.
- If the track title contains noisy metadata (like `(Official Music Video Remastered 2024)`), Lizmo automatically strips common suffixes to improve matching.
- If an English translation appears inaccurate, long-press the **EN/ORIG** chip to manually specify the original language (e.g., Hindi, Punjabi, Korean, Japanese).
</details>

---

## 🔒 Privacy & Permissions

Lizmo is designed with **privacy first**:

| Permission | Purpose |
| :--- | :--- |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Reads currently playing track title, artist, album art, and controls playback. |
| `SYSTEM_ALERT_WINDOW` | Renders the floating Dynamic Island overlay over your screen. |
| `RECORD_AUDIO` | Listens for your voice commands and wake word. Audio is processed via Android's local speech recognizer and is never stored or sent to any private server. |
| `FOREGROUND_SERVICE_MICROPHONE` | Keeps the optional always-listening wake word service alive when enabled. |
| `INTERNET` | Fetches timed karaoke lyrics (LRCLIB), song searches (iTunes API), and translations (Google Translate). |

- ❌ **No telemetry or analytics**
- ❌ **No user tracking or data collection**
- ❌ **No advertisements**
- ❌ **No accounts or sign-in required**

---

## 💻 Building from Source

If you want to build the signed APK yourself:

### Prerequisites
- JDK 17
- Android SDK 34 (Android 14)
- Gradle 8.7+ (or use the included `./gradlew`)

### Steps
```bash
# 1. Clone the repository
git clone https://github.com/yudhajeetManna/android-widget-music.git
cd android-widget-music

# 2. Build the signed Release APK
./gradlew assembleRelease

# 3. The output signed APK will be located at:
# app/build/outputs/apk/release/app-release.apk
```

Or open the project directly in **Android Studio** (Koala / Ladybug or newer) and click **Run** ▶️.

---

## 📜 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.

<div align="center">
Made with ❤️ by <a href="https://github.com/yudhajeetManna">yudhajeetManna</a> for Android music lovers.
</div>

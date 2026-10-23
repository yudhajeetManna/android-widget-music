# 🏝️ Lizmo (Android)

<div align="center">

**Dynamic Island Floating Music Overlay with Synchronized Karaoke Lyrics, Real-Time Translation, and Hands-Free Voice Control for Android.**

[![Release](https://img.shields.io/badge/Release-v0.6.0%20(Signed)-blueviolet?style=for-the-badge&logo=android)](https://github.com/yudhajeetManna/android-widget-music/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Build Status](https://img.shields.io/badge/Build-Automated%20CI%2FCD-success?style=for-the-badge&logo=githubactions)](https://github.com/yudhajeetManna/android-widget-music/actions)
[![Security](https://img.shields.io/badge/Security-Signed%20Release%20Key-blue?style=for-the-badge)](#-why-is-it-safe)
[![Privacy](https://img.shields.io/badge/Privacy-Zero%20Telemetry-blue?style=for-the-badge)](#-privacy--permissions)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

[**📥 Download Latest Signed APK (v0.6.0)**](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk) • [**✨ Features**](#-features) • [**🚀 Installation**](#-quick-installation-guide) • [**🗣️ Voice Commands**](#-voice-commands-cheat-sheet) • [**🛠️ Troubleshooting**](#%EF%B8%8F-troubleshooting)

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

## 🌟 What's New in v0.6.0

| Feature | Description |
| :--- | :--- |
| 🎮 **Full Voice Media Controls** | 20+ commands: pause, play, next, back, restart, stop, shuffle (on/off), repeat (one/all/off), seek (+/- 30s, jump to min), like/dislike, volume queries & steps. |
| 🖥️ **Dynamic Command HUD** | Real-time animated pill banner on the island confirming every voice action with emojis and spring overshoot physics. |
| 🎯 **Active Music App Auto-Play** | Automatically detects your currently active streaming app (Spotify, YouTube Music, Apple Music, JioSaavn, SoundCloud, VLC, etc.) and routes song searches directly. |
| 🎙️ **Microphone Hearing Levels** | Low, Medium, and High sensitivity modes with adaptive noise floor tuning for whispering, normal speech, and loud/noisy rooms. |
| ⚡ **Instant Wake Word Response** | <50ms phoneme wake-word detection; island pops up on screen within seconds of saying `"lizmo"`. |

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
* **[👉 Download Lizmo.apk (Latest v0.6.0 Signed Release)](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)**

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

### 🗣️ Smart Voice Control & Hands-Free Media
- **Custom Wake Word Engine**: Default wake word is `lizmo` (with phonetic tolerance for `lismo`, `limo`, `lizma`). Set any custom name you prefer.
- **Instant Response**: Under 50ms trigger detection; overlay springs up immediately when spoken to.
- **Microphone Hearing Levels**: Choose between **Low**, **Medium**, and **High** sensitivity depending on ambient noise.
- **Active Music Player Routing**: Saying *"play [Song]"* checks which music app you were using and opens the track right there.
- **Command HUD Overlay**: Instant visual confirmation banner for every command right above the island.

---

## 🗣️ Voice Commands Cheat Sheet

Say your wake word followed by any of these phrases (e.g. `"lizmo pause"`), or long-press the island to speak directly:

### ⏸ Playback
| Command | Alternative Phrases | Action |
| :--- | :--- | :--- |
| **Pause** | *"pause"*, *"hold on"*, *"pause music"* | ⏸ Pauses playback |
| **Resume / Play** | *"play"*, *"resume"*, *"continue"* | ▶ Resumes music |
| **Next Track** | *"next"*, *"skip"*, *"next track"*, *"skip song"* | ⏭ Skips to next song |
| **Previous Track** | *"previous"*, *"go back"*, *"last song"* | ⏮ Jumps to previous track |
| **Restart Track** | *"restart"*, *"play from the beginning"*, *"replay"* | ⏪ Seeks back to 0:00 |
| **Stop Music** | *"stop music"*, *"stop playing"* | ⏹ Stops media playback |

### ⏩ Seeking & Progress
| Command | Example Phrases | Action |
| :--- | :--- | :--- |
| **Fast Forward** | *"skip forward 30 seconds"*, *"fast forward 1 minute"* | ⏩ Jumps ahead by duration |
| **Rewind** | *"rewind 15 seconds"*, *"go back 20 seconds"* | ⏪ Jumps back by duration |
| **Jump to Timestamp** | *"jump to 2 minutes"*, *"jump to 1 minute 30 seconds"* | 📍 Seeks directly to time |

### 🔊 Volume
| Command | Example Phrases | Action |
| :--- | :--- | :--- |
| **Volume Up** | *"volume up"*, *"turn it up"*, *"louder"* | 🔊 Increases volume by 1 step |
| **Volume Down** | *"volume down"*, *"quieter"*, *"softer"* | 🔉 Decreases volume by 1 step |
| **Set Exact Percentage** | *"set volume to 50%"*, *"volume 80"*, *"volume 30 percent"* | 🎚 Sets volume to exact level (0-100) |
| **Max Volume** | *"volume max"*, *"max volume"*, *"maximum volume"* | 📢 Sets volume to 100% |
| **Mute / Unmute** | *"mute"*, *"unmute"* | 🔇 Mutes / restores volume |
| **Query Volume** | *"what's the volume"*, *"volume level"* | ℹ️ Reads out current volume % |

### 🔀 Shuffle & 🔁 Repeat
| Command | Example Phrases | Action |
| :--- | :--- | :--- |
| **Shuffle On** | *"shuffle"*, *"shuffle on"*, *"turn on shuffle"* | 🔀 Enables shuffle |
| **Shuffle Off** | *"shuffle off"*, *"turn off shuffle"* | ➡ Disables shuffle |
| **Repeat Song** | *"repeat this song"*, *"repeat one"*, *"loop this"* | 🔂 Repeats single track |
| **Repeat Playlist** | *"repeat all"*, *"loop playlist"* | 🔁 Repeats all tracks |
| **Repeat Off** | *"repeat off"*, *"no repeat"* | ➡ Normal linear playback |

### ❤️ Like & ℹ️ Info
| Command | Example Phrases | Action |
| :--- | :--- | :--- |
| **Like / Favorite** | *"like this"*, *"thumbs up"*, *"save this song"* | ❤ Rates / likes track |
| **Dislike** | *"dislike"*, *"thumbs down"* | 👎 Dislikes track |
| **What's Playing** | *"what's playing"*, *"what song is this"* | 🎵 Reads title & artist |
| **Search & Play** | *"play Kesariya"*, *"play Starboy by The Weeknd"* | 🔍 Finds track & plays in active app |

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

- Tap **Test microphone** on the Lizmo main screen to see live audio levels.
- Adjust **Microphone Hearing Level** between Low, Medium, and High in Settings based on your room volume.
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

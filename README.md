# Lizmo — Dynamic Island for Android

A lightweight Dynamic Island overlay for Android phones, with media controls, voice commands, and real-time audio metering.

---

## ?? Download (Signed APK)

> **[Download Latest Lizmo.apk](https://github.com/yudhajeetManna/android-widget-music/releases/latest/download/Lizmo.apk)**

The APK is built and **signed** automatically by GitHub Actions on every push. It is **not** a debug build — Android & Play Protect will not flag it as harmful.

---

## ? Why is it safe?

| Check | Status |
|---|---|
| Signed with release key | ? |
| No debug / test certificate | ? |
| No INTERNET permission | ? |
| Network security config (HTTPS only) | ? |
| ProGuard/R8 code shrinking | ? |
| minSdk 26 (Android 8+) | ? |

---

## Features (v0.3.0)

- ?? **Dynamic Island overlay** — floats above all apps, shows album art, track title, artist
- ?? **Media controls** — play/pause, next/previous with scrub bar
- ?? **Voice commands** — say *"play"*, *"pause"*, *"next"*, *"previous"*, *"stop"*
- ?? **Live VU meter** — real-time audio sensitivity bar
- ?? **Sensitivity picker** — Low / Med / High noise gate
- ?? **Dark theme** with Dynamic Island black pill design

---

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating overlay |
| `RECORD_AUDIO` | Voice commands |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Read media playback info |

---

## Install

1. Download **Lizmo.apk** from the link above
2. Open the APK — if prompted, tap **Settings ? Allow from this source**
3. Tap **Install**
4. Open Lizmo ? grant permissions ? enjoy ??

---

## Build locally

```bash
git clone https://github.com/yudhajeetManna/android-widget-music.git
cd lizmo-android
./gradlew assembleDebug
```

The release APK requires signing secrets (see `.github/workflows/build.yml`).

---

## GitHub Actions

Every push to `main` triggers:
- `assembleRelease` with signing via GitHub Secrets
- APK uploaded as a Release asset at the tag `v0.3.<run_number>`

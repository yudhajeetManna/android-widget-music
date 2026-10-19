# Lizmo (Android)

Dynamic-Island style music overlay: now-playing from any app, synced lyrics (LRCLIB), 5 themes.

## Build the APK (GitHub Actions)
1. Push this folder to a GitHub repo on `main`.
2. Actions > build-apk > Run workflow (also runs on push).
3. Download the `smart-island-apk` artifact (app-debug.apk) and install it.

## Local build
Needs JDK 17 + Android SDK: `gradle assembleDebug` (or open in Android Studio).

## First run
1. Open the app > Grant notification access (enable Lizmo).
   Sideloaded? Settings > Apps > Lizmo > menu > Allow restricted settings, then retry.
2. Grant overlay permission.
3. Play music in any app. Tap the island to expand; tap the theme chip to cycle themes.

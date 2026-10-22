# Lizmo (Windows 11)

Pops up when music plays, synced lyrics, 5 themes, song search, and hands-free voice control.

## Build the installer (GitHub Actions)
1. Push this folder to a GitHub repo on `main`.
2. Wait for build-windows to go green, then open Releases and download the .exe installer.

## Use
- Runs from the tray icon (right-click > Quit Lizmo). Put a shortcut in `shell:startup` to launch with Windows.
- Hover the pill to expand. Tap the magnifier to search; Win+H dictates into the search box.
- Search results: play a 30s preview, or open the song in Spotify / YouTube Music.
- **Voice Control**:
  - Click the **🎙️** button in the control bar, or press **`Alt+V`** (or **`V`** when not in an input).
  - Supported Voice Commands:
    - **Playback**: `"play"`, `"pause"`, `"next"` / `"skip"`, `"previous"` / `"back"`, `"toggle"`
    - **Themes**: `"theme glass"`, `"theme neon"`, `"theme minimal"`, `"theme sunset"`, `"theme mono"`, `"next theme"`
    - **Lyrics**: `"lyrics"`, `"reload lyrics"`
    - **Search & Play**: `"search <song>"`, `"find <artist>"`, or `"play <title>"` (e.g. `"search Starboy"`)
    - **Island**: `"expand"`, `"collapse"`
  - **Audio Sensitivity**:
    - Tap **`Sens: LOW / MED / HIGH`** to adjust voice detection threshold based on ambient background noise.
    - Features a real-time live microphone VU level meter with visual sensitivity threshold marker.


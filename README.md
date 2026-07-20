# Float Overlay

Android floating overlay app for mobile livestreamers. Display browser-source overlays (like OBS browser sources) as floating widgets above fullscreen games while streaming from TikTok Live and similar apps.

## Features

### Floating Overlays
- Display multiple WebView-based overlays on top of other apps and fullscreen games.
- Add any browser-source URL (e.g., Sociabuzz timer, StreamElements alerts).
- Each overlay is independently configurable.
- Smart reload: the WebView is only recreated when the URL changes. Size, position, opacity, zoom, and other settings apply instantly without reloading.
- Per-overlay refresh button to reload the page manually.

### Overlay Customization
- **Crop size** — width and height in dp.
- **Position** — place anywhere on screen as pixels from the left/top edges.
- **Opacity** — whole-overlay alpha.
- **Background** — color, corner radius, or fully transparent.
- **Content zoom** — scale the page content up or down (25%–300%).
- **Content offset** — pan the page horizontally/vertically in pixels.
- **Resize handle** — drag the bottom-right corner to resize on the fly.
- **Touch-through mode** — when enabled, taps pass straight through the overlay to the game underneath.

### Controls
- **Auto-show overlays on start** — automatically open all enabled overlays when the service starts.
- **Draggable floating icon** — a small circle icon stays on screen at all times. Tap it to show/hide overlays, drag it to move it out of the way.
- **Notification badge counter** — shows pending donation/chat/viewer events on the floating icon. Clears when overlays are opened.

### Game Launcher
- A dedicated **Game** tab for launching Clash Royale in a freeform window.
- Save window presets with custom size and position as percentages of the physical screen.
- Built-in **Launch fullscreen** preset for normal play.
- Switching presets while the game is running reuses the existing task when possible.
- Freeform support depends on the device/ROM. If the game opens fullscreen, freeform is not enabled on that device (some phones need the Taskbar app or Developer Options to enable it).

## Tech Stack

- Kotlin
- Android native XML Views
- WebView for browser sources
- `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`)
- SharedPreferences + `org.json` for local persistence
- No backend or account required

## Requirements

- Android 8.0+ (API 26)
- `compileSdk = 34`, `targetSdk = 34`
- JDK 17
- Android SDK

## Build

No Android Studio required. The project includes the Gradle wrapper.

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Release APK output:

```
app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the committed keystore (`app/float_overlay.keystore`), so you can install updates without uninstalling the previous APK.

## GitHub Actions

On every push to `main`/`master`, the workflow in `.github/workflows/build-apk.yml` builds the signed release APK and uploads it as an artifact named `app-release`.

## Usage

1. Install the APK and open the app.
2. Tap **Grant Permission** and enable **Display over other apps**.
3. On the **Overlays** tab, tap **Add Overlay** and enter a name and URL.
4. Tap **Start Overlay**. The small floating circle icon appears.
5. Open your game. Tap the floating icon to show/hide the overlays.
6. Long-press and drag an overlay to move it, or drag the resize handle to resize.
7. Use the **Game** tab to launch Clash Royale in a freeform window preset, leaving empty screen space for your overlays.

## Permissions

- `SYSTEM_ALERT_WINDOW` — draw overlays over other apps.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — keep the overlay service running.
- `INTERNET` — load browser-source URLs.

## Notes

- The floating icon is intentionally kept small so it blocks as little of the game as possible. Drag it to a corner if it covers a control.
- Touch-through mode is on by default for new overlays.
- The Game Launcher checks for the global Clash Royale package (`com.supercell.clashroyale`) and the Tencent/Chinese variant (`com.tencent.tmgp.supercell.clashroyale`).

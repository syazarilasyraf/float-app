# Float

Float is a lightweight Android companion for mobile gamers. It provides floating overlays and a private, low-resource screen-streaming feature designed for one-to-one viewing.

## Features

### Floating Overlays
- Display multiple WebView-based overlays on top of other apps and fullscreen games.
- Add any browser-source URL (e.g., Sociabuzz timer, StreamElements alerts).
- Each overlay is independently configurable.
- Smart reload: the WebView is only recreated when the URL changes. Size, position, opacity, zoom, and other settings apply instantly without reloading.
- Per-overlay refresh button to reload the page manually.

### Overlay Customization
- **Crop size** — width and height in dp.
- **Position** — place anywhere on screen.
- **Opacity** — whole-overlay alpha.
- **Background** — color, corner radius, or fully transparent.
- **Content zoom** — scale page content (25%–300%).
- **Content offset** — pan the page.
- **Resize handle** — resize on the fly.
- **Touch-through mode** — taps pass through to the game underneath.

### Saved Videos
- Share TikTok or YouTube links to Float.
- Open saved videos as floating overlays.

### Game Launcher
- A dedicated **Game** tab for launching Clash Royale in a freeform window.
- Save window presets with custom size and position.

### Private Screen Streaming
- Stream your phone screen privately to a browser viewer over WebRTC.
- One streamer, one viewer.
- Works over local Wi-Fi or the Internet.
- Configurable quality (480p / 720p / 1080p) and frame rate (30 / 60 FPS).
- Optional microphone audio toggle.
- Minimal Node.js signaling server; no video is stored or transcoded on the server.
- Designed for long-running background streaming while gaming.

## Architecture

```
Float
├── Overlay Engine (FloatOverlayService)
│   ├── Web overlays
│   └── Camera overlays
├── Saved Videos
├── Game Launcher
└── Private Stream (WebRTC, MediaProjection, hardware H.264)
```

## Tech Stack

- Kotlin
- Android native XML Views
- WebView for browser-source overlays
- `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`)
- WebRTC (`com.github.webrtc-sdk:android`)
- SharedPreferences + `org.json` for local persistence
- Minimal Node.js/WebSocket signaling server

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
7. On the **Stream** tab, choose **Local network** or **Internet**, set the signaling server URL, and tap **Start Stream**.
8. Grant screen capture permission.
9. Copy the viewer link and send it to your viewer.
10. Open your game. The stream continues through the foreground service.

## Streaming Server

See [`server/README.md`](server/README.md) for setup, environment variables, TURN configuration, and deployment options.

## Permissions

- `SYSTEM_ALERT_WINDOW` — draw overlays over other apps.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` — keep overlay and stream services running.
- `INTERNET` — load browser-source URLs and connect to the signaling server.
- `CAMERA` — optional camera overlay feature.
- `RECORD_AUDIO` — optional microphone audio in streams.

## Security

- No API keys are embedded in source code.
- No analytics, advertising, accounts, or public stream directory.
- Private stream IDs are randomly generated and expire when the stream stops.
- Video is peer-to-peer WebRTC; the signaling server does not store or process video.

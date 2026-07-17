# Float Overlay

Android floating overlay app for mobile livestreamers. Display browser-source overlays (like OBS browser sources) as floating widgets above fullscreen games while streaming from TikTok Live, etc.

## Features

- Floating overlay window above other apps and fullscreen games
- Multiple browser-source URL management
- Per-overlay customization: size, opacity, background color, rounded corners, transparent background
- Minimize to a draggable floating icon
- Notification badge counter for donation/chat/viewer events
- Local storage via SharedPreferences — no backend or account required

## Tech Stack

- Kotlin
- Android native (XML Views)
- WebView for browser sources
- WindowManager overlay (`TYPE_APPLICATION_OVERLAY`)
- SharedPreferences + `org.json` for persistence

## Build

No Android Studio required. The project includes the Gradle wrapper.

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Release APK output:
```
app/build/outputs/apk/release/app-release-unsigned.apk
```

## GitHub Actions

On every push to `main`/`master`, the workflow in `.github/workflows/build-apk.yml` builds the release APK and uploads it as an artifact named `app-release`.

## Usage

1. Install the APK and open the app.
2. Tap **Grant Permission** and enable **Display over other apps**.
3. Add overlay URLs (e.g., Sociabuzz timer).
4. Tap **Start Overlay**.
5. Open your game. The floating icon appears; tap it to show the overlay.
6. Drag the icon or the overlay to reposition. Tap the down arrow to minimize.

## Test Badge Counter

Use the **Test Donation** and **Test Chat** buttons in the app to increment the badge counter on the floating icon. The counter resets when you open the overlay.

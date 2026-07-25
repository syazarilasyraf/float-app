Add a full camera overlay feature to this app. Camera overlays use the
existing overlay machinery (WindowManager container, drag, resize/crop,
opacity, lock via touchThrough, position persistence, smart reload) but
render a live camera preview instead of a WebView. They will be used two
ways: (1) small facecam over Clash Royale gameplay, (2) large/fullscreen
for IRL streaming. Read FloatOverlayService.kt, OverlayEditDialog.kt,
model/OverlayConfig.kt, dialog_overlay_edit.xml and OverlayRepository.kt
first. Run ./gradlew assembleDebug after each step and fix all errors.

URL SCHEME: overlay url "camera://front" or "camera://back" selects the
lens. Anything else renders a WebView as today.

STEP 1 — Setup
- Add CameraX dependencies: camera-core, camera-camera2, camera-lifecycle,
  camera-view (recent stable versions)
- Manifest: <uses-permission android:name="android.permission.CAMERA"/> and
  <uses-feature android:name="android.hardware.camera.any"
  android:required="false"/>
- Runtime permission: when Start Overlay is tapped and any enabled overlay
  has a camera:// URL, request CAMERA permission first; if denied, skip
  camera overlays with a Toast and start the rest

STEP 2 — Camera view
- OverlayConfig: add cameraShape (String, "square"|"circle", default
  "square") and cameraFilter (String: "normal"|"mono"|"sepia"|"warm"|
  "cool"|"vivid"|"fade", default "normal"), with JSON persistence
- In createOverlayView(): for camera:// URLs, add a PreviewView
  (implementationMode COMPATIBLE so clipping works) tagged
  "overlayCameraView"; do NOT create a WebView
- Bind CameraX Preview use case via a service-owned LifecycleOwner
  (ServiceLifecycleOwner pattern with LifecycleRegistry), lens facing from
  the URL, target resolution ~1280x720, front preview mirrored
- Defaults when adding a camera overlay in OverlayEditDialog: 140x140dp,
  cornerRadius 24

STEP 3 — Shapes
- Square: rounded-rect clip using config.cornerRadiusDp (dp -> px)
- Circle: ViewOutlineProvider returning an OVAL outline
- Both via outlineProvider + clipToOutline = true on the container
- Shape changes apply live through the smart-reload path (no camera rebind)

STEP 4 — Filters (hardware-layer color matrix, NO OpenGL)
- Apply to the PreviewView with setLayerType(LAYER_TYPE_HARDWARE, paint)
  where the paint holds a ColorMatrixColorFilter per preset:
  normal (identity / LAYER_TYPE_NONE), mono (saturation 0), sepia,
  warm (boost R, cut B), cool (boost B, cut R), vivid (saturation ~1.6),
  fade (saturation ~0.8 + raised output offset for lifted blacks)
- Filter changes apply live via smart reload; no camera rebind

STEP 5 — Flip camera without on-screen buttons
- Double-tap on the camera overlay (only when interactive, i.e.
  touchThrough OFF) flips the URL camera://front <-> camera://back in the
  repository and recreates that overlay through the existing URL-change
  path. Single-tap and drag behavior unchanged. Log via LogStore.
- Do NOT add any button to the camera overlay itself.

STEP 6 — Edit dialog
- When the URL starts with "camera://": show a Shape selector
  (Square/Circle) and a Filter dropdown (Normal, Mono, Sepia, Warm, Cool,
  Vivid, Fade); show hint "Camera overlay — zoom/offset don't apply;
  double-tap the overlay to flip camera"; disable the zoom slider and
  offset fields
- Empty name/URL validation stays as-is

STEP 7 — Lifecycle correctness
- Unbind the camera (unbindAll) when the overlay is removed via
  removeOverlayView and when the service is destroyed; camera must never
  stay locked after overlays close
- Camera init failure: LogStore error + Toast, other overlays unaffected
- scalePercent/contentOffset skip camera overlays in applyOverlayChanges;
  opacity/size/position/corner radius/touchThrough apply as usual

When done, summarize: exact steps to add a facecam, how to flip camera,
and what happens to the camera on hide vs service stop.


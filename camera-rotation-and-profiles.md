# Camera Rotation + Overlay Profiles

Feature pass for this floating overlay app: (A) camera rotation handling,
(B) overlay profiles for portrait/landscape game setups. Read
FloatOverlayService.kt, OverlayEditDialog.kt, model/OverlayConfig.kt,
OverlayRepository.kt, PresetRepository.kt, OverlayListFragment.kt,
MainActivity.kt, ui/game/GameLauncherFragment.kt and PresetEditDialog.kt
first. Run ./gradlew assembleDebug after each task and fix all errors
before continuing. Do not change unrelated behavior.

## TASK 1 — Camera rotation (auto + manual)

Camera overlays live in a Service with WindowManager windows, so they don't
receive activity-style configuration changes. When the phone rotates
(portrait Clash Royale vs landscape Minecraft/IRL), the camera preview can
render sideways.

1. OverlayConfig: add cameraRotation (String) with JSON persistence —
   values "auto" (default), "0", "90", "180", "270".
2. Auto mode: register an OrientationEventListener (or DisplayListener) in
   FloatOverlayService while any camera overlay is visible. On display
   rotation change, update every active camera overlay's Preview use case
   targetRotation to the current display rotation (update targetRotation on
   the bound Preview without full unbind/rebind if the CameraX version
   supports it; otherwise rebind just the Preview use case). Re-apply the
   shape clip afterward.
3. Manual mode: "0"/"90"/"180"/"270" forces Surface.ROTATION_* and wins
   over auto until set back to "auto".
4. OverlayEditDialog: for camera:// overlays add a "Rotation" selector
   (Auto / 0° / 90° / 180° / 270°), default Auto, prefilled from
   cameraRotation, hint: "Auto follows the phone. Set manually if the
   camera looks sideways in landscape."
5. Smart reload: cameraRotation applies LIVE on the existing camera overlay
   (no recreation). Front preview stays mirrored in all rotations;
   double-tap flip preserves the rotation setting.

## TASK 2 — Overlay profiles (snapshot per game/orientation)

A profile is a named SNAPSHOT of the entire overlay setup: the full overlay
list (including disabled overlays) with every OverlayConfig exactly as
saved (position, size, zoom, shape, filter, locked, touchThrough, zIndex,
cameraRotation — everything, including Task 1's new field).

1. Model + storage: OverlayProfile(id, name, orientation:
   "portrait"|"landscape"|"any", overlays: List<OverlayConfig>) with JSON
   serialization; new ProfileRepository following PresetRepository's
   SharedPreferences+JSON pattern (getProfiles, addOrUpdate, delete,
   getProfile).
2. UI in the Overlays tab:
   - "Save as profile": captures the current full overlay list into a new
     profile; dialog for name + orientation (default = device's CURRENT
     orientation at save time, editable)
   - "Profiles" list: name + orientation label; per profile: Apply,
     Rename, Delete (with confirm)
   - Apply = replace OverlayRepository's entire list with the profile's
     snapshot (deep copies, no shared references), then FULL reload via a
     new intent action: recreate all visible overlay views. WebView/camera
     reloads are acceptable here — profile switching happens between games,
     never mid-match. Respect zIndex ordering and bringIconToFront last.
   - Applying a profile with zero overlays is valid ("clean" profile) but
     needs a confirmation dialog.
3. Auto-switch on rotation (optional, default OFF): MainActivity toggle
   "Auto-apply profile on rotation". When ON, FloatOverlayService watches
   orientation changes; on change, if exactly one profile matches the new
   orientation apply it; if several match, apply the most recently
   created/applied and log via LogStore; if none, do nothing. Never
   auto-apply within 10 seconds of a manual apply (anti-flap).
4. Game-tab link: WindowPreset gets optional linkedProfileName (free-text
   string, editable in PresetEditDialog). When launching a game preset
   whose linkedProfileName matches an existing profile, apply that profile
   right after the game launches; log it; no match = launch as today.

## When done

Summarize:

- (a) camera rotation behavior auto vs manual
- (b) the exact tap sequence to save my current setup as a profile and to
  switch from Clash Royale (portrait) to Minecraft (landscape)
- (c) how auto-switch behaves on rotation
- (d) a manual test list for every task

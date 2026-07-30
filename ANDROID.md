# OpenKeeper for Android

This branch runs OpenKeeper on Android using data from a legally owned
Dungeon Keeper 2 installation. The APK does not contain Bullfrog/EA game
assets.

## Requirements

- Windows with JDK 25 available to Gradle
- Android SDK platform 36 and platform-tools
- One Android device authorized through ADB
- A local Dungeon Keeper 2 installation

The app targets Android 11 or newer (`minSdk 30`) and includes native
`arm64-v8a` support through jMonkeyEngine.

## Build and deploy

From PowerShell in the repository root:

```powershell
.\scripts\deploy-android.ps1 `
  -Dk2Path "C:\Program Files (x86)\Steam\steamapps\common\Dungeon Keeper 2"
```

The first run converts the DK2 assets, builds and installs the debug APK,
uploads both the converted assets and original `Data` directory to the app's
external storage, and launches OpenKeeper. The upload is large; later runs can
skip work:

```powershell
# Rebuild/install code while keeping data already on the device
.\scripts\deploy-android.ps1 -Dk2Path "C:\path\to\Dungeon Keeper 2" `
  -SkipConversion -SkipAssets

# Synchronize only changed assets
.\scripts\deploy-android.ps1 -Dk2Path "C:\path\to\Dungeon Keeper 2" `
  -SkipConversion
```

The debug APK is written to:

```text
android/build/outputs/apk/debug/android-debug.apk
```

Device data is kept below:

```text
/sdcard/Android/data/toniarts.openkeeper.android.debug/files/
|-- Converted/
`-- DK2/Data/
```

Uninstalling the app may remove this directory, so keep the PC installation.

## Mobile controls

- Tap: click/select/interact
- One-finger drag: select or tag a tile area
- Two-finger drag: pan the camera
- Pinch: zoom the camera
- Android Back: OpenKeeper's exit/back handling

### Samsung S Pen and other Android styluses

- Hover: move the Keeper cursor and preview campaign markers, tiles, and objects
- Pen tip: primary click, drag selection, and tile tagging
- Barrel button + tip: secondary/right-click action
- Barrel button while hovering: secondary action without touching the display
- Finger-only gestures are suppressed while the pen is in range for basic palm
  rejection

The Android host accepts both pure stylus and combined stylus/touchscreen input
sources. Debug builds log the tool type, pressure, tilt, orientation, distance,
and button state for device-specific tuning:

```powershell
adb logcat -s OpenKeeperSpen:D '*:S'
```

The Android host uses immersive landscape mode and a 1920x886 internal render
surface so the original fixed-pixel Nifty UI remains usable on high-density
phones.

## Current platform notes

- Campaign menus, mission briefing, in-engine camera sequences, live HUD,
  touch and S Pen selection, audio initialization, and Android pause/resume are
  enabled.
- Graphics Options is available. Android-owned resolution, fullscreen,
  antialiasing, VSync, and renderer controls are shown read-only; anisotropic
  filtering and SSAO remain configurable.
- Original pre-rendered TGQ movies are skipped because their decoder currently
  depends on desktop AWT image classes. Gameplay cinematics still run.
- Converted and original DK2 assets are intentionally excluded from Git and
  from the APK.

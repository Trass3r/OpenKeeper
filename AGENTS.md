# OpenKeeper — Developer Guide

## Project Overview

**OpenKeeper** is an open-source cross-platform remake of *Dungeon Keeper II* (v1.7 + 3 bonus packs), written in Java using [JMonkeyEngine](http://jmonkeyengine.org/) (JME). 

- **Target**: Java 25 + JME 3.9
- **Build system**: Gradle 9.x
- **License**: GPLv3
- **Current branch**: `linuxdemo` (Linux-specific fixes and CI infrastructure)

## Getting Game Data

### DK2 Demo (recommended for development/testing)

```bash
# Download
curl -L -o DungeonKeeper2Demo.exe "https://archive.org/download/DungeonKeeper2Demo/DungeonKeeper2Demo.exe"

# Extract (requires 7zip and unshield)
sudo apt-get install -y 7zip unshield xvfb xdotool
7zz x -y DungeonKeeper2Demo.exe
unshield x disk1/data1.cab
mv Program_Files DK2
```

The demo is limited — certain textures (e.g., `Trap_Lightning.png`) and sound files are absent. The game prints warnings for missing textures but doesn't crash.

## Configuration

Two properties files control the game:

### 1. `openkeeper.properties` (in CWD) — Global/Setup Settings
Controls the DK2 folder path and asset conversion version tracking:
```
DungeonKeeperIIFolder(string)=/path/to/DK2/
```
Managed by `SettingUtils.java`. To force re-conversion, remove the asset version lines from this file and delete `assets/Converted/`.

### 2. `~/.OpenKeeper/openkeeper.properties` — User Settings
Controls graphics, audio, controls, etc.:
```
Width(int)=1280
Height(int)=720
MusicEnabled(bool)=false
VoiceEnabled(bool)=false
SfxEnabled(bool)=false
```
Managed by `Settings.java` (`Settings.Setting` enum).

### Audio Renderer
To disable the OpenAL audio renderer (essential for headless/test/CI environments), set all of `MusicEnabled`, `VoiceEnabled`, and `SfxEnabled` to `false`.
When all three are disabled:
- The audio renderer is set to `null`, preventing OpenAL initialization
- NiftyGUI sound setup is skipped

## Building

```bash
# Compile only
./gradlew compileJava --no-daemon

# Build fat jar
./gradlew shadowJar --no-daemon
# Output: build/libs/OpenKeeper-1.0.jar
```

## Running for Testing

### Desktop
```bash
java -jar build/libs/OpenKeeper-1.0.jar
```

### Headless (CI/containers, no display)

For the full recipe — Xvfb bring-up, screenshot capture, visual
verification (ANSI via `chafa` + statistical fallback), the
gnome-screenshot truncation pitfall, and tear-down hygiene — see:

**`.agents/skills/openkeeper-headless-run/SKILL.md`**

Companion script: **`scripts/run_headless_screenshot.sh`**. It runs
Xvfb and the jar in the background, waits for the level to load,
captures a window screenshot, copies it into
`scratch/screenshots/<project>/` so in-project tooling can read it,
and prints both statistical (Pillow) and ANSI (`chafa`) diagnostics.
Invoke once, foreground, ~15 s warm start.

Asset conversion runs once per DK2 folder (`assets/Converted/`) —
see *Asset Conversion* below for cache logic and force-reconversion.

## Asset Conversion

**Conversion runs exactly once per DK2 folder.** The output is cached in `assets/Converted/` and reused on every subsequent launch, so a repeat run only spends the ~5-15 s it takes to load the level. The long 40-90 s wait is a first-run-only cost — when planning automation, gate the screenshot trigger on log output (e.g. `Game Steering`) rather than a fixed `sleep`.

At first run, the game converts DK2's proprietary formats to formats JME can use:
- `.wad` archives → extracted files
- `.kmf` (Keeper Model Format) → processed 3D models
- `.eng` textures → PNG/DDS textures
- `.tgq` movies → decoded video frames
- Sound files → MP2 audio

To check whether conversion has already happened, look for any non-empty subdirectory in `assets/Converted/` (e.g. `Interface/`, `Models/`, `Sounds/`).

To force re-conversion:
1. Delete `assets/Converted/`
2. Remove the asset version lines from `openkeeper.properties`

## Path Handling

- **All internal paths use forward slashes (`/`)** — includes asset keys, folder constants, and file paths
- **`File.separator` is never used** in the codebase
- The main source of backslashes is paths from `.wad` archive entries (Windows paths). These are normalized by `PathUtils.convertFileSeparators()` (replaces `\` with `/`)
- Asset keys are case-insensitive in the locator system
- All `AssetsConverter` folder constants have trailing slashes:
  ```java
  SOUNDS_FOLDER = "Sounds/"
  MODELS_FOLDER = "Models/"
  TEXTURES_FOLDER = "Textures/"
  // etc.
  ```

## Key Architecture

- **`AssetsConverter.java`** — Asset conversion pipeline and folder constants
- **`AssetUtils.java`** — Asset key canonicalization
- **`PathUtils.java`** — Path normalization, DK2 folder management
- **`Settings.java`** — User settings (enum-based, stored in `~/.OpenKeeper/`)
- **`SettingUtils.java`** — Global/project settings (stored in `openkeeper.properties`)
- **`SoundState.java`** — Game sound management (background music, voice, SFX)
- **`ModelViewer.java`** — Standalone model viewer tool

## Known Quirks

- The DK2 demo lacks certain texture files; missing texture warnings are normal
- The demo's MUSIC category has no sound files — the `BackgroundState.getNext()` loop was fixed to handle this gracefully (was StackOverflowError)
- Xvfb + xdotool are required for headless end-to-end testing
- Audio must be disabled via `MusicEnabled(bool)=false`, `VoiceEnabled(bool)=false`, and `SfxEnabled(bool)=false` for headless environments

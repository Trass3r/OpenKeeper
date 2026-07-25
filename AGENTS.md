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

### Headless (CI/containers, no display)

```bash
# Start X virtual framebuffer
Xvfb :99 -screen 0 1280x720x24 &

# Run the game
DISPLAY=:99 timeout 120 java -jar build/libs/OpenKeeper-1.0.jar -level Conquest &

# Wait for asset conversion (~40s first run), then exit
sleep 45
DISPLAY=:99 xdotool key Escape
```

- First run performs asset conversion (extracting models, textures, sounds from `.wad` archives)
- The `-level Conquest` flag skips the main menu and loads a level directly
- The demo data takes ~40 seconds to convert

### Desktop
```bash
java -jar build/libs/OpenKeeper-1.0.jar
```

## Asset Conversion

At first run, the game converts DK2's proprietary formats to formats JME can use:
- `.wad` archives → extracted files
- `.kmf` (Keeper Model Format) → processed 3D models
- `.eng` textures → PNG/DDS textures
- `.tgq` movies → decoded video frames
- Sound files → MP2 audio

Converted assets are cached in `assets/Converted/`. To force re-conversion:
1. Delete `assets/Converted/`
2. Remove asset version lines from `openkeeper.properties`

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

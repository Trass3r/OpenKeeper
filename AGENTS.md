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
- The demo data takes ~40 seconds to convert on the **first** launch only; subsequent runs reuse the asset cache and load the level in ~5-15 seconds (see *Asset Conversion* below)

### Capturing a screenshot for analysis (xdotool)

OpenKeeper wires JME's `ScreenshotAppState` to `KEY_SYSRQ` (Print Screen). On capture, the PNG is written to:

```
$HOME/.OpenKeeper/SCRSHOTS/Main1.png
```

The screenshot is owned by whichever OS user launched the JVM — **always check `whoami` first when looking for it.** On this container, `root` writes to `/root/.OpenKeeper/...` and `daytona` writes to `/home/daytona/.OpenKeeper/...`. There is no built-in way to make the game pick a different `$HOME`; just use `runuser -u <user> -- ...` or `sudo -u <user>` to control it.

End-to-end recipe (works on the second-and-onwards runs because the asset cache is already warm):

```bash
# 1. Make sure Xvfb is up
pkill -f 'Xvfb :99' 2>/dev/null
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
Xvfb :99 -screen 0 1280x720x24 -ac +extension GLX +render -noreset &

# 2. DK2 ships at mode 700/root in this workspace; let non-root users read it.
#    Ownership stays root, so the git working tree is untouched.
chmod -R o+rX DK2

# 3. Launch as the user whose $HOME you want populated.
#    DISPLAY=:99 + HOME point at daytona so screenshots land in daytona's profile.
DISPLAY=:99 runuser -u daytona -- bash -c \
  'DISPLAY=:99 nohup java -jar build/libs/OpenKeeper-1.0.jar -level Conquest > /tmp/ok.log 2>&1 &'

# 4. Wait for the level to load. First run ~60-90 s (asset conversion),
#    later runs ~5-15 s. "Game Steering" / "World" entries in /tmp/ok.log are
#    a reliable ready-signal.
while ! grep -q 'Game Steering' /tmp/ok.log 2>/dev/null; do sleep 2; done

# 5. Trigger Print Screen on the game window
WID=$(DISPLAY=:99 xdotool search --name 'OpenKeeper' | head -n 1)
DISPLAY=:99 xdotool key --window "$WID" Print
sleep 2   # ScreenshotAppState writes asynchronously
ls -lt "$HOME/.OpenKeeper/SCRSHOTS/"
```

If the level never loads, check `/tmp/ok.log` for `Dungeon Keeper II folder not found` — that almost always means a permission problem on `DK2` (re-run `chmod -R o+rX DK2`).

If `xdotool key Print` does nothing inside Xvfb, capture the X11 root window directly as a fallback:

```bash
xwd -display :99 -root -out /tmp/ok.xwd
convert /tmp/ok.xwd "$HOME/.OpenKeeper/SCRSHOTS/$(date +%s).png"
```

Quick analysis with ImageMagick + Pillow:

```bash
identify "$HOME/.OpenKeeper/SCRSHOTS/Main1.png"
python3 -c "
from PIL import Image, ImageStat
im = Image.open('$HOME/.OpenKeeper/SCRSHOTS/Main1.png').convert('RGB')
s = ImageStat.Stat(im)
print('avg', s.mean, 'std', s.stddev)
"
```

Heuristics for what the numbers tell you:
- Avg `(~93, ~72, ~56)` and brightness ~73/255 ⇒ dim warm underground dungeon — normal in-game state.
- Brightness `< 20` ⇒ black screen, menu, or game crash.
- Mostly uniform dark blue or near black ⇒ still on the loading screen / main menu.

### Desktop
```bash
java -jar build/libs/OpenKeeper-1.0.jar
```

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

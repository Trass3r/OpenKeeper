---
name: openkeeper-headless-run
description: Launch OpenKeeper (DK2 remake) in an Xvfb display, capture an in-game screenshot, copy it into the project, and verify the render statistically. Use when the user asks to "run the game", "screenshot OpenKeeper", "verify a render visually", "test the Conquest level", or any headless test of the JME-based Java game on Linux. Do not use for non-Linux, non-X11 platforms or for builds/typing.
---

# OpenKeeper — headless run + screenshot

End-to-end recipe for bringing up OpenKeeper under `Xvfb` on a Linux
host (CI, containers, dev machines), loading a level, capturing an
in-game PNG, and verifying the render. Companion script:
`scripts/run_headless_screenshot.sh` — copy it into a project or invoke
its commands inline.

This skill assumes the host has:

- JDK 25, `xvfb`, `xdotool`, `convert`/`identify` (ImageMagick), `python3` with Pillow
- An extracted `DK2/` demo data folder (see project `AGENTS.md` for the unshield/unzip recipe)
- A prebuilt fat jar (default: `build/libs/OpenKeeper-1.0.jar`)

## 1. When to use

Use this skill whenever the user wants to:

- See what the game looks like right now (does it render? does it crash?)
- Verify a regression in the dungeon render, the UI, or the loading flow
- Run OpenKeeper in CI to prove the wrapper still starts under a clean display

Do not use it for: builds (use `./gradlew shadowJar`), unit tests, or
running on a desktop with a real display server (just launch the jar
directly). On macOS / Wayland, replace `Xvfb` with `Xquartz` /
`weston-launch` and adjust accordingly.

## 2. Pitfall: screenshots must live INSIDE the project

OpenKeeper's `ScreenshotAppState` writes captures to `~/.OpenKeeper/SCRSHOTS/`

`read_files` (and most editor tools) are **blocked from paths outside
the project root**. So always **copy the captured PNG into the project**
after capture — the skill's script does this into:

    <project_root>/scratch/screenshots/Main_<epoch>_<method>.png

`scratch/` is git-ignored (see `scripts/run_headless_screenshot.sh`'s
`.gitignore` entry). Treat `scratch/screenshots/` as throwaway
artifacts.

## 3. Run-and-screenshot recipe

Three coordinated pieces, all in a single foreground script so the
long-lived JVM doesn't outlive the terminal timeout:

### 3a. Bring up the X display

```bash
pkill -f 'Xvfb :99' 2>/dev/null; sleep 1
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
Xvfb :99 -screen 0 1280x720x24 -ac +extension GLX +render -noreset >/tmp/xvfb.log 2>&1 &
XPID=$!
sleep 3
DISPLAY=:99 xdpyinfo | head -n 3   # sanity check
```

### 3b. Launch the game as the right user

The  `ScreenshotAppState` owner is whoever owns `$HOME`. So keep that in mind.

```bash
chmod -R o+rX ~/codebase/DK2
JAR=./build/libs/OpenKeeper-1.0.jar
DISPLAY=:99 nohup java -jar '$JAR' -level Conquest >/tmp/ok.log 2>&1 &
```

### 3c. Wait for the level, then capture

The "Game Steering" GameLoop line in `/tmp/ok.log` is a reliable
ready-signal (warm asset cache: ~5–15 s; first run: ~40–90 s).

```bash
for i in $(seq 1 90); do
  grep -q 'Game Steering' /tmp/ok.log && break
  sleep 1
done
# extra settle if Game Steering just engaged
sleep 12
```

### 3d. Send G keystroke to hide UI

Before capturing, send a lowercase `g` keypress to toggle off the
in-game UI. This gives a cleaner screenshot of just the dungeon
render without the sidebars, toolbar, and creature panel:

```bash
DISPLAY=:99 xdotool key --window "$WID" g
sleep 1
```

The G key is OpenKeeper/DK2's built-in UI toggle. Sending it before
capture means the screenshot shows the dungeon geometry and lighting
without the overlay UI — which is what you want for visual
verification of the 3D render quality.

Capture attempts, ranked by reliability on `Xvfb`:

| # | Command | Notes |
|---|---------|-------|
| 1 | `DISPLAY=:99 import -window $(xdotool search --name OpenKeeper \| head -n1) /tmp/shot.png` | **Cleanest** under Xvfb. Always non-truncated. |
| 2 | `DISPLAY=:99 xdotool key --window <WID> Print` | Triggers JME's `ScreenshotAppState` → `~/.OpenKeeper/SCRSHOTS/`. |
| 3 | `DISPLAY=:99 gnome-screenshot -w -d 2 -f /tmp/shot.png` | Works, but **often writes a TRUNCATED PNG under Xvfb** (no `IEND`). Reject by checking for an `IEND` chunk. |
| 4 | `xwd -display :99 -root -out /tmp/ok.xwd && convert /tmp/ok.xwd /tmp/shot.png` | Last-resort whole-root capture. Includes any window decoration. |

After the capture succeeds:

```bash
cp /tmp/shot.png ./scratch/screenshots/Main_$(date +%s)_<method>.png
identify ./scratch/screenshots/Main_*.png
```

Then tear down:

```bash
pkill -f 'OpenKeeper-1.0.jar'
kill $XPID; kill -9 $XPID; pkill -f 'Xvfb :99'
```

## 4. Pitfall: detecting TRUNCATED PNGs

`gnome-screenshot -w` under `Xvfb` (no window manager) frequently
writes a PNG that is missing its final `IEND` chunk. `ImageMagick
identify` will still accept it but **Pillow will refuse** unless you
opt in:

```python
from PIL import Image, ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True
im = Image.open("truncated.png").convert("RGB")
```

Prefer to **reject** truncated captures instead of patching them, so
your downstream tooling doesn't accumulate quiet breakage. The
companion script does this with a small Python check:

```python
import sys
with open(sys.argv[1], "rb") as fh:
    data = fh.read()
iend = data.rfind(b"IEND")
sys.exit(0 if (iend != -1 and (len(data) - iend) >= 8) else 3)
```

If your capture is truncated, fall back to `import -window <WID>` —
it doesn't have this problem.

## 5. Visual verification

There are TWO ways to actually look at an OpenKeeper screenshot.
Prefer **5a** whenever possible; fall back to **5b** when no
image→ANSI renderer is available.

### 5a. Image → ANSI rendering (preferred)

`read_files` returns the raw bytes of a PNG; it cannot render
images. To actually **see** a screenshot you need a tool that
converts pixels into terminal-displayable characters. The
recommended one is **`chafa`**:

```bash
apt-get install -y chafa            # Linux (jammy/universe has 1.8)
# macOS:  brew install chafa
chafa --size 120x40 scratch/screenshots/Main_<epoch>_imp.png
```

You'll see colored Unicode block characters approximating the
image. Reading the result:

| Region of the chafa grid | Meaning |
|---|---|
| Top ~5 rows | Status bar / room labels / dungeon-heart icon. Bright glyphs over warm tones == UI is rendered OK. |
| Center band | Lit dungeon area. If this is solid black with no variation, the game is on the loading screen / main menu. |
| Bottom ~5 rows | Console / creature/inventory row. Should be structured, not flat black. |
| Left/right rail columns | Darker, with repeating icons/labels (rooms/build spells) running vertically. Absent / solid black == sidebars didn't load. |
| Whole frame near-uniform dark blue or all-black | Crash, missing GL context, or never rendered. |

Other ANSI renderers that work just as well (use whichever is
installed):

| Tool | Install |
|---|---|
| `chafa` | `apt-get install -y chafa` (1.8.0) |
| `jp2a` | `apt-get install -y jp2a` |
| `img2txt` (libcaca-utils) | `apt-get install -y caca-utils` |
| `viu` | `cargo install viu` (Rust) |
| `ascii-image-converter` | `npm i -g ascii-image-converter` |
| `tiv` (TerminalImageViewer) | `apt-get install -y tiv` |

### 5b. Statistical verification (fallback)

If no ANSI renderer is available, fall back to Pillow + ImageStat
numbers:

| Signal | What it tells you |
|---|---|
| Overall brightness 50–80/255 with warm reddish mean (~70, ~58, ~49) | Normal lit dungeon |
| Overall brightness < 20/255, or a near-black frame | Crash, missing GL, or never-rendered |
| Top-strip brightness much higher than overall with high stddev | UI toolbar rendered (good) |
| Side rails clearly darker than center column | Sidebars present, central gameplay viewport lit (good) |
| Uniform dark blue / black with very low stddev (< 5) | Menu, loading screen, or pre-level render — keep waiting |
| Center 256×256 stddev > 25 | Dungeon has textured content (not a flat panel) |
| Edge density of center > 2× edge density of side rails | Dungeon tiles visible (good) |

A reference run on Ubuntu/Xvfb with the DK2 demo at `-level Conquest`
produced:

```
overall brightness 60.4 / 255
left  rail brightness 50.4 | std 30.5 | edge ~17.7
right rail brightness 41.0 | std 23.5 | edge ~10.0
top   strip brightness 63.5 | std 49.4
bottom strip brightness 35.4 | std 27.7
gameplay center brightness 78.9 | std 40.4 | edge ~34.3
```

Dominant palette buckets all warm/earthy (`(52,33,19)`, `(89,67,49)`,
`(131,117,104)`, …) — no blue dominance, no black dominance. Verdict:
OK, the dungeon is rendering.

## 6. Browser-use approach (experimental, does not work yet)

The `browser-use` agent can open a screenshot image in Chrome and
visually describe it. However, in the Freebuff Cloud environment it
currently returns empty results for both `file://` and `localhost`
URLs. This appears to be a Chrome DevTools connectivity issue in the
sandboxed workspace.

**Current status:** ❌ Does not work. The browser-use agent returns
`null` output when navigating to screenshot image URLs.

**Workaround:** Use the statistical verification (section 5b) or
`chafa` ANSI rendering (section 5a) instead. Pixel-level Python
analysis via Pillow can also provide useful color/region data.

If this gets fixed in the future:

```bash
# Serve the screenshot over HTTP and open in browser-use
python3 -m http.server 8765 -d scratch/screenshots &
# Then spawn browser-use with: http://localhost:8765/Main_<epoch>_*.png
```

**Note on OCR:** OCR is NOT needed for OpenKeeper screenshot analysis.
The game's text is rendered as bitmap fonts in the JME scene graph,
not as selectable/OCR-able text. Visual description of UI elements
(chafa, statistical analysis, or future vision models) is sufficient.

## 7. Troubleshooting

- `Dungeon Keeper II folder not found` in `/tmp/ok.log` → the
  `DungeonKeeperIIFolder(boolean)` in `openkeeper.properties` is wrong
  or `DK2/` is unreadable. Run `chmod -R o+rX <DK2 dir>`.
- `Xvfb` fails to start → check `/tmp/xvfb.log`. Often a stale lock
  at `/tmp/.X99-lock`. Remove and retry.
- Loop `Game Steering` never logged → either the game crashed (look
  for `SEVERE` lines above it) or the assets cache is cold. A cold
  cache takes 40–90 s; increase the wait loop accordingly.
- `gnome-screenshot -w` produces nothing → it's the truncation case,
  or there's no WM under `Xvfb` to give it a "focused window".
  Use `import -window <WID>` instead.
- `import` errors with "cannot get image" → the window id is gone.
  Look up `WID=$(xdotool search --name OpenKeeper | head -n1)` again,
  or wait longer before capturing.
- Pillow: `image file is truncated` → re-capture with a different
  method, or set `LOAD_TRUNCATED_IMAGES=True` for that file only.
- `read_files` returns `FILE_OUTSIDE_PROJECT` on the PNG → you forgot
  step 2 (copy the capture into `scratch/screenshots/`).

## 8. Quick one-liner for CI logs

```bash
bash scripts/run_headless_screenshot.sh | tee /tmp/ok_run.log
grep -E 'Capture method:|did not produce|TASK FAILED|Game Steering|in-project copy' /tmp/ok_run.log
```

The "Capture method:" + Pillow brightness line are the two signals to
gate downstream automation on.

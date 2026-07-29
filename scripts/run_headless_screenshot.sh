#!/bin/bash
# End-to-end headless run-and-screenshot recipe for OpenKeeper.
#
# Runs Xvfb + the OpenKeeper jar in background, waits for the level to
# load, then captures a window screenshot. Capture is tried in this
# order, using whichever tool is available AND produces a non-
# truncated PNG AND is at least 10 KB:
#
#   1) import -window <WID> <file>              (ImageMagick window grab; cleanest)
#   2) xdotool key --window <WID> Print         (JME's ScreenshotAppState)
#   3) gnome-screenshot -w -d <N> -f <file>     (often produces TRUNCATED PNGs
#                                                under Xvfb - included for
#                                                reference, rejection documented)
#   4) xwd -root + convert                      (whole root window fallback)
#
# WHY THE ORDER MATTERS:
#   * Under Xvfb (no WM) `gnome-screenshot -w` frequently writes a PNG
#     that is missing its final IEND chunk. ImageMagick `identify`
#     still accepts those files, but Pillow will reject them unless
#     `ImageFile.LOAD_TRUNCATED_IMAGES = True` is set. We therefore try
#     `import -window` first (clean) and check for an IEND chunk
#     before declaring any capture success.
#   * `xdotool Print` triggers JME's own ScreenshotAppState, which
#     writes a properly-terminated PNG to ~/.OpenKeeper/SCRSHOTS/.
#
# The captured PNG is ALSO copied into
#   <project_root>/scratch/screenshots/
# so it lives inside the workspace and can be opened by the editor/IDE
# and the editor's file tool. Anything outside <project_root> is
# blocked from the editor's read_files path.
#
# Designed to be invoked from a single foreground terminal call so the
# long-lived JVM does not bypass the terminal timeout.

set -u

LOG=/tmp/ok.log
SHOTS_DIR=/home/daytona/.OpenKeeper/SCRSHOTS
SCRATCH="/home/daytona/codebase/scratch/screenshots"
JAR=/home/daytona/codebase/build/libs/OpenKeeper-1.0.jar
DK2=/home/daytona/codebase/DK2

mkdir -p "$SCRATCH"
mkdir -p "$SHOTS_DIR"
chown -R daytona:daytona /home/daytona/.OpenKeeper

echo "=== cleanup any leftover Xvfb/Java ==="
pkill -f 'Xvfb :99' 2>/dev/null
pkill -f 'OpenKeeper-1.0.jar' 2>/dev/null
pkill -f 'java.*OpenKeeper' 2>/dev/null
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99
sleep 1

echo "=== chmod DK2 so daytona can read ==="
chmod -R o+rX "$DK2" 2>/dev/null
ls -ld "$DK2"

echo "=== start Xvfb on :99 ==="
: > /tmp/xvfb.log
Xvfb :99 -screen 0 1280x720x24 -ac +extension GLX +render -noreset > /tmp/xvfb.log 2>&1 &
XPID=$!
sleep 3
if ! kill -0 $XPID 2>/dev/null; then
  echo "Xvfb failed to start:"
  cat /tmp/xvfb.log
  exit 1
fi
echo "Xvfb pid=$XPID"
DISPLAY=:99 xdpyinfo 2>/dev/null | head -n 3

echo "=== launching game as daytona ==="
: > "$LOG"
disown 2>/dev/null
DISPLAY=:99 runuser -u daytona -- bash -c "
  HOME=/home/daytona DISPLAY=:99 nohup java -jar '$JAR' -level Conquest > '$LOG' 2>&1 &
  echo \$!
" > /tmp/java.pid
GPID=$(cat /tmp/java.pid)
if ! [[ "$GPID" =~ ^[0-9]+$ ]]; then
  GPID=$(pgrep -f 'OpenKeeper-1.0.jar' | head -n 1)
fi
echo "Game pid=$GPID"

echo "=== waiting for level to load (warm asset cache) ==="
READY=""
for i in $(seq 1 90); do
  if grep -q 'Game Steering' "$LOG" 2>/dev/null; then
    echo "Ready after ${i}s (Game Steering)"
    READY="GK"
    break
  fi
  if grep -q 'Dungeon Keeper II folder not found' "$LOG" 2>/dev/null; then
    echo "DK2 path error detected in log."
    break
  fi
  if [ -n "$GPID" ] && ! kill -0 "$GPID" 2>/dev/null; then
    echo "Game process exited after ${i}s."
    break
  fi
  sleep 1
done

if [ -z "$READY" ] && [ -n "$GPID" ] && kill -0 "$GPID" 2>/dev/null; then
  echo "No explicit ready-signal yet, but game is alive. Settling 12s more."
  sleep 12
fi

echo "=== last 25 log lines ==="
tail -n 25 "$LOG"

echo "=== sending G key to hide UI ==="
WID=$(DISPLAY=:99 xdotool search --name OpenKeeper 2>/dev/null | head -n 1)
echo "Window id: '$WID'"
if [ -n "$WID" ]; then
  DISPLAY=:99 xdotool key --window "$WID" g
  sleep 1
  echo "G key sent (hides UI for cleaner screenshot)"
fi

echo "=== aiming screenshot ==="
find "$SHOTS_DIR" -maxdepth 1 -name '*.png' -delete 2>/dev/null
find "$SCRATCH" -maxdepth 1 -name '*.png' -delete 2>/dev/null
rm -f /tmp/imp_shot.png /tmp/gs_shot.png /tmp/xwd_shot.png /tmp/ok.xwd

SHOT_FILE=""
METHOD=""
TRUNCATED_NOTE=""

# Returns 0 if $1 looks like an OK, non-truncated PNG.
valid_png () {
  local f="$1"
  [ -s "$f" ] || return 1
  [ "$(stat -c %s "$f" 2>/dev/null || echo 0)" -ge 10240 ] || return 1
  # Look for the IEND chunk; a PNG file without it is truncated.
  python3 - "$f" <<'PY'
import sys
with open(sys.argv[1], "rb") as fh:
    data = fh.read()
if len(data) < 12 or data[:8] != b"\x89PNG\r\n\x1a\n":
    sys.exit(2)
iend = data.rfind(b"IEND")
sys.exit(0 if (iend != -1 and (len(data) - iend) >= 8) else 3)
PY
}

attempt () {
  # attempt <tag> <description> <command...>
  local tag="$1"; shift
  local desc="$1"; shift
  echo "--- [$tag] $desc ---"
  "$@" || true
  if valid_png "/tmp/${tag}_shot.png"; then
    SHOT_FILE="/tmp/${tag}_shot.png"
    METHOD="$tag"
    echo "[$tag] produced a non-truncated PNG ($(stat -c %s "$SHOT_FILE") bytes)"
    return 0
  fi
  if [ -s "/tmp/${tag}_shot.png" ]; then
    echo "[$tag] produced a file but it is TRUNCATED (no IEND chunk)"
    TRUNCATED_NOTE="$TRUNCATED_NOTE [$tag]"
  else
    echo "[$tag] produced nothing"
  fi
  return 1
}

# (1) ImageMagick `import -window <WID>` — best quality under Xvfb.
if [ -n "$WID" ] && command -v import >/dev/null 2>&1; then
  if attempt imp "import -window $WID" \
       bash -c "DISPLAY=:99 import -window '$WID' /tmp/imp_shot.png"; then :; fi
fi

# (2) xdotool Print Screen -> JME ScreenshotAppState.
if [ -z "$SHOT_FILE" ] && [ -n "$WID" ]; then
  echo "--- [xdotool_Print] xdotool key --window $WID Print ---"
  DISPLAY=:99 xdotool key --window "$WID" Print
  sleep 3
  LATEST=$(ls -t "$SHOTS_DIR"/*.png 2>/dev/null | head -n 1)
  if [ -n "$LATEST" ] && valid_png "$LATEST"; then
    SHOT_FILE="$LATEST"
    METHOD="xdotool Print (JME ScreenshotAppState)"
    echo "[xdotool_Print] produced a non-truncated PNG ($(stat -c %s "$SHOT_FILE") bytes)"
  elif [ -n "$LATEST" ] && [ -s "$LATEST" ]; then
    echo "[xdotool_Print] produced a file but it is TRUNCATED"
    TRUNCATED_NOTE="$TRUNCATED_NOTE [xdotool_Print]"
  fi
fi

# (3) gnome-screenshot -w (frequently TRUNCATED under Xvfb).
if [ -z "$SHOT_FILE" ] && command -v gnome-screenshot >/dev/null 2>&1; then
  if attempt gs "gnome-screenshot -w -d 2 -f /tmp/gs_shot.png" \
       bash -c "DISPLAY=:99 gnome-screenshot -w -d 2 -f /tmp/gs_shot.png >/dev/null 2>&1"; then :; fi
fi

# (4) xwd -root fallback. Even whole-root captures can technically be
#     truncated during teardown - validate too.
if [ -z "$SHOT_FILE" ]; then
  echo "--- [xwd] xwd -display :99 -root + convert ---"
  xwd -display :99 -root -out /tmp/ok.xwd
  convert /tmp/ok.xwd /tmp/xwd_shot.png
  if valid_png /tmp/xwd_shot.png; then
    SHOT_FILE="/tmp/xwd_shot.png"
    METHOD="xwd -root"
    echo "[xwd] produced a non-truncated PNG"
  elif [ -s /tmp/xwd_shot.png ]; then
    echo "[xwd] produced a file but it is TRUNCATED"
    TRUNCATED_NOTE="$TRUNCATED_NOTE [xwd]"
  fi
fi

if [ -z "$SHOT_FILE" ] || [ ! -f "$SHOT_FILE" ]; then
  echo "ERROR: no non-truncated PNG produced by any method."
  echo "Truncations observed:$TRUNCATED_NOTE"
  echo "Last 25 log lines for diagnostics:"
  tail -n 25 "$LOG"
else
  echo
  echo "Capture method: $METHOD"
  echo "Raw shot: $SHOT_FILE ($(stat -c %s "$SHOT_FILE") bytes)"
  if [ -n "$TRUNCATED_NOTE" ]; then
    echo "Other attempts that wrote TRUNCATED files (not used):$TRUNCATED_NOTE"
  fi

  # Copy into the project so editor / file tools / git can see it.
  COPY="$SCRATCH/Main_$(date +%s)_${METHOD// /_}.png"
  cp "$SHOT_FILE" "$COPY"
  chown daytona:daytona "$COPY" 2>/dev/null || true

  echo "=== screenshots directory listing ==="
  echo "--- ~/.OpenKeeper/SCRSHOTS ---"
  ls -lt "$SHOTS_DIR/" 2>&1 | head -n 20
  echo "--- $SCRATCH ---"
  ls -lt "$SCRATCH/" 2>&1 | head -n 20
  echo
  echo "IN-PROJECT COPY: $COPY"

  echo "=== identify ==="
  identify "$COPY"

  echo "=== Pillow stats ==="
  python3 - <<PY
from PIL import Image, ImageStat, ImageFile
ImageFile.LOAD_TRUNCATED_IMAGES = True
im = Image.open("$COPY").convert("RGB")
W, H = im.size
print("size", im.size)
s = ImageStat.Stat(im)
print("mean RGB", s.mean)
print("stddev RGB", s.stddev)
print("extrema per channel", s.extrema)
b = 0.2126*s.mean[0] + 0.7152*s.mean[1] + 0.0722*s.mean[2]
print(f"brightness {b:.1f} / 255")

def stats(box, label):
    crop = im.crop(box)
    s2 = ImageStat.Stat(crop)
    b2 = 0.2126*s2.mean[0] + 0.7152*s2.mean[1] + 0.0722*s2.mean[2]
    print(f"{label:<22} mean=({s2.mean[0]:5.1f},{s2.mean[1]:5.1f},{s2.mean[2]:5.1f}) std={sum(s2.stddev)/3:5.1f} brightness={b2:5.1f}")

stats((0, 0, 260, H),         "Left rail")
stats((W-260, 0, W, H),       "Right rail")
stats((0, 0, W, 70),          "Top strip")
stats((0, H-100, W, H),       "Bottom strip")
stats((280, 90, W-280, H-110), "Gameplay center")

# Edge density proxy: for an in-game lit dungeon we expect higher stddev
# center than rails (vs. a flat menu/loading screen which is low everywhere).
import statistics
def edge(coord_box):
    crop = im.crop(coord_box)
    px = list(crop.getdata())
    edges = []
    for row_start in range(0, len(px) - crop.size[0], crop.size[0]):
        for x in range(crop.size[0] - 1):
            a = px[row_start + x]
            b = px[row_start + x + 1]
            edges.append(abs(a[0]-b[0]) + abs(a[1]-b[1]) + abs(a[2]-b[2]))
    return statistics.mean(edges) if edges else 0
print(f"left-rail   edge-density ~{edge((0, 0, 260, H)):.2f}")
print(f"center      edge-density ~{edge((280, 90, W-280, H-110)):.2f}")
print(f"right-rail  edge-density ~{edge((W-260, 0, W, H)):.2f}")
PY

  echo
  echo "=== chafa ANSI rendering (image -> terminal) ==="
  if command -v chafa >/dev/null 2>&1; then
    echo "--- chafa --size 120x40 --symbols all $COPY ---"
    chafa --size 120x40 --symbols all "$COPY" 2>&1 || true
    echo "--- chafa --size 160x40 --symbols ascii $COPY ---"
    chafa --size 160x40 --symbols ascii "$COPY" 2>&1 | head -n 42 || true
  else
    echo "(chafa not installed; install with: apt-get install -y chafa)"
    echo "Other ANSI renderers that work: jp2a, img2txt (caca-utils),"
    echo "viu, ascii-image-converter, tiv."
  fi
fi

echo "=== tearing down ==="
if [ -n "$GPID" ] && [[ "$GPID" =~ ^[0-9]+$ ]]; then
  kill "$GPID" 2>/dev/null
  sleep 2
  kill -9 "$GPID" 2>/dev/null
fi
pkill -f 'OpenKeeper-1.0.jar' 2>/dev/null
pkill -f 'java.*OpenKeeper' 2>/dev/null
kill "$XPID" 2>/dev/null
sleep 1
kill -9 "$XPID" 2>/dev/null
pkill -f 'Xvfb :99' 2>/dev/null
echo "=== done ==="

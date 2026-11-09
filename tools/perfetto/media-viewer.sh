#!/usr/bin/env bash
# Records opening and closing a photo in the media viewer on a phone connected with adb (docs/PERFORMANCE.md §9,
# "Opening and closing media").
#
#   tools/perfetto/media-viewer.sh [output directory] [package] [openings] [tap inset dp]
#
# Install a profileable build first and sign in (`./gradlew :app:installFossBenchmarkRelease`), open a conversation
# with a photo among its newest messages (Saved Messages, a photo without a caption, sent by yourself) and keep it on
# screen, the phone unlocked. The script finds the newest photo's bubble on screen (its description names a photo),
# then, while one Perfetto trace records (tools/perfetto/media-viewer.pbtxt), taps it near its end (an outgoing
# bubble's photo; pass a larger tap inset or tap a bit further from the end for a wide bubble), waits for the viewer,
# presses back and waits again, [openings] times (default 10).
#
# It writes media-viewer.perfetto-trace and device.txt into the output directory (default: ./media-viewer-<date>),
# and summary.txt where Perfetto's Python package is installed (pip install perfetto; summarize.py with
# media-viewer.sql). Open the trace in https://ui.perfetto.dev (read in the browser, not uploaded) to look at the
# frames inside the overlayEnter and overlayExit sections. The package defaults to dev.kyralabs.pingogram.
set -euo pipefail

out="${1:-media-viewer-$(date +%Y%m%d-%H%M%S)}"
package="${2:-dev.kyralabs.pingogram}"
openings="${3:-10}"
tap_inset_dp="${4:-48}"
here="$(cd "$(dirname "$0")" && pwd)"
device_trace=/data/misc/perfetto-traces/pingogram-media-viewer.perfetto-trace
device_config=/data/local/tmp/pingogram-media-viewer.pbtxt
device_dump=/data/local/tmp/pingogram-ui.xml
# Each opening and each closing gets this long to finish its transition and settle.
settle=2

command -v adb >/dev/null || { echo "adb is not on the PATH." >&2; exit 1; }
adb get-state >/dev/null || { echo "No device: connect the phone with USB debugging on." >&2; exit 1; }
adb shell pidof "$package" >/dev/null || { echo "$package is not running: open a conversation with a photo first." >&2; exit 1; }
mkdir -p "$out"

{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell dumpsys package "$package" | grep -m 1 versionName || true
  adb shell dumpsys package "$package" | grep -m 1 -o -E 'PROFILEABLE[A-Z_]*|DEBUGGABLE' || echo "profileable: not shown (app sections need a profileable build)"
  adb shell wm size | tr -d '\r'
  adb shell wm density | tr -d '\r'
  # The display's refresh rate the phone chose, and whether it is on battery: both change frame times.
  echo "refresh rate: $(adb shell dumpsys display | grep -m 1 -o -E 'mRefreshRate=[0-9.]+|fps=[0-9.]+' | tr -d '\r')"
  # Accessibility services make Compose work out its semantics in every frame: which ones run, by package name.
  echo "accessibility services: $(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
  echo "animator duration scale: $(adb shell settings get global animator_duration_scale | tr -d '\r')"
} > "$out/device.txt"

# The newest photo's bubble on screen: the lowest node whose description names a photo ("Ada, 9:41 AM: Photo…"), from
# a dump of the window's accessibility tree, as UiAutomator sees it.
adb shell uiautomator dump "$device_dump" >/dev/null
adb pull "$device_dump" "$out/ui.xml" >/dev/null
adb shell rm -f "$device_dump"
bounds="$(grep -o '<node[^>]*content-desc="[^"]*: Photo[^"]*"[^>]*>' "$out/ui.xml" |
  sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' | sort -k2 -n | tail -1)"
[ -n "$bounds" ] || { echo "No photo bubble on screen (ui.xml has no node described as a photo)." >&2; exit 1; }
read -r left top right bottom <<< "$bounds"
density="$(adb shell wm density | tr -d '\r' | grep -o -E '[0-9]+' | head -1)"
inset=$(( tap_inset_dp * density / 160 ))
tap_x=$(( right - inset ))
tap_y=$(( top + (bottom - top) / 3 ))
rm -f "$out/ui.xml"
echo "Photo bubble at [$left,$top][$right,$bottom]; tapping ($tap_x, $tap_y)."

duration_ms=$(( (openings * 2 * (settle + 1) + 10) * 1000 ))
echo "Recording $openings openings and closings (about $(( duration_ms / 1000 )) s). Leave the phone alone."
sed -e 's/dev\.kyralabs\.pingogram/'"$package"'/' -e "s/^duration_ms: .*/duration_ms: $duration_ms/" \
  "$here/media-viewer.pbtxt" > "$out/media-viewer.pbtxt"
adb push "$out/media-viewer.pbtxt" "$device_config" >/dev/null
# In the background on the phone, so it records while this script drives the app; it stops after duration_ms.
adb shell "cat $device_config | perfetto --background --txt -c - -o $device_trace" >/dev/null
sleep 3

for i in $(seq 1 "$openings"); do
  adb shell input tap "$tap_x" "$tap_y"
  sleep "$settle"
  adb shell input keyevent KEYCODE_BACK
  sleep "$settle"
done

echo "Waiting for the trace to end."
while adb shell pidof perfetto >/dev/null 2>&1; do sleep 2; done
adb pull "$device_trace" "$out/media-viewer.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace" "$device_config"
# The trace is tens of MB; its summary is a few KB of numbers, which is what to send.
python="$(command -v python3 || command -v python || true)"
if [ -n "$python" ] && "$python" -c "import perfetto.trace_processor" 2>/dev/null; then
  "$python" "$here/summarize.py" "$out/media-viewer.perfetto-trace" "$here/media-viewer.sql" > "$out/summary.txt"
  echo "Done: $out (send device.txt and summary.txt)."
else
  echo "Done: $out. To summarize the trace: pip install perfetto, then"
  echo "  python3 $here/summarize.py $out/media-viewer.perfetto-trace $here/media-viewer.sql > $out/summary.txt"
  echo "and send device.txt and summary.txt."
fi

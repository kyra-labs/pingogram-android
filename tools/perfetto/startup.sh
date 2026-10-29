#!/usr/bin/env bash
# Records Pingogram's cold and warm starts on a phone connected with adb (docs/PERFORMANCE.md §2, §9).
#
#   tools/perfetto/startup.sh [output directory] [package] [starts]
#
# Install a profileable build first and sign in (`./gradlew :app:installFossBenchmarkRelease`), then run this with the
# phone unlocked and the screen on. It records one Perfetto trace (tools/perfetto/startup.pbtxt) while it starts the app
# [starts] times cold (the process stopped first) and as many times warm (the process kept, the activity created again),
# and saves what `am start -W` reports for each start. A warm start keeps the process and starts the activity with
# NEW_TASK | CLEAR_TASK, which destroys it and creates it again in the running process.
#
# It writes startup.perfetto-trace, starts.txt and device.txt into the output directory (default:
# ./startup-<date>), and summary.txt where Perfetto's Python package is installed (pip install perfetto; summarize.py).
# Open the trace in https://ui.perfetto.dev (read in the browser, not uploaded), or query it with tools/perfetto/startup.sql. The package defaults to dev.kyralabs.pingogram, the starts to 5.
set -euo pipefail

out="${1:-startup-$(date +%Y%m%d-%H%M%S)}"
package="${2:-dev.kyralabs.pingogram}"
starts="${3:-5}"
here="$(cd "$(dirname "$0")" && pwd)"
device_trace=/data/misc/perfetto-traces/pingogram-startup.perfetto-trace
device_config=/data/local/tmp/pingogram-startup.pbtxt
activity="$package/dev.kyralabs.pingogram.MainActivity"
# Each start gets this long to finish drawing and settle before the next one.
settle=6

command -v adb >/dev/null || { echo "adb is not on the PATH." >&2; exit 1; }
adb get-state >/dev/null || { echo "No device: connect the phone with USB debugging on." >&2; exit 1; }
adb shell pm path "$package" >/dev/null || { echo "$package is not installed." >&2; exit 1; }
mkdir -p "$out"

{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell dumpsys package "$package" | grep -m 1 versionName || true
  adb shell dumpsys package "$package" | grep -m 1 -o "PROFILEABLE[A-Z_]*\|DEBUGGABLE" || echo "profileable: not shown (app sections need a profileable build)"
  # Accessibility services make Compose work out its semantics in every frame: which ones run, by package name.
  echo "accessibility services: $(adb shell settings get secure enabled_accessibility_services | tr -d '\r')"
} > "$out/device.txt"

home() { adb shell input keyevent KEYCODE_HOME; sleep 2; }

duration_ms=$(( (starts * 2 * (settle + 4) + 15) * 1000 ))
echo "Recording $starts cold and $starts warm starts (about $(( duration_ms / 1000 )) s). Leave the phone alone."
sed -e 's/dev\.kyralabs\.pingogram/'"$package"'/' -e "s/^duration_ms: .*/duration_ms: $duration_ms/" \
  "$here/startup.pbtxt" > "$out/startup.pbtxt"
adb push "$out/startup.pbtxt" "$device_config" >/dev/null
# In the background on the phone, so it records while this script starts the app; it stops after duration_ms.
adb shell "cat $device_config | perfetto --background --txt -c - -o $device_trace" >/dev/null
sleep 3

: > "$out/starts.txt"
for i in $(seq 1 "$starts"); do
  home
  adb shell am force-stop "$package"
  sleep 1
  echo "=== cold $i ===" >> "$out/starts.txt"
  adb shell am start-activity -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity" \
    >> "$out/starts.txt"
  sleep "$settle"
done

for i in $(seq 1 "$starts"); do
  # The process stays; NEW_TASK | CLEAR_TASK (0x10008000) destroys the activity and creates it again, a warm start.
  home
  echo "=== warm $i ===" >> "$out/starts.txt"
  adb shell am start-activity -W -f 0x10008000 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "$activity" >> "$out/starts.txt"
  sleep "$settle"
done
home

echo "Waiting for the trace to end."
while adb shell pidof perfetto >/dev/null 2>&1; do sleep 2; done
adb pull "$device_trace" "$out/startup.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace" "$device_config"
# The trace is hundreds of MB; its summary is a few KB of numbers, which is what to send.
python="$(command -v python3 || command -v python || true)"
if [ -n "$python" ] && "$python" -c "import perfetto.trace_processor" 2>/dev/null; then
  "$python" "$here/summarize.py" "$out/startup.perfetto-trace" "$here/startup.sql" > "$out/summary.txt"
  echo "Done: $out (send device.txt, starts.txt and summary.txt)."
else
  echo "Done: $out. To summarize the trace: pip install perfetto, then"
  echo "  python3 $here/summarize.py $out/startup.perfetto-trace > $out/summary.txt"
  echo "and send device.txt, starts.txt and summary.txt."
fi

#!/usr/bin/env bash
# Records Pingogram's cold and warm starts on a phone connected with adb (docs/PERFORMANCE.md §2, §9).
#
#   tools/perfetto/startup.sh [output directory] [package] [starts]
#
# Install a profileable build first and sign in (`./gradlew :app:installFossBenchmarkRelease`), then run this with the
# phone unlocked and the screen on. It records one Perfetto trace (tools/perfetto/startup.pbtxt) while it starts the app
# [starts] times cold (the process stopped first) and as many times warm (the process kept, the activity created again),
# and saves what `am start -W` reports for each start. Warm starts use Android's developer option "Don't keep
# activities" for their duration, so leaving the app destroys its activity as Macrobenchmark's warm starts do; the option
# is put back as it was, also when the script stops early.
#
# It writes startup.perfetto-trace, starts.txt and device.txt into the output directory (default:
# ./startup-<date>). Open the trace in https://ui.perfetto.dev (read in the browser, not uploaded) or summarize it with
# tools/perfetto/startup.sql. The package defaults to dev.kyralabs.pingogram, the starts to 5.
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
} > "$out/device.txt"

keep_activities="$(adb shell settings get global always_finish_activities | tr -d '\r')"
restore() { adb shell settings put global always_finish_activities "${keep_activities/null/0}" >/dev/null; }
trap restore EXIT

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

adb shell settings put global always_finish_activities 1 >/dev/null
for i in $(seq 1 "$starts"); do
  # Leaving the app destroys its activity ("Don't keep activities"); the process stays.
  home
  echo "=== warm $i ===" >> "$out/starts.txt"
  adb shell am start-activity -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity" \
    >> "$out/starts.txt"
  sleep "$settle"
done
restore
home

echo "Waiting for the trace to end."
while adb shell pidof perfetto >/dev/null 2>&1; do sleep 2; done
adb pull "$device_trace" "$out/startup.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace" "$device_config"
echo "Done: $out (send device.txt, starts.txt and startup.perfetto-trace)."

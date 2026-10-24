#!/usr/bin/env bash
# Records a video call on a phone connected with adb (docs/PERFORMANCE.md §9, "Video calls").
#
#   tools/perfetto/video-call.sh [output directory] [package]
#
# Start the video call first (both cameras on, the call screen in front), then run this. For 5 minutes it records a
# Perfetto trace with tools/perfetto/video-call.pbtxt and, once a minute, `dumpsys meminfo` of Pingogram (PSS, the
# unit of the memory budgets). It writes video-call.perfetto-trace and meminfo.txt into the output directory
# (default: ./video-call-<date>). Open the trace in https://ui.perfetto.dev (it is read in the browser, not uploaded)
# or summarize it with tools/perfetto/video-call.sql. The package defaults to dev.kyralabs.pingogram.
set -euo pipefail

out="${1:-video-call-$(date +%Y%m%d-%H%M%S)}"
package="${2:-dev.kyralabs.pingogram}"
here="$(cd "$(dirname "$0")" && pwd)"
device_trace=/data/misc/perfetto-traces/pingogram-video-call.perfetto-trace

command -v adb >/dev/null || { echo "adb is not on the PATH." >&2; exit 1; }
adb get-state >/dev/null || { echo "No device: connect the phone with USB debugging on." >&2; exit 1; }
adb shell pidof "$package" >/dev/null || { echo "$package is not running: start the video call first." >&2; exit 1; }
mkdir -p "$out"

{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell dumpsys package "$package" | grep -m 1 versionName || true
} > "$out/device.txt"

echo "Recording for 5 minutes. Keep the call going with the call screen in front."
sed 's/dev\.kyralabs\.pingogram/'"$package"'/' "$here/video-call.pbtxt" |
  adb shell perfetto --txt -c - -o "$device_trace" &
perfetto_pid=$!

: > "$out/meminfo.txt"
for minute in 0 1 2 3 4; do
  echo "=== minute $minute ===" >> "$out/meminfo.txt"
  adb shell dumpsys meminfo "$package" >> "$out/meminfo.txt"
  sleep 60
done
echo "=== minute 5 ===" >> "$out/meminfo.txt"
adb shell dumpsys meminfo "$package" >> "$out/meminfo.txt"

wait "$perfetto_pid"
adb pull "$device_trace" "$out/video-call.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace"
echo "Done: $out (send device.txt, meminfo.txt and video-call.perfetto-trace)."

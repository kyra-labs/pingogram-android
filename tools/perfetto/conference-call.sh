#!/usr/bin/env bash
# Records 3 minutes of a conference call on a phone connected with adb (docs/PERFORMANCE.md §9, "Conference calls").
#
#   tools/perfetto/conference-call.sh [output directory] [package]
#
# Join the conference call first (a t.me/call link, three others speaking, one with the camera on) and keep its screen
# in front, then run this. It records a Perfetto trace with tools/perfetto/conference-call.pbtxt and writes
# conference-call.perfetto-trace and device.txt into the output directory (default: ./conference-call-<date>), and
# summary.txt where Perfetto's Python package is installed (pip install perfetto; summarize.py). The package defaults
# to dev.kyralabs.pingogram.
set -euo pipefail

out="${1:-conference-call-$(date +%Y%m%d-%H%M%S)}"
package="${2:-dev.kyralabs.pingogram}"
here="$(cd "$(dirname "$0")" && pwd)"
device_trace=/data/misc/perfetto-traces/pingogram-conference-call.perfetto-trace

command -v adb >/dev/null || { echo "adb is not on the PATH." >&2; exit 1; }
adb get-state >/dev/null || { echo "No device: connect the phone with USB debugging on." >&2; exit 1; }
adb shell pidof "$package" >/dev/null || { echo "$package is not running: join the conference call first." >&2; exit 1; }
mkdir -p "$out"

{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell dumpsys package "$package" | grep -m 1 versionName || true
  adb shell dumpsys package "$package" | grep -m 1 -o -E 'PROFILEABLE[A-Z_]*|DEBUGGABLE' || true
} > "$out/device.txt"

echo "Recording for 3 minutes. Keep the call going with its screen in front."
sed 's/dev\.kyralabs\.pingogram/'"$package"'/' "$here/conference-call.pbtxt" |
  adb shell perfetto --txt -c - -o "$device_trace"
adb pull "$device_trace" "$out/conference-call.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace"

if python3 -c "import perfetto.trace_processor" 2>/dev/null; then
  python3 "$here/summarize.py" "$out/conference-call.perfetto-trace" "$here/conference-call.sql" > "$out/summary.txt"
  echo "Done: $out (send device.txt and summary.txt)."
else
  echo "Done: $out. To summarize the trace: pip install perfetto, then"
  echo "  python3 tools/perfetto/summarize.py $out/conference-call.perfetto-trace tools/perfetto/conference-call.sql > $out/summary.txt"
  echo "and send device.txt and summary.txt."
fi

#!/usr/bin/env bash
# Records the measure-first sections while you use the app, on a phone connected with adb (docs/PERFORMANCE.md §9,
# "Measure first"): the passcode lock's hash, a voice note's resampling for transcription, and QR decoding.
#
#   tools/perfetto/measure-first.sh [output directory] [package] [seconds]
#
# Install a profileable build first and sign in (`./gradlew :app:installFossBenchmarkRelease`), and open the app, the
# phone unlocked. While one Perfetto trace records (tools/perfetto/measure-first.pbtxt) for [seconds] (default 120),
# do on the phone what the script prints: enter the passcode a few times, transcribe a voice message, scan a QR code
# with the camera and from an image.
#
# It writes measure-first.perfetto-trace and device.txt into the output directory (default: ./measure-first-<date>),
# and summary.txt where Perfetto's Python package is installed (pip install perfetto; summarize.py with
# measure-first.sql). The package defaults to dev.kyralabs.pingogram.
set -euo pipefail

out="${1:-measure-first-$(date +%Y%m%d-%H%M%S)}"
package="${2:-dev.kyralabs.pingogram}"
seconds="${3:-120}"
here="$(cd "$(dirname "$0")" && pwd)"
device_trace=/data/misc/perfetto-traces/pingogram-measure-first.perfetto-trace
device_config=/data/local/tmp/pingogram-measure-first.pbtxt

command -v adb >/dev/null || { echo "adb is not on the PATH." >&2; exit 1; }
adb get-state >/dev/null || { echo "No device: connect the phone with USB debugging on." >&2; exit 1; }
adb shell pidof "$package" >/dev/null || { echo "$package is not running: open it first." >&2; exit 1; }
mkdir -p "$out"

{
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell getprop ro.soc.model
  adb shell dumpsys package "$package" | grep -m 1 versionName || true
  adb shell dumpsys package "$package" | grep -m 1 -o -E 'PROFILEABLE[A-Z_]*|DEBUGGABLE' || echo "profileable: not shown (app sections need a profileable build)"
  # On battery saver the cores run slower, and every section with them.
  echo "battery saver: $(adb shell settings get global low_power | tr -d '\r')"
} > "$out/device.txt"

sed -e 's/dev\.kyralabs\.pingogram/'"$package"'/' -e "s/^duration_ms: .*/duration_ms: $(( seconds * 1000 ))/" \
  "$here/measure-first.pbtxt" > "$out/measure-first.pbtxt"
adb push "$out/measure-first.pbtxt" "$device_config" >/dev/null
# In the background on the phone, so it records while you use the app; it stops after duration_ms.
adb shell "cat $device_config | perfetto --background --txt -c - -o $device_trace" >/dev/null

cat <<STEPS
Recording for $seconds s. On the phone, in this order (skip what the account has not set up):
  1. Passcode: with Settings, Privacy and security, Passcode lock on and Auto-lock "Immediately", switch to another
     app and back and enter the passcode; 3 times.
  2. Transcription: in a chat, tap "Transcribe voice message" on a voice message of 30 s or longer.
  3. QR, camera: Contacts, Scan; hold a QR code in front of the camera for 10 s.
  4. QR, image: on the same page, "Scan from image", and pick an image with a QR code.
STEPS

while adb shell pidof perfetto >/dev/null 2>&1; do sleep 2; done
adb pull "$device_trace" "$out/measure-first.perfetto-trace" >/dev/null
adb shell rm -f "$device_trace" "$device_config"
python="$(command -v python3 || command -v python || true)"
if [ -n "$python" ] && "$python" -c "import perfetto.trace_processor" 2>/dev/null; then
  "$python" "$here/summarize.py" "$out/measure-first.perfetto-trace" "$here/measure-first.sql" > "$out/summary.txt"
  echo "Done: $out (send device.txt and summary.txt)."
else
  echo "Done: $out. To summarize the trace: pip install perfetto, then"
  echo "  python3 $here/summarize.py $out/measure-first.perfetto-trace $here/measure-first.sql > $out/summary.txt"
  echo "and send device.txt and summary.txt."
fi

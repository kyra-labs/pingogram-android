#!/usr/bin/env bash
# Writes the store images of fastlane/metadata/android/en-US/images/ (F-Droid, IzzyOnDroid; docs/RELEASE.md):
# the 512 px icon and the 1024 × 500 feature graphic, rendered from the owner's artwork in art/ with headless
# Chromium, and the phone and tablet screenshots, copied from docs/screenshots/ (the README's, made from the
# Roborazzi goldens by tools/readme/screenshots.sh, so run that first after re-recording goldens).
# Usage: tools/fastlane/images.sh   (CHROMIUM=/path/to/chrome if `chromium` is not on the PATH)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
images="$root/fastlane/metadata/android/en-US/images"
chromium="${CHROMIUM:-chromium}"
command -v "$chromium" >/dev/null || {
  echo "Headless Chromium is required: set CHROMIUM to its binary" >&2
  exit 1
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Renders the HTML page $1 at $2 × $3 px into $4.
render() {
  # No sandbox: it renders only these local pages, and the sandbox refuses to run as root (CI containers).
  "$chromium" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --default-background-color=00000000 --window-size="$2,$3" --screenshot="$4" "file://$1" >/dev/null 2>&1
}

# The launcher icon: its white background and the mark on the 108-unit adaptive icon grid, full bleed (stores round it).
cat >"$work/icon.html" <<HTML
<html><body style="margin:0">
<img src="$root/art/ic_launcher_background.svg" style="position:absolute;width:512px;height:512px">
<img src="$root/art/ic_launcher_foreground.svg" style="position:absolute;width:512px;height:512px">
</body></html>
HTML

# The feature graphic: the logo on the brand scheme's light background (core/ui Color.kt, background).
cat >"$work/feature.html" <<HTML
<html><body style="margin:0;width:1024px;height:500px;background:#FAF8FF;display:flex;align-items:center;justify-content:center">
<img src="$root/art/logo_full.svg" style="width:640px">
</body></html>
HTML

mkdir -p "$images/phoneScreenshots" "$images/tenInchScreenshots"
render "$work/icon.html" 512 512 "$images/icon.png"
render "$work/feature.html" 1024 500 "$images/featureGraphic.png"

# Light screenshots, in the order a store shows them.
shots=(welcome chats chat reactions composer search settings appearance)
rm -f "$images/phoneScreenshots"/*.png "$images/tenInchScreenshots"/*.png
for i in "${!shots[@]}"; do
  cp "$root/docs/screenshots/${shots[$i]}-light.png" "$images/phoneScreenshots/$((i + 1)).png"
done
cp "$root/docs/screenshots/tablet-light.png" "$images/tenInchScreenshots/1.png"

du -ch "$images"/*.png "$images"/*/*.png | tail -1

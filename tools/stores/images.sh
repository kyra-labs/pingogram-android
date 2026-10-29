#!/usr/bin/env bash
# Writes the listing images of Samsung Galaxy Store and Huawei AppGallery (docs/STORES.md §5) into build/stores/:
#   galaxy/icon.png          512 × 512 PNG, no transparency
#   galaxy/cover.png         1200 × 675 cover image (Galaxy Store asks for it in some categories and countries only)
#   galaxy/screenshots/      the 8 light phone screenshots, padded to exactly 9:16 (666 × 1184), numbered in order
#   appgallery/icon.png      512 × 512 PNG, square, no rounded corners or padding, no transparency
#   appgallery/screenshots/  the same 8 screenshots (AppGallery asks for 9:16)
# The icon is the store icon of fastlane/metadata/android/en-US/images/ (tools/fastlane/images.sh, from art/); the
# screenshots are docs/screenshots/ (tools/readme/screenshots.sh, from the goldens), padded by tools/play/screenshots.py
# without scaling. The cover is rendered from art/ with headless Chromium, as tools/fastlane/images.sh renders the
# feature graphic; without Chromium it is skipped.
# Usage: tools/stores/images.sh   (CHROMIUM=/path/to/chrome if `chromium` is not on the PATH). Needs Python 3 and Pillow.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="$root/build/stores"
chromium="${CHROMIUM:-chromium}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

rm -rf "$out/galaxy" "$out/appgallery"
mkdir -p "$out/galaxy/screenshots" "$out/appgallery/screenshots"

# Screenshots: exactly 9:16, light, in the order a store shows them (as tools/fastlane/images.sh orders them).
python3 "$root/tools/play/screenshots.py" --exact "$root/docs/screenshots" "$work/shots" >/dev/null
shots=(welcome chats chat reactions composer search settings appearance)
for i in "${!shots[@]}"; do
  for store in galaxy appgallery; do
    cp "$work/shots/${shots[$i]}-light.png" "$out/$store/screenshots/$((i + 1)).png"
  done
done

# The icon on white, without an alpha channel, which some store forms refuse.
python3 - "$root/fastlane/metadata/android/en-US/images/icon.png" "$out" <<'PYTHON'
import sys
from PIL import Image

source, out = sys.argv[1], sys.argv[2]
with Image.open(source) as icon:
    icon = icon.convert("RGBA")
    flat = Image.new("RGB", icon.size, (255, 255, 255))
    flat.paste(icon, mask=icon.getchannel("A"))
    if flat.size != (512, 512):
        raise SystemExit(f"{source} is {flat.size}, not 512 x 512: run tools/fastlane/images.sh")
    for store in ("galaxy", "appgallery"):
        flat.save(f"{out}/{store}/icon.png")
PYTHON

# Galaxy Store's cover image: the logo on the brand scheme's light background, as the feature graphic.
if command -v "$chromium" >/dev/null; then
  cat >"$work/cover.html" <<HTML
<html><body style="margin:0;width:1200px;height:675px;background:#FAF8FF;display:flex;align-items:center;justify-content:center">
<img src="$root/art/logo_full.svg" style="width:720px">
</body></html>
HTML
  # No sandbox: it renders only this local page, and the sandbox refuses to run as root (CI containers).
  "$chromium" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size=1200,675 --screenshot="$work/cover.png" "file://$work/cover.html" >/dev/null 2>&1
  python3 -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").save(sys.argv[2])' \
    "$work/cover.png" "$out/galaxy/cover.png"
else
  echo "No Chromium (set CHROMIUM): galaxy/cover.png skipped" >&2
fi

find "$out" -name '*.png' | sort | while read -r f; do
  python3 -c 'import sys; from PIL import Image; im = Image.open(sys.argv[1]); print(f"{im.size[0]}x{im.size[1]} {im.mode}")' "$f" |
    sed "s|^|${f#"$root/"}: |"
done

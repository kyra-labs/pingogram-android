#!/usr/bin/env bash
# Copies chosen Roborazzi goldens into docs/screenshots/ for the README, downscaled and optimized.
# Re-run after re-recording goldens: tools/readme/screenshots.sh (needs ImageMagick's `convert`).
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="$root/docs/screenshots"
phone_width=540  # compact goldens are 1233 px wide (411 dp at xxhdpi)
wide_width=1080  # expanded goldens are 2520 px wide (840 dp), same density

auth="feature/auth/src/test/snapshots/dev.kyralabs.pingogram.feature.auth.ui"
chats="feature/chats/src/test/snapshots/dev.kyralabs.pingogram.feature.chats.ui"
chat="feature/chat/src/test/snapshots/dev.kyralabs.pingogram.feature.chat.ui"
settings="feature/settings/src/test/snapshots/dev.kyralabs.pingogram.feature.settings.ui"
app="app/src/test/snapshots/dev.kyralabs.pingogram.ui"

# output name | width | golden (relative to the repository root)
shots=(
  "welcome-light|$phone_width|$auth.AuthScreenScreenshotTest.welcome.light.png"
  "welcome-dark|$phone_width|$auth.AuthScreenScreenshotTest.welcome.dark.png"
  "chats-light|$phone_width|$chats.ChatListScreenScreenshotTest.contentWithFolders.light.png"
  "chats-dark|$phone_width|$chats.ChatListScreenScreenshotTest.contentWithFolders.dark.png"
  "chat-light|$phone_width|$chat.ChatScreenScreenshotTest.groupChat.light.png"
  "chat-dark|$phone_width|$chat.ChatScreenScreenshotTest.groupChat.dark.png"
  "reactions-light|$phone_width|$chat.ChatScreenScreenshotTest.emojiAndReactions.light.png"
  "reactions-dark|$phone_width|$chat.ChatScreenScreenshotTest.emojiAndReactions.dark.png"
  "composer-light|$phone_width|$chat.composer.ComposerScreenshotTest.formatted.light.png"
  "composer-dark|$phone_width|$chat.composer.ComposerScreenshotTest.formatted.dark.png"
  "search-light|$phone_width|$chats.SearchScreenScreenshotTest.resultsLight.png"
  "search-dark|$phone_width|$chats.SearchScreenScreenshotTest.resultsDark.png"
  "settings-light|$phone_width|$settings.SettingsScreenScreenshotTest.light.png"
  "settings-dark|$phone_width|$settings.SettingsScreenScreenshotTest.dark.png"
  "appearance-light|$phone_width|$settings.AppearanceScreenScreenshotTest.light.png"
  "appearance-dark|$phone_width|$settings.AppearanceScreenScreenshotTest.dark.png"
  "tablet-light|$wide_width|$app.ChatsListDetailScreenshotTest.chatOpenLight.png"
  "tablet-dark|$wide_width|$app.ChatsListDetailScreenshotTest.chatOpenDark.png"
)

command -v convert >/dev/null || {
  echo "ImageMagick's 'convert' is required" >&2
  exit 1
}

for shot in "${shots[@]}"; do
  IFS='|' read -r _ _ golden <<<"$shot"
  [[ -f "$root/$golden" ]] || {
    echo "Missing golden: $golden" >&2
    exit 1
  }
done

mkdir -p "$out"
rm -f "$out"/*.png

for shot in "${shots[@]}"; do
  IFS='|' read -r name width golden <<<"$shot"
  # Lanczos downscale, then a 256-color palette without dithering (the flat UI colors stay clean),
  # no metadata, maximum zlib compression.
  convert "$root/$golden" -filter Lanczos -resize "${width}x" -strip +dither -colors 256 \
    -define png:compression-level=9 -define png:exclude-chunks=all \
    "PNG8:$out/$name.png"
  echo "$name.png $(du -k "$out/$name.png" | cut -f1) KB"
done

echo "Total: $(du -ch "$out"/*.png | tail -1 | cut -f1)"

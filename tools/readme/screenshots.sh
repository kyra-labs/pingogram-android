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
contacts="feature/contacts/src/test/snapshots/dev.kyralabs.pingogram.feature.contacts.ui"
app="app/src/test/snapshots/dev.kyralabs.pingogram.ui"

# output name | width | golden (relative to the repository root)
shots=(
  "chats-light|$phone_width|$chats.ChatListScreenScreenshotTest.contentWithFolders.light.png"
  "chats-dark|$phone_width|$chats.ChatListScreenScreenshotTest.contentWithFolders.dark.png"
  "wallpaper-chat-light|$phone_width|$chat.ChatWallpaperColorsScreenshotTest.warmFill.light.png"
  "wallpaper-chat-dark|$phone_width|$chat.ChatWallpaperColorsScreenshotTest.warmFill.dark.png"
  "chat-light|$phone_width|$chat.ChatLookScreenshotTest.group.light.png"
  "chat-dark|$phone_width|$chat.ChatLookScreenshotTest.group.dark.png"
  "channel-light|$phone_width|$chat.ChatLookScreenshotTest.channelPosts.light.png"
  "channel-dark|$phone_width|$chat.ChatLookScreenshotTest.channelPosts.dark.png"
  "wallpaper-light|$phone_width|$settings.wallpaper.WallpaperPageScreenshotTest.accountLight.png"
  "wallpaper-dark|$phone_width|$settings.wallpaper.WallpaperPageScreenshotTest.accountDark.png"
  "search-light|$phone_width|$chats.SearchScreenScreenshotTest.resultsLight.png"
  "search-dark|$phone_width|$chats.SearchScreenScreenshotTest.resultsDark.png"
  "contacts-light|$phone_width|$contacts.ContactsTabScreenshotTest.light.png"
  "contacts-dark|$phone_width|$contacts.ContactsTabScreenshotTest.dark.png"
  "settings-light|$phone_width|$settings.SettingsScreenScreenshotTest.light.png"
  "settings-dark|$phone_width|$settings.SettingsScreenScreenshotTest.dark.png"
  "appearance-light|$phone_width|$settings.AppearanceScreenScreenshotTest.light.png"
  "appearance-dark|$phone_width|$settings.AppearanceScreenScreenshotTest.dark.png"
  "welcome-light|$phone_width|$auth.AuthScreenScreenshotTest.welcome.light.png"
  "welcome-dark|$phone_width|$auth.AuthScreenScreenshotTest.welcome.dark.png"
  "tablet-light|$wide_width|$app.ChatsListDetailScreenshotTest.chatOpenLight.png"
  "tablet-dark|$wide_width|$app.ChatsListDetailScreenshotTest.chatOpenDark.png"
)

# The banner at the top of the README: these screens side by side with rounded corners and a soft shadow, once per
# theme (hero-light.webp, hero-dark.webp).
hero=(chats wallpaper-chat chat settings)
hero_width=420  # each screen's width in the banner
hero_gap=36
hero_radius=44

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
rm -f "$out"/*.png "$out"/*.webp

for shot in "${shots[@]}"; do
  IFS='|' read -r name width golden <<<"$shot"
  # Lanczos downscale, then a 256-color palette without dithering (the flat UI colors stay clean),
  # no metadata, maximum zlib compression.
  convert "$root/$golden" -filter Lanczos -resize "${width}x" -strip +dither -colors 256 \
    -define png:compression-level=9 -define png:exclude-chunks=all \
    "PNG8:$out/$name.png"
  echo "$name.png $(du -k "$out/$name.png" | cut -f1) KB"
done

for theme in light dark; do
  # A hairline outline keeps each screen's edge visible on GitHub's page of the same theme.
  if [[ "$theme" == dark ]]; then outline="rgba(255,255,255,0.16)"; else outline="rgba(0,0,0,0.10)"; fi
  frames=()
  for screen in "${hero[@]}"; do
    golden=""
    for shot in "${shots[@]}"; do
      IFS='|' read -r name _ path <<<"$shot"
      [[ "$name" == "$screen-$theme" ]] && golden="$path"
    done
    frame="$(mktemp --suffix=.png)"
    height=$(convert "$root/$golden" -resize "${hero_width}x" -format '%h' info:)
    # Rounded corners, then a soft shadow on a transparent background.
    convert "$root/$golden" -filter Lanczos -resize "${hero_width}x" -alpha set \
      \( -size "${hero_width}x${height}" xc:none -fill white \
      -draw "roundrectangle 0,0,$((hero_width - 1)),$((height - 1)),$hero_radius,$hero_radius" \) \
      -compose DstIn -composite -compose Over -fill none -stroke "$outline" -strokewidth 2 \
      -draw "roundrectangle 1,1,$((hero_width - 2)),$((height - 2)),$hero_radius,$hero_radius" \
      \( +clone -background black -shadow 28x14+0+10 \) +swap -background none -compose Over -layers merge +repage \
      "$frame"
    frames+=("$frame")
  done
  args=()
  for i in "${!frames[@]}"; do
    ((i > 0)) && args+=(-size "${hero_gap}x1" xc:none)
    args+=("${frames[$i]}")
  done
  # WebP keeps the soft shadow's transparency at a fraction of a PNG's size.
  convert -background none "${args[@]}" -gravity center +append -strip \
    -quality 88 -define webp:alpha-quality=100 -define webp:method=6 "$out/hero-$theme.webp"
  rm -f "${frames[@]}"
  echo "hero-$theme.webp $(du -k "$out/hero-$theme.webp" | cut -f1) KB"
done

echo "Total: $(du -ch "$out"/*.png "$out"/*.webp | tail -1 | cut -f1)"

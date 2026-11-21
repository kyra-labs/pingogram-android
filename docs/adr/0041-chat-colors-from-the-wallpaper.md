# 0041. Chat colors from the wallpaper

- Status: accepted (owner, 2026-11-21)
- Builds on the chat wallpapers of 2026-11-20 (`docs/TDLIB.md` "Backgrounds", `docs/DESIGN_SYSTEM.md` §6 "Chat
  appearance").

## Context

Chats can show a photo, a pattern or colors behind their messages: the account's wallpaper for light and dark theme,
or a chat's own. The conversation still uses the app's color theme, so a warm photo sits under blue bubbles, and the
top bar and the composer bar cover the wallpaper as opaque bands. The owner's reference screenshots (2026-11-21) show
a conversation that takes its colors from its wallpaper, fills the screen with it, and floats the composer over it.

Material's own way to derive colors from an image is Material Color Utilities (MCU): quantize the image (Celebi),
rank the colors (Score), and build a scheme from the chosen seed in HCT. Android does this for the system wallpaper;
Compose's `dynamicColorScheme` only reads the system's result. Pingogram already ports a small part of MCU
(`Harmonize.kt`: CAM16 and harmonization) and generates its color themes with MCU 0.4.0 at build time
(`tools/theme/generate.mjs`), with the 2021 color spec.

## Decision

1. **One wallpaper page.** Chat appearance's wallpaper and a chat's "Wallpaper for this chat" open the same page: a
   large live preview of the conversation in its future colors, "Reset to default", and a panel with Dimming (dark
   theme, 30% to start), Blur (photos), "Choose a photo" and Telegram's wallpapers and patterns as one grid. No
   "Motion" option. "Apply" in the top bar sets the account's wallpaper directly; for a chat it opens a choice of
   "Apply for me" and "Apply for me and <name>", the second locked and disabled without Telegram Premium. The lock
   only informs: Pingogram shows no purchase flow.
2. **Per-chat colors from the wallpaper, no color picker.** A conversation with a wallpaper uses a color scheme made
   from the wallpaper's seed color: bubbles, accents, links, the composer, the send button, the jump-to-bottom button
   and date chips. Everything outside that conversation keeps the user's color theme. The scheme is MCU's
   `SchemeContent` (spec 2021, as the color themes), which keeps the colors close to the image, for light and dark
   theme and the three contrast levels; AMOLED applies on top as it does to every scheme. A chat without a wallpaper
   keeps today's colors exactly.
3. **Port MCU, no new dependency.** The rest of MCU 0.4.0 that this needs is ported to Kotlin in `core:ui`, next to
   `Harmonize.kt`: HCT (with the analytic solver), the Celebi quantizer (Wu, then weighted k-means) and Score, tonal
   palettes, the dislike analyzer, the temperature cache, contrast, and dynamic colors with the 2021 spec's roles
   behind `SchemeContent`. It keeps MCU's Apache-2.0 headers; `config/licenses/extra.json` and the README's Credits
   say what is ported. Tests compare it with MCU's own results. The port is merged in several pull requests, each
   within the size limit.
4. **The seed is computed once and kept.** A photo's seed comes from Celebi and Score on a downscaled copy, off the main
   thread, when the wallpaper is applied; for a wallpaper someone else set on a chat, the first time that chat opens.
   It is kept on the device, keyed by the Telegram background's id, and backed up with the other wallpaper settings,
   so opening a chat never quantizes an image. Patterns and colors take their seed from their fill colors, which needs
   no image.
5. **A blurred top bar, blurred once.** The conversation's wallpaper fills the screen, behind the status bar. The top
   bar shows the wallpaper's top slice blurred; the blur is computed once, at the screen's size, when the wallpaper
   is drawn the first time, and cached, on every Android version. There is no live backdrop blur: `RenderEffect`
   exists only from Android 12 and blurs on every frame. The messages scroll in a rounded container below the bar,
   never under it. The composer floats as a solid pill over the wallpaper. The status bar's icons are light or dark
   from the brightness of the wallpaper's top.

## Consequences

- Each chat with a wallpaper has colors of its own, derived the same way the system derives Material You, with
  Material's contrast guarantees for text on bubbles at every contrast level.
- About 3,500 lines of ported code to keep in step with MCU by hand; they are kept close to the reference so a diff
  against upstream stays readable.
- Opening a chat costs no image analysis; the first open of a chat whose wallpaper someone else set does it once, in
  the background, and draws today's colors until it is done.
- The blurred top bar is a still image: content does not show through it, by design, so scrolling costs nothing.
- `docs/DESIGN_SYSTEM.md` §6 and §13.5 and `REQUIREMENTS.md` FR-S1 describe the page and the conversation;
  `docs/PERFORMANCE.md` keeps its conversation-scroll budget, measured with a photo wallpaper too.

# 0015 — Color themes: generated Material 3 schemes, picked like Pixel's Wallpaper & style

Status: accepted (owner request, 2026-10-22; `REQUIREMENTS.md` FR-S2, `docs/DESIGN_SYSTEM.md` §2.1, §6)

## Context

Appearance offered two sources of color: the wallpaper's (Material You, Android 12+) or the brand scheme seeded from
the icon's blue (`#2156C4`, SchemeVibrant, owner decision 2026-10-19). The owner asked for a choice of color themes,
as Pixel's Wallpaper & style offers "Wallpaper colors" and "Basic colors": the brand, about a dozen curated Material 3
themes and a monochrome one, each with its light, dark and contrast schemes, AMOLED black and the system's contrast
working with every one.

Material 3 builds a scheme from a seed color and a *variant* (material-color-utilities' `DynamicScheme` subclasses:
TonalSpot, Vibrant, Expressive, Neutral, Monochrome, Fidelity, Content, Rainbow, Fruit Salad). Android builds the
wallpaper's scheme with the same library and the same variants ("styles" on Pixel). Material Theme Builder and Android's
wallpaper colors default to TonalSpot.

Sources: Material 3 "Color roles" and "Dynamic color" (m3.material.io/styles/color), material-color-utilities
(github.com/material-foundation/material-color-utilities, `scheme/`), Material Theme Builder
(material-foundation.github.io/material-theme-builder), Pixel's Wallpaper & style (basic colors, styles).

## Decision

- **Themes** (`core:domain`'s `ThemePalette`, stored by name, unknown names read as Pingogram):
  - Pingogram: `#2156C4`, **SchemeVibrant** (the owner's choice; TonalSpot greyed the icon's blue).
  - Blue, Teal, Green, Lime, Yellow, Orange, Red, Pink, Purple, Indigo and Brown: the Material Design 500 colors
    (`#2196F3`, `#009688`, `#4CAF50`, `#CDDC39`, `#FFEB3B`, `#FF9800`, `#F44336`, `#E91E63`, `#9C27B0`, `#3F51B5`,
    `#795548`) with **SchemeTonalSpot**, Material Theme Builder's default and Android's default style for wallpaper
    colors, so a basic color sits beside the wallpaper's as Pixel shows them.
  - Monochrome: **SchemeMonochrome** (greys; the error role stays red).
- **No style choice.** Pixel offers styles (Tonal spot, Vibrant, Expressive, …) for wallpaper colors; Pingogram reads
  the wallpaper's scheme from Android's color roles and cannot restyle it without a runtime color library. A style
  choice for the basic colors alone would multiply the choices (13 × 4) for little, so each theme has one variant.
- **Generation**: `tools/theme/generate.mjs` (Node, `@material/material-color-utilities` pinned in its lockfile)
  writes every theme's six schemes (light and dark × standard, medium and high contrast, levels 0 / 0.5 / 1) into
  `core:ui`'s `Color.kt` as compact ARGB tables, built into a `ColorScheme` only when first asked for and kept
  (`themeColorScheme`), and each theme's splash screen backgrounds and themes into `:app` (`res/values*/palette_splash_*`,
  `SplashPaletteThemes.kt`). The output is committed and in ktlint's format; CI generates it again and fails on any
  difference, so nobody edits it by hand. The app adds no color library and computes nothing at run time.
- **Choosing**: Settings → Appearance → Theme, a page of its own (`ColorThemeRoute`): a live preview (a chat row, two
  bubbles and a button in the theme in use), "Wallpaper colors" (Android 12+) and "Basic colors" as chips split into
  the scheme's primary, secondary and tertiary, the chosen one morphing into a cookie shape with a check. Choosing a
  basic color saves it and turns dynamic color off in one write (`AppearanceRepository.setPalette`); choosing
  wallpaper colors turns dynamic color on and keeps the basic color, which the theme falls back to where the
  wallpaper's scheme has no contrast levels (a medium or high contrast before Android 14), instead of always the
  brand's. The Appearance page's "Dynamic color" switch became the "Theme" row.
- **Everything else follows the theme**: AMOLED black and every contrast level (generated per theme), the cross-fade
  when it changes (`animateColorScheme`), the splash screen of the next start on Android 13+, and notifications'
  placeholder avatars (`AvatarIcons`).

## Consequences

- `Color.kt` grows to about 4,600 generated lines (13 themes × 6 schemes × 48 roles); a theme costs nothing until
  chosen. A new theme is a line in the generator and an entry in `ThemePalette`, whose exhaustive `when`s fail to
  compile until both agree, plus its name string.
- Changing a seed or the generator changes `ThemePalettesTest`'s snapshot, on purpose.
- The notification accent color (`notification_accent`) stays the brand's: a notification has no theme
  (`docs/DESIGN_SYSTEM.md` §6), and following it would pass the theme through every notification builder.
- Before Android 13 the splash screen cannot know the settings (unchanged): it shows the manifest's theme.

# Design system — Material 3 Expressive (complete spec)

Pingogram follows Google's **Material 3 Expressive** guidance in full: the color system, type
scale, shape scale, motion system, component set, layout rules, state system and accessibility
rules. This document is normative. If a Material 3 Expressive token or component exists for a
job, use it; never invent a parallel one. When something here conflicts with the current
material.io guidance or the Compose Material 3 API, follow Google and update this file.

Sources of truth (check them, they change):
- https://m3.material.io — Foundations (Color, Typography, Shape, Motion, Layout,
  Accessibility, Interaction states) and every Component page, "Expressive" variants.
- Compose Material 3 release notes + API reference (`androidx.compose.material3`, ≥ 1.4.0).
- Google Fonts page for Google Sans Flex (axis ranges, OFL license).

Every screen is audited against §12 before it is marked done. Rules the code does not follow yet are tracked in
`docs/audit/m3-expressive-gap-analysis.md` (the owner's decisions of 2026-10-19 are written into this file).

---

## 1. Theme root

```kotlin
@Composable
fun PingogramTheme(
    themeMode: ThemeMode = ThemeMode.System,     // System | Light | Dark
    dynamicColor: Boolean = true,                // Android 12+ only
    contrast: Contrast = Contrast.Standard,      // Standard | Medium | High (follows system on 14+)
    amoled: Boolean = false,                     // true-black surfaces in dark
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val colorScheme = rememberPingogramColorScheme(dark, dynamicColor, contrast, amoled)
    val pingogramColors = remember(colorScheme, dark) { pingogramColors(colorScheme, dark) }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = PingogramShapes,                 // §4
        typography = pingogramTypography(),       // Google Sans Flex, §3
    ) {
        CompositionLocalProvider(
            LocalPingogramColors provides pingogramColors,          // §2.2, MaterialTheme.pingogramColors
            LocalPingogramSpacing provides DefaultSpacing,          // §7, MaterialTheme.spacing
            LocalRippleThemeConfiguration provides RippleDefaults.InsetFocusRingThemeConfiguration, // §2.3
            content = content,
        )
    }
}
```

- The Expressive API (`MotionScheme.expressive()`, `MaterialShapes`, the `*Emphasized` styles,
  `Shapes.largeIncreased`…) is public from material3 1.5; 1.4.0 keeps it internal. In 1.5.0-beta01
  almost all of it is stable. Only these still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`:
  - `MaterialShapes`
  - `RoundedPolygon.toShape()` / `toPath()` and `Morph.toPath()`
  - `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`
  - `PullToRefreshDefaults.LoadingIndicator`

  `@ExperimentalMaterial3Api` remains on the bottom sheets, the tooltips and `AppBarWithSearch`. Opt in only where
  an API still requires it (`docs/audit/m3-expressive-gap-analysis.md` §7).

- Root is always `MaterialExpressiveTheme`. `MaterialTheme` (non-expressive) is forbidden.
- `MainActivity` passes the Appearance settings (FR-S2) to `PingogramTheme`. When one changes, every
  color role cross-fades to the new scheme on `defaultEffectsSpec()` (§5, `animateColorScheme`); the
  first scheme shows at once.
- Everything reads tokens via `MaterialTheme.colorScheme`, `MaterialTheme.typography`,
  `MaterialTheme.shapes`, `MaterialTheme.motionScheme`. **No literal `Color(0xFF…)`, `sp`
  font sizes, `dp` radii or `tween()` in feature modules.** Lint rule `PingogramHardcodedToken`
  (custom detekt rule in `build-logic`) fails the build on these.

## 2. Color system

### 2.1 Scheme generation
- Brand seed: `#2156C4`, the blue of the app icon (`art/`; chosen by the owner 2026-10-19). `tools/theme/generate.mjs` generates
  the 6 schemes (light + dark × standard/medium/high contrast) with material-color-utilities, the
  Material Theme Builder algorithm (`SchemeVibrant`, contrast 0 / 0.5 / 1), into
  `core/ui/.../theme/Color.kt`. Change the seed there and run `npm ci && npm run generate`;
  never hand-edit the output.
  - `SchemeVibrant` was chosen by the owner on 2026-10-19. `SchemeTonalSpot` turned the icon's vivid blue into a
    greyish `#495D92`; Expressive turns `primary` green and Fidelity makes the containers saturated
    (`docs/audit/images/brand-scheme-variants.png`). `VARIANT=TonalSpot npm run generate` builds another variant to
    compare.
  - This applies to the brand schemes only; dynamic color follows the system.
- Dynamic color (Material You) on Android 12+ by default: `dynamicLightColorScheme` /
  `dynamicDarkColorScheme`. User toggle to fall back to brand schemes. On Android 14+ those read the
  platform's color roles (`system_primary_light`…); some systems (Samsung's One UI) fill only the
  Android 12 tonal palettes (`system_accent1_*`…) from the wallpaper and leave the roles at AOSP's stock
  blue. So the roles are used only when their primary has the palettes' hue; otherwise the scheme is
  built from the palettes as Compose does on Android 12–13 (`systemDynamicColors`, `core:ui`), and a
  medium or high contrast falls back to the brand scheme there.
- Contrast follows the system setting on Android 14+ (`UiModeManager.contrast`), otherwise the
  user's choice in Appearance settings.
- AMOLED: in dark mode replace `background`, `surface`, `surfaceContainerLowest` with `#000000`
  and shift the container ladder down one tone; keep all `on*` roles unchanged. Pages that normally sit on a
  container (Settings) turn black too, and their rows move to `surfaceContainer` (§2.2).

### 2.2 Role usage (never pick colors by taste)

| Element | Roles |
|---|---|
| App background / list | `background` / `surface` |
| Cards, sheets, dialogs | `surfaceContainerLow` → `surfaceContainerHighest` ladder by prominence |
| Top app bar (scrolled) | `surfaceContainer` |
| Chat list and conversation | top bar on `surfaceContainer` (the `Scaffold`'s container); content on a `surface` sheet with `extraLarge` top corners below it (`ContentSheet`, `core:ui`) |
| Selected chat row | `secondaryContainer` / `onSecondaryContainer`, `large` shape (the expressive list's selected shape), inset `spacing.small` from the sheet's sides and `ListItemDefaults.SegmentedGap` apart |
| Panes on expanded windows | each pane its own rounded container (`extraLarge`) on a `surfaceContainer` window, `surface` inside; the empty detail pane shows its placeholder in the same container |
| Settings pages | page and its transparent top bar on `surfaceContainerHigh` (`settingsPageColor()`, `feature:settings`), no `ContentSheet`; rows on `surface`, the segmented list item's own container (`ListItemDefaults.segmentedColors()`): lighter than the page in light, darker in dark, as in Google's Material 3 Expressive account menus, and one step above the floating navigation bar's `surfaceContainer`. With AMOLED the page is true black and the rows sit on `surfaceContainer`. Leading icons sit in a tonal circle, one `*Container` role per group (Android 16 Settings). Selected radio row `secondaryContainer` (the list item's selected colors); destructive rows `error` content |
| Navigation bar | `surfaceContainer` (the floating bar's toolbar); active indicator `secondaryContainer` / `onSecondaryContainer` |
| Navigation rail | `surface` (Material's default container); active indicator `secondaryContainer` / `onSecondaryContainer` |
| Primary actions (FAB, send, CTA) | `primaryContainer` / `onPrimaryContainer` (FAB), `primary` / `onPrimary` (filled button) |
| Jump to bottom | `FloatingActionButton` in `secondaryContainer` / `onSecondaryContainer` (Material's secondary container FAB style), not the primary action |
| Outgoing bubble | `primaryContainer` / `onPrimaryContainer` |
| Incoming bubble | `surfaceContainerHigh` / `onSurface` |
| Selected bubble | `secondaryContainer` / `onSecondaryContainer` |
| Emoji-only message | no container; time and state `onSurfaceVariant`, read checks `primary` |
| Reaction badge | `surfaceContainerHighest` / `onSurfaceVariant`, chosen `secondaryContainer` / `onSecondaryContainer`, a `surface` ring; a 48 dp touch target around the 28 dp badge |
| Service / date pill | `surfaceContainerHighest` / `onSurfaceVariant` |
| Unread badge | `primary` / `onPrimary`; muted: `surfaceVariant` / `onSurfaceVariant` |
| Mentions / links | `primary`; visited/secondary links `tertiary` |
| Error / failed send / delete | `error`, `errorContainer` / `onErrorContainer`; a failed message shows "Not sent · Tap to retry" in `onErrorContainer` on an `errorContainer` label under its bubble, with the error icon |
| Online indicator | `LocalPingogramColors.online` — Material "harmonized" green (`MaterialColors.harmonize`) toward `primary` |
| Avatar placeholders | circles; 4 pairs from container roles: `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `surfaceContainerHighest` / `onSurfaceVariant`, with matching `on*`, chosen by `id % 4`. Never the `*Fixed` roles: they keep one light tone in dark theme, and Material says not to use them where contrast matters. On a selected or open row (`secondaryContainer`), placeholders use `primary` / `onPrimary` |
| Dividers, outlines | `outlineVariant`; focus rings `outline` |
| Scrims | `scrim` at 32% |
| Disabled | content 38% `onSurface`, container 12% `onSurface` |

- Any extra semantic color (voice waveform, poll bars, reaction glow) is derived in
  `pingogramColors()` (`PingogramColors`, read with `MaterialTheme.pingogramColors`) from the
  scheme (tonal shifts, harmonization) — not hard-coded.
- Surfaces use the tonal container ladder; **no shadow elevation** except FAB and floating toolbars
  (level 3, incl. the floating navigation bar), menus and dialogs (level 2/3) per M3 elevation tokens.

### 2.3 State layers
- Hover 8%, focus 10%, pressed 10%, dragged 16% of the content color, drawn as state layers
  (Compose `ripple()`). These are the material3 defaults. For keyboard focus, `PingogramTheme` uses Material 3
  Expressive's inset focus rings (`RippleDefaults.InsetFocusRingThemeConfiguration` in
  `LocalRippleThemeConfiguration`, material3 1.5): clearer on tablets and with a keyboard than the opacity
  focus layer. Per-ripple `RippleAlpha` overrides are deprecated since material3 1.5. No custom pressed
  colors.

## 3. Typography — Google Sans Flex (variable)

### 3.1 Font asset
- **Google Sans Flex**, SIL Open Font License 1.1, downloaded from Google Fonts and bundled as
  `core/ui/src/main/res/font/google_sans_flex.ttf` (variable TTF) with its license shipped as
  `core/ui/src/main/assets/licenses/google_sans_flex_OFL.txt` (`res/font/` accepts only font
  files) and an entry in the OSS licenses screen. Bundling (not Downloadable Fonts) keeps the `foss`
  flavor independent of Play Services and avoids first-launch flashes.
- Verify the axis table of the downloaded file with `fonttools varLib.instancer -h` /
  `ttx -t fvar`. Expected axes (check ranges in the file):

| Axis | Tag | Use in Pingogram |
|---|---|---|
| Weight | `wght` | the Material 3 token weights (400, 500); emphasized styles +100 |
| Width | `wdth` | 100 for every style |
| Optical size | `opsz` | **always set equal to the style's font size in sp** (auto-opsz is not applied by Android) |
| Grade | `GRAD` | 0 in both themes (the bundled file's range is 0–100; Google's own styles use 0) |
| Slant | `slnt` | 0; italic = `slnt −10` (true variable slant, never fake italic) |
| Round | `ROND` | **100 for the emphasized styles** (the rounded look Material 3 Expressive gives emphasis), 0 for the base styles |

- These are Google's own settings for the font on Android, which Pingogram follows: Android's SystemUI
  type scale (`TypeScaleTokens.kt` in `frameworks/base/packages/SystemUI/compose/core`) and Pixel's
  Google Sans Flex families (`variable-<style>` and `variable-<style>-emphasized` in the device's
  `fonts_customization.xml`). Both keep the Material 3 sizes and line heights.

- Ranges in the bundled file (read from its `fvar` table): `opsz` 6–144, `wdth` 25–151,
  `wght` 1–1000, `GRAD` **0–100**, `ROND` 0–100, `slnt` −10–0.
- `GRAD` has no negative range, so a dark-theme −25 would be clamped to 0. The owner dropped it on 2026-10-19:
  text renders at grade 0 in both themes, like Google's own styles.
- Small optical sizes set text wider and taller, as the font intends. Compared with the default `opsz` 18,
  `opsz` 14 sets a line about 5% wider and `opsz` 11–12 about 8–9% wider, with an x-height of 0.525–0.530 em
  instead of 0.510. So the same sp reads slightly larger than in apps that leave `opsz` at its default. This is
  kept on purpose. Density is handled by the layout (§7), not by changing the type.

### 3.2 Compose implementation
```kotlin
private fun gsf(weight: Int, opsz: Float, rond: Float, grad: Int = 0, wdth: Float = 100f, slnt: Float = 0f) =
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight(weight),
        style = if (slnt != 0f) FontStyle.Italic else FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight),
            FontVariation.Setting("opsz", opsz),
            FontVariation.Setting("ROND", rond),
            FontVariation.grade(grad),
            FontVariation.width(wdth),
            FontVariation.slant(slnt),
        ),
    )

// One FontFamily per type style so opsz/ROND/GRAD match that style exactly.
fun gsfFamily(weights: List<Int>, opsz: Float, rond: Float, grad: Int) = FontFamily(
    weights.flatMap { w ->
        listOf(gsf(w, opsz, rond, grad), gsf(w, opsz, rond, grad, slnt = -10f))
    },
)
```
- Every distinct variation setting becomes its own Typeface when first used, so families stay
  small: a regular style holds its weight and its emphasized weight (what a bold span resolves to),
  an emphasized style only its own weight, each upright and italic. No style synthesizes a weight or
  slant (`FontSynthesis.None`): a bold span takes the heaviest font of its style instead of a faked
  bold. The typography is the same in both themes, built once and cached (`core/ui/.../theme/Type.kt`).
- The fonts are `GoogleSansFlexFont`s (`core/ui/.../theme/GoogleSansFlexFont.kt`) rather than
  `Font(R.font.google_sans_flex, …)`: their loader makes each typeface once per process. Compose's
  own typeface caches hold 16 entries, fewer than the fonts a list and a conversation use, and each
  reload made a new typeface whose text the platform laid out and rasterized again.
- `pingogramTypography()` builds the **full M3 Expressive type scale** (display, headline,
  title, body, label × large/medium/small = 15 styles) **and all 15 `*Emphasized` variants**
  from the `Typography` constructor parameters, with `GRAD` 0.
- Mapping (weight / opsz / ROND): display, headline, `titleLarge` and body 400 · `titleMedium`,
  `titleSmall` and label 500 (the Material 3 token weights), each with `opsz` = size and ROND 0.
  Emphasized: weight +100 and ROND 100, as Google's emphasized Google Sans Flex families. (Compose's
  emphasized tokens, still marked provisional in material3, put `titleMedium`, `titleSmall` and the
  labels at 700; Google's own families use 600.)
- Sizes and line heights: the M3 token values. Letter spacing: Google's for Google Sans Flex (the
  SystemUI type scale): 0, and 0.1 sp for `bodySmall`, `labelMedium` and `labelSmall`. The M3 tokens'
  tracking (up to 0.5 sp) is Roboto's: the font's optical size axis already spaces small sizes
  apart, and both together set text wider than Material lays it out. `lineHeightStyle`
  = `LineHeightStyle(Alignment.Center, Trim.None)`; `platformStyle = PlatformTextStyle(includeFontPadding = false)`.
- Font scaling: all sizes in `sp`; the chat "message text size" setting multiplies `bodyLarge`
  only (0.85–1.3) and re-derives `opsz`. Non-linear font scaling (Android 14) is respected.
- Bold text (Android 12+): `PingogramTheme` passes `Configuration.fontWeightAdjustment` to
  `pingogramTypography`, which registers each font at its weight plus the adjustment. Compose adds the
  adjustment both when it picks a font from the family and to that font's `wght`, so fonts registered
  at their plain weights would pick the heavier emphasized font and add the adjustment on top; this way
  text gets bolder by the adjustment once (`GoogleSansFlexRenderingTest`).
- Fallbacks: Android's font fallback chain renders glyphs Google Sans Flex lacks (CJK, Arabic,
  Devanagari, emoji) with the system fonts and Noto; no font files are bundled for them.
- Tabular figures for times/counters: `fontFeatureSettings = "tnum"`.
- Material's typography (August 2026) adapts line heights to the script's height. We track it until
  Compose exposes it, and keep the M3 line heights meanwhile.

### 3.3 Style assignment

| Element | Style |
|---|---|
| Large flexible app bar titles (profile) | `displaySmallEmphasized` collapsing to `titleLargeEmphasized` |
| Medium flexible app bar titles (Settings home and pages) | `headlineMediumEmphasized` collapsing to `titleLargeEmphasized` |
| Screen titles in a small app bar (chat list, archive, search) | `titleLargeEmphasized` |
| Conversation title / status | `titleMediumEmphasized` / `bodySmall` |
| Auth step headline | `headlineMediumEmphasized`, with a `MaterialShapes` icon badge above |
| Chat list row title | `titleMediumEmphasized` when unread, `titleMedium` when read |
| Chat list preview / message text | `bodyMedium` (one line) / `bodyLarge` |
| Timestamps, badges, meta | `labelSmall` (`tnum`) |
| Verification codes | `headlineSmall` (`tnum`), centered |
| Buttons | filled (primary) buttons `labelLargeEmphasized`; tonal, outlined and text buttons `labelLarge` |
| Section headers in settings | `titleSmallEmphasized` in `primary` (settings pages; Settings home has none, §6) |
| Account card name (Settings home) | `titleLarge`; phone `bodyLarge`, username `bodyMedium` in `onSurfaceVariant`; "Premium" label `labelMediumEmphasized` |
| Empty state title / body | `headlineSmallEmphasized` / `bodyMedium` |
| Code blocks in messages | `bodyMedium` + `FontFamily.Monospace` (system) |

## 4. Shape system

- Shape scale (M3 Expressive): `extraSmall 4` · `small 8` · `medium 12` · `large 16` ·
  `largeIncreased 20` · `extraLarge 28` · `extraLargeIncreased 32` · `extraExtraLarge 48` · full = pill.
- `MaterialShapes` (the 35 expressive polygons) are the only source of non-rounded-rect shapes. They are used for:
  - states: the selected avatar's `SoftBurst` check, the call screen avatar (`Boom` morph on ringing)
  - story rings
  - reaction bursts (`Burst`/`SoftBurst`)
  - the QR login frame (`Cookie12Sided`) and the auth step badges
  - loading indicator polygons
  - empty-state illustrations: soft shapes, so errors use `Cookie4Sided`, never the spiky `Burst`

  Avatars and their placeholders are circles, as in Google Messages and Contacts. Six silhouettes in one list read
  as noise.
- **Shape morphing** (`Morph`, `MaterialShapes.*.toShape()`, `animateFloatAsState` with a
  spatial spring): FAB press (`Circle → Cookie9Sided`), toggle buttons (`Circle ↔ Square`),
  selected avatar, record button while recording, reaction added.
- Content sheets (chat list, conversation): `extraLarge` top corners, square at the bottom edge of
  the window; content scrolls inside them, clipped to the corners.
- List rows in a sheet sit `spacing.small` in from its sides and half of
  `ListItemDefaults.SegmentedGap` from the rows around (their content keeps the list item's
  padding): at rest `ListItem`'s `extraSmall`, pressed, open and selected in selection mode `large` (the expressive
  list's selected shape), morphing on the list item's fast spatial spring. Swipe actions reveal the same inset
  container with `large` corners, and the row being dragged shows its own `large` container (as when pressed) so it
  never slides with a square edge.
- Settings rows are Material 3 Expressive segmented list items (`SegmentedListItem`): each row its own
  container, `ListItemDefaults.SegmentedGap` (2 dp) apart, with the shapes of
  `ListItemDefaults.segmentedShapes(index, count)`: `large` outer corners at the ends of a group
  (top of the first row, bottom of the last, all four for a single row) and `extraSmall` between
  rows; pressed, focused and selected rows morph to `large` all round on the motion scheme's fast
  spatial spring (the list item's own animation). The account card at the top of Settings rests on
  `extraLarge` and morphs to the same `large` when pressed.
- Message bubbles follow Google's Material 3 Expressive messaging: `extraLarge` corners, which
  make a one-line bubble a full pill (corners are capped at half the height), and no tail.
  Consecutive messages of a sender stack `spacing.extraSmall` apart and meet with `extraSmall`
  corners on the sender's side (the end for outgoing, the start for incoming): the first of a group
  is `extraLarge` at the top and `extraSmall` at the bottom there, a middle one `extraSmall` at
  both, the last `extraSmall` at the top and `extraLarge` at the bottom; the far side stays
  `extraLarge`, and a message alone is `extraLarge` all round. The eight shapes are built once per
  theme (`MessageBubbleShapes`, `core:ui`).
- Media in bubbles: photos, videos and albums clip to the same shape, square only where a header or a caption joins
  them. The media viewer's container transform morphs from and back to exactly that clip shape.
- Padding: text and the time keep `spacing.medium` from the sides and `spacing.small` from top and bottom, clear of
  the round corners.
- Time: the time and the state show on the last bubble of a sender's group only (and on every message that stands
  alone), as Google Messages does. Tapping a bubble shows its time, and screen readers always hear it.
- Radius: user-adjustable later in Appearance settings (`extraLarge` by default).
- Reply quotes and link previews are tonal rounded cards (`medium` corners, the bubble's block color) without a
  leading accent bar. Inline code, `pre` blocks and spoilers sit on `small`-rounded backgrounds. Voice messages
  keep their waveform.
- Emoji-only messages (1–3 emoji and nothing else, no formatting, reply or forward) show in
  `displaySmall` without a bubble, the time and state below them.
- Reactions sit on a bubble's bottom corner on the far side as round badges (§2.2), up to three
  side by side, then "+N", which opens the message's actions; they overlap the bubble by its bottom
  padding, so they never cover the time, and hang below content without a bubble. Each takes a 48 dp touch
  target without moving the layout.
- Components keep their M3 default shape tokens (buttons pill, cards `medium`, sheets
  `extraLarge` top, dialogs `extraLarge`, chips `small`, text fields `extraSmall` top).

## 5. Motion system

- `MotionScheme.expressive()`; animation specs come **only** from
  `MaterialTheme.motionScheme`:
  - **Spatial** springs (movement, size, shape; slight overshoot): `defaultSpatialSpec()`,
    `fastSpatialSpec()`, `slowSpatialSpec()`.
  - **Effects** springs (color, alpha, blur; no overshoot): `defaultEffectsSpec()`,
    `fastEffectsSpec()`, `slowEffectsSpec()`.
- Mapping: screen transitions, sheet open/close, list reordering, shape morphs → spatial
  default; press feedback, FAB menu, chips → spatial fast; large layout changes (expanding app
  bar, list-detail pane) → spatial slow; color/alpha crossfades → effects default; reactions,
  typing dots, send confirmation → effects fast.
- Navigation transitions:
  - **Shared-axis X** for every screen pushed above a tab: conversation, archive, search, settings pages. It is
    built in `core:ui` (`sharedAxisXEnter` / `sharedAxisXExit`): a slide by `SharedAxisDistance` on
    `defaultSpatialSpec` with fades on the effects springs, mirrored on pop. It moves to material3's
    `MaterialTransitions.HorizontalSharedAxis` once 1.6 is published.
  - **Shared-axis Y** for vertical flows (auth).
  - **Fade-through** only for tab switches and chat folder switches.
  - **Container transform** via `SharedTransitionLayout` for avatar → profile, thumbnail → media viewer, the New
    chat FAB → New chat, and the search button → search.
- Predictive back: Navigation's predictive back with our transitions (`predictiveBackPopExit`,
  `core:ui`): the screen being left scales to 90% on `defaultSpatialSpec` and fades on
  `slowEffectsSpec` over the destination beneath, which shows at full opacity. Navigation seeks the
  transition with the gesture's progress and plays the rest after release at the same pace, so the
  springs' curves are the gesture's curve. Navigation's default (scale to 70% without fading)
  leaves an opaque card that is cut at the end. Custom surfaces
  (media viewer, attach sheet, selection toolbar) implement `PredictiveBackHandler` and scale
  to 90% with the Expressive back-progress curve.
- Lists: `Modifier.animateItem()` on every `LazyColumn` item; new message enters with a spatial
  spring from the bottom; swipe actions reveal icons with scale springs; pull/overscroll uses
  the stretch overscroll default.
- Loading: `LoadingIndicator` (morphing polygons) for indeterminate, `LinearWavyProgressIndicator`
  / `CircularWavyProgressIndicator` for determinate (uploads, downloads, voice playback). A screen whose
  content usually comes from TDLib's local database at once shows its indicator only after **200 ms** (Material's
  threshold: nothing under 200 ms, a loading indicator up to 5 s, progress beyond), fading in on
  `defaultEffectsSpec` (`DelayedLoadingIndicator`, `core:ui`), so it never flashes. Every such screen uses it:
  chat list, conversation, settings pages, Appearance, the auth progress step.
- Micro-interactions with haptics (`LocalHapticFeedback`):
  - `LongPress`: selection, the message menu
  - `TextHandleMove`: swipe threshold (chat rows, swipe to reply)
  - `Confirm`: sent, code accepted
  - `Reject`: failed send, wrong code or password (`Confirm` and `Reject` on API 34+)
  - `ToggleOn` / `ToggleOff`: switches and reactions
  - `SegmentTick`: slider steps and choices in a connected button group

  No raw `Vibrator` patterns.
- Reduced motion: if animator duration scale is 0, replace springs with snaps and skip shape
  morphs; essential information is never conveyed by motion alone.
- Performance rule: animate with `graphicsLayer {}` / `Modifier.offset {}` lambdas, never by
  recomposing layout for each frame (see `docs/PERFORMANCE.md`).

## 6. Component mapping (screen → M3 Expressive components)

| UI element | Component |
|---|---|
| Chat list top bar | Small `TopAppBar` (64 dp, `titleLargeEmphasized`, pinned) on `surfaceContainer`, the list on a rounded sheet below (`ContentSheet`). The chat list is a dense list, and a medium flexible bar cost ~1.5 rows at rest. Search opens from the navigation's search button. The connection pill (FR-L5) sits below the bar |
| Global search | **Compact**: a full-screen destination (`SearchRoute`) with the M3 search bar expanded, built from `SearchBarDefaults.InputField` with the bar's colors, divider and full-screen insets. Not `ExpandedFullScreenSearchBar`, which opens a dialog window that would stay above the chats a result opens and outside predictive back. **Medium and wider**: docked, as Material asks; the field and its results sit in the list pane, and a result opens in the detail pane. Opened from the search button of the floating navigation bar or the rail, with a container transform. Under the field: a row of `FilterChip`s (`FilterChipDefaults.shapes()`: All, Chats, Contacts, Messages, Media, Links). Results come in sections, each a group of segmented list items with gaps between groups, under `titleSmallEmphasized` labels. One `LinearWavyProgressIndicator` under the field while searching; a section's error in an `errorContainer` segment |
| Conversation top bar | `TopAppBar` small, avatar + two-line title (`titleMediumEmphasized`), on `surfaceContainer` with the messages on a rounded sheet below (`ContentSheet`). At most two filled icon-button actions (Material: avoid overflow menus in app bars). The title opens the profile (M4) |
| Folder tabs | `ButtonGroup` connected `ToggleButton`s with `animateWidth` press physics in a 56 dp row (40 dp buttons, `spacing.small` above and below), in a horizontal scroll that brings the selected button into view and fades at the edges, so the group never overflows into its menu; selected label `labelLargeEmphasized`; fallback `PrimaryScrollableTabRow` if > 6 |
| Main navigation | **Compact width: floating navigation bar** (§6.1). Medium and expanded widths, and windows of compact height (phones in landscape): `NavigationSuiteScaffold` → `WideNavigationRail` collapsed, the search `FloatingActionButton` in its header. Large and extra-large widths (≥ 1200 dp): the rail expanded, search as an `ExtendedFloatingActionButton` |
| New chat | One `MediumExtendedFloatingActionButton` "New chat" (filled edit icon, collapsing to the medium FAB on scroll down, `Modifier.animateFloatingActionButton`), bottom-end above the floating navigation bar or in the list pane. It opens the New chat screen (new group, new channel, new contact, then contacts) with a container transform. Not a FAB menu: Material says not to pair one with a floating toolbar or a navigation rail |
| Jump to bottom | `FloatingActionButton` (56 dp) in `secondaryContainer`, unread count in a `Badge`, bottom-end above the composer (Material no longer recommends the small FAB) |
| Selection mode | `HorizontalFloatingToolbar` (expressive, `FloatingToolbarDefaults.vibrantFloatingToolbarColors()`), each icon button with a `PlainTooltip` (`TooltipBox`); the top bar becomes a small `TopAppBar` with close and "N selected" (`titleLarge`) |
| Composer | A 64 dp bar on `surfaceContainer`. **Field**: a filled tonal pill like Google Messages' — `BasicTextField(state)` with `TextFieldDefaults.decorator`, `TextFieldDefaults.roundedShape` and `tonalColors()`, 48 dp minimum height, the emoji button as its leading icon. **Attach**: a `FilledTonalIconButton` "+" at `IconButtonDefaults.smallContainerSize()` before it. **Send**: an icon-only `SplitButtonLayout` at `SplitButtonDefaults.SmallContainerHeight` (send · menu with schedule and silent), shown when there is text; the mic for voice recording takes its place when empty (a `FilledIconButton` that morphs to a pill with a wavy indicator). Icon buttons keep 48 dp touch targets. **Width**: capped at the bubbles' max width on wide windows. Formatting shows in the field as in bubbles (an output transformation), a spoiler on `tertiaryContainer`; scheduling = `DatePicker`, then `TimePicker`, in a `DatePickerDialog` |
| Attach menu | `ModalBottomSheet` + `ButtonGroup` of options; Gallery opens the system Photo Picker (no recent-photos carousel: it would need media permissions, `REQUIREMENTS.md` §7) |
| Message actions | Long-press lifts the message over a `Scrim`, with the content behind blurred (`RenderEffect`, API 31+; the scrim alone below 31), as Google Messages does. The quick reactions sit above it as a `ToggleButton` row with shape morph, ending in a medium `FilledIconButton` that opens the reactions picker. An expressive menu (`DropdownMenuPopup` with `DropdownMenuGroup`s, destructive items in `error`) sits below it. If the message does not fit, it moves into view on `defaultSpatialSpec` with a `LongPress` haptic. A second long-press selects text in the lifted message. With TalkBack, or when the window is too short, the same items show in a `ModalBottomSheet` |
| Reactions picker | In a `ModalBottomSheet` (with TalkBack: in the actions sheet, in place of its actions; content crossfades on effects springs, the sheet resizes on `defaultSpatialSpec`): the message's own reactions as a `HorizontalUncontainedCarousel` of reaction chips (emoji over count), then every reaction the user can add (TDLib's top, recent, popular) as a lazy grid of the same `ToggleButton`s under `titleSmallEmphasized` section headers; a bubble's "+N" opens the sheet there. Custom emoji show their sticker's still picture, or a placeholder |
| Chat list row | `ListItem` (leading 48 dp circular avatar, title, one line of preview with the sender inline, trailing time + badge): Material's two-line item, 72 dp. Inset in its sheet, `large` when selected (§4). Swipe `SwipeToDismissBox`: read/unread at the start, archive at the end (a full swipe triggers it), pin, mute and delete in the selection toolbar and as screen-reader actions |
| Chips (filters, mentions) | `FilterChip`, `AssistChip`, `SuggestionChip` |
| Settings rows | Groups of `SegmentedListItem`s (`SettingsGroup`, `feature:settings`) with `ListItemDefaults.segmentedShapes` / `segmentedColors` / `SegmentedGap` (§2.2, §4), outlined leading icons, the label in the list item's `bodyLarge`; a `titleSmallEmphasized` header in `primary` above each group on settings pages, none on Settings home (its single-row groups would repeat their row, as Google's account menus show them bare), groups `spacing.medium` apart there and `spacing.extraLarge` with headers. Clickable rows use the clickable list item. Switches use the clickable one with `Role.Switch` semantics and a trailing `Switch` showing a check icon in its thumb when on (`thumbContent`). Single choices use the selectable one with a leading `RadioButton`, or, where every label fits, a connected button group in a row of its own: `ToggleButton`s with `ButtonGroupDefaults.connectedLeading/Middle/TrailingButtonShapes()` (segmented buttons are deprecated in Material 3 Expressive). Buttons and progress sit in a segment of the same container and shape (`SettingsSegment`); text fields sit directly on the page (rounded tonal style). Leading icons sit in a tonal circle, one `*Container` role per group. Every row of a page has an icon, or none does. **Pages**: a `MediumFlexibleTopAppBar` like Settings home, its width capped with the content. **Expanded windows**: list-detail, with Settings home as the list |
| Settings account card | A single `SegmentedListItem` (`extraLarge` at rest): avatar in a `primary` ring with an edit badge, name, phone, username, a "Premium" status label (`tertiaryContainer`, Telegram Premium only, never an offer) and a `FilledTonalIconButton` chevron; the whole card opens Edit profile and is one item for screen readers |
| Buttons | M3 Expressive sizes: `ButtonDefaults.ExtraSmall…ExtraLarge` container heights. The default (40 dp) for actions in forms and flows: auth puts its primary action at the bottom end and the secondary as a `TextButton` at the bottom start, as Google's sign-in does. Medium (56 dp) and up only for hero moments (Welcome's "Start messaging"), never full width on wide windows. `FilledTonalButton` for secondary, `OutlinedButton` rare, `TextButton` in dialogs; every button with `ButtonDefaults.shapes()` press morph |
| Text fields | Forms (auth): filled `TextField`s, passwords `SecureTextField`, the country a read-only field with `ExposedDropdownMenuDefaults.TrailingIcon` that opens the picker (its search a `SearchBarDefaults.InputField`, the current country selected). Composer and Edit profile: the rounded tonal field (`TextFieldDefaults.roundedShape` + `tonalColors()`) on the page, no enclosing segment. One style per region |
| Icon buttons | `IconButton`/`FilledIconButton` with Expressive shapes (`IconButtonDefaults.*Shape`) and sizes; toggleable ones use `IconToggleButton` with shape morph |
| Progress | `LoadingIndicator`, `ContainedLoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| Dialogs | `AlertDialog` for destructive confirmations only; `BasicAlertDialog` for custom |
| Snackbars | `SnackbarHost` with M3 `Snackbar`; errors are never dialogs |
| Notification permission request (chat list, Android 13+) | Filled `Card` on `surfaceContainerHigh` at the top of the list's sheet, inset `spacing.small` like the rows: `ic_notifications` in `primary`, `titleMedium` title, `bodyMedium` text in `onSurfaceVariant`, `TextButton` "Not now" and `Button` "Allow" / "Open settings" (both with `ButtonDefaults.shapes()`); grows in and shrinks away (`expandVertically` on `defaultSpatialSpec`, fades on the effects springs). Never a dialog |
| Notifications | `MessagingStyle` conversations; small icon `ic_notification`, accent the brand seed (`notification_accent`): a notification has no theme. Placeholder avatars: initials on the §2.2 avatar pairs of the wallpaper's (or brand) light scheme, in Google Sans Flex |
| Media viewer | Full-bleed `Scaffold`, `HorizontalPager`, container-transform entry and exit. Actions in a `HorizontalFloatingToolbar` (standard colors): Forward, Share, Save, Delete (where allowed), each with a tooltip, then More (`DropdownMenuPopup`: show in chat, copy, …). Caption and controls capped at `contentMaxWidth` |
| Profile header | `LargeFlexibleTopAppBar` + shape-morphing avatar (`MaterialShapes`), `HorizontalUncontainedCarousel` of photos |
| Tooltips / onboarding | `TooltipBox` with `PlainTooltip` on icon-only buttons in toolbars, `RichTooltip` for onboarding |
| Date pickers (schedule) | `DatePicker` + `TimePicker` in `DatePickerDialog` |
| Navigation drawer | not used (folders live in tabs) |
| Empty states | `MaterialShapes` illustration (soft shapes; errors `Cookie4Sided`) + `headlineSmallEmphasized` + `bodyMedium` + a `Button` where there is a next step (the chat list's: "New chat") |

### 6.1 Floating navigation bar (compact windows)

Google's Material 3 Expressive apps moved primary navigation into a floating pill (Google Photos,
2026: tabs in a pill, search in a separate circle beside it). Material has no component for it;
the nearest, the floating toolbar, is meant for page actions. Pingogram builds the pattern from
Material parts (`app/.../ui/PingogramNavigationBar.kt`):

- **Container**: `HorizontalFloatingToolbar` with `standardFloatingToolbarColors()`
  (`surfaceContainer`), its default shape and level-3 elevation, `FloatingToolbarDefaults.ScreenOffset`
  (16 dp) from the window sides and above the system navigation bar.
- **Tabs**: one per top-level destination, `Role.Tab` in a `selectableGroup`. The selected tab shows
  its filled icon and its label (`labelLargeEmphasized`) on a full-pill `secondaryContainer`
  indicator in `onSecondaryContainer`; the others show only their label (`labelLarge`) in the
  toolbar's content color. Where the labels do not fit (narrow phones, large font scales: measured,
  never truncated), the other tabs show their outlined icon instead, named by the label for
  TalkBack. This deviates on purpose from Material's navigation bar, which wants an icon and a label on every item,
  because the floating bar follows Google Photos rather than a Material component. Tapping the selected tab scrolls
  its screen back to the top.
- **Search**: `FloatingToolbarDefaults.StandardFloatingActionButton` beside the pill, a circle in the
  toolbar's own colors, opening global search.
- **Motion**: the indicator fades in on `defaultEffectsSpec` and grows along one axis from the tab's center on
  `fastSpatialSpec`, as Material's navigation indicator does (both read in the draw or layout phase); the icon slides
  in with `fastSpatialSpec` + `fastEffectsSpec`; the bar slides away on screens pushed above a tab
  and back on return (`defaultSpatialSpec` in, `fastSpatialSpec` out), by its own height, fading on the
  matching effects springs, in its graphics layer (`FloatingNavigationBar`). It keeps the tab below the
  pushed screen selected on the way, and stays composed while hidden (not placed, not in the semantics
  tree), so coming back does not compose it again.
- **Layout**: the bar floats over the top-level screens, which draw behind it. Their `Scaffold` uses
  `screenContentWindowInsets()` (`core:ui`), which adds `LocalNavigationBarInsets` (the bar, its
  margin below and the same margin above), so lists end clear of the bar. The insets depend on the
  window only, never on the destination: changing that static local recomposes every screen.
  Screens pushed above a tab, where the bar hides, do not read it.
- **Stepping aside**: a top-level screen that shows a floating toolbar of its own in the bar's place
  (the chat list's selection toolbar, as Google Photos does while selecting) hides the bar with
  `HideNavigationBar(hidden)` (`core:ui`, honoured by `PingogramApp`). The bar returns as soon as the
  screen stops asking or leaves the composition; `LocalNavigationBarInsets` stays meanwhile, and the
  toolbar sits where the bar was (`floatingToolbarInsets()`).
- Medium and wider windows keep the `WideNavigationRail`, and so do windows of compact height (phones in landscape,
  tabletop), where height is scarce. The bar is for compact-width windows only.

## 7. Layout

- `Scaffold` per screen; `contentWindowInsets`, `innerPadding`; `enableEdgeToEdge()`;
  transparent system bars; `viewport-fit`-style safe handling of display cutouts.
- Spacing scale 4/8/12/16/24/32 dp (Material's 2026 spacing system is built on the same 8 dp base); margins
  16 dp compact, 24 dp medium+, applied on every screen. Feature code reads them as tokens from
  `MaterialTheme.spacing` (`extraSmall`…`extraExtraLarge`, `windowMarginCompact`, `windowMarginMedium`), never as
  `dp` literals.
- Breakpoints (Material's name since May 2026 for window size classes), via `currentWindowAdaptiveInfoV2()`, which
  reports large and extra-large widths:
  - **Compact** (< 600 dp): single pane + floating navigation bar (§6.1).
  - **Medium** (600–839 dp): single pane + collapsed rail.
  - **Expanded** (840–1199 dp): `ListDetailPaneScaffold` for chats | conversation and for Settings home | page.
    The list is a fixed pane of 360–412 dp and the detail is flexible. Each pane is its own rounded container
    (§2.2), and a `VerticalDragHandle` resizes them (`paneExpansionState`, anchors 360 dp, 412 dp and a centered
    split), keeping the width across restarts. Both panes have top bars of the same height.
  - **Large / extra-large** (≥ 1200 dp): as expanded, with the rail expanded; a third pane (profile, chat info) as
    a supporting pane at XL (M4).
  - **Compact height** (phones in landscape): the rail instead of the floating bar (§6.1).

  `SupportingPaneScaffold` for profile/shared media. Canonical layouts only.
- Auth on medium+ widths and on compact heights: two panes, the step's headline and illustration at the start and
  its form with the actions at the end, so the primary action is always visible.
- Pane behaviour on foldables: `FoldingFeature`-aware hinge avoidance (`material3-adaptive`).
- **Density** (owner, 2026-10-19: the app must not look bigger than Google's apps on the same phone):
  - Use Material's component sizes, not larger ones: small app bars on dense lists, 40 dp buttons and icon buttons
    (48 dp touch targets), two-line list items at 72 dp.
  - Chat rows: 48 dp avatars (`sizes.avatarRow`) and one preview line.
  - Composer: a 64 dp bar.
  - Bubbles: 12 dp side padding and one time per group.
  - Check: on a 412 × 915 dp phone at 100% font the chat list shows at least nine rows and a one-line message
    bubble is at most 40 dp tall.
- Touch targets ≥ 48 dp (`Modifier.minimumInteractiveComponentSize()`); 8 dp min spacing
  between targets.
- RTL: `start/end`, mirrored icons where M3 says so (`autoMirrored`).

## 8. Iconography & imagery

- **Material Symbols Rounded**, weight 400, filled for selected nav items; import as vector
  drawables from the Material Symbols site with the `ROND`-matching rounded style. Size 24 dp.
- Launcher icon: adaptive (foreground / background / **monochrome** layers), themed icon
  support; splash via `core-splashscreen` using the same vector. Notification small icon:
  monochrome 24 dp glyph.
- The owner's artwork lives in `art/` (SVG, the source); the app's vectors are converted from it:
  `app` `ic_launcher_*` (the mark `#2156C4` on white, the icon's own colors, also on the splash
  screen's icon circle), `core:notifications` `ic_notification`, `core:ui` `ic_pingogram` (the mark)
  and `logo_pingogram` (mark and wordmark). In the UI the mark and logo are one-color and tinted
  with theme roles (Welcome: `onPrimaryContainer` on its `primaryContainer` shape; About: `primary`),
  so they follow dynamic color.
- Avatars: circles, photos and placeholders alike; `MaterialShapes` only for states (the selection check, calls).
- Images: inside bubbles they take the bubble's shape (§4); `extraLarge` in media viewer thumbnails.

## 9. Accessibility (Material + Android standards)

- Contrast ≥ 4.5:1 text, ≥ 3:1 large text/icons/UI boundaries in all contrast levels.
- `contentDescription` on every icon-only control; decorative images `null`; merged semantics
  per message bubble ("{sender}, {time}: {content}, {status}"); `customActions` for swipe
  equivalents; `LiveRegion` for connection state; `role` set on custom toggles.
- Font scale to 200% (non-linear on API 34+) without truncation; no fixed heights on text.
- Keyboard & mouse: focus order, `Tab` navigation, Enter to send (Ctrl+Enter configurable),
  hover states on large screens.
- Reduced motion & reduced transparency respected; no flashing content > 3 Hz.
- Run TalkBack + Accessibility Scanner before marking any screen done.

## 10. Dark theme and contrast rules

- Dark uses the M3 dark scheme (not inverted light); surfaces follow the tonal ladder;
  Google Sans Flex at `GRAD` 0 (§3.1); images get a 0.9 `ColorFilter` dim only in media-heavy lists.
- Medium/high contrast schemes change colors only; typography weight is unchanged (Material
  rule), but `GRAD +25` may be applied in high contrast for readability.

## 11. Don'ts

- Don't mix Material 2 (`androidx.compose.material.*`), AppCompat widgets or custom-drawn
  "material-like" components.
- Don't hard-code colors, sizes, durations, easing, or fonts.
- Don't reproduce Telegram's bubble styling, icon set, sounds, or layout; this is a
  Google-style app.
- Don't use shadows for hierarchy; use tonal surfaces.
- Don't ship skeleton shimmers; use `LoadingIndicator` or tonal placeholders.
- Don't add `-opt-in` compiler flags for experimental APIs project-wide; one `@OptIn` per composable.

## 12. Per-screen M3 Expressive audit (must pass before "done")

- [ ] Only `MaterialTheme.*` tokens; `PingogramHardcodedToken` detekt rule passes
- [ ] Color roles per §2.2; light, dark, dynamic, AMOLED, medium & high contrast screenshots OK
- [ ] Google Sans Flex styles per §3.3; `opsz` equals size; emphasized styles where specified; `GRAD` 0
- [ ] Density per §7: Material's component sizes; the screen is no bigger than Google's equivalent on the same phone
- [ ] Shapes from the scale or `MaterialShapes`; at least one meaningful shape morph where the screen has a primary action
- [ ] All animations use `motionScheme` specs; shared-element / container transform where listed in §5
- [ ] Components match the §6 mapping (no custom lookalikes)
- [ ] Edge-to-edge, insets, predictive back, adaptive layout at compact/medium/expanded/large and compact height
- [ ] Goldens for light, dark, AMOLED, high contrast, RTL and a 700 dp width
- [ ] Accessibility §9 checks pass; haptics per §5
- [ ] Reduced motion path verified
- [ ] Performance budget in `docs/PERFORMANCE.md` met (no jank in the screen's Macrobenchmark)

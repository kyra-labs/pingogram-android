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

Every screen is audited against §12 before it is marked done.

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
        typography = pingogramTypography(dark),   // Google Sans Flex, §3
    ) {
        CompositionLocalProvider(
            LocalPingogramColors provides pingogramColors,          // §2.2, MaterialTheme.pingogramColors
            LocalPingogramSpacing provides DefaultSpacing,          // §7, MaterialTheme.spacing
            LocalRippleThemeConfiguration provides RippleDefaults.OpacityFocusThemeConfiguration, // §2.3
            content = content,
        )
    }
}
```

- The Expressive API (`MotionScheme.expressive()`, `MaterialShapes`, the `*Emphasized` styles,
  `Shapes.largeIncreased`…) is public from material3 1.5; 1.4.0 keeps it internal. `MaterialShapes`
  and `toShape()` still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

- Root is always `MaterialExpressiveTheme`. `MaterialTheme` (non-expressive) is forbidden.
- Everything reads tokens via `MaterialTheme.colorScheme`, `MaterialTheme.typography`,
  `MaterialTheme.shapes`, `MaterialTheme.motionScheme`. **No literal `Color(0xFF…)`, `sp`
  font sizes, `dp` radii or `tween()` in feature modules.** Lint rule `PingogramHardcodedToken`
  (custom detekt rule in `build-logic`) fails the build on these.

## 2. Color system

### 2.1 Scheme generation
- Brand seed: `#E8457C` (placeholder; owner decides once). `tools/theme/generate.mjs` generates
  the 6 schemes (light + dark × standard/medium/high contrast) with material-color-utilities, the
  Material Theme Builder algorithm (`SchemeTonalSpot`, contrast 0 / 0.5 / 1), into
  `core/ui/.../theme/Color.kt`. Change the seed there and run `npm ci && npm run generate`;
  never hand-edit the output.
- Dynamic color (Material You) on Android 12+ by default: `dynamicLightColorScheme` /
  `dynamicDarkColorScheme`. User toggle to fall back to brand schemes.
- Contrast follows the system setting on Android 14+ (`UiModeManager.contrast`), otherwise the
  user's choice in Appearance settings.
- AMOLED: in dark mode replace `background`, `surface`, `surfaceContainerLowest` with `#000000`
  and shift the container ladder down one tone; keep all `on*` roles unchanged.

### 2.2 Role usage (never pick colors by taste)

| Element | Roles |
|---|---|
| App background / list | `background` / `surface` |
| Cards, sheets, dialogs | `surfaceContainerLow` → `surfaceContainerHighest` ladder by prominence |
| Top app bar (scrolled) | `surfaceContainer` |
| Navigation bar / rail | `surfaceContainer`; active indicator `secondaryContainer` / `onSecondaryContainer` |
| Primary actions (FAB, send, CTA) | `primaryContainer` / `onPrimaryContainer` (FAB), `primary` / `onPrimary` (filled button) |
| Outgoing bubble | `primaryContainer` / `onPrimaryContainer` |
| Incoming bubble | `surfaceContainerHigh` / `onSurface` |
| Selected bubble | `secondaryContainer` / `onSecondaryContainer` |
| Service / date pill | `surfaceContainerHighest` / `onSurfaceVariant` |
| Unread badge | `primary` / `onPrimary`; muted: `surfaceVariant` / `onSurfaceVariant` |
| Mentions / links | `primary`; visited/secondary links `tertiary` |
| Error / failed send / delete | `error`, `errorContainer` / `onErrorContainer` |
| Online indicator | `LocalPingogramColors.online` — Material "harmonized" green (`MaterialColors.harmonize`) toward `primary` |
| Avatar placeholders | 7 pairs from `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `primaryFixed`, `secondaryFixed`, `tertiaryFixed`, `surfaceVariant` with matching `on*`, chosen by `id % 7` |
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
  (Compose `ripple()`). These are the material3 defaults; `PingogramTheme` pins the opacity-based
  focus indication with `LocalRippleThemeConfiguration` (per-ripple `RippleAlpha` overrides are
  deprecated since material3 1.5). No custom pressed colors.

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
| Weight | `wght` | 300–800 across the scale; emphasized styles +200 |
| Width | `wdth` | 100 default; 90–95 for dense labels/time stamps; 110 for display |
| Optical size | `opsz` | **always set equal to the style's font size in sp** (auto-opsz is not applied by Android) |
| Grade | `GRAD` | 0 light theme, **−25 in dark theme** (prevents blooming), +25 for emphasis without reflow |
| Slant | `slnt` | 0; italic = `slnt −10` (true variable slant, never fake italic) |
| Round | `ROND` | **100 for display/headline/title** (the "Rounded" look Material 3 Expressive uses), 0 for body/label |

- Ranges in the bundled file (read from its `fvar` table): `opsz` 6–144, `wdth` 25–151,
  `wght` 1–1000, `GRAD` **0–100**, `ROND` 0–100, `slnt` −10–0. Because `GRAD` has no negative
  range, the platform clamps the dark theme's −25 to 0, so only emphasized styles get lighter in
  dark (25 → 0). **Open question for the owner:** keep −25 (a no-op until a file with negative
  grades ships) or shift the scale up (light 25 / dark 0, emphasized +25).

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
  small: a regular style holds its weight and its emphasized weight (what a bold span resolves to,
  instead of a synthesized bold), an emphasized style only its own weight, each upright and
  italic. The typography is built once per light/dark and cached (`core/ui/.../theme/Type.kt`).
- `pingogramTypography(dark)` builds the **full M3 Expressive type scale** (display, headline,
  title, body, label × large/medium/small = 15 styles) **and all 15 `*Emphasized` variants**
  from the `Typography` constructor parameters, with `GRAD = if (dark) -25 else 0`.
- Baseline mapping (weight / opsz / ROND): display 400 / 57–36 / 100 · headline 500 / 32–24 / 100 ·
  title 600 / 22–14 / 100 · body 400 / 16–12 / 0 · label 500 / 14–11 / 0 (wdth 95 for labelSmall).
  Emphasized: weight +200 (capped 800), GRAD +25.
- Line heights and letter spacing: use the M3 token values from material.io; `lineHeightStyle`
  = `LineHeightStyle(Alignment.Center, Trim.None)`; `platformStyle = PlatformTextStyle(includeFontPadding = false)`.
- Font scaling: all sizes in `sp`; the chat "message text size" setting multiplies `bodyLarge`
  only (0.85–1.3) and re-derives `opsz`. Non-linear font scaling (Android 14) is respected.
- Fallbacks: Android's font fallback chain renders glyphs Google Sans Flex lacks (CJK, Arabic,
  Devanagari, emoji) with the system fonts and Noto; no font files are bundled for them.
- Tabular figures for times/counters: `fontFeatureSettings = "tnum"`.

### 3.3 Style assignment

| Element | Style |
|---|---|
| Large collapsing app bar titles | `displaySmallEmphasized` collapsing to `titleLargeEmphasized` |
| Screen titles | `titleLargeEmphasized` |
| Chat list title | `titleMediumEmphasized` when unread, `titleMedium` when read |
| Chat list preview / message text | `bodyMedium` / `bodyLarge` |
| Timestamps, badges, meta | `labelSmall` (wdth 95, `tnum`) |
| Buttons | `labelLargeEmphasized` |
| Section headers in settings | `titleSmallEmphasized` in `primary` |
| Empty state title / body | `headlineSmallEmphasized` / `bodyMedium` |
| Code blocks in messages | `bodyMedium` + `FontFamily.Monospace` (system) |

## 4. Shape system

- Shape scale (M3 Expressive): `extraSmall 4` · `small 8` · `medium 12` · `large 16` ·
  `largeIncreased 20` · `extraLarge 28` · `extraLargeIncreased 32` · `extraExtraLarge 48` · full = pill.
- `MaterialShapes` (the 35 expressive polygons) are the only source of non-rounded-rect shapes:
  avatar placeholders (`Cookie9Sided`, `Clover8Leaf`, `Sunny`, `Pill`…), story rings, reaction
  bursts (`Burst`/`SoftBurst`), QR login frame (`Cookie12Sided`), loading indicator polygons,
  empty-state illustrations, call screen avatar (`Boom` morph on ringing).
- **Shape morphing** (`Morph`, `MaterialShapes.*.toShape()`, `animateFloatAsState` with a
  spatial spring): FAB press (`Circle → Cookie9Sided`), toggle buttons (`Circle ↔ Square`),
  selected avatar, record button while recording, reaction added.
- Message bubbles: `large` with the tail-side corner `small`; grouped messages use `medium`
  between members of a group; user-adjustable 12–24 dp in Appearance settings.
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
- Navigation transitions: Material shared-axis X for sibling screens, shared-axis Y for
  vertical flows (auth), fade-through for tab switches and chat folder switches, container
  transform via `SharedTransitionLayout` for avatar → profile, thumbnail → media viewer, FAB → new
  chat.
- Predictive back: screens use Navigation's default M3 predictive back; custom surfaces
  (media viewer, attach sheet, selection toolbar) implement `PredictiveBackHandler` and scale
  to 90% with the Expressive back-progress curve.
- Lists: `Modifier.animateItem()` on every `LazyColumn` item; new message enters with a spatial
  spring from the bottom; swipe actions reveal icons with scale springs; pull/overscroll uses
  the stretch overscroll default.
- Loading: `LoadingIndicator` (morphing polygons) for indeterminate, `LinearWavyProgressIndicator`
  / `CircularWavyProgressIndicator` for determinate (uploads, downloads, voice playback).
- Micro-interactions with haptics (`LocalHapticFeedback`): `LongPress` (selection, reaction
  picker), `TextHandleMove` (swipe threshold), `Confirm` (sent) / `Reject` (failed) on API 34+,
  `SegmentTick` for slider steps. No raw `Vibrator` patterns.
- Reduced motion: if animator duration scale is 0, replace springs with snaps and skip shape
  morphs; essential information is never conveyed by motion alone.
- Performance rule: animate with `graphicsLayer {}` / `Modifier.offset {}` lambdas, never by
  recomposing layout for each frame (see `docs/PERFORMANCE.md`).

## 6. Component mapping (screen → M3 Expressive components)

| UI element | Component |
|---|---|
| Chat list top bar | `MediumFlexibleTopAppBar` (collapsing to small); search opens from the navigation's search button |
| Global search | Full-screen destination (`SearchRoute`) with the M3 `SearchBar` expanded; opened from the search button of the floating navigation bar or the rail |
| Conversation top bar | `TopAppBar` small, avatar + two-line title; `DropdownMenu` overflow |
| Folder tabs | `ButtonGroup` connected `ToggleButton`s with `animateWidth` press physics, in a horizontal scroll so the group never overflows into its menu; selected label `labelLargeEmphasized`; fallback `PrimaryScrollableTabRow` if > 6 |
| Main navigation | **Compact: floating navigation bar** (§6.1). Medium/expanded: `NavigationSuiteScaffold` → `WideNavigationRail` collapsed, the search `FloatingActionButton` in its header |
| New chat / new group | `FloatingActionButtonMenu` with `ToggleFloatingActionButton`; in compact windows bottom-end, above the floating navigation bar |
| Selection mode | `HorizontalFloatingToolbar` (expressive, `FloatingToolbarDefaults.vibrantFloatingToolbarColors()`) |
| Composer | M3 `TextField` (outlined, `extraLarge` corners) inside `Surface(surfaceContainer)`; send = `SplitButton` (send / schedule · silent); voice record = `FilledIconButton` → morphs to pill with wavy indicator |
| Attach menu | `ModalBottomSheet` + `HorizontalMultiBrowseCarousel` of recent photos + `ButtonGroup` of options |
| Message actions | `ModalBottomSheet`; quick reactions as `ToggleButton` row with shape morph |
| Reactions picker | `HorizontalUncontainedCarousel` of reaction chips; full grid in sheet |
| Chat list row | `ListItem` (leading avatar, trailing time + badge); swipe `SwipeToDismissBox` |
| Chips (filters, mentions) | `FilterChip`, `AssistChip`, `SuggestionChip` |
| Settings rows | `ListItem` in `Card(colors = surfaceContainer)`; `Switch`, `Slider` (expressive track/thumb), `SegmentedButton`, `RadioButton` |
| Buttons | M3 Expressive sizes: `ButtonDefaults.ExtraSmall…ExtraLarge` container heights; `FilledTonalButton` for secondary, `OutlinedButton` rare, `TextButton` in dialogs |
| Icon buttons | `IconButton`/`FilledIconButton` with Expressive shapes (`IconButtonDefaults.*Shape`) and sizes; toggleable ones use `IconToggleButton` with shape morph |
| Progress | `LoadingIndicator`, `ContainedLoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| Dialogs | `AlertDialog` for destructive confirmations only; `BasicAlertDialog` for custom |
| Snackbars | `SnackbarHost` with M3 `Snackbar`; errors are never dialogs |
| Media viewer | full-bleed `Scaffold`, `HorizontalPager`, `FlexibleBottomAppBar` actions, container-transform entry |
| Profile header | `LargeFlexibleTopAppBar` + shape-morphing avatar (`MaterialShapes`), `HorizontalUncontainedCarousel` of photos |
| Tooltips / onboarding | `TooltipBox` with `RichTooltip` |
| Date pickers (schedule) | `DatePicker` + `TimePicker` in `DatePickerDialog` |
| Navigation drawer | not used (folders live in tabs) |
| Empty states | `MaterialShapes` illustration + `headlineSmallEmphasized` + `bodyMedium` + `Button` |

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
  TalkBack.
- **Search**: `FloatingToolbarDefaults.StandardFloatingActionButton` beside the pill, a circle in the
  toolbar's own colors, opening global search.
- **Motion**: the indicator fades in on `defaultEffectsSpec` (read in the draw phase); the icon slides
  in with `fastSpatialSpec` + `fastEffectsSpec`; the bar slides away on screens pushed above a tab
  and back on return (`defaultSpatialSpec` in, `fastSpatialSpec` out).
- **Layout**: the bar floats over the top-level screens, which draw behind it. Their `Scaffold` uses
  `screenContentWindowInsets()` (`core:ui`), which adds `LocalNavigationBarInsets` (the bar, its
  margin below and the same margin above), so lists end clear of the bar.
- **Stepping aside**: a top-level screen that shows a floating toolbar of its own in the bar's place
  (the chat list's selection toolbar, as Google Photos does while selecting) hides the bar with
  `HideNavigationBar(hidden)` (`core:ui`, honoured by `PingogramApp`). The bar returns as soon as the
  screen stops asking or leaves the composition; `LocalNavigationBarInsets` stays meanwhile, and the
  toolbar sits where the bar was (`floatingToolbarInsets()`).
- Medium and wider windows keep the `WideNavigationRail`; the bar is for compact windows only.

## 7. Layout

- `Scaffold` per screen; `contentWindowInsets`, `innerPadding`; `enableEdgeToEdge()`;
  transparent system bars; `viewport-fit`-style safe handling of display cutouts.
- Spacing scale 4/8/12/16/24/32 dp; margins 16 dp compact, 24 dp medium+. Feature code reads
  them as tokens from `MaterialTheme.spacing` (`extraSmall`…`extraExtraLarge`,
  `windowMarginCompact`, `windowMarginMedium`), never as `dp` literals.
- Window size classes via `currentWindowAdaptiveInfo()`: **compact** single pane + floating navigation bar (§6.1);
  **medium** navigation rail; **expanded** `ListDetailPaneScaffold` (chats | conversation),
  `SupportingPaneScaffold` for profile/shared media. Canonical layouts only.
- Pane behaviour on foldables: `FoldingFeature`-aware hinge avoidance (`material3-adaptive`).
- Touch targets ≥ 48 dp (`Modifier.minimumInteractiveComponentSize()`); 8 dp min spacing
  between targets.
- RTL: `start/end`, mirrored icons where M3 says so (`autoMirrored`).

## 8. Iconography & imagery

- **Material Symbols Rounded**, weight 400, filled for selected nav items; import as vector
  drawables from the Material Symbols site with the `ROND`-matching rounded style. Size 24 dp.
- Launcher icon: adaptive (foreground / background / **monochrome** layers), themed icon
  support; splash via `core-splashscreen` using the same vector. Notification small icon:
  monochrome 24 dp glyph.
- Avatars: circle by default; `MaterialShapes` only for placeholders and states.
- Images: `large` corner radius inside bubbles, `extraLarge` in media viewer thumbnails.

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
  `GRAD −25` on Google Sans Flex; images get a 0.9 `ColorFilter` dim only in media-heavy lists.
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
- [ ] Google Sans Flex styles per §3.3; `opsz` equals size; emphasized styles where specified; dark `GRAD −25`
- [ ] Shapes from the scale or `MaterialShapes`; at least one meaningful shape morph where the screen has a primary action
- [ ] All animations use `motionScheme` specs; shared-element / container transform where listed in §5
- [ ] Components match the §6 mapping (no custom lookalikes)
- [ ] Edge-to-edge, insets, predictive back, adaptive layout at compact/medium/expanded
- [ ] Accessibility §9 checks pass; haptics per §5
- [ ] Reduced motion path verified
- [ ] Performance budget in `docs/PERFORMANCE.md` met (no jank in the screen's Macrobenchmark)

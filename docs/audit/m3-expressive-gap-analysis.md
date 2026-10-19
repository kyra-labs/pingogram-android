# Material 3 Expressive gap analysis

Audit date: 2026-10-19 · App state: `main` at `2dcf9f3` (v0.3.0-beta.2) · material3 `1.5.0-beta01`, Compose BOM
`2026.09.00`, adaptive `1.3.0` (from the BOM), navigation suite `1.5.0-beta01`.

This report compares Material 3 Expressive, as Google defines it on m3.material.io and ships it in Compose and in its
own apps today, with Pingogram's screens, shared components and `docs/DESIGN_SYSTEM.md`. It is research and analysis
only: nothing in the app changed. Each finding has an ID (`G01`…), a priority, an effort, the files involved, the
Compose API to use, a source and, where needed, an owner decision (`D1`…). Another session implements them.

Contents

1. [Overview](#1-overview)
2. [Prioritized backlog](#2-prioritized-backlog)
3. [Decisions needed from the owner](#3-decisions-needed-from-the-owner)
4. [Material 3 Expressive today](#4-material-3-expressive-today)
5. [Details per screen and component](#5-details-per-screen-and-component)
6. [Review of `DESIGN_SYSTEM.md` and the other docs](#6-review-of-design_systemmd-and-the-other-docs)
7. [Compose APIs: what exists, what we use, what to adopt](#7-compose-apis-what-exists-what-we-use-what-to-adopt)
8. [Cross-cutting: accessibility, motion, adaptive, performance](#8-cross-cutting-accessibility-motion-adaptive-performance)
9. [Method and limits](#9-method-and-limits)
10. [What Material's figures show](#10-what-materials-figures-show)
11. [Sources](#11-sources)

---

## 1. Overview

### Verdict

Pingogram is already a careful Material 3 Expressive app at the token level. `MaterialExpressiveTheme` with
`MotionScheme.expressive()`, the full expressive shape scale, all 30 type styles in Google Sans Flex with per-style
variable axes, color roles everywhere, springs from `MaterialTheme.motionScheme` only, `@OptIn` per composable,
expressive `ListItem`/`SegmentedListItem`, `ButtonGroup`, `SplitButtonLayout`, `HorizontalFloatingToolbar`,
`LoadingIndicator` and wavy progress, a container transform into the media viewer, predictive back with our own
transitions, and strong TalkBack semantics. No feature module has a literal color, size or duration.

The gaps are of four kinds:

1. **Missing Expressive moments and components that Google's guidance and apps rely on.** No FAB or new-chat entry on
   the chat list (so no primary action and no FAB morph), shared-axis transitions never used (everything fades
   through), no filter chips in search, no expanded rail on large windows, no draggable pane divider, no tooltips.
2. **Places where we follow our own spec but Google has moved on.** Segmented buttons are deprecated in Expressive
   (connected button groups replace them), the outlined composer field next to Google Messages' filled pill, the
   selected chat row as a full pill where the expressive list uses `large` corners, a FAB menu planned beside a
   floating toolbar (which Material says not to do), an overflow menu in the conversation's app bar (Material says to
   avoid them), the full-screen search on tablets (Material docks it from medium width up).
3. **Visible defects found in the goldens.** The send split button starves the composer field (unusable at 200%
   font), reaction badges with 28 dp touch targets, avatars on `*Fixed` roles glaring in dark theme, folder tabs
   clipped mid-button, the media viewer's corners snapping at the end of the return transform, the auth CTA below the
   fold in phone landscape, undelayed loading indicators that the spec forbids.
4. **Requirements outside design that the audit ran into.** Sponsored messages are not shown in channels
   (`REQUIREMENTS.md` §2 makes this mandatory), and there is no OSS licenses screen (`DESIGN_SYSTEM.md` §3.1 requires
   the font's OFL entry there).

### Scorecard

| Screen / area | Tokens | Components | Motion | Adaptive | A11y | Biggest gap |
|---|---|---|---|---|---|---|
| Theme (`core:ui`) | ✅ | ✅ | ✅ | — | ✅ | Muted brand palette; `*Fixed` avatar colors in dark (G07, G43) |
| App shell / navigation | ✅ | ⚠️ | ⚠️ | ⚠️ | ✅ | Fade-through everywhere (G03); rail never expands (G16) |
| Chat list | ✅ | ⚠️ | ✅ | ⚠️ | ✅ | No FAB / new-chat entry (G02) |
| Search | ✅ | ⚠️ | ⚠️ | ⚠️ | ✅ | No filters, flat results, not docked on tablets (G19) |
| Conversation | ✅ | ⚠️ | ✅ | ⚠️ | ⚠️ | Composer layout (G05, G20); 28 dp reactions (G06); sponsored (G01) |
| Media viewer | ✅ | ⚠️ | ⚠️ | ⚠️ | ✅ | Corner snap (G08); sparse chrome (G38) |
| Auth | ✅ | ⚠️ | ✅ | ❌ | ✅ | Landscape CTA below fold, no two-pane (G09) |
| Settings | ✅ | ⚠️ | ✅ | ❌ | ✅ | Deprecated segmented buttons (G13); no list-detail (G18) |
| Contacts / profile | ✅ | ❌ | — | — | ✅ | Placeholder / not started (M4) |

✅ matches · ⚠️ partly · ❌ missing.

### Top 15 changes

The fifteen changes with the most effect on how Google-like the app feels, plus the one compliance gap:

1. **G01** Show sponsored messages in channels (compliance, not design, but mandatory).
2. **G02** Give the chat list its primary action: a medium FAB "New chat" with a container transform (D1).
3. **G05 + G20** Rebuild the composer the Google Messages way: filled tonal pill, emoji inside, tonal attach, compact
   icon send that leaves the field its width, capped width on tablets.
4. **G03** Shared-axis X for pushed screens, keeping fade-through for tabs and folders.
5. **G15 + G16** Tablet layouts: panes in their own containers, aligned headers, drag handle, an expanded rail on
   large windows, one search affordance.
6. **G13 + G14** Settings controls: connected button groups instead of segmented buttons, switches with a check thumb.
7. **G06** 48 dp touch targets for reaction badges.
8. **G07** Avatar placeholders that do not glare in dark theme (and fewer silhouettes, D4).
9. **G19** Search with filter chips, grouped result sections, one progress indicator and a docked layout on tablets.
10. **G21** Message actions anchored to the lifted message, as Google Messages does since September 2026 (D3).
11. **G04 + G10** Spec hygiene: delayed loading indicators everywhere, motion specs only from the motion scheme.
12. **G09 + G29** Auth: two panes on wide and landscape windows, larger expressive headlines.
13. **G17 + G18** Settings: large collapsing page titles and list-detail on expanded windows.
14. **G24 + G25** Selection and swipe: small contextual bar, `large` selected rows, a visible container while swiping.
15. **G08 + G38** Media viewer: correct corners in the return transform, labelled actions, width-capped chrome.

Added after the owner's feedback that the app looks too big: **G63–G69** (§5.10). These cover a smaller chat list
header, 48 dp avatars and one-line previews in chat rows, a 64 dp composer, tighter bubbles and default-height auth
buttons. Together they show about 15% more chats per screen.

---

## 2. Prioritized backlog

**Priority**: **Must** = breaks a requirement, a `DESIGN_SYSTEM.md` rule, an accessibility minimum or a legal
obligation, or is a visible defect. **Should** = brings the screen in line with current Material guidance or Google's
apps. **Nice** = polish.
**Effort**: **S** ≤ 1 day, **M** a few days, **L** a week or more / several PRs.
Paths are shortened: `chats/` = `feature/chats/src/main/kotlin/dev/kyralabs/pingogram/feature/chats/ui/`, and likewise
`chat/`, `media/`, `auth/`, `settings/`, `contacts/`; `ui/` = `core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/`;
`app/` = `app/src/main/kotlin/dev/kyralabs/pingogram/`.

### Must

| ID | Finding | Effort | Files | Compose API / approach | Source | Decision | Status |
|---|---|---|---|---|---|---|---|
| G01 | Sponsored messages are not shown in channels. No code references them, yet channels are readable. | L | `core:data` (message repository), `chat/` (list item) | TDLib `getChatSponsoredMessages` and the view/click/report functions of the pinned `td_api.tl`; a filled `Card` with a "Sponsored" label and its button, in the message list | `REQUIREMENTS.md` §2; [Telegram API terms][tg-terms] | D18 | Open |
| G02 | The chat list has no primary action: no FAB, no FAB menu (FR-L7, §6), so no FAB shape morph (§12) and no FAB → new chat transform (§5). "No chats yet" has no button. | M | `chats/ChatListScreen.kt`, `app/ui/PingogramApp.kt`, `ui/component/EmptyState.kt` | `MediumExtendedFloatingActionButton` (collapses to `MediumFloatingActionButton` on scroll) with `Modifier.animateFloatingActionButton`; container transform via `SharedTransitionLayout`; or `FloatingActionButtonMenu` + `ToggleFloatingActionButton` (stable in 1.5) | [m3 FAB][m3-fab], [m3 FAB menu][m3-fab-menu], [m3 extended FAB][m3-extended-fab]; [Google Messages start chat][9to5-gm-search] | D1 | Open |
| G03 | Every non-full-screen destination fades through (`PingogramNavHost.kt:258-311`). §5 asks for shared-axis X for pushed screens (conversation, archive, settings pages, search). `SharedAxisDistance` is unused outside auth. | M | `app/navigation/PingogramNavHost.kt`, `ui/theme/Motion.kt` | `slideInHorizontally`/`slideOutHorizontally` by `SharedAxisDistance` on `defaultSpatialSpec` + fades on effects specs, mirrored for pop; later `MaterialTransitions.HorizontalSharedAxis` (on androidx main, not released) | `DESIGN_SYSTEM.md` §5; [MDC motion][mdc-motion]; [androidx main API][ax-api-main] | D21 | Open |
| G04 | Loading indicators that show at once, against §5 and `PERFORMANCE.md` §4: chat list (`ChatListScreen.kt:180`, also without a content description), `SettingsLoading` (`SettingsStates.kt:50`), Appearance's loading state, `AuthProgressScreen`. | S | those files | `DelayedLoadingIndicator` (`ui/component`) | `DESIGN_SYSTEM.md` §5; [m3 loading indicator][m3-loading] (nothing under 200 ms) | D10 | Done |
| G05 | The send `SplitButtonLayout` is `MediumContainerHeight` (56 dp, `ComposerButtons.kt:51`) and about a third of a phone's width: with text the field wraps every few words, at 200% font it shows one or two words per line. | M | `chat/composer/ComposerButtons.kt`, `chat/composer/Composer.kt` | Icon-only send: `SplitButtonLayout` at `SplitButtonDefaults.SmallContainerHeight`/`ExtraSmallContainerHeight` with an icon leading button, or a `FilledIconButton(shapes = IconButtonDefaults.shapes())` with the schedule/silent menu on long-press | [m3 split button][m3-split]; [m3 text fields][m3-text-fields] (do not starve input); [Google Messages composer][9to5-gm-chat] | D2 | Blocked: M3 session |
| G06 | Reaction badges are 28 dp (`PingogramSizes.kt:76`) with a plain `clickable`: below the 48 dp minimum (`REQUIREMENTS.md` §6.3, §7 of the spec). | S–M | `chat/BubbleParts.kt`, `ui/theme/PingogramSizes.kt` | Keep the 28 dp visual; extend the hit area to 48 dp (`Modifier.minimumInteractiveComponentSize()` on a wrapper, or a larger pointer region) without moving the layout | [m3 accessibility][m3-a11y-elements]; `REQUIREMENTS.md` §6.3 | — | Open |
| G07 | Avatar placeholders on `primaryFixed`/`secondaryFixed`/`tertiaryFixed` keep their light tone in dark theme: three of seven chats show bright pastel blobs (`PingogramColors.kt:190-192`). In light theme the open chat's `primaryContainer` avatar almost disappears on its `secondaryContainer` row. | S | `ui/theme/PingogramColors.kt`, `DESIGN_SYSTEM.md` §2.2 | Container roles only (or `*FixedDim` with `on*FixedVariant` in dark); pick the open row's avatar pair against `secondaryContainer` | [m3 color roles][m3-color-roles] (fixed roles keep one tone in both themes; "don't use fixed colors where contrast matters") | D4 | Done |
| G08 | The media viewer's shared bounds morph to `shapes.large` (`MediaPage.kt:101`), but in the bubble the media is clipped to the bubble's shape (`extraLarge`/`extraSmall`, square under a caption): the corners snap at the end of the way back. §8 ("images: `large` inside bubbles") contradicts §4. | S | `media/MediaPage.kt`, `chat/MessageContentBody.kt`, `ui/transition/SharedMedia.kt`, `DESIGN_SYSTEM.md` §4, §8 | Pass the media's real clip shape (from `MessageBubbleShapes` and its position) as the counterpart shape; delete the §8 sentence | `DESIGN_SYSTEM.md` §4, §8 | — | Open |
| G09 | Auth on wide and short windows: in phone landscape "Start messaging" and "Next" are below the fold; on tablets one 560 dp column sits in empty space with a full-width 560 dp button. | M | `auth/components/AuthWindowLayout.kt`, `auth/AuthRoute.kt`, `auth/WelcomeScreen.kt` | Two panes (hero + title │ form + actions) when width ≥ medium or height is compact; actions pinned in the form pane | [m3 breakpoints][m3-breakpoints]; [m3 panes][m3-panes] | — | Open |
| G10 | The country list's `animateItem()` uses Compose's default specs, not the motion scheme (§5: specs come only from `MaterialTheme.motionScheme`). | S | `auth/CountryPickerSheet.kt` | `animateItem(fadeInSpec = defaultEffectsSpec(), placementSpec = defaultSpatialSpec(), fadeOutSpec = fastEffectsSpec())` as in `BlockedUsersList.kt` | `DESIGN_SYSTEM.md` §5 | — | Done |
| G11 | No OSS licenses screen. FR-S1 lists licenses in About, and §3.1 requires the Google Sans Flex OFL entry there. | M | `settings/AboutScreen.kt`, new licenses page | `SegmentedListItem` list of libraries; full text in a pushed page | `REQUIREMENTS.md` FR-S1; `DESIGN_SYSTEM.md` §3.1 | D17 | Done (#70) |

### Should

| ID | Finding | Effort | Files | Compose API / approach | Source | Decision | Status |
|---|---|---|---|---|---|---|---|
| G12 | Folder tabs: the connected group runs off the screen edge and is cut mid-button; a selected folder off screen is not scrolled into view. Material: groups never wrap, extra buttons go to a trailing overflow menu. | S–M | `chats/FolderTabs.kt` | Scroll the selected button into view (`BringIntoViewRequester`); or `ButtonGroup`'s `overflowIndicator` menu; or `PrimaryScrollableTabRow` when it does not fit | [m3 button groups][m3-button-groups] | D14 | Open |
| G13 | Settings single choices (theme, contrast) use `SingleChoiceSegmentedButtonRow` + `SegmentedButton`, which Expressive deprecates in favour of connected button groups. | S | `settings/AppearanceItems.kt:91-154` | `Row(spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween))` of `ToggleButton(shapes = ButtonGroupDefaults.connectedLeading/Middle/TrailingButtonShapes())`, as `FolderTabs.kt` does | [m3 button groups][m3-button-groups]; [m3 switch][m3-switch]; [m3 segmented buttons][m3-segmented] | — | Blocked: M3 session |
| G14 | Switches have no thumb icon. Material recommends a check (on) / no icon or X (off); Android 16 Settings shows it. | S | `settings/` switch rows, `chat/attach/PollFormSheet.kt` | `Switch(thumbContent = { if (checked) Icon(check, Modifier.size(SwitchDefaults.IconSize)) })` | [m3 switch][m3-switch]; [Android 16 QPR1][9to5-a16-qpr1] | — | Blocked: M3 session |
| G15 | List-detail on expanded windows: list sheet, gutter and detail share one surface, so pane edges vanish; the two sheet tops sit ~80 dp apart (medium bar vs small bar); no drag handle; the empty detail pane floats on bare background. | M | `app/ui/ChatsListDetail.kt`, `ui/component/ContentSheet.kt` | Panes as rounded containers on `surfaceContainer`; `ThreePaneScaffold` `paneExpansionState` + `VerticalDragHandle` + `Modifier.paneExpansionDraggable`, anchors at 360/412 dp; one header height for both panes | [m3 list-detail][m3-list-detail]; [m3 panes][m3-panes]; [adaptive release notes][dac-adaptive-release] | D9 | Open |
| G16 | The rail is always the collapsed `WideNavigationRail`, even at 1280 dp. Material replaces the drawer with the expanded rail on large/XL windows. The search affordance differs: grey circle on phones, `primaryContainer` rounded square on the rail. | M | `app/ui/PingogramApp.kt` | `NavigationSuiteType.WideNavigationRailExpanded` on large widths (or a menu button toggling `rememberWideNavigationRailState`); search as `ExtendedFloatingActionButton` when expanded; one color style for both | [m3 navigation rail][m3-nav-rail]; [m3 breakpoints][m3-breakpoints]; [nav suite API][ax-api-navsuite] | — | Open |
| G17 | Pushed settings pages use a small pinned bar; Android 16 Settings and Google's account pages use large collapsing titles. On wide windows the bar spans the window while the content is a centered 560 dp column. | S–M | `settings/SettingsComponents.kt:90-140` | `MediumFlexibleTopAppBar` (as Settings home) or `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior`; the bar width-capped with the content | [m3 app bars][m3-app-bars]; [Android 16 settings][9to5-a16-settings] | D8 | Blocked: M3 session |
| G18 | Settings has no list-detail on expanded windows; pages push full screen. Material names settings as a list-detail case, and two panes are fine at medium for low-density content. | M–L | `feature/settings/.../navigation/SettingsNavigation.kt`, `app/` | `NavigableListDetailPaneScaffold` with Settings home as the list | [m3 list-detail][m3-list-detail]; [m3 breakpoints][m3-breakpoints] | — | Blocked: M3 session |
| G19 | Search: no filter chips (§6 lists `FilterChip`), flat full-bleed results unlike the inset chat rows, two large `LoadingIndicator`s stacked while searching, a red error line without a container, no transition from the search button, full-screen even on tablets. | M | `chats/SearchScreen.kt`, `SearchResults.kt`, `SearchRows.kt` | `FilterChip(shapes = FilterChipDefaults.shapes())` row under the field; result sections as `SegmentedListItem` groups with gaps; one `LinearWavyProgressIndicator` under the field; error in an `errorContainer` segment; `ExpandedDockedSearchBarWithGap` or a docked list on medium+ | [m3 search][m3-search]; [m3 chips][m3-chips]; [Google Messages search][9to5-gm-search] | D6 | Open |
| G20 | Composer field: an `OutlinedTextField` with a full-strength outline reads as a form; the paperclip has no container; there is no emoji button and nothing in the trailing slot while empty; the composer spans 800+ dp on tablets. | M | `chat/composer/Composer.kt`, `ComposerButtons.kt`, `DESIGN_SYSTEM.md` §6 | `TextField(state, shape = TextFieldDefaults.roundedShape, colors = TextFieldDefaults.tonalColors())` (1.5.0-alpha21); emoji `IconButton` as leading icon; `FilledTonalIconButton` "+" for attach; mic in the trailing slot once voice recording lands; width cap like bubbles | [Google Messages composer][9to5-gm-chat]; [m3 text fields][m3-text-fields]; [release notes][dac-m3-release] | D2 | Blocked: M3 session |
| G21 | Message actions open a generic `ModalBottomSheet` with no preview of the message. Google Messages (stable September 2026) lifts the message over a blurred background with reactions above and an action menu below. | L | `chat/MessageActionsBottomSheet.kt`, `chat/ChatScreen.kt` | `DropdownMenuPopup` + `DropdownMenuGroup` (expressive menus) anchored to the bubble; blur via `RenderEffect` (API 31+) over a `scrim`; keep the sheet for TalkBack and small heights | [Google Messages menu][9to5-gm-menu]; [m3 menus][m3-menus] (bottom sheets on compact) | D3 | Blocked: M3 session |
| G22 | A failed send shows only a small `error` icon. §2.2 asks for `errorContainer`; Google Messages shows "Not sent" with tap to retry. No `Reject` haptic. | S | `chat/BubbleMeta.kt`, `chat/MessageBubble.kt` | `errorContainer` label row "Not sent · Tap to retry" under the bubble; `HapticFeedbackType.Reject` (API 34+) | `DESIGN_SYSTEM.md` §2.2, §5 | — | Open |
| G23 | Conversation top bar: no actions, title not tappable, name in plain `titleMedium`. §6 plans a `DropdownMenu` overflow, which Material says to avoid in app bars (one or two actions, more in a toolbar). | S (UI) | `chat/ChatTopBar.kt`, `DESIGN_SYSTEM.md` §6 | Up to two filled icon buttons (call later, search in chat); title opens the profile (M4) or a supporting pane; `titleMediumEmphasized` | [m3 app bars][m3-app-bars]; [Google Messages chat][9to5-gm-chat] | — | Open |
| G24 | Chat list selection: the medium bar shows "3 selected" as a huge headline; selected rows become full pills (bulky on 3-line rows, nearly touching); toolbar buttons have no tooltips. | S | `chats/ChatListTopBar.kt`, `chats/ChatListItem.kt`, `chats/SelectionToolbar.kt` | Small `TopAppBar` while selecting; `ListItemDefaults.shapes(selectedShape = shapes.large)`; `TooltipBox` + `PlainTooltip` (`@ExperimentalMaterial3Api`) on toolbar buttons | [m3 lists][m3-lists] (selected = 16 dp); [m3 tooltips][m3-tooltips] | D5 | Open |
| G25 | Swipe actions: the dragged row has no container, so its text slides with a square edge against the rounded action; only read/unread and archive exist; nothing visible hints at the hidden actions. | M | `chats/ChatRow.kt` | Give the row its `large` container while dragged (as when pressed); add pin/mute/delete, configurable later; a full swipe triggers the end action | [m3 lists][m3-lists] (swipe guidance); [Gmail swipe][9to5-gmail-containers] | — | Open |
| G26 | Formatted text and placeholders: inline code and `pre` on square blocks, spoilers as flat rectangles, custom emoji as dark dots, unloaded stickers as hard-edged pink squares. | S–M | `chat/BubbleParts.kt`, `chat/BubbleTheme.kt`, `chat/content/` sticker | Rounded `small` backgrounds per line range (`drawBehind` on the text layout); spoiler with a tonal noise or blur; transparent or rounded tonal sticker placeholder; a loading glyph for custom emoji | `DESIGN_SYSTEM.md` §4, §11 | — | Open |
| G27 | Window margins stay 16 dp on medium+ (§7: 24 dp) in the chat list and settings; the conversation list uses 12 dp at every width. | S | `chats/ChatListTopBar.kt:112`, `chats/FolderTabs.kt:100`, `settings/SettingsComponents.kt`, `chat/ChatScreen.kt:234` | `MaterialTheme.spacing.windowMarginMedium` by window class, read once per screen | `DESIGN_SYSTEM.md` §7; [m3 spacing][m3-spacing] | — | Open |
| G28 | No haptics in auth and settings; none on failures. §5 lists `Confirm`/`Reject`, `SegmentTick`. | S | `auth/`, `settings/`, `chat/` | `Confirm` on code accepted, `Reject` on wrong code/password and failed send, `ToggleOn/Off` on switches, `SegmentTick` on choice groups | `DESIGN_SYSTEM.md` §5 | — | Open |
| G29 | Auth step titles are a small `titleLargeEmphasized` line; about 60% of each screen is empty. Google's sign-in flows lead with large headlines and a shape. | M | `auth/components/AuthComponents.kt:36-61` | `headlineMediumEmphasized` (or a `LargeFlexibleTopAppBar` title) with a `MaterialShapes` icon badge per step, as the QR step already has | [m3 type tokens][m3-type-tokens]; [m3 shape principles][m3-shape-principles] | — | Open |
| G30 | Country field is a `ListItem` posing as a text field (16 dp corners next to filled fields, no dropdown arrow); the picker's search is a plain `TextField`; the current country is not marked. | S–M | `auth/PhoneNumberScreen.kt:82-105`, `auth/CountryPickerSheet.kt:56-102` | Read-only `TextField` with `ExposedDropdownMenuDefaults.TrailingIcon` opening the sheet; `SearchBarDefaults.InputField`; `ListItem(selected = …)` | [m3 text fields][m3-text-fields]; [m3 search][m3-search] | — | Open |
| G31 | Code entry: email and recovery codes have no OTP autofill hint; the code field is a plain left-aligned field. | S | `auth/CodeScreen.kt`, `auth/EmailScreens.kt:106-112`, `auth/PasswordScreens.kt:101-107` | Email OTP autofill hint; centered `headlineSmall` with `tnum` and wider tracking | `DESIGN_SYSTEM.md` §3.2 (`tnum`) | — | Open |
| G32 | Text field styles are mixed: filled in auth, outlined inside rounded segments in Edit profile (box in a box). | S | `settings/EditProfileSections.kt:179-218`, `auth/components/AuthTextFields.kt` | One style app-wide; candidate: `TextFieldDefaults.roundedShape` + `tonalColors()` directly on the page | [m3 text fields][m3-text-fields] (don't mix in one region) | D24 | Blocked: M3 session |
| G33 | Settings leading icons are plain monochrome glyphs. Android 16 Settings, the Google Account page and Chrome settings put icons in colored tonal circles. | M | `settings/SettingsGroups.kt` | A small tonal circle (`*Container` role per category) behind the 20 dp icon | [Android 16 QPR1][9to5-a16-qpr1]; [Google Account][9to5-account] | D7 | Blocked: M3 session |
| G34 | The Contacts tab has no top bar and sits on `background`, unlike Chats and Settings; "No contacts yet" reads as if the user has none. | S | `contacts/ContactsScreen.kt:19-34` | `MediumFlexibleTopAppBar` + `ContentSheet` like Chats; copy "Contacts are coming in a later version" | `DESIGN_SYSTEM.md` §2.2 | — | Open |
| G35 | 84 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` (plus 8 combined). In 1.5.0-beta01 only `MaterialShapes`, `toShape`/`toPath`, `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults` and `PullToRefreshDefaults.LoadingIndicator` need it. CLAUDE.md asks for "the exact annotation required". | S | all modules | Remove the stale opt-ins; keep `@ExperimentalMaterial3Api` for sheets, tooltips, `AppBarWithSearch`, `TopAppBar` scroll APIs that still carry it | [material3 1.5.0-beta01 API][ax-api-beta01] | — | Open |
| G36 | Previews and goldens cover light, dark and 200% font only. The §12 audit asks for dynamic, AMOLED, medium and high contrast; nothing covers RTL or 600–839 dp. | M | `ui/preview/PingogramPreviews.kt`, screenshot tests | Preview annotations and one golden per screen for AMOLED, high contrast, RTL and `w700dp` | `DESIGN_SYSTEM.md` §12 | — | Open |
| G37 | Floating navigation bar: tapping the active tab does not scroll to the top; the indicator only fades in, while Material's expands along one axis. | S | `app/ui/PingogramNavigationBar.kt`, `chats/ChatListScreen.kt` | Re-select → `animateScrollToItem(0)`; indicator width on `fastSpatialSpec` in `graphicsLayer`/layout | [m3 navigation bar][m3-nav-bar] | — | Open |
| G38 | Media viewer chrome is two unlabelled icons (share, save); no forward, delete, show in chat or more; caption and slider span 840 dp on tablets. | M | `media/MediaActionsBar.kt`, `media/MediaChrome.kt` | `FlexibleBottomAppBar` with more actions (or a `HorizontalFloatingToolbar` as Google Photos' viewer), overflow `DropdownMenuPopup`; width cap | [Google Messages viewer][9to5-gm-viewer]; [m3 toolbars][m3-toolbars] | D20 | Open |
| G39 | Empty and error states: "No chats yet" is a dead end until G02; the error illustration is the spiky `Burst`, which reads as alarm; illustrations never move. | S | `ui/component/EmptyState.kt`, `chats/ChatListScreen.kt:476-542` | An action `Button` at `ButtonDefaults.MediumContainerHeight`; a soft shape for errors; optional slow rotation in `graphicsLayer`, off with reduced motion | [m3 shape principles][m3-shape-principles]; [M2 empty states][m2-empty] | — | Open |
| G40 | Text is not selectable (FR-C3); no swipe to reply. Google Messages has both (second long-press selects; swipe right replies, stable September 2026). | M | `chat/MessageBubble.kt`, `chat/ChatScreen.kt` | `SelectionContainer` on the lifted message; `AnchoredDraggableState` swipe with `TextHandleMove` haptic at the threshold | [Google Messages features][9to5-gm-features] | — | Open |
| G41 | Every bubble carries its own time, often on its own line: denser and taller than Google Messages, which shows times per group and on a swipe. | M | `chat/BubbleMeta.kt`, `chat/MessageBubble.kt` | Time on the last bubble of a group (or on tap); full times in the semantics | [Google Messages features][9to5-gm-features] | D15 | Open |
| G42 | Reply quotes and link previews use a colored leading bar, and voice uses a bar waveform: the most Telegram-like elements. §11 says not to reproduce Telegram's styling. | S–M | `chat/BubbleParts.kt` (`QuoteBlock`), `chat/content/Waveform.kt` | Tonal rounded quote card without the bar; waveform kept or a `LinearWavyProgressIndicator`-style scrubber | `DESIGN_SYSTEM.md` §11 | D16 | Open |
| G43 | The brand fallback palette is `SchemeTonalSpot`, which turns the icon's vivid `#2156C4` into a greyish `#495D92` primary. | S | `tools/theme/generate.mjs`, `ui/theme/Color.kt` | Generate `SchemeVibrant`/`SchemeExpressive`/`SchemeFidelity` side by side and choose | [m3 color system][m3-color-system] | D12 | Done |
| G44 | `DESIGN_SYSTEM.md` has outdated or self-contradictory rules (see §6). | S | `docs/DESIGN_SYSTEM.md`, `CLAUDE.md`, `REQUIREMENTS.md` | Doc PR after the owner decisions | §6 | several | Open |
| G63 | The chat list spends 176 dp on its header at rest (112 dp medium flexible bar + 64 dp folder row) before the first chat. | S | `chats/ChatListTopBar.kt`, `chats/ChatListScreen.kt` | Small `TopAppBar` with `titleLargeEmphasized`; `pinnedScrollBehavior` | [m3 app bars][m3-app-bars]; §5.10 | D25 | Open |
| G64 | Folder tab row is 64 dp for 40 dp buttons. | S | `chats/FolderTabs.kt` | `spacing.small` above and below (56 dp) | §5.10 | D26 | Open |
| G65 | Chat row avatars are 56 dp; Material's list avatar is 40 dp. | S | `ui/theme/PingogramSizes.kt`, `chats/ChatListItem.kt`, `chats/ArchiveRow.kt` | New `avatarRow` = 48 dp | [m3 lists][m3-lists]; §5.10 | D27 | Open |
| G66 | Two- and three-line previews make rows 79–94 dp apart and uneven. | S | `chats/ChatListItem.kt` | `maxLines = 1` preview (sender inline) → 72 dp rows | [m3 lists][m3-lists]; §5.10 | D28 | Open |
| G67 | Composer bar 72 dp with a 56 dp field and 56 dp buttons. | M | `chat/composer/Composer.kt`, `ComposerButtons.kt` | `BasicTextField` + `TextFieldDefaults.decorator` (48 dp pill, tonal colors); `IconButtonDefaults.smallContainerSize()`; send at `SplitButtonDefaults.SmallContainerHeight` | §5.10; [Google Messages composer][9to5-gm-chat] | D29 | Blocked: M3 session |
| G68 | Bubble text sits 16 dp from the bubble's sides. | S | `chat/MessageBubble.kt`, `DESIGN_SYSTEM.md` §4 | `spacing.medium` horizontal padding | §5.10 | D30 | Open |
| G69 | Auth main buttons are 56 dp and full width. | S | `auth/components/AuthComponents.kt` | Default `Button` height, bottom end; secondary `TextButton` bottom start; Welcome keeps the medium size | §5.10 | D31 | Open |
| G70 | No density check in the per-screen audit. | S | `DESIGN_SYSTEM.md` §12 | Add: ≥ 9 chat rows and one-line bubbles ≤ 40 dp on a 412 × 915 dp phone at 100% font | §5.10 | — | Open |

### Nice to have

| ID | Finding | Effort | Files | Compose API / approach | Source | Decision | Status |
|---|---|---|---|---|---|---|---|
| G45 | Splash: static icon, background fixed to the brand scheme even with wallpaper colors, no exit animation. | S–M | `app/src/main/res/values*/themes.xml`, `app/MainActivity.kt` | Animated vector icon; `setOnExitAnimationListener` scale/fade | [Android Show 2025][g-android-show] | — | Open |
| G46 | `isReducedMotion()` reads the setting once (`SharedMedia.kt:109`); shape morphs are not skipped explicitly (§5). | S | `ui/transition/SharedMedia.kt`, `ui/theme/Shape.kt` | Observe the animator scale; snap morphs when off | `DESIGN_SYSTEM.md` §5 | — | Open |
| G47 | Flexible top bars pick their title style by comparing `LocalTextStyle.current.fontSize` with `titleLarge` (`ChatListTopBar.kt:135`, `SettingsScreen.kt:213-224`): breaks if material3 changes the bar's styles. | S | those files | Read the bar's own row (expanded vs collapsed) from the scroll state, or pin it with a test | — | — | Open |
| G48 | Jump to bottom is a `SmallFloatingActionButton` on `secondaryContainer`; Expressive no longer recommends the small FAB, and §2.2 says FABs use `primaryContainer`. | S | `chat/JumpToBottomButton.kt` | `FloatingActionButton` (56 dp) or a `FilledTonalIconButton` at `mediumContainerSize`; write the choice into §6 | [m3 FAB][m3-fab] | — | Open |
| G49 | Typing is plain text ("Grace is typing…"); §5 lists typing dots on fast effects springs; the conversation header has no live region. | S | `chat/ChatTopBar.kt` | Three dots animated in `graphicsLayer`; `liveRegion = Polite` | `DESIGN_SYSTEM.md` §5, §9 | — | Open |
| G50 | Auth banners have no icon; flood-wait and resend countdowns are plain text. | S | `auth/components/AuthBars.kt` | Leading icon; `CircularWavyProgressIndicator(progress)` for the countdown | [m3 progress][m3-progress] | — | Open |
| G51 | Expressive inset focus rings (`RippleDefaults.InsetFocusRingThemeConfiguration`) exist; the theme pins the opacity focus, which equals the default. | S | `ui/theme/Theme.kt` | Switch to inset rings for keyboard users | [release notes][dac-m3-release] (alpha18) | D23 | Open |
| G52 | Phones in landscape and tabletop foldables get the floating bar (the suite returns `ShortNavigationBarMedium` for compact height), using scarce height; §7 says medium windows use the rail. | S | `app/ui/PingogramApp.kt:239-242` | Map compact height to `WideNavigationRailCollapsed` | [nav suite source][ax-api-navsuite]; [m3 breakpoints][m3-breakpoints] | D19 | Open |
| G53 | Delete and log-out dialogs render edge to edge in the goldens. Probably Robolectric's dialog window; check on a device that they keep 280–560 dp. | S | `chats/DeleteChatDialog.kt`, `settings/SettingsStates.kt` | Verify; nothing to change if fine | [m3 dialogs][m3-dialogs] | — | Open |
| G54 | AMOLED Settings: rows go black, the page stays grey. | S | `settings/SettingsComponents.kt:61-63` | Page `surface` (black) with rows on `surfaceContainer` under AMOLED | `DESIGN_SYSTEM.md` §2.1–2.2 | D22 | Blocked: M3 session |
| G55 | Blocked users: unblock is an "X" icon; numbers are unformatted. | S | `settings/BlockedUsersList.kt` | `TextButton` "Unblock"; TDLib's phone format | [m3 icon buttons][m3-icon-buttons] | — | Blocked: M3 session |
| G56 | Buttons without the Expressive press morph: `SettingsError` (`SettingsStates.kt:76`), new-login buttons (`SessionItems.kt:124-139`), auth back button. | S | those files | `shapes = ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()` | [m3 buttons][m3-buttons] | — | Open |
| G57 | Dark theme images are not dimmed to 0.9 in media-heavy lists (§10). | S | `chat/content/` | `ColorFilter` on the image in dark | `DESIGN_SYSTEM.md` §10 | — | Open |
| G58 | Chat rows vary between 2- and 3-line heights; at 200% font the meta column truncates titles hard. | S–M | `chats/ChatListItem.kt`, `chats/ChatMeta.kt` | One preview line in folders with many chats, or time on the title line at large scales | [m3 lists][m3-lists] | — | Open |
| G59 | `GRAD −25` in dark is a no-op (the font's `GRAD` is 0–100); high contrast does not use the optional `GRAD +25`. | S | `ui/theme/Type.kt`, `DESIGN_SYSTEM.md` §3, `CLAUDE.md` 4b | Drop −25 from the docs; optionally +25 in high contrast | `DESIGN_SYSTEM.md` §3.1 | D11 | Partly done: GRAD 0; the optional high-contrast +25 open |
| G60 | Performance rules: bubbles are nested `Column`/`Box`, not one custom `Layout` per type (`PERFORMANCE.md` §4); the viewer builds page descriptions in composition (`MediaViewerScreen.kt:343-429`). | L / S | `chat/MessageBubble.kt`, `media/MediaViewerScreen.kt` | Custom `Layout` per bubble frame; strings in `semantics {}` | `PERFORMANCE.md` §4, §10 | — | Open |
| G61 | Auth predictive back only scales the step; it does not seek the shared-axis step transition. | S–M | `auth/AuthRoute.kt:156-214` | `SeekableTransitionState` driven by the back progress | [predictive back design][dac-predictive-back] | — | Open |
| G62 | Material's typography (August 2026) adapts line heights to four script-height categories; nothing covers it. | S | `ui/theme/Type.kt`, `DESIGN_SYSTEM.md` §3 | Track until Compose exposes it; note in §3 | [m3 typography][m3-type] | — | Open |

---

## 3. Decisions needed from the owner

**Status (2026-10-19): decided.** The owner accepted every recommendation below ("go with your recommendations for
all decisions, it should follow Material 3 Expressive and Google standards"). D25–D32 were added afterwards for the
density findings (§5.10) under the same instruction. Where a recommendation offered a choice, the decision is the
first option named in it. The rules are now in `DESIGN_SYSTEM.md`, `REQUIREMENTS.md` (FR-L7, FR-C6), `CLAUDE.md`
rule 4b and `ROADMAP.md`.

Each decision lists the options and a recommendation. Findings that wait on it are named.

| ID | Question | Options | Recommendation | Blocks |
|---|---|---|---|---|
| D1 | How do people start a new chat? | (a) `FloatingActionButtonMenu` (new message / group / channel / contact), as §6 says today; (b) one medium extended FAB "New chat" opening a New chat screen with "New group", "New channel" and contacts, as Google Messages' Start chat | **(b)**. Material says not to pair a FAB menu with a floating toolbar or a rail, and our floating bar is a floating toolbar on phones and a rail on tablets; a single FAB is allowed with both. It also gives the container transform §5 asks for. Destinations depend on M4. | G02 |
| D2 | Composer look | (a) outlined field, 56 dp split send (today, §6); (b) Google Messages: filled tonal pill with emoji inside, tonal "+" attach, compact icon send (split menu on long-press or a small trailing toggle), mic when empty | **(b)** | G05, G20 |
| D3 | Message actions | (a) bottom sheet (today; Material suggests sheets on compact); (b) Google Messages' floating menu anchored to the lifted message over a blurred background | **(b)** for touch, keep (a) for TalkBack and very short windows | G21 |
| D4 | Avatar placeholders | (a) six `MaterialShapes` silhouettes and seven color pairs incl. `*Fixed` (today, §2.2, §4); (b) one silhouette or circles, container roles only; shapes kept for selection and states | **(b)**, or at least drop the `*Fixed` pairs. Google Messages and Contacts use circles; Material allows "circular or expressive shapes" for people, but six in one list reads as noise. | G07 |
| D5 | Selected chat row shape | (a) full pill (today, §4); (b) `large` (16 dp), the expressive list token | **(b)** | G24 |
| D6 | Global search on tablets | (a) full-screen destination everywhere (today); (b) docked results on medium+ (Material's default) | **(b)** in the list pane on expanded windows; the full-screen destination stays on phones | G19 |
| D7 | Tonal icon containers in Settings (Android 16 style)? | yes / no | **Yes**, one container color per group, not per row | G33 |
| D8 | Settings page titles | (a) small pinned (today); (b) medium flexible, as Settings home; (c) large flexible | **(b)**: same family as the home page | G17 |
| D9 | Panes on expanded windows | (a) flat, one surface (today); (b) each pane its own rounded container on `surfaceContainer` | **(b)**, as Google Messages and Gmail on tablets | G15 |
| D10 | Loading indicator delay | 150 ms (today) / 200 ms (Material) | **200 ms** | G04 |
| D11 | `GRAD −25` in dark (open question in §3.1) | keep / drop | **Drop** from §3, §10 and `CLAUDE.md` 4b: it is a no-op with the bundled font and Google's own styles use 0 | G59 |
| D12 | Brand scheme variant | TonalSpot (today) / Vibrant / Expressive / Fidelity | Compare all four on the goldens; Vibrant keeps the icon's blue closest. Compared in [images/brand-scheme-variants.png](images/brand-scheme-variants.png): Expressive turns `primary` green, Fidelity makes the containers saturated and `tertiary` orange; **Vibrant** chosen | G43 |
| D13 | Floating navigation bar labels | (a) unselected tabs label-only, icon-only when labels do not fit (today, like Google Photos); (b) Material's rule: icon and label on every item | **(a)**, documented as a deliberate deviation, since the bar itself is outside Material's component set | §6 |
| D14 | Folder tabs that do not fit | (a) scroll and bring the selected one into view; (b) overflow menu (Material); (c) switch to `PrimaryScrollableTabRow` | **(a)** with an edge fade; (c) above six folders as §6 already says | G12 |
| D15 | Message timestamps | each bubble (today) / last bubble of a group + on tap or swipe | **Per group** | G41 |
| D16 | Quote bars and bar waveform | keep / restyle | **Restyle** quotes as tonal cards; keep the waveform (it is information, not decoration) | G42 |
| D17 | How to build the licenses list | (a) our own Gradle task that collects POM licenses; (b) a third-party library (AboutLibraries) — needs approval under "ask before adding dependencies" | **(a)** | G11 |
| D18 | Sponsored message card | design review and Telegram ToS check by the owner | A filled `Card` labelled "Sponsored", the advertiser's button, and an "About" / report entry | G01 |
| D19 | Navigation on phones in landscape | floating bar (today) / rail | **Rail**: height is the scarce dimension | G52 |
| D20 | Media viewer actions | which actions (share, save, forward, delete, show in chat, more) and docked bar vs floating toolbar | Forward, share, save, delete in a floating toolbar, the rest in "More" | G38 |
| D21 | Shared-axis X now or wait for material3 1.6 `MaterialTransitions` | build in `core:ui` now / wait | **Build now** (small), swap when 1.6 is published | G03 |
| D22 | AMOLED Settings page | grey page, black rows (today) / black page, rows on `surfaceContainer` | **Black page** | G54 |
| D23 | Expressive inset focus rings | keep opacity focus / switch | **Switch**: clearer for keyboard users on tablets and ChromeOS | G51 |
| D24 | One text field style | filled / outlined / Compose 1.5's rounded tonal | **Rounded tonal** for the composer and Edit profile, filled for auth forms | G32 |
| D25 | Chat list app bar | medium flexible (today) / small | **Small** (64 dp): the chat list is a dense list, and it gives back ~1.5 rows | G63 |
| D26 | Folder tab row height | 64 dp (today) / 56 dp | **56 dp** | G64 |
| D27 | Chat row avatar | 56 dp (today) / 48 dp / 40 dp (Material's list token) | **48 dp** | G65 |
| D28 | Chat row preview lines | two (today) / one | **One** | G66 |
| D29 | Composer size | 72 dp bar, 56 dp field and buttons (today) / 64 dp bar, 48 dp pill, 40 dp buttons | **64 dp** | G67 |
| D30 | Bubble side padding | 16 dp (today) / 12 dp | **12 dp** | G68 |
| D31 | Auth buttons | medium, full width (today) / default height at the bottom end, as Google's sign-in | **Default**; Welcome keeps its medium hero button | G69 |
| D32 | `opsz` = size makes small text 5–9% wider than at the font's default | keep / pin `opsz` to 18 | **Keep**: it is how the font is designed, and the layout changes fix the density | §5.10 |

---

## 4. Material 3 Expressive today

### 4.1 What it is and why

- **Research basis.** Google calls M3 Expressive its most researched design update: 46 studies with more than 18,000
  participants over about three years, using eye tracking, surveys, experiments and usability tests. Expressive designs
  let people spot key elements up to 4× faster, and people over 45 found them as fast as younger people. A larger Send
  button placed above the keyboard was found 4× faster. The same research warns that removing labels hurt usability,
  that a "strong minority" prefers calmer designs, and that "no amount of emotion can compensate for a lack of clarity"
  ([Google Design research][g-research]; [launch post][g-launch]; [Android Show][g-android-show]).
- **Tools.** Color, shape, size, motion and containment are used to make key actions stand out and to group related
  things. On Wear, Google's developer guidance ranks expression in levels (foundational, excellent, transformative) and
  asks for "hero moments" ([Wear benefits][g-wear-benefits]; [levels of expression][g-wear-levels]). "Expressive can
  mean quiet. And it can also mean loud." ([Design Notes][g-design-notes]).
- **May 2025 launch.** New components: button groups, FAB menu, loading indicator, split button and toolbars. Also 35
  library shapes with built-in morphing, springs instead of durations and easing, and emphasized type styles.
  Retirements:
  - the baseline navigation bar, for the shorter flexible bar
  - the navigation drawer, for the expanded rail
  - the bottom app bar, for the docked toolbar
  - the baseline medium and large app bars, for the flexible ones
  - the small FAB
  - segmented buttons, for connected button groups

  ([9to5Google: navigation][9to5-nav]; [toolbars][9to5-toolbars]; [loading][9to5-loading];
  [m3 app bars][m3-app-bars]; [m3 FAB][m3-fab]; [m3 button groups][m3-button-groups]).
- **After launch.** Expressive menus came in November 2025: gaps instead of dividers, vibrant styles, and submenu shape
  morphs ([9to5Google][9to5-menus]; [m3 menus][m3-menus]). The expressive list followed in December 2025: segmented
  style, 2 dp gaps, selection highlight and shape morph ([m3 lists][m3-lists-overview]). The May 2026 layout update
  added a layout scaffold, spacing tokens on an 8 dp base, fixed panes of 360/412 dp, and renamed "window size classes"
  to "breakpoints" ([m3 layout][m3-layout-overview]; [breakpoints][m3-breakpoints]; [panes][m3-panes];
  [spacing][m3-spacing]; [I/O 2026][m3-io26]). Material Android is now "Compose-first" ([blog][m3-compose-first]).
  Typography gained script-aware line heights in August 2026 ([m3 typography][m3-type]).
- **Compose.** material3 1.4.0 (stable) has no Expressive API at all. 1.5.0-beta01 (2026-10-07) has all of it, and
  almost everything there is stable (§7) ([release notes][dac-m3-release]; [API file][ax-api-beta01]).

### 4.2 Foundations in numbers

| Foundation | Material 3 Expressive | Pingogram |
|---|---|---|
| Motion | Spatial springs, damping/stiffness: default 0.8/380, fast 0.6/800, slow 0.8/200. Effects springs never overshoot: 1.0/1600, 1.0/3800, 1.0/800. Speed by size: fast for small components, default for partial screens, slow for full screen ([tokens][ax-motion-tokens]; [MDC motion][mdc-motion]; [m3 motion][m3-motion]) | Uses the scheme only ✅; mapping per §5 ✅; transitions G03 |
| Shape | 10 corner steps: 0, 4, 8, 12, 16, 20, 28, 32, 48, full; 35 library shapes; morph on interaction; optical roundness for nested corners (inner = outer − padding) ([corner scale][m3-shape-scale]; [shape morph][m3-shape-morph]) | Full scale ✅; library shapes for placeholders and states ✅; nested media uses the bubble's corners, see G08 |
| Type | 15 base + 15 emphasized styles; emphasis for selection, unread, badges, primary actions ([type tokens][m3-type-tokens]; [applying type][m3-applying-type]) | All 30 styles in Google Sans Flex ✅; emphasized unread titles ✅ |
| Color | Roles, fixed roles (one tone in both themes), the surface container ladder, three contrast levels, dynamic color ([roles][m3-color-roles]; [system][m3-color-system]) | All ✅; avatar `*Fixed` misuse G07; palette variant G43 |
| States | Hover 8%, focus 10%, pressed 10%, dragged 16% ([state layers][m3-states]) | Material defaults ✅ |
| Elevation | Tonal surfaces; shadows for FABs, toolbars, menus, dialogs ([elevation][m3-elevation]) | ✅ |
| Layout | Compact < 600, medium 600–839, expanded 840–1199, large 1200–1599, XL ≥ 1600. Panes: one at compact and medium, two from expanded, up to three at XL; fixed panes 360/412 dp; 40–60 characters per line ([breakpoints][m3-breakpoints]; [list-detail][m3-list-detail]; [window size classes][dac-wsc]) | List-detail on expanded ✅; no large/XL behavior, no pane resizing (G15, G16) |
| Accessibility | 48 dp targets; 4.5:1 / 3:1 contrast; selection shown by two properties; labels describe the action ([elements][m3-a11y-elements]) | Strong; reaction targets G06 |

### 4.3 How Google's apps apply it (patterns relevant to a messaging client)

| Pattern | Google apps | Pingogram |
|---|---|---|
| Top bar on a background layer, content on a rounded-top container | Google Messages list and thread, Gmail, Chat ([GM chat][9to5-gm-chat]; [GM home][9to5-gm-home]; [roundup][9to5-roundup]) | ✅ `ContentSheet` |
| Lists as segmented containers with 2 dp gaps and rounder ends | Gmail (Sep 2025), Android 16 Settings, Chrome settings, Google Account ([Gmail][9to5-gmail-containers]; [Chrome][9to5-chrome-settings]; [Account][9to5-account]) | ✅ Settings; chat rows inset with gaps ✅ |
| Colored tonal icon circles in settings | Android 16 Settings, Google Account ([A16 QPR1][9to5-a16-qpr1]; [Droid Life][dl-a16-qpr1]) | ❌ G33 |
| Switches with check/X in the thumb | Android 16 Settings ([A16 QPR1][9to5-a16-qpr1]) | ❌ G14 |
| Selection by shape morph (rounded rectangle ↔ pill) | Messages Start chat, Quick Settings tiles, Phone in-call ([GM search][9to5-gm-search]; [Phone][9to5-phone]) | ✅ avatar morph, row shapes |
| Connected button group for tab-like switches in panels | Messages emoji/GIF/sticker panel, Fitbit ranges ([GM chat][9to5-gm-chat]; [Fitbit][9to5-fitbit-toolbar]) | ✅ folder tabs, ❌ settings (G13) |
| Attach menu as pill buttons in a container | Messages "+" menu ([AP][ap-gm-chat]) | ✅ `ButtonGroup` in a sheet |
| Message long-press: floating menu anchored to the message, blurred background, bouncy reposition | Messages, stable Sep 2026 ([menu][9to5-gm-menu]; [wide][9to5-gm-menu-wide]) | ❌ G21 |
| Swipe to reply, swipe for timestamps, receipts in a circle on the bubble | Messages, Sep 2026 ([features][9to5-gm-features]) | ❌ G40, G41 |
| Search with filter grid and results in cards | Messages ([GM search][9to5-gm-search]); testing a bottom search bar ([AA][aa-gm-shuffle]) | ❌ G19 |
| Media viewer with blurred backdrop and rounded photo | Messages, Dec 2025 ([viewer][9to5-gm-viewer]) | ⚠️ black backdrop, G38 |
| Floating pill of tabs + separate search circle | Google Photos, Jul 2026 ([Android][9to5-photos-android]; [iOS][9to5-photos-ios]) | ✅ §6.1 |
| FAB / extended FAB / FAB menu for "create" | Messages (Start chat), Gmail, Keep, Calendar, Drive ([Keep][9to5-keep]; [Calendar][9to5-calendar]; [Drive][9to5-drive]) | ❌ G02 |
| Loading indicator for short waits, wavy progress for long ones | Photos backup, Play Store, Fitbit ([Photos][9to5-photos-backup]; [Fitbit][9to5-fitbit]) | ✅ |
| Search app bar (avatar and menu outside a taller pill) on search-centric homes | Gmail, Keep, Drive ([Gmail search][9to5-gmail-search]) | — (search lives in the nav bar; fine) |
| Containers can go too far | 9to5Google's year-one review: over-containerized lists, oversized buttons; Drive dropped per-file containers; Fitbit dropped its floating toolbar ([recap][9to5-recap]; [Fitbit][9to5-fitbit-toolbar]) | Keep containment purposeful (D7 one color per group) |

---

## 5. Details per screen and component

Each subsection lists what matches, what is missing or deviates, and the IDs above.

### 5.1 Theme and tokens (`core:ui/theme`)

**Matches.**
- `MaterialExpressiveTheme` with `MotionScheme.expressive()`, `PingogramShapes` (full expressive scale), and
  `pingogramTypography(dark, fontWeightAdjustment)` (`Theme.kt:53-58`).
- The color scheme cross-fades in Oklab on `defaultEffectsSpec`.
- Dynamic color with the One UI hue check, system contrast on Android 14+, and AMOLED surfaces shifted one step
  (`ColorScheme.kt`, `DynamicColorScheme.kt`).
- 30 type styles with `opsz` = size, `ROND` 100 for the emphasized styles, `tnum` on labels, and bold-text adjustment
  (`Type.kt`). Typefaces are cached once per process (`GoogleSansFlexFont.kt`).
- `PingogramColors` is derived from the scheme. Spacing and size tokens exist.

**Gaps.**
- `*Fixed` avatar pairs glare in dark theme (G07).
- The brand palette is muted (G43).
- Dark `GRAD −25` has no effect, and high contrast never uses `GRAD +25` (G59).
- `windowMarginMedium` is defined but unused in the chats and settings screens (G27).
- `SharedAxisDistance` is used only by auth (G03).
- `isReducedMotion()` does not react when the setting changes (G46).
- Previews cover only light, dark and 200% (G36).
- The opacity focus configuration equals the default; inset focus rings are an option (G51).
- 84 stale Expressive opt-ins (G35).

**Compose to adopt.** `RippleDefaults.InsetFocusRingThemeConfiguration` (D23). `MaterialTransitions` once 1.6 ships.
Nothing else is missing at the theme root.

### 5.2 App shell and navigation (`:app`)

**Matches.**
- The floating navigation bar (§6.1) is built from `HorizontalFloatingToolbar` with
  `FloatingToolbarDefaults.StandardFloatingActionButton` for search.
- Tabs use `Role.Tab` in a `selectableGroup`; labels are measured, never truncated.
- It slides away on pushed screens in the graphics layer and stays composed.
- The rail comes through `NavigationSuiteScaffold` with `primaryActionContent`.
- `SharedTransitionLayout` wraps the `NavHost`, and predictive back pops at 90% with a fade
  (`Motion.kt:46-55`). This matches Android's guidance for full-screen surfaces: 90% scale with a fade-through,
  `(0.1, 0.1, 0, 1)` progress ([predictive back][dac-predictive-back]).
- List-detail uses `NavigableListDetailPaneScaffold`, with hinge avoidance from `calculatePaneScaffoldDirective`.

**Gaps.**
- **Transitions (G03).** Pushing the conversation, archive, search or a settings page fades through. Fade-through is
  right only for tab and folder switches (§5).
- **Container transforms.** Avatar → profile (M4) and FAB → new chat (G02) are missing. The search button → search
  transition is missing too (G19).
- **Rail (G16).**
  - It never expands on large or XL windows, which Material recommends.
  - The rail's search button style differs from the phone's.
  - The rail sits on `surface`, the Material default, while §2.2 says `surfaceContainer`. The doc should change, not
    the code (§6).
- **Landscape phones (G52).** They get the floating bar, because the suite returns `ShortNavigationBarMedium` for
  compact height. This contradicts §7's "medium: rail".
- **Navigation bar behaviour (G37).** Re-selecting the active tab does not scroll to the top, and the indicator does
  not expand along one axis.
- **List-detail (G15).** No pane containment, misaligned sheet tops, no drag handle. On the expanded empty shell, both
  panes show a full empty state side by side (two headlines competing).
- **Keyboard (§9).** No shortcuts such as Ctrl+F or `/` for search, or tab switching.

**Material vs our floating bar.** Material's navigation bar requires labels on every item, filled active icons and
outlined inactive ones, and full window width. It also says toolbars are for page actions, never primary navigation
([m3 navigation bar][m3-nav-bar]; [m3 toolbars][m3-toolbars]; [MDC floating toolbar][mdc-floating-toolbar]). §6.1
already records that the bar follows Google Photos rather than a Material component. The remaining deviation, inactive
tabs label-only or icon-only, is D13.

### 5.3 Chat list (`feature:chats`)

**Matches.**
- `MediumFlexibleTopAppBar` over a `surface` `ContentSheet` on `surfaceContainer`, as Google Messages does.
- The connection pill sits below the bar, with a live region.
- Folder tabs are a connected `ButtonGroup` of `ToggleButton`s with `animateWidth`, falling back to
  `PrimaryScrollableTabRow` above six folders.
- Rows are expressive interactive `ListItem`s inset `spacing.small`, `SegmentedGap` apart, with press/selected morphs
  on the list item's spring. Each row is one semantics node built lazily, with custom actions.
- `SwipeToDismissBox` reveals actions with a scale spring and haptics.
- The selection toolbar is a `HorizontalFloatingToolbar` in vibrant colors (Material: vibrant marks a temporary mode)
  with predictive back, and it hides the nav bar, as Material requires for a toolbar and a nav bar.
- The archive row, emphasized unread titles, badges and the muted badge follow §2.2/§3.3.
- Draw-phase folder fade, keyed/content-typed lazy list, recomposition tests.

**Gaps.**
- **No primary action, so the empty state is a dead end (G02, G39).** This is the screen's largest Expressive gap: no
  FAB, no FAB morph, no container transform. Material's FAB guidance makes the medium FAB the most recommended size on
  phones ([m3 FAB][m3-fab]).
- **Loading indicator (G04).** It shows immediately and has no description.
- **Folder tabs (G12).** Clipped at the edge; the selected tab is not scrolled into view.
- **Selection mode (G24).** The headline-size "3 selected" title, bulky full-pill rows, and no tooltips. Material's
  expressive list uses `large` corners for selected items, not a pill ([m3 lists][m3-lists]).
- **Swipe (G25).** No container on the moving row, and two actions only. Material asks for the primary action at the
  end, a full swipe to trigger it, and another visible path to the hidden actions.
- **Avatars (G07, D4).** Six polygon silhouettes per screen, and pastel `*Fixed` blobs in dark theme.
- **Rows (G58).** Uneven heights; titles truncated at 200%.
- **Margins (G27).** 16 dp at every width.
- **Top bar.** No account avatar or overflow menu (Messages, Gmail and Photos put the account avatar top-end). Settings
  is a tab here, so this stays optional.
- **Dialogs (G53).** The delete dialog renders edge to edge in the goldens. Verify on a device.

**Goldens seen.**
- `content`, `contentWithFolders`, `selecting`, `swipeToArchiveDark`, `swipeToReadLight`, `loading`, `empty`, `error`
  and `fontScale200`, plus the `ChatListAdaptive` expanded variants.
- On expanded windows the rows are capped at 560 dp inside a full-width sheet, which leaves wide empty bands.

### 5.4 Global search (`feature:chats` Search*)

**Matches.**
- A full-screen destination built from `SearchBarDefaults.InputField` with `rememberSearchBarState`. Material allows
  search as a standalone destination reached from navigation, and §6 explains why `ExpandedFullScreenSearchBar` (a
  dialog window) is not used.
- People row, recent chats with remove buttons, section headers in `titleSmallEmphasized` `primary`, match highlights,
  and the result count announced.

**Gaps.**
- **Filters (G19).** No filter chips. Material shows filter chips and category labels with gaps between groups, and
  Google Messages has a 2×4 filter grid ([m3 search][m3-search]; [GM search][9to5-gm-search]).
- **Results (G19).** Flat full-bleed rows, unlike the inset chat rows.
- **Loading (G19).** Two big loading indicators stacked.
- **Errors (G19).** The section error is red text with no container.
- **Tablets (G19, D6).** Material puts docked results on medium and expanded windows.
- **Entry transition (G19).** None from the search button.
- **People row.** A custom `Column`/`clickable` row whose names truncate. Acceptable, but `HorizontalUncontainedCarousel`
  is the Material component for it.
- **Not there yet.** Voice search; recent queries (only recent chats are kept).

### 5.5 Conversation (`feature:chat`)

**Matches.**
- **Layout.** Small `TopAppBar` on `surfaceContainer` over a rounded `surface` sheet. Reverse `LazyColumn` with keys
  and content types; the anchor-first opening position.
- **Bubbles.** Exactly per §4: `extraLarge` corners, `extraSmall` where a sender's messages meet, 4 dp inside a group
  and 8 dp between groups. Outgoing on `primaryContainer`, incoming on `surfaceContainerHigh`. Emoji-only messages
  have no bubble. Media clip to the bubble; album mosaics have 2 dp gaps.
- **Reactions.** Badges sit on the far bottom corner with a `surface` ring.
- **Actions sheet.** `ModalBottomSheet` with a quick-reaction row of `ToggleButton`s (round → square morph) and
  clickable `ListItem`s.
- **Composer.**
  - formatting as an output transformation; reply/edit bars on spatial springs
  - `SplitButtonLayout` send with silent and schedule
  - schedule via `DatePickerDialog` → `TimePicker`
  - attach as a `ButtonGroup` in a sheet
  - poll form, upload progress on `CircularWavyProgressIndicator`
- **Voice and audio.** `LinearWavyProgressIndicator` for audio; a wavy download ring for voice.
- **Accessibility.** One merged semantics node per message with custom actions, built in `semantics {}`.
- **Haptics.** `LongPress`, `ToggleOn/Off` on reactions, `Confirm` on send.

**Gaps.**

*Composer.*
- **G05.** The 56 dp split button starves the field. This is the most visible defect in the conversation goldens
  (`composer.withText`, `withText.fontScale200`).
- **G20, D2.** The outlined field, bare paperclip, no emoji button, empty trailing slot, full width on tablets. Google
  Messages uses a filled pill with the emoji button inside and a "+" in a tonal circle ([GM chat][9to5-gm-chat];
  [AP][ap-gm-chat]). Compose 1.5.0-alpha21 added `TextFieldDefaults.roundedShape` and `tonalColors()` for this
  look ([release notes][dac-m3-release]).
- **Not built (M3 milestone).** Voice recording (§6's mic → pill morph with a wavy indicator), the emoji/sticker panel
  (Messages uses a connected button group for its tabs), location attach, send-as.

*Messages and reactions.*
- **G06.** 28 dp reaction targets.
- **Reactions picker.** No full grid and no burst on add (§4, §6, FR-C7). §6 calls for a `HorizontalUncontainedCarousel`
  of reaction chips. Material's carousels are for visual items, so a `LazyVerticalGrid` in the sheet fits better (§6
  review). Counts are raw ("1204" rather than "1.2K").
- **G22.** The failed-send treatment.
- **G21, D3.** Long-press opens a generic sheet. Google Messages anchors a floating menu to the lifted message.
- **G40.** No selectable text, no swipe to reply.
- **No selection mode.** Neither selected bubbles nor a vibrant floating toolbar (§2.2, §6).
- **G41, D15.** Timestamps on every bubble.
- **G26.** Square code blocks, flat spoilers, dot custom emoji, pink sticker placeholders.
- **G42, D16.** Telegram-like quote bars.
- **Link previews.** A hard-wired `onSurface` title (`BubbleParts.kt:101`); on an outgoing bubble this would differ
  from the rest of the bubble's text.
- **Media in bubbles.** No inline video or GIF playback, static stickers only, no map preview for locations. These are
  M3-milestone items.

*Chrome.*
- **G23.** The top bar has no actions and no profile tap; the name is not emphasized.
- **G49.** No live region for typing.
- **G48.** The jump button is a small FAB on `secondaryContainer`.
- **Pinned messages bar.** Missing (FR-C9).
- **G27.** List margins are 12 dp at every width.

### 5.6 Media viewer (`feature:media`)

**Matches.**
- Full-bleed dark theme on `scrim`, immersive bars, `HorizontalPager` keyed by message.
- Pinch and double-tap zoom, with semantics and custom actions.
- `PredictiveBackHandler` at 90% scale; swipe down to dismiss.
- A container transform both ways (`sharedMediaBounds`), turned off while zoomed or with reduced motion.
- The expressive `Slider` with state, a `FlexibleBottomAppBar` for actions, and wavy progress for downloads.

**Gaps.**
- **G08.** The corner snap in the return transform.
- **G38, D20.**
  - **Actions.** Only share and save, both unlabelled. Google Messages' viewer offers quick reply and emoji; Google
    Photos uses a floating toolbar ([GM viewer][9to5-gm-viewer]).
  - **Chrome width.** No cap on tablets.
  - **Overflow.** No "More" menu.
- **Backdrop.** A blurred backdrop of the current photo, like Messages', would be optional polish
  (`RenderEffect` on API 31+, `PERFORMANCE.md` §5).
- **Video controls.** No mute, speed or ±10 s skip.
- **G60.** Page descriptions are built in composition.

### 5.7 Auth (`feature:auth`)

**Matches.**
- **Transitions.** Shared-axis Y between steps on the motion scheme's springs; predictive back.
- **Main button.**
  - Expressive press morph (`ButtonDefaults.shapesFor(MediumContainerHeight)`)
  - a `LoadingIndicator` replaces the label while a request runs, and the label stays as the description
- **Shapes.**
  - the Welcome hero morphs Cookie9Sided → SoftBurst while "Start messaging" is held
  - the QR frame is `Cookie12Sided`, with `Cookie6Sided` step badges
- **Password field.** The visibility toggle is an `IconToggleButton` with `toggleableShapes()`.
- **Autofill hints.** Phone, SMS code, password, new password, names, email.
- **Countdowns.** Flood wait and resend countdowns are announced at most every 15 s.
- **Large font.** At 200% the layout falls back to a centered column without clipping.

**Gaps.**
- **G09.** Phone landscape and tablets.
- **G29.** Small titles and empty screens.
- **G30.** The fake text field for the country, the plain search field, no selected mark.
- **G31.** OTP hints and the code field's look.
- **G28.** No haptics.
- **G50.** Banners without icons, countdowns without progress.
- **G61.** Predictive back scales the step but does not seek the transition.
- **AuthTextButton alignment.** Text buttons in the content column are indented by the button padding and do not line
  up with the 16 dp margin; align their labels or use tonal buttons.
- **Top bar.** The connection status sits in the bar's title slot; the chat list shows it as a pill below the bar.
  Pick one pattern.
- **`SecureTextField`.** `SecureTextField`/`OutlinedSecureTextField` (`TextFieldState`) replace the hand-built
  password field, and main deprecates the `String` text-field overloads ([androidx main API][ax-api-main]).
- **ToS link buttons.** The two link buttons repeat the agreement sentence above them; inline links (`LinkAnnotation`)
  would remove that row.

### 5.8 Settings (`feature:settings`)

**Matches.**
- The page and its transparent bar sit on `surfaceContainerHigh`. Rows are material3 `SegmentedListItem`s with
  `segmentedShapes`, `segmentedColors` and `SegmentedGap`; section headers are `titleSmallEmphasized` in `primary`.
- The account card is a single `SegmentedListItem` on `extraLarge` with a ring, an edit badge, the Premium label and a
  tonal chevron, exactly per §6.
- Radio rows are selectable list items with the `secondaryContainer` selected shape. Switch rows carry `Role.Switch`.
  Choices fall back to radio rows at large font sizes.
- The profile photo upload shows a `CircularWavyProgressIndicator` ring, the module's best Expressive moment.
- Destructive confirmations use `AlertDialog`.

**Gaps.**
- **G13.** Deprecated segmented buttons.
- **G14.** Switch thumbs without icons.
- **G33, D7.** No tonal icon containers, which is the biggest visual difference from Android 16 Settings
  ([A16 QPR1][9to5-a16-qpr1]).
- **G17, D8.** Small page bars.
- **G18.** No list-detail.
- **G04.** Undelayed loading.
- **G32.** Box-in-a-box fields in Edit profile.
- **G54.** The AMOLED page stays grey.
- **G55, G56.** Small button issues.
- **Icon alignment.** On Privacy and security, the privacy rows have no icons and the security rows do, so the text
  columns do not line up. Give all rows an icon or none.
- **G11.** No licenses screen.
- **FR-S1 pages not built (M3 milestone).** Notifications, Chat settings (text size, corner radius, wallpaper, seed
  color), Language, Passcode, 2FA editing, auto-download.
- **New pages.** Should use the same components: `Slider(state)` with stop indicators and `SegmentTick` haptics for
  text size; connected groups for single choices; color swatches as `ToggleButton`s on `MaterialShapes`.

### 5.9 Contacts and profile

- **Contacts.** An `EmptyState` only, on `background`, with no top bar (G34). FR-P1 is M4.
- **Profile.** The module has no sources yet. When built (M4), follow §6's plan:
  - a `LargeFlexibleTopAppBar` with a shape-morphing avatar
  - a `HorizontalUncontainedCarousel` of photos
  - action pills (Call, Message, Video), as Google Contacts' expressive contact page does
    ([Contacts][9to5-contacts])
  - on XL windows, a supporting pane

### 5.10 Sizes and density: why the app looks big

The owner reported that Pingogram looks too big on their phone next to other apps. Nothing scales the app:
- no `LocalDensity` override
- no font-scale change
- every size in dp and sp

Measured in the goldens (411 × 891 dp at 3×, so 1 dp = 3 px), the size comes from a handful of layout choices that
add up, plus a small effect of the font's optical size:

| Element | Pingogram (measured) | Material token / Google apps | Effect |
|---|---|---|---|
| Chat list header at rest | `MediumFlexibleTopAppBar` 112 dp + folder row 64 dp = **176 dp** before the first chat (plus the status bar) | Small app bar **64 dp** ("small: dense layouts", [m3 app bars][m3-app-bars]); Google Messages' home keeps a slim bar ([GM home][9to5-gm-home]) | ~1.5 rows lost at the top |
| Folder tab row | 40 dp buttons with 12 dp above and below = 64 dp | Small button 40 dp; 8 dp padding is enough | 8 dp |
| Chat row avatar | **56 dp** (`avatarMedium`) | List item avatar token **40 dp** (`ListTokens`, [m3 lists][m3-lists]); Google Messages enlarged its photos in the M3E redesign ([GM home][9to5-gm-home]) | The text column is narrower, so previews wrap |
| Chat row height | **~79 dp** apart with a two-line preview, **~94 dp** with three lines | Two-line list item **72 dp**, three-line 88 dp | ~10–20% taller rows |
| Chat rows visible (412 × 915 phone, 32 dp status bar, gesture nav, floating bar) | ~8.4 without folders, ~7.6 with folders | — | Proposed changes give ~9.7 / ~8.9 (+15–17%) |
| Composer bar | **72 dp**: 56 dp outlined field, 56 dp attach and send buttons | Text field 56 dp; small icon button 40 dp; Google Messages' composer is a slim pill ([GM chat][9to5-gm-chat]) | 8–16 dp and the field's width (G05) |
| Bubble | 16 dp horizontal padding; one-line bubble 40 dp; a time on every bubble (often a line of its own) | No Material rule; Google Messages shows times per group and on a swipe ([GM features][9to5-gm-features]) | Taller threads (G41) |
| Auth main button | Medium button **56 dp**, full width | Default button 40 dp; Google's sign-in puts a normal-width "Next" at the bottom end | Feels oversized; 9to5Google criticised oversized Expressive buttons ([recap][9to5-recap]) |
| Settings | Header 112 dp, account card 96 dp, rows 72/88 dp, 12 dp between groups | Android Settings uses a large collapsing title; Android 17 made its cards shorter ([Android 17][9to5-a17]) | Close to Google's; leave |
| Type | M3 sizes (16 sp messages and titles, 14 sp previews, 11 sp times) with `opsz` = size | Same sizes in Material and Google's apps | See below |

**Optical size.** Google Sans Flex widens and opens its letters at small optical sizes. Measured in the bundled file:
- at `opsz` 14 (`bodyMedium`) a line is **5%** wider than at the font's default `opsz` 18
- at `opsz` 11–12 (`labelSmall`/`labelMedium`, times and badges) it is **8–9%** wider
- x-height rises from 0.510 to 0.525–0.530 em

Pingogram sets `opsz` to the size, as the font is designed and as §3 requires. An app that leaves `opsz` at the
default, or that uses Roboto, sets the same sp narrower. This explains part of the "big" impression in previews and
times, but it is the font working as intended. **Keep it** (D32); the layout changes below do more.

**What to change** (all within Material's tokens; decided under the owner's 2026-10-19 instruction, see D25–D32):
- **G63, D25.** Chat list: a small `TopAppBar` (64 dp) with `titleLargeEmphasized`, no collapsing. Settings home keeps
  its medium flexible bar, as Android Settings has a large title.
- **G64, D26.** Folder tab row 56 dp: 40 dp buttons with `spacing.small` above and below.
- **G65, D27.** Chat row avatar 48 dp, a new size token: between Material's 40 dp and today's 56 dp, for a row with
  title, preview and a meta column. Contacts and search keep 40 dp, the account card 56 dp.
- **G66, D28.** One line of preview per chat (the sender inline, as today), so rows are Material's 72 dp two-line
  item.
- **G67, D29.** Composer bar 64 dp:
  - a 48 dp filled tonal pill: `BasicTextField` + `TextFieldDefaults.decorator` with tighter vertical padding,
    because `TextField` enforces 56 dp
  - 40 dp icon buttons with 48 dp touch targets
  - a small (40 dp) icon split send (G05)
- **G68, D30.** Bubble text 12 dp (`spacing.medium`) from the sides, 8 dp top and bottom; time per group (D15).
- **G69, D31.** Auth: default-height (40 dp) buttons at the bottom end, the secondary action as a text button at the
  bottom start, as in Google's sign-in. Welcome's "Start messaging" stays medium (the hero moment).
- **G70.** A density check in the per-screen audit (§12 of the spec): the chat list shows at least nine rows, and a
  one-line message bubble is at most 40 dp tall, on a 412 × 915 dp phone at 100% font.

### 5.11 Shared components (`core:ui/component`)

| Component | Status | Notes / IDs |
|---|---|---|
| `ContentSheet` | ✅ | On tablets, pane containment is D9 / G15 |
| `DelayedLoadingIndicator` | ✅ | Not used everywhere (G04); delay D10 |
| `EmptyState` | ⚠️ | Static; harsh error shape; no action by default (G39) |
| `Avatar` / selection / online dot | ✅ perf | Colors G07, shapes D4; online ring stays `surface` on selected rows (minor) |
| `MessageBubbleShapes` | ✅ | Viewer counterpart shape G08 |
| `SharedMedia` | ✅ | Reduced motion read once (G46) |
| Navigation bar insets / visibility | ✅ | — |
| `ErrorText` | ✅ | — |
| Previews | ⚠️ | Coverage G36 |

---

## 6. Review of `DESIGN_SYSTEM.md` and the other docs

Rules that are outdated, contradict current Google guidance, contradict each other, or are not implemented. **Applied
2026-10-19**: after the owner's decisions, `DESIGN_SYSTEM.md`, `REQUIREMENTS.md`, `CLAUDE.md` and `ROADMAP.md` were
updated along these lines. Row 30 (compileSdk 37) stays open until the AARs are checked.

| # | Where | Today | Problem | Proposal |
|---|---|---|---|---|
| 1 | §1 | "`MaterialShapes` and `toShape()` still need `@OptIn`" | True, but it does not say that everything else is stable in 1.5.0-beta01, which is why there are 84 stale opt-ins (G35) | List what still needs `ExperimentalMaterial3ExpressiveApi` (`MaterialShapes`, `toShape`/`toPath`, `LoadingIndicator`, `ContainedLoadingIndicator`, `PullToRefreshDefaults.LoadingIndicator`) and `ExperimentalMaterial3Api` (sheets, tooltips, `AppBarWithSearch`) |
| 2 | §2.2 Navigation bar / rail | Rail on `surfaceContainer` | Material's collapsed rail is `surface` (container optional); the code uses `surface` | "Rail: `surface` (Material default); bar: `surfaceContainer`" |
| 3 | §2.2 Avatar placeholders | 7 pairs incl. `*Fixed` | `*Fixed` roles are the same light tone in dark theme (G07) | Container roles, or `*FixedDim` in dark (D4) |
| 4 | §2.2 Settings pages, AMOLED | Rows true black, page grey | Inverted on OLED (G54) | D22 |
| 5 | §3.1, §3.2, §10; `CLAUDE.md` 4b | `GRAD −25` in dark | No-op with the bundled font; Google uses 0 | Drop (D11) |
| 6 | §3.3 | No entry for the medium flexible bar title, conversation title or settings page titles; "Large collapsing app bar titles" are not used anywhere | Missing mappings | Medium flexible: `headlineMediumEmphasized` → `titleLargeEmphasized`; conversation: `titleMediumEmphasized`; settings pages per D8 |
| 7 | §3.3 Buttons | All buttons `labelLargeEmphasized` | Material uses emphasized type for *primary* actions | Emphasized for filled/primary buttons; base `labelLarge` for text and tonal buttons (minor) |
| 8 | §4 list rows | Selected row a full pill | Expressive list token: selected `large` (16 dp) | D5 |
| 9 | §4 vs §8 | §4: media clip to the bubble shape; §8: "Images: `large` corner radius inside bubbles" | Contradiction; the viewer follows §8 and snaps (G08) | Delete the §8 sentence |
| 10 | §5 Loading | Indicator after a short wait (150 ms in code) | Material: nothing under 200 ms | D10 |
| 11 | §5 Navigation transitions | Shared-axis X for sibling screens | Not implemented; material3 main adds `MaterialTransitions.HorizontalSharedAxis` | Keep the rule; note the API (D21) |
| 12 | §5 Predictive back | Destination beneath at full size | Android's guidance has the entering screen grow from 110% to 100% too ([predictive back][dac-predictive-back]) | Optional; note it |
| 13 | §6 Folder tabs | Connected group in a horizontal scroll | Material: groups never wrap; overflow into a menu | D14 |
| 14 | §6 New chat | `FloatingActionButtonMenu` with `ToggleFloatingActionButton` | Material: do not pair a FAB menu with a floating toolbar or a rail | D1 |
| 15 | §6 Conversation top bar | `DropdownMenu` overflow | Material: avoid overflow menus in app bars; at most two actions | Drop the overflow; actions in the profile / supporting pane |
| 16 | §6 Composer | Outlined field, `extraLarge` corners | Google Messages uses a filled tonal pill; Compose 1.5 adds `roundedShape`/`tonalColors()` | D2 |
| 17 | §6 Settings rows | "`SegmentedButton`s in a row of their own" | Segmented buttons are deprecated in Expressive | Connected `ToggleButton` group (G13) |
| 18 | §6 Attach menu | `HorizontalMultiBrowseCarousel` of recent photos | Needs media read permission, against "Photo Picker instead of storage permission" (`REQUIREMENTS.md` §7) | Drop the carousel, or use the platform's embedded photo picker where available (verify availability) |
| 19 | §6 Reactions picker | `HorizontalUncontainedCarousel` of chips | Carousels are for visual items; reactions are a grid of choices | Quick row of `ToggleButton`s + `LazyVerticalGrid` in the sheet |
| 20 | §6 Media viewer | `FlexibleBottomAppBar` actions | Material retired the bottom app bar for the docked toolbar; whether `FlexibleBottomAppBar` is Compose's docked toolbar was not verified | Verify; consider a floating toolbar (D20) |
| 21 | §6 Global search | Full-screen destination | Material docks search on medium+ | D6 |
| 22 | §6 (missing) | Jump-to-bottom button not in the table | Small FAB is no longer recommended | Add a row (G48) |
| 23 | §6.1 | Unselected tabs label-only; icon-only when labels do not fit | Material: labels on every item, filled/outlined icons; re-tap scrolls to top | Record the deviation (D13); add re-tap to top (G37) |
| 24 | §7 Layout | "Window size classes", compact/medium/expanded; "medium: navigation rail" | Material renamed them breakpoints, added large/XL, fixed panes 360/412 dp, an 8 dp spacing system; the nav suite gives landscape phones a bar | Update terms; add large/XL behavior (expanded rail, G16; three panes at XL later); state the landscape rule (D19) |
| 25 | §7 Margins | 16 dp compact, 24 dp medium+ | Not implemented (G27) | Keep; implement |
| 26 | §10 | `GRAD +25` may be applied in high contrast | Not implemented; works with the font's 0–100 range | D11 |
| 27 | §12 audit | Light/dark/dynamic/AMOLED/contrast screenshots | No goldens for most of these (G36) | Keep; add coverage |
| 28 | §3 (missing) | — | Material's August 2026 script-height line heights | Note as tracked (G62) |
| 29 | `CLAUDE.md` rule "exact `@OptIn`" | — | Stale opt-ins violate it (G35) | Fix code; keep the rule |
| 30 | `REQUIREMENTS.md` §3 compileSdk 37 | "Required by the latest stable Compose (BOM 2026.09.00 / Compose 1.12)" | material3 1.5.0-alpha23's notes say the compileSdk 37 requirement was removed; not verified for Compose 1.12 itself | Verify the AARs' `minCompileSdk`, then correct the rationale |

---

## 7. Compose APIs: what exists, what we use, what to adopt

Versions in use: material3 1.5.0-beta01 (2026-10-07; latest pre-release; stable is 1.4.0, which has no Expressive
API). Navigation suite 1.5.0-beta01. Adaptive 1.3.0 (stable; 1.4.0-alpha03 is the latest pre-release). No BOM contains
material3 1.5, so the explicit pins are right. material3 1.5.0-beta01 needs Compose ≥ 1.12.0 and brings
`material3-ripple` 1.5.0-beta01, which the BOM does not manage. No 1.6.0 alpha is published; androidx main is at
1.6.0-alpha01. ([Release notes][dac-m3-release]; [adaptive][dac-adaptive-release]; [BOM mapping][dac-bom];
[API file][ax-api-beta01].)

**Opt-ins in 1.5.0-beta01.**
- **`@ExperimentalMaterial3ExpressiveApi`:**
  - `MaterialShapes`
  - `RoundedPolygon.toShape()` / `toPath()`, `Morph.toPath()`. There is no public `Morph.toShape()`.
  - `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults`
  - `PullToRefreshDefaults.LoadingIndicator`
- **`@ExperimentalMaterial3Api`:**
  - `ModalBottomSheet`, `BottomSheet`, `rememberBottomSheetState` and the rest of the sheet API
  - `AppBarWithSearch`
  - all tooltip APIs
  - carousel parallax
  - `ComposeMaterial3Flags`
- **`@ExperimentalMaterial3AdaptiveApi`:** almost all of adaptive-layout and adaptive-navigation 1.3.0.
- **Stable:** everything else below.

| API | Status (1.5.0-beta01) | Used today | Adopt for |
|---|---|---|---|
| `MaterialExpressiveTheme`, `MotionScheme.expressive()`, emphasized `Typography`, `Shapes(*Increased, extraExtraLarge)` | stable | ✅ | — |
| `MaterialShapes`, `toShape` | XM3E | ✅ | — |
| `ButtonDefaults.shapes()/shapesFor()`, button sizes XS–XL | stable | ✅ partly | G56, G39 |
| `ButtonGroup`, connected `ToggleButton` shapes | stable | ✅ folders | G13 settings |
| `SplitButtonLayout` + `SplitButtonDefaults` (5 sizes) | stable | ✅ 56 dp | G05: smaller size |
| `IconButton(shapes)`, sizes XS–XL, widths Narrow/Uniform/Wide, `IconToggleButton(toggleableShapes)` | stable | ✅ | composer "+" (G20), wide send |
| `FloatingActionButtonMenu`, `ToggleFloatingActionButton` | stable | ❌ | D1 alternative |
| `MediumFloatingActionButton`, `Medium/LargeExtendedFloatingActionButton`, `Modifier.animateFloatingActionButton` | stable | ❌ | G02 |
| `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`, `TopAppBar(subtitle)` | stable | Medium ✅ | G17 settings pages, profile (M4) |
| `AppBarWithSearch` | XM3 | ❌ | Not needed (search is in the nav bar) |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` (+ FAB variants, `exitAlwaysScrollBehavior`) | stable | ✅ horizontal | Media viewer (G38), vertical on tablets |
| `FlexibleBottomAppBar` | stable | ✅ viewer | — |
| `SearchBar(state)`, `ExpandedDockedSearchBar(WithGap)` (popup), `ExpandedFullScreenSearchBar` (dialog), `SearchBarDefaults.InputField(textFieldState, …)` | stable | InputField ✅ | Docked on tablets (G19, D6); country picker (G30) |
| `FilterChip/InputChip(shapes = …Defaults.shapes())`, `tonalFilterChipColors()` | stable | ❌ | Search filters (G19) |
| `ListItem` / `SegmentedListItem` (clickable, selectable, toggleable), `ListItemDefaults.segmentedShapes/segmentedColors/SegmentedGap` | stable | ✅ | Search results groups (G19), message actions |
| `LoadingIndicator`, `ContainedLoadingIndicator` | XM3E | ✅ | — |
| `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` | stable | ✅ | Countdowns (G50), search progress (G19) |
| `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel`, `HorizontalCenteredHeroCarousel` | stable | ❌ | Profile photos (M4), search people row |
| `DropdownMenuPopup`, `DropdownMenuGroup`, `SelectableDropdownMenuItem`, `CheckableDropdownMenuItem`, `MenuDefaults.itemVibrantColors()` | stable | ❌ (classic `DropdownMenu`) | Message menu (G21), send menu, overflow menus |
| `TooltipBox`, `PlainTooltip`, `RichTooltip` | XM3 | ❌ | Toolbars and icon-only buttons (G24) |
| `TextField(state, shape = TextFieldDefaults.roundedShape, colors = tonalColors())` | stable | ❌ | Composer (G20), Edit profile (G32) |
| `SecureTextField` / `OutlinedSecureTextField` | stable | ❌ | Auth passwords |
| `ExposedDropdownMenuBox` (+ `ExposedDropdownMenuDefaults.TrailingIcon`) | stable | ❌ | Country field (G30) |
| `Switch(thumbContent)` | stable | ❌ thumb | G14 |
| `Slider(state)`, `VerticalSlider`, `SliderDefaults.CenteredTrack` | stable | ✅ viewer | Text size setting (M3 milestone) |
| `TimePicker` expressive modes, `VibrantTimePickerDialog` | stable | `TimePicker` ✅ | Schedule send: `TimePickerDialog` instead of a time picker inside `DatePickerDialog` |
| `ShortNavigationBar` + `ShortNavigationBarItem` | stable | via suite | Could build the floating pill's items (indicator, press, semantics for free) |
| `WideNavigationRail` expanded state, `ModalWideNavigationRail` (dialog window) | stable | collapsed ✅ | G16 |
| `NavigationSuiteScaffold(primaryActionContent)` | stable | ✅ | Extended FAB in an expanded rail |
| `ListDetailPaneScaffold` pane expansion: `rememberPaneExpansionState`, `VerticalDragHandle`, `paneExpansionDraggable`; `AnimatedPane` shapes (adaptive 1.4.0-alpha01) | XAD / stable | ❌ | G15 |
| `SupportingPaneScaffold`, levitate / reflow strategies | XAD | ❌ | Profile and chat info (M4) |
| `RippleDefaults.InsetFocusRingThemeConfiguration` | stable | ❌ | D23 |
| `Scrim` | stable | ❌ | Message menu backdrop (G21) |
| `MaterialTransitions.FadeThrough` / `HorizontalSharedAxis` | **unreleased** (main, 1.6.0-alpha01) | hand-built fade-through | G03 later |

Gotchas worth knowing before implementing:
- **Release notes vs API file.** The alpha25 notes say `SplitButtonLayout` is deprecated for `SplitButton`. No such
  change is in beta01; `SplitButtonLayout` is the only split button. Trust the API file.
- **Components that open their own window.** `ExpandedFullScreenSearchBar` and `ModalWideNavigationRail` are dialog
  windows, `ExpandedDockedSearchBar` is a popup and `ModalBottomSheet` is a separate window. Each handles predictive
  back itself, outside Navigation's.
- **Sheets.** `rememberModalBottomSheetState` is deprecated for `rememberBottomSheetState(initialValue, enabledValues)`.
  The partially expanded state is no longer skipped automatically. The code is already on the new API.
- **List item heights.** Expressive list items now size to their line count.
- **`LocalMotionScheme`** was removed (alpha27).
- **Text fields.** Main deprecates the `String`/`TextFieldValue` overloads in favour of `TextFieldState`.

---

## 8. Cross-cutting: accessibility, motion, adaptive, performance

**Accessibility.**
- **Strong already:**
  - merged semantics per row and per message, built lazily, with custom actions for swipe and reactions
  - headings, roles (`Tab`, `Checkbox`, `Switch`, `RadioButton`), live regions for connection, selection and result
    counts
  - autofill hints in auth
  - 200% font goldens
- **Open:**
  - reaction targets (G06)
  - no description on the chat list's loading indicator (G04)
  - no tooltips on icon-only toolbar buttons (G24)
  - no live region for typing (G49)
  - no keyboard shortcuts (§5.2)
  - failed sends shown by a small icon only (G22). Material asks that errors carry an icon *and* text, and that
    selection be shown by two properties ([m3 accessibility][m3-a11y-elements]).
- **Not covered by goldens:** RTL and medium width (G36).

**Motion.**
- **Matches:**
  - every spec comes from the motion scheme
  - draw/layout-phase animation throughout
  - shape morphs: list items, toggle buttons, split button, Welcome hero, avatar selection
  - wavy and shape loading indicators
  - predictive back per Android's guidance
- **Missing:**
  - shared-axis X (G03)
  - FAB morph and FAB → screen container transform (G02)
  - search entry transition (G19)
  - reaction burst (§4)
  - typing dots (G49)
  - the nav indicator's one-axis expansion (G37)
- **Reduced motion:** Compose's animator scale snaps springs, and shared bounds turn off. The explicit path is G46.
  Material's motion pages say nothing about reduced motion, so §5's rule stays the project's own.
- **Haptics:** missing in auth and settings and on failures (G28).

**Adaptive.**
- **Compact:** good.
- **Medium (600–839 dp):** the rail with a single pane, which matches Material's "one pane recommended". Not covered by
  goldens.
- **Expanded:** list-detail works, but the panes are not contained and have no drag handle (G15). Auth and Settings
  have no two-pane layout (G09, G18). Chrome is not width-capped in the media viewer or composer (G38, G20).
- **Large / XL:** no expanded rail (G16), no third pane yet (M4).
- **Phone landscape:** the auth CTA is below the fold (G09), and the floating bar costs height (G52).
- **Foldables:** hinge avoidance comes only from `calculatePaneScaffoldDirective`. There is no tabletop handling (for
  example, the media viewer split across the fold), which is optional.

**Performance.**
- Each recommendation here is built from components already in the app or from stable material3 components. None adds
  a dependency.
- Things to watch when implementing:
  - **Blur behind the message menu (G21):** `RenderEffect`, API 31+ only, with a scrim fallback below. Measure with the
    conversation benchmark.
  - **Morphing FAB (G02):** must not recompose the list while scrolling. Use `animateFloatingActionButton` or a
    `graphicsLayer` scale; collapse driven by a `derivedStateOf` on the list state.
  - **Pane drag handle (G15):** resizing re-measures both panes; keep the conversation's bubble cache keyed by width.
  - **Delayed loading indicators (G04):** these improve first-frame cost on the startup-critical chat list.
  - **Unchanged by this report:** the bubble `Layout` rewrite and the semantics strings (G60) are `PERFORMANCE.md` §4
    items in their own right.

---

## 9. Method and limits

- **Research.**
  - m3.material.io pages were read through a JavaScript-rendering reader, because a plain fetch returns only the
    title. A few pages never rendered (navigation rail guidelines, list specs, motion transitions, badges); their
    numbers come from Compose's token files and the MDC-Android docs.
  - Google's own app behaviour comes from first-party posts where they exist, and otherwise from press coverage
    (mostly 9to5Google, Android Police, Droid Life), with dates. Android Authority refused most direct fetches.
  - Compose facts come from the AndroidX public API file at the exact 1.5.0-beta01 commit, the release notes, Maven
    metadata and POMs. The local machine had no Gradle cache.
  - Not verified first-hand:
    - per-size button label styles (diagrams only)
    - whether Compose's `FlexibleBottomAppBar` is Material's docked toolbar
    - the embedded photo picker for Compose
    - the material3 AARs' `minCompileSdk`
- **Code.** Every screen file and shared component was read. About 290 of the ~400 Roborazzi goldens were viewed
  (every distinct test, with light/dark/expanded/200% variants sampled), plus the README screenshots.
- **Goldens are Robolectric renders.** Dialog widths, sheet heights and text rasterization may differ on a device.
  Findings that rest on those alone are marked "verify" (G53).
- **Milestone.** Items that belong to later milestones (composer voice, emoji panel, profile, contacts, pinned bar) are
  listed for completeness but not counted as deviations.

---

## 10. What Material's figures show

Material makes many of its rules only in figures, as Do / Don't / Caution pairs. These are the figures behind the
findings above, in words.

| Page | Figure | Rule it makes | Relevant to |
|---|---|---|---|
| [Corner radius scale][m3-shape-scale] | The old 3-step scale beside the new 10-step one; a padding / outer / inner radius diagram; a nested carousel with smaller inner corners (Do) next to one with equal radii (Don't) | Optical roundness: inner radius = outer − padding; no large or full corners on dense components such as cards | G08 |
| [Shape principles][m3-shape-principles], [shape morph][m3-shape-morph] | Abstract shapes "to help people express themselves"; shapes morphing into each other; a pressed button group changing widths | Shapes for emphasis and expression, never as the only carrier of meaning; morph on interaction | D4, G02 |
| [Motion][m3-motion] | Token chart: a scheme → three speeds → a spatial and an effects token each; expressive vs standard curves | Springs by size and property | G03 |
| [Type scale tokens][m3-type-tokens] | The full scale; an active-call UI with live translation using emphasized styles; Google Sans Flex line heights adapting per language | Emphasis for selection, unread, badges and primary actions; script-aware line heights | §6 #6, G62 |
| Type: [editorial treatments][m3-type] | A navigation bar with bold labels (Caution); filter chips with a heavier grade when selected; a slider whose type width and weight grow with brightness | No heavy weights at small sizes; grade for emphasis without reflow; negative grade can offset dark mode | D11 |
| [Color roles][m3-color-roles] | Same HCT tone with different HSL lightness; a FAB on `primaryFixed` keeping its color across themes vs one on `primaryContainer` shifting; pairs like primary-on-primaryContainer marked Don't | Roles only in their pairs; fixed roles only where one tone in both themes is wanted, never where contrast matters | G07 |
| [Breakpoints][m3-breakpoints] | Compact/medium/expanded side by side; an email app at three breakpoints; a messaging app with a second pane showing the selected conversation; Do: nav bar swapped for a rail at expanded; Do: collapsed rail expanded at XL; Don't: a button swapped for a menu | Reveal more on wider windows, swap only equivalent components; expand the rail on XL | G15, G16 |
| [List-detail][m3-list-detail] | Email at medium with inbox and email side by side; one-pane phones and two-pane tablets; a two-pane layout with a selected item next to a one-pane layout with a Back button | Selected state only in two-pane lists; Back only in the single-pane detail; placeholder in an empty detail pane | G15 |
| [App bars][m3-app-bars] | Four stacked bars (search, small, medium flexible, large flexible); the retired baseline medium/large bars; Do one filled action / Don't two; Do filled Save and Download icons / Caution outlined; Don't a wrapped headline in a small bar; Do/Don't straight corners and full width | One or two actions, avoid overflow menus, filled icons, flexible bars | G17, G23, §6 #15 |
| [Toolbars][m3-toolbars] | An inbox with a nav bar, then the opened email with a toolbar of contextual actions; Don't a floating toolbar with navigation labels above a nav bar; Do an overflow icon / Don't a toolbar running off screen; standard vs vibrant; a vibrant toolbar over neutral text without elevation; Don't square icon buttons in a floating toolbar | Toolbars are page actions; never with a nav bar; vibrant for temporary modes | Selection toolbar ✅, G38 |
| [Navigation bar][m3-nav-bar] | Compact and medium bars (vertical vs horizontal items); Don't 7 items, Don't 2 items; "Music catalog" truncated (Don't) and shrunk (Don't); Don't icons without labels; Don't an indicator on every item; Don't a full-width bar on an expanded window; Do the FAB above the bar, right-aligned | Labels required; one indicator; rail from expanded | D13, G37 |
| [Navigation rail][m3-nav-rail] | Collapsed and expanded rails side by side; M2 rail vs M3 pill indicator | Expanded rail replaces the drawer | G16 |
| [Button groups][m3-button-groups] | A welcome screen with two narrow icon buttons beside "Get started"; Do three colors sharing one square shape / Caution round beside square; a volume picker and two filters as connected groups; Don't mixed color styles in one connected group ("My files / Shared / Computers") | Connected groups for selection; one color style; never wrap | G12, G13 |
| [Split button][m3-split] | Split buttons in many sizes; a "Canada" filter beside icon buttons; a large promo split button; a vibrant driving split button; a playback-speed split button; Don't "32 minutes away" with a refresh icon; six menu alignments | Main action + related menu; the trailing icon rotates 180° and never changes | G05 |
| [Icon buttons][m3-icon-buttons] | Four styles default/toggle; a red hang-up; "Raise hand" tonal between outlined and filled; a heart with a container on an image | Emphasis by style; selection by two properties; containers on imagery | G20, G38 |
| [FAB][m3-fab], [FAB menu][m3-fab-menu], [extended FAB][m3-extended-fab] | 18 bad FAB actions (cut, trash, volume, archive…); Do five FAB-menu items / Don't one; Do toolbar + FAB / Don't toolbar + FAB menu; Caution text-only items, Don't icon-only, Don't stretched or square items; a music albums FAB menu | One positive primary action; FAB menu never with a floating toolbar or rail | G02, D1 |
| [Loading indicator][m3-loading] | Anatomy; the morph sequence; a contained circle over content; sizes 24–240 dp | Nothing under 200 ms; indicator 200 ms–5 s; progress beyond | G04, D10 |
| [Progress indicators][m3-progress] | Linear and circular at 1% (a dot); a determinate linear with a stop dot; RTL mirroring; sizes 24–240 dp | Wavy for longer processes; one indicator per group | G19, G50 |
| [Carousel][m3-carousel] | Items shrinking with the device; text hiding on small items; Do readable multi-browse / Don't a sliver-thin small item; Caution 5 lines per item; Don't full-screen items stopping halfway | Visual items, "Show all" for accessibility | §6 #19 |
| [Lists][m3-lists] | Party-planning lists with selected items highlighted; a music list with leading image; a contact list with circular and expressive avatar crops; Don't media in the middle; Do segmented gaps and filled items for a group / Don't dividers in contained lists; a recipe item swiped to reveal archive | Selected = `large`; gaps not dividers; swipe needs a visible alternative | G24, G25, D4, D5 |
| [Menus][m3-menus] | Standard vs grouped menu with one gap; a vibrant "Line spacing" menu with a submenu; dietary choices with checkmarks; a text-selection menu over highlighted text; a bottom sheet on a phone vs the same as a menu on a tablet | Gaps for groups (max two); checkmark + vibrant highlight for selection; sheets on compact | G21, D3 |
| [Dialogs][m3-dialogs] | Do a blocking prompt / Don't low-priority info (use a snackbar); a dialog repositioned on a tablet with 56 dp margins | Max two actions; specific headline | G53 |
| [Bottom sheets][m3-sheets] | A sharing sheet, a song action list, a half-height modal, scrim-tap and swipe-down dismissal, full width on phones vs max width on tablets | Modal ≤ 50% initial height; tapping the scrim closes | Message actions, attach |
| [Search][m3-search] | A search bar at the top of a message inbox (hero); docked results on a tablet vs full-screen on a phone; a row of five contact avatars; location and calendar results separated by a gap from people | Docked on medium+; category labels and gaps; "Search your messages" | G19, D6 |
| [Switch][m3-switch] | Handles with check and X icons; Don't ambiguous icons (moon, pencil) | Thumb icon shows state; connected groups for opposing options | G14, G13 |
| [Text fields][m3-text-fields] | Filled vs outlined; error with icon; a field stretched across a large screen (Don't) | Don't mix styles in one region; don't stretch | G20, G32 |

---

## 11. Sources

Material Design (m3.material.io). The layout pages under `/m3/pages/` and the blog are from the May 2026 update.

- Foundations:
  - [Shape: corner radius scale][m3-shape-scale], [principles][m3-shape-principles], [shape morph][m3-shape-morph]
  - [Motion: how it works][m3-motion], [specs][m3-motion-specs]
  - [Typography][m3-type], [type scale tokens][m3-type-tokens], [applying type][m3-applying-type]
  - [Color roles][m3-color-roles], [how the system works][m3-color-system]
  - [Elevation][m3-elevation], [state layers][m3-states], [accessibility: elements][m3-a11y-elements]
- Layout:
  - [Layout overview][m3-layout-overview], [breakpoints][m3-breakpoints], [panes][m3-panes], [spacing][m3-spacing]
  - [List-detail][m3-list-detail], [canonical examples][m3-canonical]
- Components:
  - [App bars][m3-app-bars] ([specs][m3-app-bars-specs]), [toolbars][m3-toolbars]
  - [Navigation bar][m3-nav-bar] ([overview][m3-nav-bar-overview]), [navigation rail][m3-nav-rail]
    ([specs][m3-nav-rail-specs]), [navigation drawer][m3-nav-drawer]
  - [Buttons][m3-buttons], [button groups][m3-button-groups], [segmented buttons][m3-segmented],
    [split button][m3-split], [icon buttons][m3-icon-buttons]
  - [FAB][m3-fab], [FAB menu][m3-fab-menu], [extended FAB][m3-extended-fab]
  - [Loading indicator][m3-loading], [progress indicators][m3-progress]
  - [Carousel][m3-carousel], [lists][m3-lists] ([overview][m3-lists-overview]), [cards][m3-cards], [chips][m3-chips]
  - [Menus][m3-menus], [dialogs][m3-dialogs], [bottom sheets][m3-sheets], [search][m3-search]
  - [Sliders][m3-sliders], [switch][m3-switch], [tabs][m3-tabs], [snackbar][m3-snackbar], [tooltips][m3-tooltips]
  - [Text fields][m3-text-fields], [date pickers][m3-date-pickers], [time pickers][m3-time-pickers]
- Blog: [What's new at I/O 2026][m3-io26], [Material is Compose-first][m3-compose-first]
- Archive: [M2 empty states][m2-empty]

Google design, Android and AndroidX (first party):

- Design and launch:
  - [Expressive design: Google's UX research][g-research]
  - [Material 3 Expressive launch][g-launch]
  - [The Android Show: I/O edition][g-android-show]
  - [Androidify: building delightful UI with Compose][g-androidify]
  - [Google Sans Flex][g-gsf]
  - [Design Notes: Inside M3 Expressive][g-design-notes]
- Android developer guidance:
  - Wear: [benefits of expressive design][g-wear-benefits], [levels of expression][g-wear-levels]
  - [Predictive back design][dac-predictive-back]
  - [Window size classes][dac-wsc]
- Releases:
  - [material3 release notes][dac-m3-release], [material3-adaptive release notes][dac-adaptive-release],
    [BOM mapping][dac-bom]
- AndroidX API files:
  - [material3 1.5.0-beta01 API file][ax-api-beta01]
  - [navigation suite 1.5.0-beta01 API][ax-api-navsuite]
  - [adaptive 1.3.0 API][ax-api-adaptive]
  - [androidx main API (1.6.0-alpha01)][ax-api-main]
  - [Expressive motion tokens][ax-motion-tokens], [material3 tokens][ax-tokens]
- MDC-Android docs: [floating toolbar][mdc-floating-toolbar], [motion][mdc-motion], [bottom navigation][mdc-bottom-nav],
  [navigation rail][mdc-nav-rail]
- Sample: [Jetchat][jetchat]
- Telegram: [API terms of service][tg-terms]

Google's apps (press coverage):

- Google Messages:
  - [chat screen][9to5-gm-chat], [chat screen beta][9to5-gm-chat-beta], [homepage][9to5-gm-home]
  - [search and Start chat][9to5-gm-search], [more changes][9to5-gm-more], [account menu][9to5-gm-account]
  - [photo viewer][9to5-gm-viewer], [long-press menu][9to5-gm-menu], [long-press menu wide][9to5-gm-menu-wide]
  - [2026 features][9to5-gm-features], [chat themes][9to5-gm-themes]
  - [Android Police chat screen][ap-gm-chat], [bubble animation][ap-gm-bubble]
  - [Android Authority September 2026 shuffle][aa-gm-shuffle]
- Google Photos: [floating bar on iOS][9to5-photos-ios], [floating bar on Android][9to5-photos-android],
  [backup redesign][9to5-photos-backup]
- Android system and settings:
  - [Android 16 QPR1][9to5-a16-qpr1], [Android 16 settings][9to5-a16-settings], [Droid Life QPR1][dl-a16-qpr1]
  - [Android 17][9to5-a17], [Google Account][9to5-account], [Chrome settings][9to5-chrome-settings]
- Other Google apps:
  - [Phone][9to5-phone], [Contacts][9to5-contacts], [Gmail containers][9to5-gmail-containers],
    [Gmail search][9to5-gmail-search]
  - [Clock][9to5-clock], [Keep][9to5-keep], [Files][9to5-files], [Calendar][9to5-calendar], [Drive][9to5-drive]
  - [Fitbit][9to5-fitbit], [Fitbit drops its floating toolbar][9to5-fitbit-toolbar], [Gemini at I/O 2026][9to5-gemini]
- Material 3 Expressive coverage:
  - [Navigation changes][9to5-nav], [toolbars][9to5-toolbars], [loading indicator][9to5-loading], [menus][9to5-menus]
  - [Redesign roundup][9to5-roundup], [year-one recap][9to5-recap]
  - [TechCrunch on the early leak][tc-leak]

[m3-shape-scale]: https://m3.material.io/styles/shape/corner-radius-scale
[m3-shape-principles]: https://m3.material.io/styles/shape/overview-principles
[m3-shape-morph]: https://m3.material.io/styles/shape/shape-morph
[m3-motion]: https://m3.material.io/styles/motion/overview/how-it-works
[m3-motion-specs]: https://m3.material.io/styles/motion/overview/specs
[m3-type]: https://m3.material.io/styles/typography/overview
[m3-type-tokens]: https://m3.material.io/styles/typography/type-scale-tokens
[m3-applying-type]: https://m3.material.io/styles/typography/applying-type
[m3-color-roles]: https://m3.material.io/styles/color/roles
[m3-color-system]: https://m3.material.io/styles/color/system/how-the-system-works
[m3-elevation]: https://m3.material.io/styles/elevation/applying-elevation
[m3-states]: https://m3.material.io/foundations/interaction/states/state-layers
[m3-a11y-elements]: https://m3.material.io/foundations/designing/elements
[m3-layout-overview]: https://m3.material.io/foundations/layout/understanding-layout/overview
[m3-breakpoints]: https://m3.material.io/m3/pages/breakpoints
[m3-panes]: https://m3.material.io/m3/pages/scaffold/panes
[m3-spacing]: https://m3.material.io/m3/pages/spacing/overview
[m3-list-detail]: https://m3.material.io/foundations/layout/canonical-layouts/list-detail
[m3-canonical]: https://m3.material.io/m3/pages/canonical-examples/overview
[m3-app-bars]: https://m3.material.io/components/app-bars/guidelines
[m3-app-bars-specs]: https://m3.material.io/components/app-bars/specs
[m3-toolbars]: https://m3.material.io/components/toolbars/guidelines
[m3-nav-bar]: https://m3.material.io/components/navigation-bar/guidelines
[m3-nav-bar-overview]: https://m3.material.io/components/navigation-bar/overview
[m3-nav-rail]: https://m3.material.io/components/navigation-rail/overview
[m3-nav-rail-specs]: https://m3.material.io/components/navigation-rail/specs
[m3-nav-drawer]: https://m3.material.io/components/navigation-drawer/overview
[m3-buttons]: https://m3.material.io/components/buttons/overview
[m3-button-groups]: https://m3.material.io/components/button-groups/guidelines
[m3-segmented]: https://m3.material.io/components/segmented-buttons/overview
[m3-split]: https://m3.material.io/components/split-button/guidelines
[m3-icon-buttons]: https://m3.material.io/components/icon-buttons/guidelines
[m3-fab]: https://m3.material.io/components/floating-action-button/overview
[m3-fab-menu]: https://m3.material.io/components/fab-menu/guidelines
[m3-extended-fab]: https://m3.material.io/components/extended-fab/guidelines
[m3-loading]: https://m3.material.io/components/loading-indicator/guidelines
[m3-progress]: https://m3.material.io/components/progress-indicators/guidelines
[m3-carousel]: https://m3.material.io/components/carousel/guidelines
[m3-lists]: https://m3.material.io/components/lists/guidelines
[m3-lists-overview]: https://m3.material.io/components/lists/overview
[m3-cards]: https://m3.material.io/components/cards/guidelines
[m3-chips]: https://m3.material.io/components/chips/guidelines
[m3-menus]: https://m3.material.io/components/menus/guidelines
[m3-dialogs]: https://m3.material.io/components/dialogs/guidelines
[m3-sheets]: https://m3.material.io/components/bottom-sheets/guidelines
[m3-search]: https://m3.material.io/components/search/guidelines
[m3-sliders]: https://m3.material.io/components/sliders/guidelines
[m3-switch]: https://m3.material.io/components/switch/guidelines
[m3-tabs]: https://m3.material.io/components/tabs/guidelines
[m3-snackbar]: https://m3.material.io/components/snackbar/guidelines
[m3-tooltips]: https://m3.material.io/components/tooltips/guidelines
[m3-text-fields]: https://m3.material.io/components/text-fields/guidelines
[m3-date-pickers]: https://m3.material.io/components/date-pickers/guidelines
[m3-time-pickers]: https://m3.material.io/components/time-pickers/overview
[m3-io26]: https://m3.material.io/blog/whats-new-at-io26
[m3-compose-first]: https://m3.material.io/blog/material-is-compose-first
[m2-empty]: https://m2.material.io/design/communication/empty-states.html
[g-research]: https://design.google/library/expressive-material-design-google-research
[g-launch]: https://blog.google/products/android/material-3-expressive-android-wearos-launch/
[g-android-show]: https://android-developers.googleblog.com/2025/05/the-android-show-io-edition.html
[g-androidify]: https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html
[g-gsf]: https://design.google/library/google-sans-flex-font
[g-design-notes]: https://design.google/library/design-notes-material-3-expressive-liam-spradlin
[g-wear-benefits]: https://developer.android.com/design/ui/wear/guides/get-started/benefits
[g-wear-levels]: https://developer.android.com/design/ui/wear/guides/get-started/levels-expression
[dac-predictive-back]: https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
[dac-wsc]: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
[dac-m3-release]: https://developer.android.com/jetpack/androidx/releases/compose-material3
[dac-adaptive-release]: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
[dac-bom]: https://developer.android.com/develop/ui/compose/bom/bom-mapping
[ax-api-beta01]: https://github.com/androidx/androidx/blob/19991f2c0858aadfcb442ce02b2c8e4743c96898/compose/material3/material3/api/current.txt
[ax-api-navsuite]: https://github.com/androidx/androidx/blob/19991f2c0858aadfcb442ce02b2c8e4743c96898/compose/material3/material3-adaptive-navigation-suite/api/current.txt
[ax-api-adaptive]: https://github.com/androidx/androidx/blob/19991f2c0858aadfcb442ce02b2c8e4743c96898/compose/material3/adaptive/adaptive/api/1.3.0-rc01.txt
[ax-api-main]: https://github.com/androidx/androidx/blob/0b91a4e9bb55cf9591fa2b9ccfe0f900d612e399/compose/material3/material3/api/current.txt
[ax-motion-tokens]: https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt
[ax-tokens]: https://github.com/androidx/androidx/tree/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens
[mdc-floating-toolbar]: https://github.com/material-components/material-components-android/blob/master/docs/components/FloatingToolbar.md
[mdc-motion]: https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md
[mdc-bottom-nav]: https://github.com/material-components/material-components-android/blob/master/docs/components/BottomNavigation.md
[mdc-nav-rail]: https://github.com/material-components/material-components-android/blob/master/docs/components/NavigationRail.md
[jetchat]: https://github.com/android/compose-samples/tree/main/Jetchat
[tg-terms]: https://core.telegram.org/api/terms
[9to5-gm-chat]: https://9to5google.com/2025/08/26/google-messages-chat-redesign/
[9to5-gm-chat-beta]: https://9to5google.com/2025/06/02/google-messages-material-3-expressive-chat/
[9to5-gm-home]: https://9to5google.com/2025/06/04/google-messages-expressive-redesign-expands/
[9to5-gm-search]: https://9to5google.com/2025/08/19/google-messages-material-3-expressive-redesign/
[9to5-gm-more]: https://9to5google.com/2025/06/06/google-messages-material-3-expressive-more/
[9to5-gm-account]: https://9to5google.com/2025/10/17/google-messages-account-menu/
[9to5-gm-viewer]: https://9to5google.com/2025/12/04/google-messages-photo-viewer-redesign/
[9to5-gm-menu]: https://9to5google.com/2026/08/06/google-messages-menu-redesign/
[9to5-gm-menu-wide]: https://9to5google.com/2026/10/01/google-messages-long-press-menu-wide/
[9to5-gm-features]: https://9to5google.com/2026/08/29/new-google-messages-features/
[9to5-gm-themes]: https://9to5google.com/2026/08/27/google-messages-chat-themes-background/
[ap-gm-chat]: https://www.androidpolice.com/google-messages-just-got-a-fresh-new-look-and-the-chat-screen-is-way-more-expressive/
[ap-gm-bubble]: https://www.androidpolice.com/google-messages-new-bubble-animation/
[aa-gm-shuffle]: https://www.androidauthority.com/google-messages-ui-shuffle-september-2026-3713858/
[9to5-photos-ios]: https://9to5google.com/2026/02/12/google-photos-floating-toolbar/
[9to5-photos-android]: https://9to5google.com/2026/07/08/google-photos-floating-bottom-bar-android/
[9to5-photos-backup]: https://9to5google.com/2025/08/05/google-photos-backup-redesign/
[9to5-a16-qpr1]: https://9to5google.com/2025/09/03/android-16-qpr1-pixel/
[9to5-a16-settings]: https://9to5google.com/2025/05/20/android-16-settings-redesign/
[dl-a16-qpr1]: https://www.droid-life.com/2025/05/20/lets-look-at-android-16-qpr1-and-all-this-material-3-expressive-design/
[9to5-a17]: https://9to5google.com/2026/06/16/google-android-17-pixel-launch/
[9to5-account]: https://9to5google.com/2025/07/16/google-account-expressive-redesign/
[9to5-chrome-settings]: https://9to5google.com/2026/04/02/chrome-material-3-expressive-settings/
[9to5-phone]: https://9to5google.com/2025/08/21/google-phone-material-3-expressive-redesign/
[9to5-contacts]: https://9to5google.com/?p=683818
[9to5-gmail-containers]: https://9to5google.com/2025/09/02/gmail-material-3-expressive-containers/
[9to5-gmail-search]: https://9to5google.com/2025/08/15/gmail-material-3-expressive-search/
[9to5-clock]: https://9to5google.com/2025/08/27/google-clock-material-3-expressive-redesign/
[9to5-keep]: https://9to5google.com/2025/08/21/google-keep-material-3-expressive-redesign/
[9to5-files]: https://9to5google.com/2025/08/18/files-m3-expressive-redesign/
[9to5-calendar]: https://9to5google.com/2025/09/08/google-material-3-expressive-redesign/
[9to5-drive]: https://9to5google.com/2025/08/27/google-drive-material-3-expressive-redesign/
[9to5-fitbit]: https://9to5google.com/2025/11/09/fitbit-material-3-expressive/
[9to5-fitbit-toolbar]: https://9to5google.com/?p=707998
[9to5-gemini]: https://9to5google.com/2026/05/19/gemini-app-google-io-2026/
[9to5-nav]: https://9to5google.com/2025/05/14/material-3-expressive-navigation/
[9to5-toolbars]: https://9to5google.com/2025/05/18/material-3-expressive-toolbars/
[9to5-loading]: https://9to5google.com/2025/05/16/material-3-expressive-loading-indicator/
[9to5-menus]: https://9to5google.com/2025/11/22/material-3-expressive-menus/
[9to5-roundup]: https://9to5google.com/2025/11/17/google-material-3-expressive-redesign/
[9to5-recap]: https://9to5google.com/2025/12/27/recap-material-3-expressive/
[tc-leak]: https://techcrunch.com/2025/05/05/google-accidentally-reveals-details-about-its-new-android-design-language-material-3-expressive/

# Motion audit against Material 3 Expressive

Date: 2026-11-11. Scope: every animation in the main sources of `core:ui`, `:app` and each `feature:*` module, read
against Material 3 Expressive's motion system, `docs/DESIGN_SYSTEM.md` §4–§6 and §13, and `docs/PERFORMANCE.md` §4.
Research and documentation only: no app code changed. The fixes are planned in `docs/plans/motion-expressive-plan.md`.
File and line numbers are those of `main` at `b7b13571`; later commits may have moved them by a few lines.
The questions of §5 were decided by the owner on 2026-11-11; the decisions are in §6.

How it was done:

- Every call of the animation APIs was listed with its full argument list and read in context: `tween`, `keyframes`,
  `spring`, `snap`, `repeatable`, easings and durations; `animate*AsState`, `Animatable`, `animate`, `animateTo`,
  `updateTransition`, `rememberInfiniteTransition`; `AnimatedVisibility`, `AnimatedContent`, `Crossfade`,
  `animateContentSize`, `animateItem`; enter and exit transitions, shared bounds and container transforms;
  `graphicsLayer`, `offset { }`, morphs; gestures and the haptics beside them.
- Missing motion has no call to search for. It was found by reading the screens: state that swaps with a plain `if`
  or `when`, lists without `animateItem`, rows a user removes, bars and sheets that come and go.
- Nothing was run on a device. Jump sizes are read from the layout code, and spring timings are computed from the
  constants. Each High and Medium finding was read again in the code before it went into this report.
- Limits: `m3.material.io` renders with JavaScript and could not be read by the tools used, so Material's own pages
  are cited by address only (§1.5), and the rules below rest on the Compose and Android sources, which could be read.
  About a third of the Settings pages were read end to end, the others where a search or a scan of conditional rows
  hit. material3's internals were read from the pinned AAR's bytecode, not its sources.

## 1. The system, in short, with sources

### 1.1 Two kinds of spring, three speeds

Material 3 Expressive animates with springs from a motion scheme, never with durations and easing curves. A scheme
has six specs ([MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)):

- **Spatial** specs are for what changes "the shape or bounds of the component": position, size, scale, rotation,
  corner shapes, morphs. They may overshoot.
- **Effects** specs are for what does not: "For color or alpha animations use the `effects` equivalent". Color,
  opacity and blur. They never overshoot, so a fade cannot flicker past its end.
- Each comes in three speeds. Material's Android library states the rule
  ([Motion.md](https://raw.githubusercontent.com/material-components/material-components-android/master/docs/theming/Motion.md)):
  **fast** for small components (switches, buttons), **slow** for full-screen animations, **default** for what is
  between (bottom sheets, navigation drawers). An element that changes shape and color at once takes two springs, a
  spatial one and an effects one.

Pingogram's theme uses `MotionScheme.expressive()` (`docs/DESIGN_SYSTEM.md` §1,
`core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/theme/Theme.kt`). The real constants, read with `javap` from
the pinned `androidx.compose.material3:material3:1.5.0-beta02` AAR (`ExpressiveMotionTokens`, `StandardMotionTokens`)
and equal to the androidx sources
([ExpressiveMotionTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt),
[StandardMotionTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StandardMotionTokens.kt)):

| Spec (`MaterialTheme.motionScheme`) | Expressive: damping ratio / stiffness | Standard: damping ratio / stiffness | Expressive, computed: overshoot, time to within 1% |
|---|---|---|---|
| `fastSpatialSpec()` | 0.6 / 800 | 0.9 / 1400 | 9.5%, about 0.27 s |
| `defaultSpatialSpec()` | 0.8 / 380 | 0.9 / 700 | 1.5%, about 0.30 s |
| `slowSpatialSpec()` | 0.8 / 200 | 0.9 / 300 | 1.5%, about 0.41 s |
| `fastEffectsSpec()` | 1.0 / 3800 | 1.0 / 3800 | none, about 0.11 s |
| `defaultEffectsSpec()` | 1.0 / 1600 | 1.0 / 1600 | none, about 0.17 s |
| `slowEffectsSpec()` | 1.0 / 800 | 1.0 / 800 | none, about 0.24 s |

The last column is arithmetic on the constants for a unit mass (overshoot `exp(-πζ/√(1-ζ²))`, envelope
`exp(-ζ√k·t)`), not a measurement. A spring that is awaited ends later than it looks: `Animatable.animateTo` returns
only within its visibility threshold (0.01 by default), about twice the time above. Code that waits for a spring before
it acts therefore acts late (findings M-013, M-054).

**What material3's own components use** (the motion tokens each class refers to in the pinned
`material3:1.5.0-beta02` AAR, read with `javap`), which shows the rule at work:

- Fast spatial: toggle buttons, button groups, segmented buttons, chips, switch, radio button, list items, menus,
  tooltips, snackbars, the FAB and its menu, the floating toolbar, the navigation bar and rail, text fields. `Button`
  and `IconButton` morph their pressed shape on the default effects spec instead.
- Default spatial: the navigation drawers (`ModalNavigationDrawer`, `DismissibleNavigationDrawer`), the wide
  navigation rail's layout and its modal form, tabs, navigation items, checkbox, the FAB, date and time pickers.
- Slow spatial: one use only, the full-screen search bar. `rememberSearchBarState` expands on the slow spatial spec
  and collapses on the default one (its `animationSpecForExpand` and `animationSpecForCollapse` defaults).
- The top bars' snap (`TopAppBarDefaults`) and the modal bottom sheet use the default effects spec.
- material3-adaptive 1.3.0 moves its panes on a spring of 0.8 / 380, the default spatial values (1.3).

So what covers part of the screen moves at the default speed, and what grows to fill it opens at the slow one and
closes at the default one.

### 1.2 What goes on which spec

`docs/DESIGN_SYSTEM.md` §5 maps Pingogram's motion onto the scheme, and is the rule this audit applies:

| Motion | Spec |
|---|---|
| Screen transitions, sheets, list reordering, shape morphs | `defaultSpatialSpec()` |
| Press feedback, FAB menu, chips, small pops | `fastSpatialSpec()` |
| Large layout changes: an expanding app bar, a list-detail pane | `slowSpatialSpec()` |
| Color and alpha crossfades | `defaultEffectsSpec()` |
| Reactions, typing dots, send confirmation | `fastEffectsSpec()` |

The transition patterns (§5): shared axis X for a screen pushed above a tab, shared axis Y for vertical flows (auth),
fade through only for the navigation tabs, a slide for chat folders, container transforms for avatar → profile,
thumbnail → media viewer, New chat and search. They are built once in `core:ui`
(`core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/theme/Motion.kt`,
`core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/transition/SharedMedia.kt`). material3 1.5.0-beta02 has no
`MaterialTransitions` class to take them from (checked in the AAR), as §5 already says.

Predictive back: Android's guidance scales the leaving surface from 100% to 90% along the interpolator
(0.1, 0.1, 0, 1) ([Predictive back design](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back)).
`Motion.kt:115` uses that curve for custom surfaces, and Navigation seeks `predictiveBackPopExit` with the gesture.

Shape morphing: `Morph` between two `RoundedPolygon`s, its progress on a spatial spring
([Shapes in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/shapes)). `rememberMorphShape`
(`core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/theme/Shape.kt`) does it and reads the progress in the draw
phase.

### 1.3 Compose defaults that do not follow the scheme

Leaving out a spec does not give the theme's motion. It gives Compose's own default, which no theme changes:

| API, when no spec is passed | What runs | Source |
|---|---|---|
| `AnimatedVisibility` | `fadeIn() + expandIn()` in, `shrinkOut() + fadeOut()` out (in a `Column`: `expandVertically` / `shrinkVertically`), each a spring of damping 1, stiffness 400 (`StiffnessMediumLow`) | [AnimatedVisibility](https://developer.android.com/reference/kotlin/androidx/compose/animation/AnimatedVisibility.composable) |
| `fadeIn()`, `fadeOut()`, `slide*`, `expand*`, `shrink*`, `scale*` | the same spring (1 / 400) | [animation package](https://developer.android.com/reference/kotlin/androidx/compose/animation/package-summary) |
| `AnimatedContent` | `fadeIn(tween(220, delayMillis = 90)) + scaleIn(0.92f, tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))`, and a size change on a spring of 1 / 400 | [AnimatedContent.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/AnimatedContent.kt) |
| `SizeTransform()` without its spec lambda, or `togetherWith` alone | the size spring 1 / 400 whenever the two contents differ in size | same |
| `Crossfade` | `tween()`: 300 ms, `FastOutSlowInEasing` | [Crossfade.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/Crossfade.kt) |
| `animateContentSize()` | spring 1 / 400 | [AnimationModifier.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/AnimationModifier.kt) |
| `Modifier.animateItem()` | fade in, placement and fade out each on a spring 1 / 400 | [LazyItemScope](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyItemScope) |
| `animate*AsState`, `Animatable.animateTo`, `animate()` | `spring()`: damping 1, stiffness 1500 (`StiffnessMedium`) | [SpringSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/SpringSpec) |
| `sharedBounds` / `sharedElement` | bounds on a spring 1 / 400, `fadeIn()` / `fadeOut()` | [SharedTransitionScope.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/SharedTransitionScope.kt) |
| `NavHost` | `fadeIn(tween(700))` / `fadeOut(tween(700))`; predictive pop scales to 70% | [DefaultNavTransitions](https://raw.githubusercontent.com/androidx/androidx/androidx-main/navigation/navigation-compose/src/androidMain/kotlin/androidx/navigation/compose/DefaultNavTransitions.android.kt) |
| `animateScrollToItem(index)`, `bringIntoView()` | foundation's own scroll animation; `animateScrollToItem` takes no spec at all | Compose foundation |
| `animateScrollToPage(page)` | `spring()` 1 / 1500 unless a spec is passed | Compose foundation pager |
| `AnimatedPane` (material3-adaptive 1.3.0) | `PaneMotionDefaults`: a hard-coded spring of 0.8 / 380, the expressive default spatial values, not read from the theme | pinned AAR, `javap` |
| Coil's `crossfade(true)` | a tween | `core/ui/.../component/Avatar.kt:215` says so and fades itself |

material3 components that do read the scheme (references to the motion tokens in the pinned AAR's bytecode): buttons,
toggle buttons and icon buttons (press morph), FAB and FAB menu, list items (the segmented shapes' morph), switch,
checkbox, radio button, chips, modal bottom sheet, menus, tooltips, snackbars, top app bars, search bar, navigation
items, tabs, floating toolbar, button groups, date and time pickers, pull to refresh, text fields, split button.
Components that do not: `Slider`, `LoadingIndicator` and the wavy progress indicators (their own loops),
`SwipeToDismissBox`, `Badge`, `AlertDialog`, `Scaffold`.

### 1.4 Interruption, phases and reduced motion

- **Interruption.** A spring that is retargeted keeps its velocity ("guarantees the continuity of velocity when
  target value changes amid animations",
  [Customize animations](https://developer.android.com/develop/ui/compose/animation/customize)). That holds for
  `animate*AsState`, `Animatable.animateTo` and transitions. It does not hold for the suspend function `animate(from,
  to)`, which starts at the velocity it is given (zero by default) and is not cancelled by a second call, nor for code
  that calls `snapTo` before `animateTo`. A gesture's release must pass its velocity on, and a new touch must cancel the
  settle that is still running.
- **Phases.** `docs/PERFORMANCE.md` §4: animated values are read in the layout or draw phase (`graphicsLayer { }`,
  `Modifier.offset { }`, `drawBehind`, a `ColorProducer`), not in composition. `val x by animate*AsState(...)` passed
  to a composable parameter recomposes that composable every frame.
- **Reduced motion.** With the animator duration scale at 0, Compose ends every animation at its next frame
  ([MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)).
  That covers everything that goes through a spec. What it does not cover is checked by `isReducedMotion()`
  (`core/ui/.../transition/ReducedMotion.kt`): loops that would restart for ever, shape morphs
  (`snapWhenReducedMotion`), container transforms, and motion driven by a finger rather than a spec.

### 1.5 Sources that could not be read

Material's guidance pages are the primary design source and should be read in a browser beside this report. Their
content is not quoted here because the tools could not render them:

- Motion overview and how it works: `https://m3.material.io/styles/motion/overview/how-it-works`
- Motion specs (the scheme's tokens): `https://m3.material.io/styles/motion/overview/specs`
- Transitions (container transform, shared axis, fade through): `https://m3.material.io/styles/motion/transitions/transition-patterns`
- Shape morphing: `https://m3.material.io/styles/shape/shape-morph`

Where this report says "Material", it means the Compose Material 3 API and tokens, Material's Android library document
and `docs/DESIGN_SYSTEM.md`, which was written from those pages.

## 2. Where the project stands

The project is in good shape on the points a search can find:

- No `tween(`, `keyframes`, literal `spring(` or easing in any module's main sources. The one `CubicBezierEasing` is
  the predictive back curve in `core:ui` (`theme/Motion.kt:115`), not a duration.
- All 34 `AnimatedVisibility` calls pass `enter` and `exit` built from the scheme. All 44 `AnimatedContent` calls pass
  a `transitionSpec` from the scheme. All 25 `animate*AsState` calls pass a scheme spec. The one `Crossfade` and the
  one `animateContentSize` do too.
- The three `NavHost`s (`:app`'s, the Chats tab's extra pane, Settings' detail pane) set every transition from
  `Motion.kt`.
- Morphs go through `rememberMorphShape`, which snaps with reduced motion. Loops stop with reduced motion.

What is left falls into four groups, in order of what users see:

1. **Missing motion** (the largest group, 54 of 126 findings): state that swaps with a plain `if`, so a line, a card or a whole
   page jumps. Progress lines above buttons, rows that depend on a switch, dropped rows after a drag, bars that pop.
2. **Gestures that cannot be interrupted**: a release that drops its velocity, a settle that a new touch does not
   cancel, an outcome that waits for a spring to end.
3. **Compose defaults left in place**: 14 `animateItem()` calls, a few `SizeTransform`s, programmatic scrolls.
4. **Inconsistency**: the same kind of bar leaves on two different specs, the same change animates on one screen and
   pops on another.

### Categories and severities

Categories: **Missing** (a state change with no animation where the system or §6 asks for one), **Wrong spec** (a
Compose default left in place, spatial used for color or opacity or effects for movement, the wrong speed), **Wrong
phase** (animated state read in composition), **Not interruptible** (restarts from zero, drops velocity, a new touch
does not take over, or an outcome waits for the spring), **Reduced motion** (ignores it), **Inconsistent** (the same
interaction animated differently), **Duplicate** (hand-rolled motion a core helper or material3 already has).

Severities, as the brief sets them, with how this report reads them:

- **High**: visible jank; a jump of the page, a bar or the control under the finger; a gesture that gets stuck or
  fights the finger when repeated; reduced motion broken.
- **Medium**: a wrong spec or speed that can be seen; a hard cut; a jump inside one card, or of list rows after the
  user's own action.
- **Low**: consistency; not noticeable without comparing two places.

No finding is in the "Reduced motion" category: everything found either goes through a spec, which Compose ends at
once, or already checks `isReducedMotion()`. Two places were questions for the owner instead (Q-14, Q-19).

## 3. Findings

IDs are stable: the plan refers to them. "Expected" names the rule: §n is `docs/DESIGN_SYSTEM.md`, P§n is
`docs/PERFORMANCE.md`, "1.x" is this report. A row that names several files is one finding with one fix.

### 3.1 `core:ui`

Paths are under `core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-001 | `component/ExtraPaneWidth.kt:125-129`, `:154` | The third pane's width after its drag handle is released | Each release launches `animate(from, to, defaultSpatialSpec)`. A running settle is not cancelled when the handle is grabbed again, so the settle and the drag both write `state.width`. The release velocity is ignored | One cancellable settle that starts at the release velocity (1.4). §5 puts a list-detail pane on `slowSpatialSpec` (Q-04) | Not interruptible | Medium | On extra-large windows, grabbing the handle again within the settle makes the pane jitter |
| M-002 | `component/ListReorderState.kt:86-91` | A dragged row when the finger lifts (chat folders, sticker sets, pinned forum topics) | `end()` sets `offset = 0f` and `draggedId = null` in one frame, so the row jumps the rest of the way into its slot | Settle the remaining offset on `defaultSpatialSpec`, starting at the drag's velocity (§5: list reordering → spatial default) | Missing | High | The row snaps under the finger at every drop (seen at M-038 and M-104) |
| M-003 | `component/TabReselection.kt:55` | The list scrolling to its top when the selected tab is tapped again | `animateScrollToItem(0)`: foundation's own scroll animation, which takes no spec | A scheme scroll, as `feature:chat`'s `JumpToBottomButton.kt:123` does with `animateScrollBy(distance, spec)` (Q-09) | Wrong spec | Low | The scroll to top moves unlike the other programmatic scrolls |
| M-004 | `component/AvatarSelection.kt:124-144` | The selected avatar's morph, the fade of the avatar under it and the check's fade | One spatial progress drives the morph and all three alphas, clamped to 0–1 | The morph and the check's size on the spatial spring, the alphas on an effects spring (1.1: two springs for shape and color) | Wrong spec | Low | The fade ends when the spring first crosses its end, a little early; hardly visible |
| M-005 | `navigation/OverlayNavigator.kt:221-224` | The overlay's (media viewer's) own fade in and out | `defaultEffectsSpec` both ways | Other surfaces enter on default and leave on fast effects (`theme/Motion.kt:31-55`); one rule (Q-02) | Inconsistent | Low | None alone |
| M-006 | `component/PhotoEditor.kt:140-148` | The upload ring ↔ the loading indicator on a profile or chat photo | Plain `if / else` | An effects crossfade, as other state swaps | Missing | Low | The indicator pops when preparing turns into uploading |
| M-007 | `component/TabHeader.kt:64` | The tab headers' collapse settling after a scroll | material3's `exitUntilCollapsedScrollBehavior` default snap, which reads the scheme's default effects spec (bytecode) | §5 says an expanding app bar is a large layout change on `slowSpatialSpec`. The library and the document disagree (Q-05) | Inconsistent | Low | None known; listed because the document says otherwise |

### 3.2 `:app`

Paths are under `app/src/main/kotlin/dev/kyralabs/pingogram/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-008 | `ui/PingogramNavigationBar.kt:254-257` (read at `:293`, `:299`) | The floating navigation bar's tab content color | `val contentColor by animateColorAsState(...)` passed to `Icon(tint)` and `Text(color)`, so each tab recomposes every frame of the crossfade | Read in the draw phase: a `ColorProducer` for the text and a tint applied in a layer (P§4) | Wrong phase | Medium | Recomposition of the app's main chrome on every tab change |
| M-009 | `ui/PingogramNavigationBar.kt:273` | A tab's horizontal padding as it becomes selected | `padding(horizontal = if (selected) spacing.large else spacing.medium)` changes in one frame while the indicator and the icon move on springs | Animate the padding on `fastSpatialSpec` in the layout phase, with the icon's reveal (§6.1 Motion) | Missing | Medium | The tabs' labels shift by a few dp at once on every tab change |
| M-010 | `navigation/PingogramNavHost.kt:125-131`, `:192-196` | Predictive back from a full-screen destination that has no back handler of its own (the QR scanner, New story's picking step) | Navigation seeks `fadeOut(defaultEffectsSpec)`. `core:ui`'s `Motion.kt:74-83` says of this spring that it "would have faded the screen to a fifth by 30% of the gesture" | `predictiveBackPopExit` (90% scale, `slowEffectsSpec` fade), as every other destination (§5 Predictive back) | Wrong spec | Medium | The screen is nearly gone a third of the way into the back gesture |
| M-011 | `ui/ChatsListDetail.kt:176` | The Chats tab's panes entering and leaving | `AnimatedPane(boundsAnimationSpec = slowSpatialSpec)`; enter and exit transitions left at the adaptive library's hard-coded spring (1.3) | Pane transitions from the scheme too, so bounds and slide share a speed (Q-04) | Inconsistent | Low | A pane's slide and its resize run at different speeds |
| M-012 | `navigation/PingogramNavHost.kt:150`, `:164`, `:178`, `:192` | Auth ↔ main graph, and any change that is neither a push nor full screen | Fade through | §5: fade through "only for switching the navigation tabs" (Q-06) | Inconsistent | Low | None; the document does not name a transition for signing in or out |

### 3.3 `feature:chats`

Paths are under `feature/chats/src/main/kotlin/dev/kyralabs/pingogram/feature/chats/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-013 | `ui/FolderSwipe.kt:149-151` (with `:131-132`) | Swiping the chat list to the next folder | The folder is selected only after `offset.animateTo(width, fastSpatialSpec)` returns (1.1: about twice the visible time). A new drag in that window calls `snapTo` in `onDragStarted`, which cancels the `animateTo`, so `onSelect` never runs | Select at the release that commits, and let the slide finish on its own (1.4) | Not interruptible | High | Quick repeated swipes advance one folder only; the second swipe drags the neighbour against blank space, and the tab highlight trails the list |
| M-014 | `ui/FolderSlide.kt:122-129` (called at `:267`) | Tapping a second folder tab while the first slide still runs | `bringIn` snaps the offset to a full width toward the tab whatever the current position, and drops the velocity. Its KDoc says the slide "carries on from there" | Start from `position + toward * width`, as the swiped branch does | Not interruptible | High | A one-frame jump of up to the list's width on fast tab taps |
| M-015 | `ui/FolderSwipe.kt:108`, `:149` against `ui/FolderSlide.kt:271` | The full-width slide between two folders' lists | A swipe that commits lands on `fastSpatialSpec` (0.6 damping, about 10% overshoot of what is left, which opens a strip beside the neighbour); a tab tap moves the same two lists on `defaultSpatialSpec` | One spec for the same move. §6 "Swipe actions" names `fastSpatialSpec` for the swipe; 1.1 puts fast on small components (Q-07) | Inconsistent | Medium | A swipe lands with a bounce, a tap does not |
| M-016 | `ui/ChatRow.kt:107`, `:187`; `ui/ArchiveRowStyles.kt:149` | A chat row or the archive row sliding away and springing back after a swipe | `SwipeToDismissBox`'s own settle and `reset()`, not on the scheme (1.3) | `defaultSpatialSpec`, as swipe to reply and the folder swipe spring back (Q-10) | Inconsistent | Low | Rows return with a different feel from bubbles |
| M-017 | `ui/ChatRow.kt:182-185` | The `Confirm` haptic of a row swipe | Fires in `onDismiss`, after the row has settled off screen | At the release that commits (§5 haptics) | Inconsistent | Low | The haptic trails the gesture |
| M-018 | `ui/ChatListHeaderStatus.kt:42` | Connection pill ↔ status line under "Chats" | `togetherWith` without a `SizeTransform` spec; the two differ in size, so Compose's size spring (1 / 400) runs | `SizeTransform(clip = false) { _, _ -> defaultSpatialSpec() }`, as `ui/ChatListTopBar.kt:378` | Wrong spec | Low | The subtitle resizes on a non-scheme spring |
| M-019 | `ui/ChatListTopBar.kt:254` | "Chats" ↔ "3 selected" | No `SizeTransform` spec; the widths differ, with the default clip | A scheme `SizeTransform` | Wrong spec | Low | The title is clipped while its width springs |
| M-020 | `ui/FolderSwipe.kt:116`, `:143-144` | The folder swipe's threshold haptic | `TextHandleMove` at 30% of the position, but a fling's projected landing decides the commit, so the two can disagree | A haptic tied to the commit decision | Inconsistent | Low | A fling that changes the folder gives no haptic; a drag past 30% flung back gives one |
| M-021 | `ui/SearchScreen.kt:281` | The selected filter chip scrolling into view | `bringIntoView()`: Compose's default scroll | `defaultSpatialSpec`, as the folder tabs (`core:ui` `TabScroll.kt:60`) (Q-09) | Inconsistent | Low | Chips scroll unlike tabs |
| M-022 | `ui/SearchPeople.kt:121` | The top people row leaving | `fadeOut(defaultEffectsSpec)` | `fastEffectsSpec`, as the other bars' exits (Q-02) | Inconsistent | Low | A slightly slower exit |
| M-023 | `ui/SearchPeople.kt:54` | People inside the top people row as the query narrows | `items(...)` without `animateItem` | `animateItem` on the scheme, as `stories/StoryRings.kt:102` (§5 Lists) | Missing | Low | Avatars pop and reshuffle |
| M-024 | `ui/SearchScreen.kt:348-349` | The search progress line | Fades in; `if (!shown) return` removes it at once | Fade out on an effects spring | Missing | Low | The line pops off |
| M-025 | `ui/create/ChatPhotoButton.kt:143` | A picked group or channel photo appearing | Coil's `crossfade(true)`, a tween (1.3) | The effects-spring fade `core:ui`'s `Avatar` uses | Wrong spec | Low | None visible; the only tween left in the features |
| M-026 | `ui/create/MemberPickerScreen.kt:228` | The picked people's chips scrolling to the newest | `animateScrollToItem` (no spec possible) | A scheme scroll (Q-09) | Wrong spec | Low | None alone |
| M-027 | `ui/create/MemberPickerScreen.kt:235` | The picked people's row leaving | `shrinkVertically(fastSpatialSpec)`; eleven other bars shrink on `defaultSpatialSpec` | One rule for bars leaving (Q-01) | Inconsistent | Low | The same kind of bar leaves two ways |
| M-028 | `navigation/GlobalSearchState.kt:125`; `ui/create/CreateChatSteps.kt:120`, `:148` | Back that closes docked search or a created channel's step | Plain `BackHandler`: no preview of where back leads | §5 asks custom surfaces for `PredictiveBackHandler`; auth's steps seek (Q-08) | Inconsistent | Low | Back gives no preview there |

### 3.4 `feature:chat`

Paths are under `feature/chat/src/main/kotlin/dev/kyralabs/pingogram/feature/chat/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-029 | `ui/composer/ComposerPanelState.kt:233`, `:311` | The emoji panel or a bot keyboard opening while no keyboard is up, and closing by back or a recording | `windowInsetsBottomHeight` goes from the navigation inset to the panel's height in one frame. Back is a plain `BackHandler` | `defaultSpatialSpec` for a partial-screen surface (1.1), its height read in the layout phase; §5 asks custom surfaces for predictive back (Q-11) | Missing | High | The composer and the whole conversation jump by about the keyboard's height |
| M-030 | `ui/NextChannelPullState.kt:120-124` | The pull to the next channel settling back | `animate(pull, 0f)` runs inside `onPreFling`, which a new drag does not cancel. It writes `pull` every frame until it ends, bypasses `pullTo`'s clamp and drops the release velocity | A cancellable settle with the release velocity, clamped (1.4) | Not interruptible | High | Pulling again right after a release is ignored or jitters |
| M-031 | `ui/MessageBubble.kt:190` | Entering and leaving selection mode | `if (selection != null) SelectionCheck(...)` adds the check to every row in one frame, so incoming bubbles shift by its width while the toolbar slides in on springs | Reveal the check's width on `fastSpatialSpec` in the layout phase with an effects fade (§6 "Selection mode") | Missing | High | Every incoming bubble jumps sideways |
| M-032 | `ui/voice/RecordButton.kt:462`, `:467`; `ui/voice/RecordingBar.kt:128-133` | The record pill that followed the finger toward cancel or lock | `drag.reset()` zeroes the offset at once on release, cancel and lock; the "Slide to cancel" hint's alpha and shift snap too | Spring back on `defaultSpatialSpec`, as swipe to reply (Q-17) | Missing | Medium | The pill jumps back by up to the cancel distance |
| M-033 | `ui/MessageActionsMenu.kt:434-437` | The lifted message returning to its place as the menu closes | The move goes to 0 on `fastSpatialSpec`, but the menu is removed when the `fastEffectsSpec` fade ends (about 0.11 s), before the move has. The opening uses `defaultSpatialSpec` for the same move | Wait for the move, or a move that does not outlast the fade (Q-16) | Wrong spec | Medium | A small snap when a message had been moved to fit |
| M-034 | `ui/voice/RecordButton.kt:190-200` | The record button's container and content colors | Two `by animateColorAsState` read in composition and passed to `filledIconButtonColors`, so the button, its tooltip box and its crossfade recompose every frame | A draw-phase read (P§4) | Wrong phase | Medium | Per-frame recomposition as a recording starts and ends |
| M-035 | `ui/composer/Composer.kt:323` | The text field pill ↔ the recording bar | Plain `if / else`, while the record button's width springs beside it | An effects crossfade | Missing | Medium | The pill's content pops |
| M-036 | `ui/BubbleParts.kt:163-171` | A reaction badge added to or removed from a bubble | Badges pop in and out; only an existing badge's colors crossfade. There is no `Burst` in the module | §4 lists "reaction added" among the shape morphs and reaction bursts among the `MaterialShapes` uses (Q-12) | Missing | Medium | New reactions appear without motion |
| M-037 | `ui/ChatTopBar.kt:96` | The conversation's top bar ↔ the "N selected" bar | Instant swap | A crossfade, as the chat list's title and the in-chat search bar (`ui/search/ChatSearchSlots.kt:43`) | Missing | Low | The top bar pops |
| M-038 | `ui/topics/ForumTopicsScreen.kt:200-202` | Dropping a dragged pinned topic | The offset becomes 0 at once (root: M-002) | Settle on `defaultSpatialSpec`, as the other rows slide | Missing | High | The row snaps from the finger to its slot |
| M-039 | `ui/SwipeToReply.kt:127`, `:143` | A bubble released after a swipe to reply | `onStopped` ignores the velocity; `animate(from, 0f)` starts from rest | Pass the release velocity | Not interruptible | Low | A brief stall on a flick back |
| M-040 | `ui/ReactionToggleState.kt:62` | Tapping a reaction badge again mid-crossfade | `snapTo(0f)` restarts the crossfade | Continue from the current fraction | Not interruptible | Low | A flicker on a double tap |
| M-041 | `ui/composer/Composer.kt:260`; `ui/bot/CommandSuggestions.kt:84`; `ui/inline/InlineResultsArea.kt:114`; `ui/panel/StickerSuggestionsArea.kt:50` | Bars above the composer leaving | `shrinkVertically(fastSpatialSpec)`; the pinned, translate, scheduled, typing, forward, share and other bars use `defaultSpatialSpec`. §6 names the fast exit only for the sticker suggestions | One rule (Q-01) | Inconsistent | Low | The same kind of bar leaves two ways |
| M-042 | `ui/ChatTitle.kt:167-171` | Status ↔ "typing…" ↔ "Connecting…" under the chat's name | A `when` swap | An effects crossfade, as the chat list header's status | Missing | Low | The status pops |
| M-043 | `ui/JumpToBottomButton.kt:59`, `:221` | The jump-to-bottom and jump-to-mention buttons showing and hiding | A hand-rolled `AnimatedVisibility(scaleIn + fadeIn)` | material3's `Modifier.animateFloatingActionButton(visible, alignment, ...)` is in the pinned version (checked in the AAR) and reads the scheme | Duplicate | Low | None visible |
| M-044 | `ui/ChatScreen.kt:667` | A new message arriving | It fades in place while its neighbours slide | §5: "new message enters with a spatial spring from the bottom" (Q-13) | Missing | Low | No entry movement |
| M-045 | `ui/bot/CommandSuggestions.kt:123`; `ui/search/SearchSenderSheet.kt:136`; `ui/bot/BotCommandsBottomSheet.kt:94`; `ui/pinned/PinnedMessagesSheet.kt:75`; `ui/panel/GifTabContent.kt:255`; `ui/poll/PollVotersSheet.kt:122`; `ui/interactions/MessageInteractionsSheet.kt:148` | Rows of lists that are filtered, paged or removed while shown | No `animateItem` | §5: `animateItem` on every lazy item; `ui/forward/ForwardBottomSheet.kt:284` does it | Missing | Low | Rows pop |
| M-046 | `ui/panel/EmojiPanel.kt:114` | Emoji / Stickers / GIFs content | A `when` swap | An effects crossfade, as the Saved Messages tabs | Missing | Low | The panel's content pops |
| M-047 | `ui/NextChannelPullState.kt:93`, `:230-233` | Arming the pull to the next channel | A haptic on arming only (chat rows and the folder swipe tick both ways); "Pull up" ↔ "Release" swaps at once | One convention | Inconsistent | Low | No cue when backing out |
| M-048 | `ui/voice/RecordingBar.kt:84` | The held ↔ locked recording bar | No `SizeTransform` spec; the sizes differ only at large font scales | The scheme `SizeTransform` | Wrong spec | Low | None at 100% font |
| M-049 | `ui/topics/ForumTopicsScreen.kt:200` | Forum topic rows | `animateItem` with Compose's defaults (1.3) | The three scheme specs, as the module's other lists | Wrong spec | Low | Topics move on a different spring from chats |
| M-050 | `ui/bot/CommandSuggestions.kt:109`; `ui/inline/InlineResultsArea.kt:211` | Keyboard picks scrolling into view | `animateScrollToItem` (no spec possible) | A scheme scroll (Q-09) | Wrong spec | Low | None alone |
| M-051 | `navigation/ChatNavigation.kt:114` | Back from a topic or thread in the detail pane | Plain `BackHandler` over a shared-axis change | Predictive, as pushed screens (Q-08) | Inconsistent | Low | Back gives no preview in the detail pane |

### 3.5 `feature:calls`

Paths are under `feature/calls/src/main/kotlin/dev/kyralabs/pingogram/feature/calls/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-052 | `ui/CallScreen.kt:998` (with `:1058`, `:1080`) | The incoming call's answer slider when released | `onDragStopped = { onRelease() }` ignores the velocity; `animate(offset, target, spec)` starts from rest, runs in the draggable's coroutine, and nothing cancels it when the handle is grabbed again, so the spring and the drag both write `offset` | Pass the release velocity, cancel the settle on a new drag, and let a fling toward a side count (1.4) | Not interruptible | High | A short slide followed by a quick second try fights the finger; a flick to answer does nothing |
| M-053 | `ui/comments/GroupCallCommentsPane.kt:449` (with `:458`) | Floating reactions over the comments | `reactions.forEach { FloatingEmoji(...) }` without `key(reaction.id)`, so each `Animatable` is remembered by position. The view model keeps the last six (`GroupCallCommentsViewModel.kt:209`), so from the seventh on every slot's reaction changes and all six restart | `key(reaction.id)` around each, and drop finished ones | Not interruptible | High | Old reactions float up again in a burst with every new one |
| M-054 | `ui/CallScreen.kt:1062` (with `:941-948`) | Answering or declining after the slider is released past 70% | `animateTo(end, fastSpatialSpec)` must return before `onAnswer`, `onDecline` and the `Reject` haptic run (1.1: an awaited spring ends late) | Act and give the haptic at the release; let the handle finish alone (§5 haptics) | Not interruptible | Medium | The call is answered, and the haptic comes, noticeably after the finger lifts |
| M-055 | `ui/GroupCallScreen.kt:259` | The voice chat's body when "Comments" is toggled | `if (commenting) { CommentingBody(); return }` swaps rows and a docked panel for tiles, compact controls and a 60% pane in one frame | A large layout change: `slowSpatialSpec` for the resize with effects fades (§5) | Missing | High | The whole screen jumps on a toggle that itself morphs |
| M-056 | `ui/GroupCallScreen.kt:242` | The user's own camera view above the participants | Plain `if`; the list below jumps by the tile's height. The sharing pill just above it (`:226`) grows in | `expandVertically(defaultSpatialSpec)` with an effects fade, as its sibling | Missing | High | The participants jump when the camera turns on or off |
| M-057 | `ui/GroupCallVideoGrid.kt:78-93` (items at `ui/GroupCallScreen.kt:527`, `ui/GroupCallTiles.kt:73`) | Video tiles appearing, leaving, and changing from square to 16:9 when pinned | A plain `Column` and `Row`; the lazy item that holds them has no `animateItem` | Bounds on `defaultSpatialSpec`, fades on effects springs (Q-15) | Missing | Medium | Tiles pop and re-flow at once on a tap to pin |
| M-058 | `ui/GroupCallVideoGrid.kt:147` | The "speaking" outline of a video tile | `Modifier.border` added or removed in composition | The avatar rings' fade (`ui/GroupCallScreen.kt:563`, `ui/GroupCallTiles.kt:132`): `fastEffectsSpec` in the draw phase, still with `LocalStillCallAnimations` | Inconsistent | Medium | The outline flickers while the rings beside it fade |
| M-059 | `ui/CallScreen.kt:162-168` (also `:372`, `:393`, `:550`) | A call when a camera turns on or off; the "Video paused" pill; the self-view card; the key's emojis over video | Plain `if / else` between the voice and video layouts; the emojis grow in on a voice call (`:218`), not on a video call | An effects crossfade between the layouts; pills and card as the sharing pill (§6 "Call screen") (Q-15) | Missing | Medium | A hard cut from the avatar to the video |
| M-060 | `ui/CallScreen.kt:653` | The explanation under the key's emoji pill | `if (expanded) Text(...)` | Grow in on `defaultSpatialSpec` with an effects fade, as the pill | Missing | Medium | The header grows at once and the avatar jumps down |
| M-061 | `ui/invite/InvitePeopleSheet.kt:262-263` | A picked contact's row | Shape and color are plain values on `clip().background()` | §4: a selected row morphs to `large` on the list item's fast spatial spring; `history/RecentCallRow.kt` gets it from `ListItemDefaults.shapes()` | Missing | Medium | The row's container pops while its avatar morphs into a check |
| M-062 | `ui/CallScreen.kt:954` (with `:1067-1074`) | The slider handle's nudge on a tap | Each tap launches a new `nudge()` that nothing cancels: two `animate` calls in a row, each from rest | One cancellable job | Not interruptible | Low | A double tap stutters |
| M-063 | `history/RecentCallsScreen.kt:241`, `:254` | Day headers and the footer when a call is deleted | No `animateItem`; the rows have it (`:249`) | §5: every lazy item | Missing | Low | Headers jump while rows slide |
| M-064 | `history/StartCallSheet.kt:220`; `ui/invite/InvitePeopleSheet.kt:249` | Contact rows while the search filters | Keyed items without `animateItem` | §5 Lists | Missing | Low | Rows pop |
| M-065 | `ui/GroupCallScreen.kt:367`, `:276` | Mute ↔ Raise hand in the compact row; the docked panel when the call ends | Plain `if`; the docked panel crossfades the same swap (`:679`) and the one-to-one screen fades its controls (`ui/CallScreen.kt:237`) | The sibling's transition | Inconsistent | Low | The same change animates in one layout and pops in the other |
| M-066 | `ui/CallScreen.kt:224`; `ui/CallRatingSheet.kt:182`; `ui/GroupCallScreen.kt:428` | The grow-in pills leaving | Three variants: shrink default with a default fade; shrink default with a fast fade; shrink only, no fade | One pairing (Q-01, Q-02) | Inconsistent | Low | Consistency only |
| M-067 | `ui/CallControls.kt:59`; `history/RecentCallsScreen.kt:295` | "Back to the app" and the page error's "Try again" | No `shapes =`, so no press morph | §6 Buttons and Icon buttons | Inconsistent | Low | Consistency only |
| M-068 | `ui/ScreenSharing.kt:43` | The "Share screen" toggle | No `ToggleOn` / `ToggleOff` haptic; the other call toggles have one (`ui/CallControls.kt:264`) | §5 haptics | Inconsistent | Low | Consistency only |
| M-069 | `ui/GroupCallTiles.kt:79`, `:84` | "Invite link" and "Add people" in the tiles grid | No `animateItem`; the list's add row has it (`ui/GroupCallScreen.kt:541`) | §5 Lists | Inconsistent | Low | Consistency only |
| M-070 | `history/RecentCallsScreen.kt:249`; `ui/GroupCallScreen.kt:541`, `:545`; `ui/GroupCallTiles.kt:89`; `ui/comments/GroupCallCommentsPane.kt:247` | Rows and tiles that have `animateItem` | `animateItem()` with Compose's defaults (1.3) | Fades on effects springs, placement on `defaultSpatialSpec` | Wrong spec | Low | Rows move on a non-scheme spring |
| M-071 | `ui/GroupCallScreen.kt:679` | The docked panel's Mute ↔ Raise hand | `SizeTransform(clip = false)` without a spec | The scheme's `defaultSpatialSpec` | Wrong spec | Low | None unless the two differ in size |

### 3.6 `feature:media`

Paths are under `feature/media/src/main/kotlin/dev/kyralabs/pingogram/feature/media/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-072 | `ui/DismissState.kt:104` (with `ui/MediaViewerUiState.kt:369`, `ui/MediaPlayback.kt:146-151`, `ui/MediaViewerViewModel.kt:404-405`) | A swipe up on a video that has not started (power saving's play button, or still loading) | `playsVideo` is true once the item is in the player, so the swipe up calls `dismiss(onSwipeUp)` and sets `isDismissed`. `handOff()` returns null without `hasStarted`, so no effect is sent and nothing springs back | Dismiss only when the hand-off can happen; else spring back with the velocity | Missing | High | The video stays displaced on a dimmed background; drag, the back button and Esc no longer work, only system back leaves. To confirm on a device |
| M-073 | `ui/MediaViewerScreen.kt:290-292` (with `ui/ViewerEnter.kt:22-32`) | The viewer's bottom bar after the opening transform | It returns until the way in has settled, then composes `AnimatedVisibility(visible = true)` for the first time, which shows at once. The comment "the bars fade in anyway" does not hold | A `MutableTransitionState(false)` with its target true, so `fadeIn(defaultEffectsSpec)` runs | Missing | Medium | The bar pops in at full opacity |
| M-074 | `ui/zoom/ZoomState.kt:122-137` | Panning a zoomed photo | No velocity and no decay on release | Keep the fling (1.4) | Not interruptible | Medium | The photo stops dead when the finger lifts |
| M-075 | `ui/MediaViewerScreen.kt:355`, `:368`; `ui/MediaViewerKeys.kt:129` | A page turn by a side tap, an arrow key or a screen reader | `animateScrollToPage(target)`: the pager's default spring (1 / 1500) | `defaultSpatialSpec`, as §6 "Media viewer" says and `story/StoryViewerScreen.kt:180` does | Wrong spec | Medium | Tap-to-turn moves unlike the stories pager |
| M-076 | `story/StoryViewerScreen.kt:188` | The story being left as a tap or its end moves to the next chat | The current chat changes first, so the outgoing page turns into its placeholder in the frame the slide starts | Keep the outgoing page's content until the pager settles | Missing | Medium | The picture turns into a grey frame, which then slides out |
| M-077 | `ui/DismissState.kt:191`, as used by `story/StoryViewerScreen.kt:80`, `:135-136` | The stories viewer after a completed predictive back | `leaveAtBackScale()` moves the 90% scale from the draw phase into the layout. That serves the media viewer's container transform; the stories viewer only fades, and its chrome sits inside the scaled content | Keep the draw-phase scale through the fade | Inconsistent | Medium | Text and buttons change size and the page is measured again in the first exit frame. To confirm on a device |
| M-078 | `storypost/NewStoryRoute.kt:50-63` | New story: picking ↔ composing | A `when` on the step, back by a plain `BackHandler` | A flow's steps animate and back is predictive (§5) (Q-08, Q-18) | Missing | Medium | A full-screen hard cut both ways |
| M-079 | `storypost/PickStrip.kt:142` (with `:90-95`) | A dragged tile when dropped | Ending the drag swaps `graphicsLayer { translationX }` for `animateItem`; what is left of the offset (up to half a tile) is dropped | Settle the offset to 0 on `defaultSpatialSpec` | Missing | High | The tile jumps into its slot under the finger |
| M-080 | `storypost/StoryPeopleSheet.kt:262-263` | A picked person's row | As M-061 | §4: the selected row's morph | Missing | Medium | The row pops |
| M-081 | `ui/zoom/ZoomState.kt:108` | Double-tap, key or screen-reader zoom repeated mid-way | `animate(0f, 1f)` on a fraction restarts from rest | Carry the velocity | Not interruptible | Low | A slight hitch on a quick second double tap |
| M-082 | `ui/DismissState.kt:196` | A cancelled predictive back in both viewers | `fastSpatialSpec` on full-screen content; its bounce is cut flat by the clamp in `predictiveBackScale`; a new gesture does not cancel it | Default speed, or §5's effects return, cancelled by a new gesture (Q-22) | Wrong spec | Low | An abrupt end to the return |
| M-083 | `ui/MediaChrome.kt:195`, `:203` | The caption and video controls as the page changes | Plain `if` | An effects crossfade | Missing | Low | The bar's content pops as pages settle |
| M-084 | `storypost/NewStoryScreen.kt:168` | The large item shown when another tile is tapped | Instant swap | An effects crossfade | Missing | Low | A hard cut |
| M-085 | `storypost/PickStrip.kt:98`, `:100`; `storypost/StoryPeopleSheet.kt:249` | The "preparing" and "Add more" tiles; people rows | No `animateItem` (the preparing tiles have no key either) | §5 Lists | Missing | Low | They jump while the picks slide |
| M-086 | `storypost/StoryComposeParts.kt:141`, `:148`, `:150` | The link dialog's buttons | No `shapes =` | §6 Buttons | Inconsistent | Low | Consistency only |
| M-087 | `story/StoryBottomBar.kt:129` | The reactions pill leaving | `shrinkVertically(fastSpatialSpec)`; the other grow-in bars of the module shrink on default | One rule (Q-01) | Inconsistent | Low | Consistency only |
| M-088 | `storyalbum/StoryAlbumsSection.kt:49` | The "Albums" section on a profile once loaded | It appears at once and pushes the content below | Grow in on `defaultSpatialSpec` with an effects fade | Missing | Low | The profile's content below shifts once |
| M-089 | `storyalbum/StoryAlbumScreen.kt:230`; `storypost/PickStrip.kt:94` | Album tiles and picked tiles | `animateItem` with Compose's defaults (1.3) | The scheme specs | Wrong spec | Low | Tiles move on a non-scheme spring |

### 3.7 `feature:profile`

Paths are under `feature/profile/src/main/kotlin/dev/kyralabs/pingogram/feature/profile/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-090 | `chatinfo/settings/ChatSettingsScreen.kt:114`; `chatinfo/ChatPermissionsScreen.kt:92`; `chatinfo/actions/RecentActionsScreen.kt:151` | The "saving" wavy line at the top of the page | `if (state.isSaving) SavingIndicator()` as the first child of a spaced column: every switch or chip tap pushes the whole page down and back | A fixed slot or an overlay, its alpha on `defaultEffectsSpec` (§6 "Group and channel settings": "at the top while it answers") (Q-20) | Missing | High | The whole page jumps on every toggle |
| M-091 | `chatinfo/MemberRightsScreen.kt:166`; `chatinfo/EditInviteLinkScreen.kt:191` | The same line above the buttons | Plain `if` | As M-090 | Missing | Medium | The buttons jump on save |
| M-092 | `chatinfo/welcome/WelcomeMessagesScreen.kt:158-197`; `chatinfo/ChatMembersScreen.kt:294-298`; `chatinfo/community/CommunityScreen.kt:179`; `chatinfo/actions/RecentActionsScreen.kt:155`, `:173` | The header, "Add message", footer, preview and "more" items around animated rows | No `animateItem`, while the rows between them have it: they snap to their new place while the rows still fade and slide | The rows' `animateItem` specs (§5: every lazy item) | Inconsistent | Medium | The footer and the Add button jump over rows still moving; the welcome switch pops three blocks in |
| M-093 | `ui/ProfileScreen.kt:246-287`; `chatinfo/ChatInfoScreen.kt:254-262` | The Photos, Info, Members and Administration sections | Keyed full-width grid items without `animateItem`; photos and the full info arrive after the first frame | Fade in on `defaultEffectsSpec`, placement on `defaultSpatialSpec` | Missing | Medium | Sections pop in and push the media grid down |
| M-094 | `chatinfo/settings/ChatSettingsScreen.kt:178`, `:267`, `:301` | The reaction picker, "Show authors' profiles", "Show topics as tabs" | `if` / `takeIf` on the switch just flipped | Expand and shrink on `defaultSpatialSpec` with effects fades | Missing | Medium | The groups below jump |
| M-095 | `chatinfo/requests/JoinRequestsScreen.kt:137-152`; `chatinfo/InviteLinksScreen.kt:255`, `:329` | An answered join request; "Add all / Dismiss all"; a revoked link moving to "Revoked links" | A plain `Column` | Fade and placement, as the members' rows | Inconsistent | Medium | Rows pop |
| M-096 | `ui/SharedMediaContent.kt:111-128`, `:153` | Shared media on a tab change; media cells | Loading, empty and items swap at once; media cells have no `animateItem`, file and link rows do | Treat cells and rows alike; the tabs' transition is open (Q-18) | Inconsistent | Low | A hard cut between tabs |
| M-097 | `chatinfo/ChatMembersScreen.kt:289`; `chatinfo/actions/RecentActionsScreen.kt:169`; `chatinfo/community/CommunityScreen.kt:176`; `chatinfo/welcome/WelcomeMessagesScreen.kt:169`; `ui/SharedMediaContent.kt:161`, `:170`; `ui/SharedMediaRows.kt:104` | Rows that have `animateItem` | `animateItem()` with Compose's defaults (1.3) | The scheme specs | Wrong spec | Low | Rows move on a non-scheme spring |
| M-098 | `ui/ProfilePanes.kt:117`, `:120` | The profile's two panes on expanded windows | `AnimatedPane` without `boundsAnimationSpec`: the adaptive library's hard-coded spring | The spec the Chats tab's panes use (Q-04) | Wrong spec | Low | The panes resize at a different speed from the Chats tab's |

### 3.8 `feature:contacts`

Paths are under `feature/contacts/src/main/kotlin/dev/kyralabs/pingogram/feature/contacts/ui/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-099 | `NewContactScreen.kt:357-358` | The flood-wait note and the progress line above "Add" | Plain `if`: the button just tapped drops by the line's height and comes back | A reserved slot or an expand on `defaultSpatialSpec` with an effects fade (Q-20) | Missing | High | The button moves under the finger on every submit |
| M-100 | `NewContactScreen.kt:196` | Username ↔ phone field on the method toggle | Plain `if` | A crossfade with `SizeTransform` on `defaultSpatialSpec`, as the same page does for `notOnTelegram` (`:156`) | Missing | Medium | The form below jumps |
| M-101 | `ContactsScreen.kt:315` | The sticky letter headers | No `animateItem`; the rows use `Modifier.row` with scheme specs | The same specs | Inconsistent | Low | A header snaps while rows slide |
| M-102 | `ContactFormParts.kt:209` | The flood-wait ring | A copy of `core:ui`'s `CountdownIndicator` | Use the core component | Duplicate | Low | None |
| M-103 | `NewContactScreen.kt:137` | Back from "not on Telegram" to the form | Plain `BackHandler` | Predictive, as auth's steps (Q-08) | Inconsistent | Low | Back gives no preview |

### 3.9 `feature:settings`

Paths are under `feature/settings/src/main/kotlin/dev/kyralabs/pingogram/feature/settings/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-104 | `ui/folders/ChatFoldersList.kt:146`; `ui/stickers/StickersList.kt:233` | A dragged folder or sticker set when the finger lifts | `translationY = if (dragged) reorder.offset else 0f`; the offset is zeroed in one frame (root: M-002) | Settle on `defaultSpatialSpec` (§5; §6 "Chat folders") | Missing | High | The row snaps under the finger at every drop |
| M-105 | `ui/DataStorageScreen.kt:152` | The storage card at the top of Data and storage | Plain `if (usage is Content)`: it appears once the statistics answer and pushes every group down | Grow in (`expandVertically` on `defaultSpatialSpec`, an effects fade), or reserve its place | Missing | High | The whole page jumps down just after it opens |
| M-106 | `ui/account/FlowStepState.kt:59`; `ui/twostep/TwoStepVerificationSteps.kt:287`; `ui/account/DeleteAccountParts.kt:103`; `ui/network/EditProxyScreen.kt:268`; `ui/qr/QrLoginScreen.kt:162`; `ui/stickers/create/StickerTiles.kt:113`; `ui/folders/ChatFolderEditParts.kt:318`; `ui/network/ProxyLinkSheet.kt:173`; `ui/folders/invite/FolderInviteSheet.kt:205`; `ui/stickers/set/StickerSetSheet.kt:203` | The wavy progress line above a step's buttons | Inserted with a plain `if`: the button just tapped drops by the line and its spacing and jumps back when Telegram answers. The steps' `SizeTransform` runs on a step change only | Reserve the line's height, or expand it on `defaultSpatialSpec` with an effects fade; `feature:auth` keeps the button still (`ui/components/AuthComponents.kt:298-306`) (Q-20) | Missing | High | The button moves under the finger on every submit |
| M-107 | `ui/SettingsScreen.kt:133`, `:150` | Settings home's header ↔ its search bar; home ↔ results | Two plain `if (search.isOpen)`: the large header is replaced by the small bar in one frame | A crossfade on the effects springs with the height on `defaultSpatialSpec` (§6 "In-chat search": "the two bars cross-fade on the effects springs") | Missing | High | The page jumps up or down as search opens or closes |
| M-108 | `ui/NotificationSettingsScreen.kt:172` | The "Notifications are off" banner | Plain `if` | Expand and shrink on `defaultSpatialSpec` with effects fades, as auth's banners | Missing | Medium | The page jumps when the user comes back from Android's settings |
| M-109 | `ui/NotificationSettingsScreen.kt:131-141`; `ui/network/NetworkUsageScreen.kt:119` | Private / Groups / Channels (and Mobile / Wi-Fi / Roaming) switching the group below | Instant swap; the groups differ in their rows | A crossfade with `SizeTransform` on `defaultSpatialSpec` (§6 "New channel") | Missing | Medium | The groups below jump |
| M-110 | `ui/PrivacyDataCards.kt:59` | The privacy checkup card on "Not now" | `return` removes it in one frame | `shrinkVertically` on `defaultSpatialSpec` with a fade | Missing | Medium | The page jumps up by the card's height |
| M-111 | `ui/SettingsGroups.kt:87-95` (root); seen at `ui/NotificationStoryItems.kt:47`, `ui/PrivacySettingScreen.kt:186`, `:204`, `ui/AppearanceChats.kt:104`, `ui/AutoDownloadItems.kt:63`, `ui/network/ProxiesScreen.kt:147`, `:159`, `ui/network/EditProxyScreen.kt:150` | Rows that depend on a switch or choice just made | `SettingsGroup` composes its rows in a plain `Column`: they pop in and out | Expand and shrink on `defaultSpatialSpec` with effects fades, once, in `SettingsGroup` (Q-21) | Missing | Medium | The groups below jump on a toggle |
| M-112 | `ui/network/EditProxyScreen.kt:212` | The credential fields as the protocol changes | A plain `when`: two fields ↔ one | A crossfade with `SizeTransform`, as `ui/account/phone/ChangePhoneNumberScreen.kt:154` | Missing | Medium | The form below jumps by a field |
| M-113 | `ui/ActiveSessionsScreen.kt:145`, `:164`; `ui/websites/ConnectedWebsitesScreen.kt:167`; `ui/lockedchats/LockedChatsScreen.kt:157`; `ui/privacy/exceptions/PrivacyExceptionsScreen.kt:139`; `ui/shownfirst/ChatsShownFirstScreen.kt:122`; `ui/passkeys/PasskeysScreen.kt:221`; `ui/network/ProxiesScreen.kt:175`; `ui/stickers/create/StickerTiles.kt:87` | Rows the user removes or moves | `forEach` in a plain `Column` or `FlowRow`: the row vanishes and the rest jump | A fade out on `fastEffectsSpec` and placement on `defaultSpatialSpec`, as `settingsListRow` (`ui/SettingsComponents.kt:320`) gives Blocked users, Notification exceptions, folders and stickers | Inconsistent | Medium | The same action animates on some pages and pops on others |
| M-114 | `ui/account/FlowStepState.kt:58`; `ui/twostep/TwoStepVerificationSteps.kt:286`; `ui/account/DeleteAccountParts.kt:102` | The flood-wait note above the buttons | `?.let`, instant | Expand, as auth's flood-wait banner (`feature:auth` `ui/components/AuthBars.kt:166-171`) | Inconsistent | Medium | The buttons jump when the wait starts and ends |
| M-115 | `ui/passcode/PasscodeLockScreen.kt:101`; `ui/lockedchats/LockedChatsScreen.kt:115` | The passcode entry → the unlocked page | A plain `when` | A crossfade on the effects springs, as the step flows | Missing | Medium | A hard cut after a right passcode |
| M-116 | `ui/ColorThemeParts.kt:102`; `ui/ChatAppearanceParts.kt:150`; `ui/profilecolor/ProfileColorScreen.kt:220`; `ui/ThemeModeCards.kt:125`, `:136` | The check on a chosen chip; the ring and check of a theme-mode card | `if (selected)` and a literal border color: they pop while the chip's shape morphs on a spring | Scale in on `fastSpatialSpec`, fade on `fastEffectsSpec` (§5: chips); the ring's color on `defaultEffectsSpec` | Missing | Low | The check pops ahead of the morph |
| M-117 | `ui/passcode/PasscodeEntryParts.kt:191` | PIN dots on delete or after a wrong PIN | Only entering animates (`:197-199`); removed dots vanish and the rest re-centre in one frame | Scale out on `fastSpatialSpec`, placement on the same | Missing | Low | Dots pop out |
| M-118 | `ui/passcode/PasscodeEntryParts.kt:93-108` | The passcode badge's icon | The color from `animateColorAsState` is passed to `Icon(tint = …)` in composition; the lock ↔ error icon swaps at once, while auth's badge crossfades both in the draw phase (`feature:auth` `ui/components/AuthComponents.kt:143-169`) | A draw-phase read (P§4) and a crossfade | Wrong phase | Low | The icon recomposes each frame of the fade |
| M-119 | `ui/SessionSwipe.kt:100` | "End session" behind a swiped row | `if (swiping)`: no growth, no pop at the threshold | §5: "swipe actions reveal icons with scale springs" (Q-10) | Inconsistent | Low | The reveal is static |
| M-120 | `ui/twostep/TwoStepVerificationFields.kt:158` | The flood-wait ring | Its own `animateFloatAsState` and wavy ring, the same as `core:ui`'s `CountdownIndicator` | Use the core component | Duplicate | Low | None |
| M-121 | `navigation/SettingsListDetail.kt:131`, `:142` | Settings' panes entering and leaving | As M-011 | As M-011 (Q-04) | Inconsistent | Low | As M-011 |
| M-122 | `ui/twostep/TwoStepVerificationFlowScreen.kt:140`; `ui/account/email/LoginEmailScreen.kt:126`; `ui/account/phone/ChangePhoneNumberScreen.kt:145`; `ui/stickers/create/NewStickerSetScreen.kt:84`; `ui/passcode/SetPasscodeScreen.kt:79`; `ui/home/SettingsSearch.kt:125` | Back that returns a step, or closes settings search | Plain `BackHandler`: the step crossfades only after the gesture ends | §5 asks custom surfaces for `PredictiveBackHandler`; auth's steps seek theirs (Q-08) | Inconsistent | Low | Back gives no preview in the step flows |

### 3.10 `feature:auth`

Paths are under `feature/auth/src/main/kotlin/dev/kyralabs/pingogram/feature/auth/ui/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-123 | `AuthStepTransition.kt:58` (with `:50-54`) | A cancelled predictive back, then a new back gesture | `scope.launch { returnTo(…) }` is never cancelled: its seeks, its `snapTo` and `isSeeking = false` run while the new gesture is seeking | Keep the job and cancel it when a gesture starts (1.4) | Not interruptible | Medium | A second back swipe within the return flickers and snaps to the start |
| M-124 | `components/AuthBars.kt:57`, `:71`; `CodeScreen.kt:113` | The connection status title, the back arrow, the countdown → "Resend" | Plain `if` | A crossfade on `defaultEffectsSpec` | Missing | Low | Small pops |

### 3.11 `feature:scanner` and `feature:feedback`

Paths are under `feature/scanner/src/main/kotlin/dev/kyralabs/pingogram/feature/scanner/` and
`feature/feedback/src/main/kotlin/dev/kyralabs/pingogram/feature/feedback/`.

| ID | File:line | What animates | Current spec | Expected spec and why | Category | Severity | User impact |
|---|---|---|---|---|---|---|---|
| M-125 | scanner `QrScannerScreen.kt:175-200`, `:235` | The camera rationale card ↔ the viewfinder; the torch button | A plain `when` / `if`; only the result card crossfades (`:280`) | §6 "QR code scanner": "Steps crossfade on effects springs with `SizeTransform` on `defaultSpatialSpec`" | Missing | Medium | The card cuts to the camera when the permission is granted |
| M-126 | feedback `FeedbackScreen.kt:275` | The report's text hiding | It leaves on `fastSpatialSpec` and `fastEffectsSpec` and enters on default: a partial-screen block | `defaultSpatialSpec` for what is not a small component (1.1) (Q-01) | Inconsistent | Low | None alone |

### 3.12 Counts

| Module | High | Medium | Low | Total |
|---|---|---|---|---|
| `core:ui` | 1 | 1 | 5 | 7 |
| `:app` | 0 | 3 | 2 | 5 |
| `feature:chats` | 2 | 1 | 13 | 16 |
| `feature:chat` | 4 | 5 | 14 | 23 |
| `feature:calls` | 4 | 6 | 10 | 20 |
| `feature:media` | 2 | 7 | 9 | 18 |
| `feature:profile` | 1 | 5 | 3 | 9 |
| `feature:contacts` | 1 | 1 | 3 | 5 |
| `feature:settings` | 4 | 8 | 7 | 19 |
| `feature:auth` | 0 | 1 | 1 | 2 |
| `feature:scanner`, `feature:feedback` | 0 | 1 | 1 | 2 |
| **Total** | **19** | **39** | **68** | **126** |

By category: Missing 54, Inconsistent 35, Wrong spec 18, Not interruptible 13, Wrong phase 3, Duplicate 3, Reduced
motion 0.

### 3.13 What is fine

What was read and found right is listed in the plan under "What not to change", so the sessions that fix the findings
leave it alone: the transitions in `core:ui`'s `Motion.kt`, the morphs, the draw-phase nodes, the gestures that
already keep their velocity, and every `AnimatedVisibility` and `AnimatedContent` whose specs come from the scheme.

### 3.14 Tests

Test sources were searched apart from the main sources. They hold no animation of their own to audit; what matters
is which motion rules they check today:

- **Reduced motion**: `core:ui`'s `transition/ReducedMotionTest.kt` (the helper and the morph snap) and
  `feature:chat`'s `TypingDotsTest.kt`. No feature screen has a test that runs it with the animator duration scale at 0.
- **Phases**: `RecompositionCounter` tests for the chat list, the conversation, scheduled messages, profiles, group
  members, contacts and the voice chat (`ChatListRecompositionTest`, `ChatRecompositionTest`,
  `ScheduledRecompositionTest`, `ProfileRecompositionTest`, `ChatMembersRecompositionTest`, `ContactsRecompositionTest`,
  `GroupCallFrameCostTest`). None covers the navigation bar's tabs (M-008), the record button (M-034) or the passcode
  badge (M-118).
- **Gestures**: `ChatListFolderSwipeTest` and `ChatListFolderSwitchTest` cover the folder swipe and slide, but not a
  second swipe during the first one's settle (M-013) or a second tab tap during a slide (M-014).
- **Transforms and frame cost**: `:app`'s `MediaViewerTransitionTest`, `OverlayNavigatorTest`, `core:ui`'s
  `SharedMediaTest`, and the `*ScrollCostTest`s.

The plan names the test each pull request adds.

## 4. The lint rule's gaps

`PingogramHardcodedToken` (`build-logic/detekt-rules/src/main/kotlin/dev/kyralabs/pingogram/detekt/PingogramHardcodedToken.kt`)
is why the features hold no tween today. What it catches, in feature production sources only
(`FEATURE_PRODUCTION_SOURCE`):

- always: `tween`, `TweenSpec`, `keyframes`, `KeyframesSpec`, `keyframesWithSpline`, `CubicBezierEasing`;
- with number literals among their arguments: `snap`, `SnapSpec`, `spring`, `SpringSpec`;
- the `Spring.*` constants, `durationMillis` and `delayMillis` with literals, and the named easings.

What slips through:

1. **Compose defaults**, which are the real gap, as every "Wrong spec" finding above is one: `AnimatedVisibility`
   without `enter` and `exit`; `AnimatedContent` without `transitionSpec`; `Crossfade` without `animationSpec`;
   `animateContentSize()` and `animateItem()` without specs; `animate*AsState` without `animationSpec`; one-argument
   `animateTo` and `animate`; `fadeIn()`, `fadeOut()`, `slide*`, `expand*`, `shrink*` and `scale*` without a spec;
   `SizeTransform` without its lambda and `togetherWith` without `using`; `sharedBounds` and `sharedElement` without
   `boundsTransform`; `AnimatedPane` without `boundsAnimationSpec`; `animateScrollToPage` without a spec.
2. **`spring()` with no arguments or with named constants** (`spring(dampingRatio = Bouncy)` where `Bouncy` is a
   `const`), and `exponentialDecay` / `splineBasedDecay` with literals.
3. **Loops**: `infiniteRepeatable`, `repeatable`, `rememberInfiniteTransition`. None is in the features today; a new
   one should say how it stops with reduced motion.
4. **The scheme used directly**: `MotionScheme.standard()` or `expressive()` in a feature instead of
   `MaterialTheme.motionScheme`.
5. **Other animation systems**: Coil's `crossfade(...)` (M-025), `ValueAnimator`, `ObjectAnimator`,
   `ViewPropertyAnimator`.
6. **A phase smell**: `val x by animate*AsState(...)`, which reads the value in composition wherever `x` is used
   (M-008, M-034, M-118). A rule cannot prove the phase, but it can ask for the `State` to be kept and read in a
   lambda.
7. **`:app` and `core:ui`** are not checked at all. `core:ui` is where specs are meant to be built, so it would need
   its own narrower rule (literal tweens and durations outside `theme/` and `transition/`); `:app` holds screens and
   could take the features' rule (Q-26).

The plan (`docs/plans/motion-expressive-plan.md`, "The lint rule") proposes a second rule, `PingogramMotionDefault`,
for items 1–6, so that the existing rule's meaning does not change.

## 5. Questions for the owner

Each is a design decision this audit does not make. They were open when the audit was written and are decided now:
the decisions are in §6. "The audit's reading" below is what the audit thought before the check of §6, kept as it
was written.

| # | Question | Where | What the code and the documents say |
|---|---|---|---|
| Q-01 | Should a bar or block that grew in shrink away on `defaultSpatialSpec` or on `fastSpatialSpec`? | M-027, M-041, M-066, M-087, M-126 | 18 call sites use default, 7 use fast. §6 names the fast exit only for the sticker suggestions; §6.1 gives the navigation bar "default in, fast out". 1.1's speed rule would say default for a partial-screen bar. The audit's reading: default, with fast kept where §6 names it |
| Q-02 | Should exits fade on `fastEffectsSpec` everywhere (enter default, exit fast)? | M-005, M-022, M-066 | `Motion.kt`'s shared axis and fade through, and most bars, do so. Five sites fade out on default. The audit's reading: yes, one rule |
| Q-03 | Should a whole screen growing out of a button (New chat, search, avatar → profile) move on `defaultSpatialSpec` or `slowSpatialSpec`? | `core:ui` `transition/SharedMedia.kt:292-300` | §5 says screen transitions are spatial default; 1.1 says full-screen animations are slow. The code follows §5. Not a finding; the document wins unless the owner changes it |
| Q-04 | Should panes move on `slowSpatialSpec` throughout? | M-001, M-011, M-098, M-121 | §5 says a list-detail pane is `slowSpatialSpec`. The Chats and Settings panes' bounds do; their enter and exit, the profile's panes and the third pane's handle use 0.8 / 380. The audit's reading: the document is right, as a pane is the largest thing that moves on a tablet; the code should follow it |
| Q-05 | Is the collapsing header's settle meant to be `slowSpatialSpec`? | M-007 | §5 says so; material3's own scroll behavior settles on its default effects spec. The audit's reading: keep the library's behavior and change the sentence in §5, as a top bar that reads the scheme is the component's own motion |
| Q-06 | Which transition do signing in and signing out use? | M-012 | The code fades through; §5 keeps fade through for the tabs. §5 names shared axis Y for auth's own steps only. The audit's reading: the code is reasonable (the two graphs are unrelated screens, which is what fade through is for) and §5 should say so |
| Q-07 | Should a folder swipe that commits land on `fastSpatialSpec` (as §6 "Swipe actions" says) or on `defaultSpatialSpec` (as a tab tap does)? | M-015 | The same two full-width lists move both ways. Fast overshoots by about 10%. The audit's reading: default for both, as a full-width list is not a small component, and §6's sentence changed to match |
| Q-08 | Should step-backs inside a screen (settings flows, New story, the "not on Telegram" step, a topic in the detail pane, docked search) preview with the back gesture, as auth's steps do? | M-028, M-051, M-078, M-103, M-122 | §5: custom surfaces "implement `PredictiveBackHandler`". Auth seeks its step transition; the others use `BackHandler` |
| Q-09 | Should programmatic scrolls (scroll to top on tab reselect, a chip or pick brought into view) run on the scheme? | M-003, M-021, M-026, M-050 | `animateScrollToItem` takes no spec; the conversation's jump to bottom already uses `animateScrollBy(distance, spec)`. A long list cannot know its distance, so scroll to top would need a jump near the top and then a spring |
| Q-10 | Keep `SwipeToDismissBox`'s own settle for chat rows and sessions, or build the swipe on the scheme? | M-016, M-119 | material3's component does not read the scheme (1.3). Replacing it means owning the gesture |
| Q-11 | Should the emoji panel and a bot keyboard animate in and out when no keyboard is involved, and should back preview there? | M-029 | §6 says switching to and from the keyboard "moves nothing", and is silent on opening from nothing |
| Q-12 | Are reaction bursts and the "reaction added" morph wanted on bubble badges? | M-036 | §4 lists both. `docs/PERFORMANCE.md` §4 caps concurrent animations and guards scroll cost. The audit's reading: the document is right that a reaction should not pop; a scale-in on `fastSpatialSpec` in the badge's draw phase would meet it without a burst |
| Q-13 | Should a new message slide in from the bottom, or fade in place while its neighbours slide? | M-044 | §5 says the former; the code does the latter. The audit's reading: the code is right for a reverse list, where the neighbours' slide already shows the direction and a second moving layer per message costs scroll frames; §5's sentence would change |
| Q-14 | Floating reactions in a voice chat: should the fade be on an effects spring, and should they stand still with power saving? | `feature:calls` `ui/comments/GroupCallCommentsPane.kt:460` | §6 "Voice chat comments" says each "rises … and fades on `slowSpatialSpec`", one spatial spring for both. They are off with reduced motion, not with power saving's "Stop call screen animations" |
| Q-15 | How should video change on screen: may tiles animate their bounds (each is a `TextureView`), or only fade? Which transition between the voice and video layouts of a call? | M-057, M-059 | §6 "Call screen" and "Voice chat screen" describe the layouts, not the change between them |
| Q-16 | Should the message menu wait for the lifted message to return before it is removed, at the cost of a slower close? | M-033 | The KDoc at `MessageActionsMenu.kt:409` states the current choice |
| Q-17 | Should the record pill spring back after cancel or lock, or is the snap meant as a "done" cue? | M-032 | §6 "Composer" does not say |
| Q-18 | Which transition between New story's two steps, and between a profile's shared media tabs? | M-078, M-096 | §6 names none for either. `SharedMediaTabs.kt:84-90` says the tabs switch content "as the folder tabs switch the chat list", which now slides |
| Q-19 | With animations off, auth's predictive back still slides the step with the finger. Keep, or crossfade as folders do? | `feature:auth` `ui/AuthStepTransition.kt:54` | §5: "replace springs with snaps"; a seek follows the finger, not a spring |
| Q-20 | Progress while a step waits for Telegram: a reserved slot above the buttons, or progress inside the button as auth does? | M-090, M-091, M-099, M-106, M-114 | §6 names a `LinearWavyProgressIndicator` above the buttons on many pages, and auth's button progress on its own. Either keeps the button still |
| Q-21 | Rows that depend on a switch: one enter and exit inside `SettingsGroup`, or pages with changing rows moved to lazy lists with `settingsListRow`? | M-111, M-113 | Both exist today |
| Q-22 | A cancelled back gesture in the viewers: a spatial return (now, on fast) or `defaultEffectsSpec` as §5 says of the login flow? | M-082 | §5 names the effects return for the login flow only |
| Q-23 | Story segments and the video seek bar advance every 32 ms by a `delay` loop, about 30 steps a second on a 120 Hz screen. Acceptable? | `feature:media` `story/StoryTimeline.kt:135`, `ui/MediaPlayback.kt:273` | Deliberate, per the comments there; P§4 caps stickers at 30 fps but says nothing of progress |
| Q-24 | Is pinch zoom meant to stop hard at 1× and 5× (no rubber band), and is a swipe up meant to close the stories viewer too? | `feature:media` `ui/zoom/ZoomMath.kt:116`, `story/StoryViewerScreen.kt:137` | §6 "Story viewer" names a swipe down only |
| Q-25 | Should the fast-scroll index's dot slide between letters or jump, and should a session swipe's threshold haptic fire again when `reset()` moves the row back? | `feature:contacts` `ui/FastScrollIndex.kt:139-144`; `feature:settings` `ui/SessionSwipe.kt:74-76` | §13.8 and §13.10 do not say |
| Q-26 | Should the motion lint rules cover `:app` (as a feature) and `core:ui` (literal tweens and durations only)? | §4 | `docs/TESTING.md` says "Test sources and `core`/`app` are not checked" |

## 6. The owner's decisions (2026-11-11)

The owner took this audit's recommendation for each question, after asking for a check against Material 3
Expressive's rules. The check used the two sources that could be read: Material's rule of speed by size (1.1) and
what material3's own components do in the pinned versions (1.1, "What material3's own components use"). It confirmed
22 recommendations and changed four, each marked below. The decisions are normative in `docs/DESIGN_SYSTEM.md` §5.1,
and `docs/plans/motion-expressive-plan.md` §9 names the pull request each lands in.

| # | Decision | Basis |
|---|---|---|
| Q-01 | A bar or block that grew in shrinks away on `defaultSpatialSpec`; fast exits stay only where §6 names them | Part of the screen is the default speed; material3's drawer and wide rail use the default spatial spec |
| Q-02 | Fades come in on `defaultEffectsSpec` and leave on `fastEffectsSpec`, everywhere | One rule; `Motion.kt` already does it |
| Q-03 | **Changed by the check.** A whole screen growing out of a button or a thumbnail moves on `slowSpatialSpec` on its way in and on `defaultSpatialSpec` on its way back. Shared-axis pushes keep `defaultSpatialSpec` | Material's rule: slow for full-screen animations. material3's full-screen search bar expands on the slow spatial spec and collapses on the default one (`rememberSearchBarState`). The first recommendation was to keep default both ways |
| Q-04 | **Changed by the check.** Panes move on `defaultSpatialSpec`, read from the scheme | A pane covers part of the window, and material3-adaptive moves its panes on 0.8 / 380, the default spatial values. The first recommendation was slow, as §5 said |
| Q-05 | The collapsing header keeps material3's own settle | The top bar's snap reads the scheme's default effects spec (`TopAppBarDefaults`) |
| Q-06 | Signing in and out fade through | The two graphs are unrelated screens |
| Q-07 | A committed folder swipe lands on `defaultSpatialSpec`, as a tab tap | A full-width list is not a small component |
| Q-08 | Step-backs inside a screen preview with the back gesture, through one helper | §5 and Android's predictive back guidance |
| Q-09 | Scroll to top on tab reselect runs on `defaultSpatialSpec`; other programmatic scrolls keep Compose's | It is the one users see; `animateScrollToItem` takes no spec |
| Q-10 | Chat rows and sessions keep `SwipeToDismissBox`'s own settle | Material's component, used as it is |
| Q-11 | The emoji panel and a bot keyboard grow in and shrink away on `defaultSpatialSpec` when no keyboard is involved, and back previews it | Part of the screen is the default speed |
| Q-12 | A reaction badge added to a bubble scales in on `fastSpatialSpec` in its draw phase; no burst | A badge is a small component; material3's chips use the fast spatial spec |
| Q-13 | A new message fades in place while its neighbours slide | The neighbours' slide shows the direction; a second moving layer costs scroll frames |
| Q-14 | Floating reactions rise on `slowSpatialSpec` and fade on `slowEffectsSpec`, and stop with power saving's "Stop call screen animations" | "For color or alpha animations use the `effects` equivalent" (`MotionScheme`) |
| Q-15 | Video tiles fade in and out and the voice and video layouts crossfade, on the effects springs; tiles' bounds do not animate | Opacity is an effects value; resizing a video surface every frame is not measured on a device |
| Q-16 | The message menu waits for the lifted message's return only when the message had been moved to fit | Most closes stay as fast as now |
| Q-17 | **Changed by the check.** The record pill springs back on `fastSpatialSpec` | Material's rule: fast for small components such as switches and buttons; material3's toggle buttons, button groups and switch use the fast spatial spec. The first recommendation was default, as swipe to reply |
| Q-18 | New story's two steps move on the shared axis X; the shared media tabs' content crossfades on the effects springs | A forward step is what the shared axis is for; the tabs sit inside the profile's grid |
| Q-19 | With animations off, auth's back gesture previews nothing: the step changes when the gesture commits | §5: "replace springs with snaps" |
| Q-20 | Progress while Telegram answers sits in a reserved slot above the buttons; auth keeps its progress in the button | Nothing moves, and §6 stays as written |
| Q-21 | Rows that depend on a switch grow in and shrink away inside `SettingsGroup` | One place, no page restructured |
| Q-22 | **Changed by the check.** A cancelled back gesture in the viewers returns on `defaultSpatialSpec`, and a new gesture cancels the return | The return is a change of scale, which is a spatial value (1.1). The first recommendation was `defaultEffectsSpec` |
| Q-23 | Story segments and the video seek bar keep their 32 ms steps, to be looked at on a 120 Hz phone before any change | Not measured on a device |
| Q-24 | Pinch zoom keeps its hard stops; the stories viewer closes on a swipe down only | §6 "Story viewer" names a swipe down; today a swipe up closes it too (`ui/DismissState.kt:101-104`) |
| Q-25 | The fast-scroll index's dot jumps between letters; the session swipe's haptic fires only for the user's own crossing | The dot is under the finger; a haptic answers what the user did |
| Q-26 | The motion lint rule covers `:app` as it covers the features; `core:ui` is checked for literal tweens, durations and easings only | `:app` holds screens; `core:ui` is where the helpers build their specs |

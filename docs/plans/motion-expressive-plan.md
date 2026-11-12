# Motion fixes: the plan

Date: 2026-11-11. This plan turns the findings of `docs/audit/motion-expressive-audit.md` (M-001 to M-126) into small
pull requests, grouped by module and by the session that owns the module. Read the audit's §1 first: it has the
motion scheme's real values, the mapping of motion onto its six specs, and the Compose defaults that bypass it.

How to use it:

- Each pull request below names its branch, the findings it closes, the files, the exact change and how to verify it.
- "(Q-nn)" after a finding means the owner decided how it is fixed: the decision is in §9 and in
  `docs/DESIGN_SYSTEM.md` §5.1. A new design question is never decided in code: add it to the audit's §5 and leave
  that item out until the owner has answered.
- A finding is closed when its row's "Expected" holds and the named test passes. Tick it in the table at the end of
  this file in the same pull request.

## 1. Principles

1. **Specs come from `MaterialTheme.motionScheme`, through the `core:ui` helpers** (§3). A feature never builds an
   animation spec and never leaves a spec out: a missing spec is Compose's default, not the theme's (audit 1.3).
2. **Spatial for what moves or changes size or shape, effects for color and opacity.** One element that does both
   takes two springs. An effects value is never driven by a spatial progress.
3. **Speed by size**: fast for small components and press feedback, default for bars, sheets, rows, panes and
   screen pushes, slow for a whole screen growing out of a button or a thumbnail, on its way in
   (`docs/DESIGN_SYSTEM.md` §5.1).
4. **Enter on default effects, leave on fast effects.** What grows in on `defaultSpatialSpec` shrinks away on the
   same, unless §6 names a fast exit (decided: Q-01, Q-02).
5. **Nothing jumps.** A line, a card or a row that comes and goes either animates its size or has its place
   reserved. A control never moves under the finger that just pressed it.
6. **Animated values are read in the layout or draw phase** (`docs/PERFORMANCE.md` §4): `graphicsLayer { }`,
   `Modifier.offset { }`, `drawBehind`, a `ColorProducer`, a `() -> Float`. Never `val x by animate*AsState(...)`
   passed to a composable's parameter.
7. **Gestures hand over cleanly.** A release passes its velocity to the settle. A new touch cancels the settle that is
   running. What a gesture decides (select, answer, dismiss) and its haptic happen at the release, not when a spring
   has ended.
8. **Reduced motion.** Motion on a spec ends at once by itself. Loops, morphs, container transforms and anything
   that follows a finger check `isReducedMotion()` and say what they do instead.
9. **Prefer the component.** Where material3 or `core:ui` already animates something on the scheme, use it instead
   of a copy.

## 2. Rules every pull request follows

- **No visual redesign.** Only specs, phases and missing motion change. Sizes, colors, shapes, texts and layouts at
  rest stay as they are. If a fix needs a design choice the documents do not make, stop and add a question to the
  audit's §5 instead.
- **Small and single-purpose**, one branch per entry below, Conventional Commits, signed off (`git commit -s`).
- **Goldens**: Roborazzi captures screens at rest, so goldens stay untouched. A golden that changes means the layout
  at rest changed: either that is a mistake, or the entry below says the golden is re-recorded on purpose and why.
- **Tests**: each entry names what it adds. Where a phase is fixed, a `RecompositionCounter` test proves the animation
  recomposes nothing per frame. Where motion is added to something that loops, follows a finger or morphs, a test runs
  it with reduced motion. Where scrolling or a transform is touched, the JVM frame cost is compared before and after
  (`docs/PERFORMANCE.md` §9): `./gradlew :feature:<module>:testDebugUnitTest -Ppingogram.scrollCost --tests
  '*ScrollCostTest*own*' --rerun`, one test per run, and `:app`'s `MediaViewerTransitionTest` for the viewer.
- **`./gradlew check` passes**, and the `docs/PERFORMANCE.md` §10 checklist is done.
- **`CHANGELOG.md`** "Unreleased" gets one short user-facing line per pull request that users can see, in the form
  "Smoother …" ("Smoother folder swipes", "The button no longer moves while a code is checked"). Lint, tests and
  refactors without a visible change get none.
- **Docs**: where the owner's answer changes a rule, the pull request that applies it edits `docs/DESIGN_SYSTEM.md`
  in the same change.
- **Pull request body**: the findings closed, before and after in one sentence each, how it was verified, a section
  "Touches other sessions' modules" (or "None"), and the line that `release.yml`'s signing steps are unchanged.
- **Never**: a `@Suppress` of the motion lint rules without a comment that says why; a spec built in a feature; a new
  `tween`.

## 3. Shared helpers to add first

One pull request in `core:ui` and `build-logic`, before any other: **`refactor/motion-helpers`**. It adds the
helpers and their tests and the new lint rule (§4), and changes no call site outside `core:ui`. Every helper takes the
`MotionScheme` or reads it once, as `theme/Motion.kt` does today, so previews and tests can pass their own.

Paths are under `core/ui/src/main/kotlin/dev/kyralabs/pingogram/core/ui/`.

### 3.1 Transitions: `theme/MotionTransitions.kt`

```kotlin
/** A bar, card or row that takes room: grows on the default spatial spring, fades in on the default effects one. */
fun growInVertically(motionScheme: MotionScheme, expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition

/** Its way out: shrinks on the default spatial spring (fast where §6 names it), fades on the fast effects one. */
fun shrinkOutVertically(
    motionScheme: MotionScheme,
    shrinkTowards: Alignment.Vertical = Alignment.Top,
    fast: Boolean = false,
): ExitTransition

fun growInHorizontally(motionScheme: MotionScheme, expandFrom: Alignment.Horizontal = Alignment.Start): EnterTransition
fun shrinkOutHorizontally(motionScheme: MotionScheme, shrinkTowards: Alignment.Horizontal = Alignment.Start): ExitTransition

/** A small element that appears in place (a check, a dot, a badge): scale on fast spatial, fade on fast effects. */
fun popIn(motionScheme: MotionScheme, initialScale: Float = 0f): EnterTransition
fun popOut(motionScheme: MotionScheme, targetScale: Float = 0f): ExitTransition

/** Content that only fades: in on default effects, out on fast effects. */
fun effectsEnter(motionScheme: MotionScheme): EnterTransition
fun effectsExit(motionScheme: MotionScheme): ExitTransition

/**
 * The `transitionSpec` of a state swap in place: [effectsEnter] with [effectsExit], and the size change on the
 * default spatial spring.
 */
fun <S> stateCrossfade(
    motionScheme: MotionScheme,
    clip: Boolean = false,
): AnimatedContentTransitionScope<S>.() -> ContentTransform
```

Used by: every `AnimatedVisibility` of a bar (M-022, M-027, M-041, M-066, M-087, M-126, as Q-01 and Q-02
decide, and new ones: M-056, M-060, M-088, M-094, M-105, M-108, M-110, M-111, M-114); `popIn` / `popOut` for M-116
and M-117; `stateCrossfade` for M-018, M-019, M-048, M-071 and every new crossfade. The 44 existing `AnimatedContent`
calls already write these specs out by hand and are right; they may move to `stateCrossfade` when their file is
touched, not in a sweep.

### 3.2 Composables: `component/MotionContainers.kt`

```kotlin
/** `AnimatedContent` with [stateCrossfade]: one state replaces another in place. */
@Composable
fun <T> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    clip: Boolean = false,
    contentKey: (T) -> Any? = { it },
    label: String = "StateCrossfade",
    content: @Composable AnimatedContentScope.(T) -> Unit,
)

/** `AnimatedVisibility` with [growInVertically] and [shrinkOutVertically]: content that takes room as it comes. */
@Composable
fun GrowingVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    expandFrom: Alignment.Vertical = Alignment.Top,
    fastExit: Boolean = false,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
)

/**
 * A place that is always laid out, so nothing around it moves: [content] fades in on the default effects spring and
 * out on the fast one (alpha read in the draw phase), and is out of the semantics tree while hidden.
 */
@Composable
fun ReservedSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

Used by: `StateCrossfade` for M-006, M-035, M-037, M-042, M-046, M-059, M-083, M-084, M-100, M-109, M-112, M-115,
M-124, M-125; `GrowingVisibility` for the list under 3.1; `ReservedSlot` for the progress lines (M-090, M-091, M-099,
M-106), as Q-20 decides.

### 3.3 Lazy items: `component/ItemMotion.kt`

```kotlin
/** The three specs of a lazy item: fade in on default effects, placement on default spatial, fade out on fast effects. */
@Immutable
class ItemMotion internal constructor(
    val fadeIn: FiniteAnimationSpec<Float>,
    val placement: FiniteAnimationSpec<IntOffset>,
    val fadeOut: FiniteAnimationSpec<Float>,
)

/** Read once per list, outside its items. */
@Composable
fun rememberItemMotion(): ItemMotion

fun Modifier.animateItem(scope: LazyItemScope, motion: ItemMotion, placement: Boolean = true): Modifier
fun Modifier.animateItem(scope: LazyGridItemScope, motion: ItemMotion, placement: Boolean = true): Modifier
```

These are the specs `feature:settings`' `settingsListRow` and `feature:contacts`' `Modifier.row` already pass. Used
by: M-023, M-045, M-049, M-063, M-064, M-069, M-070, M-085, M-089, M-092, M-093, M-095, M-097, M-101, M-113.

### 3.4 Gestures: `gesture/SettlingOffset.kt`

```kotlin
/**
 * An offset a finger drags and a spring settles. [value] is snapshot state, read in the layout or draw phase.
 * A drag cancels a settle that is running; a settle starts at the release velocity.
 */
@Stable
class SettlingOffset(initial: Float = 0f) {
    val value: Float
    val isSettling: Boolean

    /** Moves by [delta], within [range] if given. Cancels a running settle. */
    fun dragBy(delta: Float, range: ClosedFloatingPointRange<Float>? = null)

    /** Sets [value] at once. Cancels a running settle. */
    fun snapTo(value: Float)

    /**
     * Springs to [target] on [spec] from [velocity] in [scope]. [onSettled] runs only if nothing cancelled it.
     * With reduced motion the value is there in the next frame.
     */
    fun settle(
        scope: CoroutineScope,
        target: Float,
        spec: AnimationSpec<Float>,
        velocity: Float = 0f,
        onSettled: () -> Unit = {},
    ): Job
}
```

It is `feature:media`'s `DismissState` settle (`ui/DismissState.kt:104-112`), which already does this right, made
shareable. Used by: M-001, M-002, M-030, M-032, M-039, M-052, M-062, M-079.

### 3.5 Phase helpers: `component/Reveal.kt` and `component/AnimatedColor.kt`

```kotlin
/** Shows [fraction] of the content's width (height), clipped: read in the layout phase. Moved from `:app`. */
fun Modifier.revealHorizontally(fraction: () -> Float): Modifier
fun Modifier.revealVertically(fraction: () -> Float): Modifier

/** A color that animates without recomposing its reader: pass it to `Text(color = …)` or `Icon(painter, tint = …)`. */
@Composable
fun animateColorProducer(
    targetValue: Color,
    animationSpec: AnimationSpec<Color> = MaterialTheme.motionScheme.defaultEffectsSpec(),
): ColorProducer
```

material3 1.5.0-beta02 has `Text(text, color: ColorProducer, …)` and `Icon(painter, tint: ColorProducer?, …)`
(checked in the AAR). Used by: `revealHorizontally` for M-009 and M-031; `animateColorProducer` for M-008, M-034,
M-118.

### 3.6 Helpers the decisions add

Not in the first pull request. Each is added by the pull request that needs it (the questions are decided, §9):

- **Q-09, scrolls on the scheme**: `suspend fun LazyListState.animateScrollToTop(spec: AnimationSpec<Float>)` in
  `component/TabScroll.kt`: jumps to within a screen of the top when further away, then `animateScrollBy` on the spec.
- **Q-08, predictive step-backs**: `transition/SeekableSteps.kt`, `feature:auth`'s `AuthStepTransition` made generic
  (`rememberSeekableSteps(current, backTo, onBack)`), with M-123's fix in it from the start.

### 3.7 The helpers' tests

In `core/ui/src/test/kotlin/dev/kyralabs/pingogram/core/ui/`:

- `theme/MotionTransitionsTest.kt`: each transition runs to its end; with the animator duration scale at 0 it ends in
  one frame; `shrinkOutVertically(fast = true)` ends before the default one (frame counts on the test clock).
- `component/MotionContainersTest.kt`: `ReservedSlot` keeps its height as `visible` flips, and its content is not in
  the semantics tree while hidden; `GrowingVisibility` changes its parent's height over several frames, not one;
  `StateCrossfade` never shows neither state.
- `component/MotionContainersRecompositionTest.kt`: `ReservedSlot`'s fade and `animateColorProducer` recompose
  nothing per frame (`RecompositionCounter`).
- `gesture/SettlingOffsetTest.kt`: `dragBy` during a settle cancels it and `onSettled` does not run; a settle starts
  at the velocity given (the first frame moves in its direction even when the target is behind); with reduced motion
  it is at its target after one frame.
- `component/ItemMotionTest.kt`: a removed row's neighbours move over several frames in a `LazyColumn` and a
  `LazyVerticalGrid`.

## 4. The lint rule

A second detekt rule beside `PingogramHardcodedToken`, so the existing rule's meaning and tests do not change:
**`PingogramMotionDefault`**, in `build-logic/detekt-rules/src/main/kotlin/dev/kyralabs/pingogram/detekt/`, with
`PingogramMotionDefaultTest.kt`, registered in the rule set provider and `config/detekt/detekt.yml`. It matches by
name and arguments, as the existing rule does, on feature production sources and `:app` (Q-26). `core:ui` gets one
check only, literal tweens, durations and easings, as it is where the helpers build their specs. Its checks are
listed in the configuration, so they can be turned on in two steps.

Turned on in `refactor/motion-helpers`, with the scope above from the start (Q-26; nothing in the features or `:app`
breaks them today, counted on 2026-11-11; `:app`'s splash exit keeps its scheme with a commented suppression):

| Check | Reports |
|---|---|
| `visibility` | `AnimatedVisibility(...)` without both `enter` and `exit` |
| `content` | `AnimatedContent(...)` without `transitionSpec` |
| `crossfade` | `Crossfade(...)` without `animationSpec` |
| `contentSize` | `animateContentSize()` without `animationSpec` |
| `asState` | `animate*AsState(...)` without an animation spec argument |
| `transitions` | `fadeIn`, `fadeOut`, `scaleIn`, `scaleOut`, `slideIn*`, `slideOut*`, `expand*`, `shrink*` called without a spec |
| `springDefault` | `spring()` and `SpringSpec()` without arguments or with only named constants; `exponentialDecay`, `splineBasedDecay` |
| `loops` | `infiniteRepeatable`, `repeatable`, `rememberInfiniteTransition`: a loop needs a `@Suppress` with a comment that says how it stops with reduced motion |
| `schemeDirect` | `MotionScheme.standard()` / `MotionScheme.expressive()` outside the theme |
| `otherAnimators` | `ValueAnimator`, `ObjectAnimator`, `ViewPropertyAnimator` |

Turned on by the last pull request of this plan (`chore/motion-lint-strict`, §5.1), once the findings they would
report are fixed:

| Check | Reports | Findings it would report today |
|---|---|---|
| `items` | `animateItem()` without its three specs: use `core:ui`'s `animateItem(scope, motion)` | M-049, M-070, M-089, M-097 (14 calls) |
| `sizeTransform` | `SizeTransform(...)` without its spec lambda | M-071 |
| `pagerScroll` | `animateScrollToPage(page)` without `animationSpec` | M-075 |
| `panes` | `AnimatedPane(...)` without `boundsAnimationSpec` | M-098 |
| `coil` | Coil's `crossfade(...)` | M-025 |
| `compositionRead` | a `by animate*AsState` delegate whose value is used outside a lambda in the same function | M-034, M-118 (and M-008 in `:app`) |

What the rule cannot do, by name alone: tell `Animatable.animateTo(target)` from `SeekableTransitionState.animateTo`
or a pane scaffold's, or see a missing animation. Those stay with review and the checklist in §8.

The rule's tests follow `PingogramHardcodedTokenTest`: for each check, one snippet that is reported and one that is
not, and one snippet in a test source set and one in `core` that are not checked.

## 5. The pull requests

Order: `refactor/motion-helpers` first. Then every pull request marked **High**, in any order across sessions. Then
the Medium ones, then the Low ones. `chore/motion-lint-strict` last. Within a session, pull requests that touch the
same file go one after the other.

Sessions, as the owner set them: **A** `feature:chats` and `feature:chat`; **B** `feature:calls` and
`feature:media`; **C** `feature:profile`, `feature:contacts`, `feature:settings` and `feature:auth`; **Shared**
`:app` and `core:ui`, with the helpers. `feature:scanner` and `feature:feedback` were not named: this plan puts them
with session B, which has kept them before. Owner decision to confirm (2026-11-11).

Paths below are relative to the module's `src/main/kotlin/dev/kyralabs/pingogram/...` root, as in the audit's tables.

### 5.1 Shared: `core:ui` and `:app`

**S-1 `refactor/motion-helpers`** (first; no severity of its own)
- Adds: §3.1–3.5 and §3.7, and the rule of §4 with its first ten checks on, on the features and `:app`, and the
  literal-spec check (`literals`) on `:app` and `core:ui` (Q-26, owner 2026-11-11).
- Moves `revealHorizontally` from `:app`'s `ui/PingogramNavigationBar.kt:335` to `core:ui`.
- Verify: the new tests; `./gradlew check` (the rule's first checks report nothing today). No golden changes. No
  CHANGELOG line.

**S-2 `fix/motion-reorder-drop`** (High) — M-002, and with it M-038 and M-104
- Files: `core:ui` `component/ListReorderState.kt`; no change needed in `feature:chat`
  `ui/topics/ForumTopicsScreen.kt:200-202`, `feature:settings` `ui/folders/ChatFoldersList.kt:146` or
  `ui/stickers/StickersList.kt:233` beyond reading the offset as they do.
- Change: `end()` no longer zeroes the offset. It reports the drop (`onDrop(order)`) at once, keeps the row marked as
  settling, and settles `offset` to 0 on `defaultSpatialSpec` from the drag's velocity with `SettlingOffset`; the row
  keeps its `zIndex` and its placement animation stays off until the settle ends. A new drag during the settle takes
  over.
- Verify: a new `ListReorderStateTest` in `core:ui` (after `end()`, the offset reaches 0 over several frames; with
  reduced motion in one; a drag during the settle cancels it). Sessions A and C check their lists by hand and add
  nothing. "Touches other sessions' modules": none in code; it changes how their three lists drop.
- CHANGELOG: "Smoother drops when reordering folders, sticker sets and pinned topics".

**S-3 `fix/motion-pane-handle`** (Medium) — M-001
- File: `core:ui` `component/ExtraPaneWidth.kt:118-156`.
- Change: the width's settle goes through `SettlingOffset` (the drag cancels it, the release passes its velocity).
  The spec is `defaultSpatialSpec`, read from the scheme (Q-04).
- Verify: a test that a drag during the settle leaves the width where the finger put it.

**S-4 `fix/motion-app-shell`** (Medium) — M-008, M-009, M-010
- Files: `:app` `ui/PingogramNavigationBar.kt:240-305`, `navigation/PingogramNavHost.kt:125-131`.
- Change: M-008: `animateColorProducer` for the tab's content color, passed to `Icon(painter, tint = …)` and
  `Text(color = …)`. M-009: the selected tab's extra padding becomes a spacer revealed with `revealHorizontally` on
  the `fastSpatialSpec` progress the icon already uses, so the width changes in the layout phase. M-010:
  `predictivePopExitTransition` returns `predictiveBackPopExit(motionScheme)` for full-screen destinations too.
- Verify: `PingogramNavigationBarTest` gains a `RecompositionCounter` case (a tab change recomposes each tab once, not
  per frame); `PingogramAppTest` or a new case seeks a predictive back from the QR scanner and checks the screen is
  still visible at 30% progress; the app-level goldens with the bar are unchanged.
- CHANGELOG: "Smoother tab changes in the navigation bar", "The back gesture previews full-screen pages as it does
  others".

**S-5 `refactor/motion-core-ui`** (Low) — M-004, M-006, M-005 (Q-02), M-003 (Q-09), and the container transform's
speed (Q-03)
- Files: `core:ui` `component/AvatarSelection.kt`, `component/PhotoEditor.kt`, `navigation/OverlayNavigator.kt`,
  `component/TabReselection.kt`, `transition/SharedMedia.kt:292-299`.
- Change: M-004: a second `Animatable` on `defaultEffectsSpec` for the three alphas; the spatial one keeps the morph
  and the check's size. M-006: `StateCrossfade` between the ring and the indicator. M-005: the overlay's alpha leaves
  on `fastEffectsSpec`. M-003: `animateScrollToTop`. Q-03: the container transform's bounds and its
  corners' progress run on `slowSpatialSpec` while the container grows into the whole screen and on `defaultSpatialSpec` while it shrinks back
  (the `BoundsTransform` picks by which of the two rectangles is the larger), as material3's full-screen search bar
  expands and collapses.
- Verify: `MediaViewerTransitionTest` and `OverlayNavigatorTest` unchanged for M-005, with the JVM frame times
  compared; `ChatListRecompositionTest` (selecting recomposes nothing per frame) for M-004. Q-03: `OpenMediaBenchmark`
  and `tools/perfetto/media-viewer.sh` on the owner's phone before and after; if the media viewer's frames get worse,
  its transform keeps `defaultSpatialSpec` and only New chat, search and avatar → profile change.

**S-6 `refactor/motion-panes`** (Low) — M-011, M-098, M-121 (Q-04)
- Files: `:app` `ui/ChatsListDetail.kt:176`; `feature:profile` `ui/ProfilePanes.kt:117-120`; `feature:settings`
  `navigation/SettingsListDetail.kt:131-142`; a shared `paneMotion(motionScheme)` in `core:ui`
  `component/ListDetailPanes.kt`.
- Change: one place builds the panes' `boundsAnimationSpec` and their enter and exit from the scheme's
  `defaultSpatialSpec` (Q-04), so the bounds at `ChatsListDetail.kt:176` and `SettingsListDetail.kt:125` leave
  `slowSpatialSpec`; the three scaffolds use it.
- "Touches other sessions' modules": `feature:profile` and `feature:settings` (session C), one line each.

**S-7 `docs/motion-design-system`** (no code) — M-007, M-012
- The decisions are in `docs/DESIGN_SYSTEM.md` §5.1 since 2026-11-11, which closes M-007 (Q-05) and M-012 (Q-06).
  This pull request edits the rows of §6 and §13 that still say otherwise, once the code has caught up, and appends
  §8's checklist to §12.

**S-8 `chore/motion-lint-strict`** (last)
- Turns on the six remaining checks of §4 and updates `docs/TESTING.md` "Static analysis". `:app` and the
  literal-spec check for `core:ui` are in the rule's scope from S-1 on (Q-26).
- Verify: `./gradlew check` on `main` after every other pull request has merged.

### 5.2 Session A: `feature:chats` and `feature:chat`

**A-1 `fix/motion-folder-swipe`** (High) — M-013, M-014, M-020, M-015 (Q-07: `defaultSpatialSpec`)
- Files: `feature:chats` `ui/FolderSwipe.kt:96-155`, `ui/FolderSlide.kt:112-132`, `:255-275`.
- Change: M-013: on a release that commits, set `swiped = true` and call `onSelect` at once, then run the slide; the
  slide no longer gates the selection, so a drag that catches it keeps the new folder. M-014: `bringIn`'s tapped
  branch starts from `position + toward * width` and keeps the offset's velocity. M-020: the `TextHandleMove` haptic
  fires when the commit decision changes (position or projected landing), in both directions. M-015: both paths use
  the spec the owner picks.
- Verify: `ChatListFolderSwipeTest`: two swipes 100 ms apart select two folders; a swipe caught mid-settle shows no
  blank companion. `ChatListFolderSwitchTest`: a second tab tap mid-slide moves the list's position by less than a
  frame's travel between two frames. `ChatListRecompositionTest` (a swipe recomposes no row) and
  `ChatListScrollCostTest` before and after.
- CHANGELOG: "Smoother folder swipes, also when swiping twice quickly".

**A-2 `fix/motion-composer-panel`** (High) — M-029, with its back handling (Q-11)
- Files: `feature:chat` `ui/composer/ComposerPanelState.kt:214-245`, `:300-312`.
- Change: when the panel opens or closes with no keyboard coming or going, the area's height animates on
  `defaultSpatialSpec`, read in the layout phase as the keyboard's inset is today; keyboard ↔ panel switches stay as
  they are (they move nothing). `PredictiveBackHandler` shrinks the panel with the gesture (Q-11).
- Verify: a new `ComposerPanelMotionTest`: opening from nothing changes the composer's position over several frames;
  keyboard → panel changes it by nothing; reduced motion in one frame. `ChatRecompositionTest` (no bubble recomposes
  while it opens). `ChatScrollCostTest` unchanged.
- CHANGELOG: "The emoji panel opens smoothly instead of jumping".

**A-3 `fix/motion-chat-selection`** (High) — M-031, M-037
- Files: `feature:chat` `ui/MessageBubble.kt:180-195`, `ui/ChatTopBar.kt:96`.
- Change: M-031: the selection check is composed only while selection mode is on or its reveal is still
  running, and its width is revealed with `revealHorizontally` on one `fastSpatialSpec` progress shared
  by the list (a `() -> Float` handed down, one animation for all rows, not one per row), with an effects fade.
  M-037: `StateCrossfade` between the two top bars.
- Verify: `ChatRecompositionTest`: entering selection mode recomposes each visible row at most once and none per
  frame; `ChatScrollCostTest` with and without the change (rows not in selection mode must cost the same).
- CHANGELOG: "Smoother start and end of message selection".

**A-4 `fix/motion-chat-gestures`** (High) — M-030, M-039, M-040, M-047, M-032 (Q-17: the pill springs back on
`fastSpatialSpec`)
- Files: `feature:chat` `ui/NextChannelPullState.kt:86-126`, `:225-235`, `ui/SwipeToReply.kt:120-145`,
  `ui/ReactionToggleState.kt:55-65`, `ui/voice/RecordButton.kt:455-470`, `ui/voice/RecordingBar.kt:125-135`.
- Change: M-030: the pull is a `SettlingOffset`; `onPreFling` starts the settle with the fling's velocity in the
  state's scope and returns, a new pull cancels it, and the clamp applies to settled values too. M-039: the release
  velocity goes into the settle. M-040: the crossfade continues from its current fraction. M-047: a haptic when the
  pull disarms, and the two labels crossfade. M-032: the pill's offset settles on `defaultSpatialSpec` if the owner
  wants it to.
- Verify: `NextChannelPullStateTest` (a pull during the settle wins); `SwipeToReply` test for the velocity.
- CHANGELOG: "Pulling to the next channel responds at once after a release".

**A-5 `refactor/motion-chat-composer`** (Medium) — M-034, M-035, M-042, M-046, M-048, M-041 (Q-01)
- Files: `feature:chat` `ui/voice/RecordButton.kt:185-202`, `ui/composer/Composer.kt:255-265`, `:320-326`,
  `ui/ChatTitle.kt:165-172`, `ui/panel/EmojiPanel.kt:110-118`, `ui/voice/RecordingBar.kt:84`,
  `ui/bot/CommandSuggestions.kt:84`, `ui/inline/InlineResultsArea.kt:114`.
- Change: M-034: the button's container is drawn in `drawBehind` from a `ColorProducer` and its icon tinted with one,
  the `IconButton` keeping fixed transparent colors, so the fade recomposes nothing. M-035, M-042, M-046:
  `StateCrossfade`. M-048: `stateCrossfade`'s `SizeTransform`. M-041: `shrinkOutVertically(fast = …)` as decided.
- Verify: a `RecompositionCounter` case for the record button (starting a recording recomposes it once);
  `ChatRecompositionTest`.
- CHANGELOG: "Smoother switch between typing and recording".

**A-6 `refactor/motion-message-menu`** (Medium) — M-043, M-033 (Q-16: the menu waits for the
message's return only when the message had been moved to fit)
- Files: `feature:chat` `ui/JumpToBottomButton.kt:55-65`, `:215-225`, `ui/MessageActionsMenu.kt:405-440`.
- Change: M-043: `Modifier.animateFloatingActionButton(visible, alignment, …)` in place of the hand-built
  `AnimatedVisibility`. M-033: as the owner decides.
- Verify: the jump button's tests; goldens unchanged.

**A-7 `refactor/motion-chat-lists`** (Low) — M-045, M-049; M-050 stays as it is (Q-09)
- Files: `feature:chat` `ui/topics/ForumTopicsScreen.kt:200`, and the seven lists of M-045.
- Change: `rememberItemMotion()` once per list and `Modifier.animateItem(this, motion)` on each keyed item.
- Verify: `ChatScrollCostTest` unchanged (these are sheets, not the conversation); the sheets' tests.

**A-8 `refactor/motion-chat-list`** (Low) — M-017, M-018, M-019, M-023, M-024, M-025, M-022 (Q-02), M-027
(Q-01); M-016 (Q-10), M-021 and M-026 (Q-09) stay as they are; M-028 and M-051 go to C-9 (Q-08)
- Files: `feature:chats` `ui/ChatRow.kt:180-188`, `ui/ChatListHeaderStatus.kt:42`, `ui/ChatListTopBar.kt:254`,
  `ui/SearchPeople.kt:54`, `:121`, `ui/SearchScreen.kt:281`, `:345-350`, `ui/create/ChatPhotoButton.kt:143`,
  `ui/create/MemberPickerScreen.kt:228-235`.
- Change: M-017: the haptic moves to the release that commits. M-018, M-019: `stateCrossfade`. M-023:
  `animateItem(this, motion)`. M-024: the indicator leaves through `effectsExit`. M-025: the photo fades as
  `core:ui`'s `Avatar` does, without Coil's crossfade.
- Verify: `ChatListRecompositionTest`, `ChatListScrollCostTest` before and after (the row's swipe is touched),
  `SearchScreenTest`.

**A-9 `refactor/motion-reaction-badge`** (Medium) — M-036 (Q-12); M-044 is closed by Q-13 (the code stays, and
`docs/DESIGN_SYSTEM.md` §5 now says what it does)
- File: `feature:chat` `ui/BubbleParts.kt:163-171`.
- Change: a badge added to a bubble scales in on `fastSpatialSpec`, read in the badge's draw phase. No burst; a
  removed badge leaves as it does today.
- Verify: `ChatRecompositionTest` (a new reaction recomposes its bubble's badges only) and `ChatScrollCostTest` before
  and after.

### 5.3 Session B: `feature:calls` and `feature:media` (and `feature:scanner`, `feature:feedback`)

**B-1 `fix/motion-answer-slider`** (High) — M-052, M-054, M-062
- File: `feature:calls` `ui/CallScreen.kt:935-1085`.
- Change: `SliderState`'s offset becomes a `SettlingOffset`. `onDragStopped` passes its velocity; a fling whose
  projected landing passes 70% commits. On a commit, `onAnswer` / `onDecline` and the haptic run at the release, and
  the handle finishes on `fastSpatialSpec` by itself. A new drag cancels a settle or a nudge. `nudge()` is one
  cancellable job.
- Verify: a new `AnswerSliderTest`: a drag during the spring-back keeps the handle under the finger; a flick answers;
  `onAnswer` is called in the frame of the release; with touch exploration the buttons still show.
- CHANGELOG: "The answer slider responds to quick slides and flicks".

**B-2 `fix/motion-call-reactions`** (High) — M-053, with Q-14: the rise on `slowSpatialSpec`, the fade on
`slowEffectsSpec`, and none while power saving's "Stop call screen animations" is in effect
- File: `feature:calls` `ui/comments/GroupCallCommentsPane.kt:440-470`.
- Change: `key(reaction.id) { FloatingEmoji(...) }`, and a reaction that has finished is not composed again.
- Verify: `GroupCallFrameCostTest` gains a case: the seventh reaction starts one animation, not six; the frame cost
  with comments shown is compared before and after.
- CHANGELOG: "Reactions in voice chats no longer replay".

**B-3 `fix/motion-voice-chat-layout`** (High) — M-055, M-056, M-058, M-065, M-071
- Files: `feature:calls` `ui/GroupCallScreen.kt:220-280`, `:360-370`, `:675-690`, `ui/GroupCallVideoGrid.kt:140-150`.
- Change: M-055: the two bodies sit in a `StateCrossfade` whose size change is on `slowSpatialSpec` (the one place a
  slow spec is passed, as §5 maps large layout changes). M-056: `GrowingVisibility` around the self-view. M-058: the
  outline is drawn in `drawBehind` at the avatar rings' animated alpha, still with `LocalStillCallAnimations`.
  M-065: the compact row uses the docked panel's crossfade. M-071: `stateCrossfade`.
- Verify: `GroupCallFrameCostTest`: toggling comments recomposes the pane once and the frame cost per frame is
  compared; its existing checks (a comment recomposes the pane alone) still pass.
- CHANGELOG: "Smoother voice chat screen when comments or the camera turn on".

**B-4 `fix/motion-viewer-swipe-up`** (High) — M-072
- Files: `feature:media` `ui/MediaViewerUiState.kt:369-374`, `ui/MediaViewerScreen.kt:146-147`.
- Change: `playsVideo` is true only once the video can be handed over (the player has started it), so a swipe up on a video that cannot be handed over closes the viewer as a swipe down does; or, if
  the view model still cannot hand over, it sends an effect that springs the content back. Confirm the bug on a
  device first.
- Verify: a `MediaViewerViewModelTest` case (swipe up before the video starts closes the viewer) and a screen test
  that the content is not left displaced.
- CHANGELOG: "Swiping up on a video that has not started no longer gets stuck".

**B-5 `fix/motion-story-picker`** (High) — M-079, M-084, M-085, M-089 (its `PickStrip` part)
- Files: `feature:media` `storypost/PickStrip.kt:85-145`, `storypost/NewStoryScreen.kt:168`.
- Change: M-079: the dragged tile's offset is a `SettlingOffset` that settles to 0 on `defaultSpatialSpec` after the
  drop, before placement animation takes the tile back. M-085, M-089: keys on the preparing tiles and
  `animateItem(this, motion)` on every tile. M-084: `StateCrossfade` on the shown item.
- Verify: a `PickStripTest` case: after a drop the tile's offset reaches 0 over several frames.
- CHANGELOG: "Smoother reordering of a story's photos".

**B-6 `refactor/motion-call-screen`** (Medium) — M-060, M-067, M-068, M-059 (Q-15: the voice and video
layouts crossfade on the effects springs), M-066 (Q-01, Q-02)
- Files: `feature:calls` `ui/CallScreen.kt:160-170`, `:218-226`, `:650-655`, `ui/CallControls.kt:59`,
  `ui/ScreenSharing.kt:43`, `ui/CallRatingSheet.kt:182`, `history/RecentCallsScreen.kt:295`.
- Change: M-060: `GrowingVisibility`. M-067: `shapes = IconButtonDefaults.shapes()` / `ButtonDefaults.shapes()`.
  M-068: `ToggleOn` / `ToggleOff`. M-059 and M-066 as decided.
- Verify: `CallScreen` tests; the call goldens unchanged.

**B-7 `refactor/motion-call-lists`** (Medium) — M-061, M-063, M-064, M-069, M-070, M-057 (Q-15: tiles fade in
and out on the effects springs; their bounds do not animate)
- Files: `feature:calls` `ui/invite/InvitePeopleSheet.kt:245-265`, `history/RecentCallsScreen.kt:241-255`,
  `history/StartCallSheet.kt:220`, `ui/GroupCallScreen.kt:541-545`, `ui/GroupCallTiles.kt:79-89`,
  `ui/comments/GroupCallCommentsPane.kt:247`.
- Change: `animateItem(this, motion)` on every keyed item, headers and footers included. M-061: the row takes the
  list item's selected shapes and colors as `history/RecentCallRow.kt` does.
- Verify: `GroupCallFrameCostTest` before and after (the comments' rows are touched).

**B-8 `refactor/motion-media-viewer`** (Medium) — M-073, M-074, M-075, M-081, M-083, M-082 (Q-22:
`defaultSpatialSpec`, cancelled by a new gesture)
- Files: `feature:media` `ui/MediaViewerScreen.kt:282-300`, `:350-370`, `ui/MediaViewerKeys.kt:129`,
  `ui/zoom/ZoomState.kt:100-140`, `ui/MediaChrome.kt:190-205`, `ui/DismissState.kt:180-200`.
- Change: M-073: a `MutableTransitionState(false)` whose target is true once the way in has settled, so the bar fades
  in. M-074: the pan tracks velocity and decays on release within the photo's bounds
  (`splineBasedDecay` from the density, the one decay the lint rule then allows by a commented suppress). M-075:
  `animateScrollToPage(target, animationSpec = defaultSpatialSpec())`. M-081: the zoom fraction is an `Animatable`
  retargeted, not restarted. M-083: `StateCrossfade`.
- Verify: `:app`'s `MediaViewerTransitionTest` with the JVM frame times before and after (the bottom bar's first frame
  moved in M2 and must not come back into the transform's frames); `MediaViewerNavigationTest`.
- CHANGELOG: "Smoother media viewer: the bar fades in, zoomed photos glide, page taps match swipes".

**B-9 `refactor/motion-stories`** (Medium) — M-076, M-077, M-080, M-086, M-088, M-089 (its album part), M-078 (Q-18:
shared axis X between the two steps; its back preview comes with C-9), M-087 (Q-01), and Q-24: the stories viewer
closes on a swipe down only (`ui/DismissState.kt:101-104` takes the direction; an upward drag springs back)
- Files: `feature:media` `story/StoryViewerScreen.kt:75-140`, `:180-190`, `storypost/StoryPeopleSheet.kt:245-265`,
  `storypost/StoryComposeParts.kt:141-150`, `storyalbum/StoryAlbumsSection.kt:49`,
  `storyalbum/StoryAlbumScreen.kt:230`, `story/StoryBottomBar.kt:129`, `storypost/NewStoryRoute.kt:50-63`.
- Change: M-076: the page being left keeps its content until the pager settles. M-077: the stories viewer keeps the
  draw-phase scale through its fade (it does not call `leaveAtBackScale()`). M-080: as M-061. M-086: `shapes =`.
  M-088: `GrowingVisibility`. M-089: `animateItem(this, motion)`.
- Verify: the stories viewer's tests; confirm M-077 on a device (verified 2026-11-12, Galaxy S24 Ultra).

**B-10 `refactor/motion-scanner-feedback`** (Medium) — M-125, M-126 (Q-01)
- Files: `feature:scanner` `QrScannerScreen.kt:170-240`; `feature:feedback` `FeedbackScreen.kt:270-278`.
- Change: `StateCrossfade` between the rationale card and the viewfinder's chrome (the camera preview itself stays
  composed, it is not crossfaded), and the torch button through `effectsEnter` / `effectsExit`.

### 5.4 Session C: `feature:profile`, `feature:contacts`, `feature:settings`, `feature:auth`

**C-1 `fix/motion-settings-progress`** (High) — M-106, M-114, M-120
- Files: `feature:settings` `ui/account/FlowStepState.kt:50-66`, and the nine other files of M-106;
  `ui/twostep/TwoStepVerificationFields.kt:158`.
- Change: one shared `StepProgress(visible)` in `ui/account/FlowStepState.kt`, used by all ten: the progress line in
  a `ReservedSlot` (Q-20). The flood-wait note uses `GrowingVisibility`. M-120: `core:ui`'s `CountdownIndicator`.
- Verify: a new `StepActionsTest`: the primary button's position is the same before, during and after `isWorking`.
  Goldens of working states are re-recorded on purpose if the reserved slot adds height at rest; say so in the pull
  request.
- CHANGELOG: "Buttons no longer move while Telegram checks a code or password".

**C-2 `fix/motion-settings-home`** (High) — M-105, M-107
- Files: `feature:settings` `ui/DataStorageScreen.kt:146-156`, `ui/SettingsScreen.kt:125-155`.
- Change: M-105: `GrowingVisibility` around the storage card. M-107: `StateCrossfade` between the header and the
  search bar and between home and results, the height on `defaultSpatialSpec`.
- Verify: a test that the first group's position changes over several frames, not one, when the card arrives.
- CHANGELOG: "Smoother Data and storage and settings search".

**C-3 `fix/motion-profile-progress`** (High) — M-090, M-091
- Files: `feature:profile` `chatinfo/settings/ChatSettingsScreen.kt:114`, `chatinfo/ChatPermissionsScreen.kt:92`,
  `chatinfo/actions/RecentActionsScreen.kt:151`, `chatinfo/MemberRightsScreen.kt:166`,
  `chatinfo/EditInviteLinkScreen.kt:191`.
- Change: the saving line sits in a `ReservedSlot` (top of the page) or overlays the top bar's bottom edge; nothing
  below it moves.
- Verify: `ProfileRecompositionTest`-style case: toggling a switch moves no group.
- CHANGELOG: "Group settings no longer jump while a change is saved".

**C-4 `fix/motion-contacts-form`** (High) — M-099, M-100, M-101, M-102; M-103 goes to C-9 (Q-08)
- Files: `feature:contacts` `ui/NewContactScreen.kt:190-200`, `:350-360`, `ui/ContactsScreen.kt:315`,
  `ui/ContactFormParts.kt:209`.
- Change: as C-1 for the progress line and flood wait; `StateCrossfade` for the field; `animateItem` on the sticky
  headers (`ContactsRecompositionTest` must still pass); `CountdownIndicator`.
- CHANGELOG: "The Add contact button no longer moves while it works".

**C-5 `refactor/motion-settings-rows`** (Medium) — M-108, M-109, M-110, M-112, M-111 and M-113 (Q-21: one
grow-in and shrink-out inside `SettingsGroup`)
- Files: `feature:settings` `ui/NotificationSettingsScreen.kt:125-175`, `ui/network/NetworkUsageScreen.kt:119`,
  `ui/PrivacyDataCards.kt:59`, `ui/network/EditProxyScreen.kt:212`, `ui/SettingsGroups.kt:87-95` and the pages of
  M-113.
- Change: `GrowingVisibility` for the banner and the card; `StateCrossfade` for the segment's group and the proxy
  fields; M-111 and M-113 as the owner decides (inside `SettingsGroup`, or lazy lists with `settingsListRow`).
- Verify: the pages' tests; goldens unchanged.
- CHANGELOG: "Smoother settings pages when rows appear or go".

**C-6 `refactor/motion-passcode`** (Medium) — M-115, M-116, M-117, M-118, M-119 (the reveal's icon
pops at the threshold on `fastSpatialSpec`; the row's settle stays the component's, Q-10), and Q-25: the session
swipe's threshold haptic fires only when the user's own drag crosses it (`ui/SessionSwipe.kt:71-78`)
- Files: `feature:settings` `ui/passcode/PasscodeLockScreen.kt:101`, `ui/lockedchats/LockedChatsScreen.kt:115`,
  `ui/passcode/PasscodeEntryParts.kt:85-110`, `:185-200`, the chips of M-116.
- Change: `StateCrossfade` from the entry to the page; `popIn` / `popOut` for dots and checks; the badge's icon
  crossfades in the draw phase as `feature:auth`'s `AuthHeadlineBadge` does, with `animateColorProducer`.
- Verify: a `RecompositionCounter` case for the badge; the lock screen's goldens unchanged.

**C-7 `refactor/motion-profile-lists`** (Medium) — M-092, M-093, M-094, M-095, M-097, M-096 (Q-18: the
shared media tabs' content crossfades on the effects springs)
- Files: `feature:profile` the lists of M-092, M-095 and M-097, `ui/ProfileScreen.kt:246-287`,
  `chatinfo/ChatInfoScreen.kt:254-262`, `chatinfo/settings/ChatSettingsScreen.kt:178-301`.
- Change: `animateItem(this, motion)` on every keyed item, headers and footers included; `GrowingVisibility` for the
  rows a switch reveals; the plain columns of M-095 become keyed lazy items or wrap their rows in `GrowingVisibility`.
- Verify: `ProfileRecompositionTest` and `ChatMembersRecompositionTest` still pass (scrolling recomposes nothing).

**C-8 `fix/motion-auth-back`** (Medium) — M-123, M-124
- Files: `feature:auth` `ui/AuthStepTransition.kt:45-62`, `ui/components/AuthBars.kt:55-72`, `ui/CodeScreen.kt:113`.
- Change: the return job is kept and cancelled when a new gesture starts; `StateCrossfade` for the small swaps. Q-19:
  with animations off (`isReducedMotion()`), the gesture seeks nothing and the step changes when it commits.
- Verify: an `AuthStepTransitionTest` case: cancel, then a second gesture within the return, ends on the right step;
  and one with the animator duration scale at 0, where the shown step does not move during the gesture.

**C-9 `refactor/motion-step-back`** (Low; Q-08) — M-122, M-103, and with sessions A and B M-028, M-051,
M-078
- Adds `core:ui`'s `SeekableSteps` (§3.6) and moves `feature:auth` onto it; each flow then seeks its step crossfade
  with the back gesture.

## 6. Findings by pull request

| Pull request | Session | Severity | Findings |
|---|---|---|---|
| S-1 `refactor/motion-helpers` | Shared | – | helpers, lint rule ✓ (#434) |
| S-2 `fix/motion-reorder-drop` | Shared | High | M-002, M-038, M-104 ✓ (#457) |
| S-3 `fix/motion-pane-handle` | Shared | Medium | M-001 ✓ (#480) |
| S-4 `fix/motion-app-shell` | Shared | Medium | M-008, M-009, M-010 ✓ (#453) |
| S-5 `refactor/motion-core-ui` | Shared | Low | M-003, M-004, M-005, M-006, and Q-03 ✓ (#465) |
| S-6 `refactor/motion-panes` | Shared | Low | M-011, M-098, M-121 ✓ (#481) |
| S-7 `docs/motion-design-system` | Shared | Low | M-007, M-012 ✓ (#484) |
| S-8 `chore/motion-lint-strict` | Shared | – | the rule's last checks |
| A-1 `fix/motion-folder-swipe` | A | High | M-013, M-014, M-015, M-020 ✓ (#458) |
| A-2 `fix/motion-composer-panel` | A | High | M-029 ✓ (#461) |
| A-3 `fix/motion-chat-selection` | A | High | M-031, M-037 ✓ (#463) |
| A-4 `fix/motion-chat-gestures` | A | High | M-030, M-032, M-039, M-040, M-047 ✓ (#466) |
| A-5 `refactor/motion-chat-composer` | A | Medium | M-034, M-035, M-041, M-042, M-046, M-048 ✓ (#468) |
| A-6 `refactor/motion-message-menu` | A | Medium | M-033 ✓ (#470), M-043 no change (owner, 2026-11-12) |
| A-7 `refactor/motion-chat-lists` | A | Low | M-045, M-049 ✓ (#474), M-050 stays (Q-09) |
| A-8 `refactor/motion-chat-list` | A | Low | M-017 to M-019, M-022 to M-025, M-027 ✓ (#476); M-016, M-021, M-026 stay (Q-10, Q-09); M-028, M-051 go with C-9 |
| A-9 `refactor/motion-reaction-badge` | A | Medium | M-036 ✓ (#472) (M-044 is closed by Q-13) |
| B-1 `fix/motion-answer-slider` | B | High | M-052, M-054, M-062 ✓ (#440) |
| B-2 `fix/motion-call-reactions` | B | High | M-053 ✓ (#449) |
| B-3 `fix/motion-voice-chat-layout` | B | High | M-055, M-056, M-058, M-065, M-071 ✓ (#450) |
| B-4 `fix/motion-viewer-swipe-up` | B | High | M-072 ✓ (#451) |
| B-5 `fix/motion-story-picker` | B | High | M-079, M-084, M-085, M-089 (strip) ✓ (#452) |
| B-6 `refactor/motion-call-screen` | B | Medium | M-059, M-060, M-066, M-067, M-068 ✓ (#454, #459) |
| B-7 `refactor/motion-call-lists` | B | Medium | M-057, M-061, M-063, M-064, M-069, M-070 ✓ (#455) |
| B-8 `refactor/motion-media-viewer` | B | Medium | M-073, M-074, M-075, M-081, M-082, M-083 ✓ (#456) |
| B-9 `refactor/motion-stories` | B | Medium | M-076, M-077, M-078, M-080, M-086, M-087, M-088, M-089 ✓ (#452, #460; M-077 verified on device) |
| B-10 `refactor/motion-scanner-feedback` | B | Medium | M-125, M-126 ✓ (#462) |
| C-1 `fix/motion-settings-progress` | C | High | M-106, M-114, M-120 ✓ (#464) |
| C-2 `fix/motion-settings-home` | C | High | M-105, M-107 ✓ (#469) |
| C-3 `fix/motion-profile-progress` | C | High | M-090, M-091 ✓ (#471) |
| C-4 `fix/motion-contacts-form` | C | High | M-099, M-100, M-101, M-102 ✓ (#473), M-103 ✓ (#483) |
| C-5 `refactor/motion-settings-rows` | C | Medium | M-108 to M-113 ✓ (#475) |
| C-6 `refactor/motion-passcode` | C | Medium | M-115 to M-119 ✓ (#477) |
| C-7 `refactor/motion-profile-lists` | C | Medium | M-092 to M-097 ✓ (#478) |
| C-8 `fix/motion-auth-back` | C | Medium | M-123, M-124 ✓ (#479) |
| C-9 `refactor/motion-step-back` | C | Low | M-122, M-051, M-078, M-103 ✓ (#483); M-028 → #482 |

Every finding from M-001 to M-126 is in one row. A finding listed in two rows (M-089, and the step-backs of C-9) is
split between them as the entries above say.

## 7. What not to change

These were read and are right. A pull request of this plan leaves them as they are:

- **`core:ui` `theme/Motion.kt`**: shared axis X and Y, fade through, `predictiveBackPopExit`, `predictiveBackScale`
  and its curve (0.1, 0.1, 0, 1), which is Android's own.
- **`core:ui` `theme/Shape.kt` `rememberMorphShape`** and every morph that goes through it: default spatial,
  `snapWhenReducedMotion`, progress read in the draw phase.
- **`core:ui`**: `SelectionBack`, `DelayedLoadingIndicator`, `OnlineIndicator`'s node, `ColorSchemeTransition` (it
  recomposes on purpose, only while a theme changes), `EmptyState`'s turn (stops with reduced motion),
  `ReducedMotion.kt`, `TabScroll`, the container transform in `transition/SharedMedia.kt` (only its speed changes, with
  S-5, Q-03) and
  `PostponeOverlayEnter`.
- **`:app`**: the three `NavHost`s' transitions, `SplashScreenExit.kt` (the scheme outside composition, duration
  scale honoured), `LockableApp.kt`, the New chat button's fades, `FloatingNavigationBar`'s slide and alpha read in its
  graphics layer, the docked search's crossfade (`ui/ChatsListDetail.kt:339`, equal sizes).
- **`feature:chats`**: `FolderSwipe`'s release velocity and draw-phase reads; `FolderSlide`'s fades; the rows' swipe
  icon pop; `StoryRings` (keyed, scheme `animateItem`, ring in the draw phase).
- **`feature:chat`**: `SwipeToReply`'s phases and icon pop; `TypingDots`; `PollBar`; `MessageJumpState`; the jump to
  bottom's scroll (`animateScrollBy` on the scheme, cancelled by a touch); the message menu's opening; `MediaImage`,
  the date pill, `SelectionCheck`'s own draw, `RichDetails`' chevron, `UploadProgress`, the pinned bar's segments; the
  record button's width in the layout phase and its haptics; the keyboard ↔ panel switch, which moves nothing; every
  `animateItem` that passes the three scheme specs; the 18 `AnimatedContent`s with a scheme `SizeTransform`.
- **`feature:calls`**: the ringing avatar's turn (stops for reduced motion, power saving and
  `InfiniteAnimationPolicy`) and morph; the slider's offset in `offset { }` and its `TextHandleMove` at 70%; the
  speaking rings; the rating sheet's star pop; the sheets' crossfades.
- **`feature:media`**: `DismissState`'s swipe to close (keeps the velocity, cancels its settle, draw and layout
  reads); the scrim and chrome following the gesture; `PostponeOverlayEnter`; the images' own effects fades;
  `animateContentSize(defaultSpatialSpec)` in `MediaChrome.kt:247`; the story segments drawn in `drawBehind`; the
  stories pager's `defaultSpatialSpec` scroll; `PickStrip`'s haptics and drag in its graphics layer.
- **`feature:auth`**: the headline badge (morph on spatial, color and icons on effects, in `drawBehind`); the shared
  axis Y steps; the cancelled back's return on `defaultEffectsSpec` (§5 says so); `AuthBanner`; Welcome's hero morph.
- **`feature:settings`**: the passcode shake (one spring with an initial velocity, in `graphicsLayer`); `PinPad`;
  `settingsListRow`; the 13 step crossfades; sliders and their `SegmentTick`.
- **`feature:profile` and `feature:contacts`**: the profile top bar's morph; the profile images' fades; the shared
  media tabs' scroll; `collapsingWith` in the contacts header; the fast-scroll index's draw and haptics;
  `Modifier.row`.
- **material3's own motion**: press morphs, list item shapes, sheets, menus, snackbars, tooltips, the top bars'
  collapse (Q-05). They read the scheme already.

## 8. Checklist to append to `docs/DESIGN_SYSTEM.md` §12

S-7 adds these lines under §12's "All animations use `motionScheme` specs" item:

- [ ] Every animation call passes a spec from `MaterialTheme.motionScheme` or a `core:ui` motion helper; none is left
      at Compose's default (`PingogramMotionDefault` passes)
- [ ] Spatial specs for position, size, scale, rotation and shape; effects specs for color and opacity; speed by size
      (fast: small components; default: bars, sheets, rows, panes, screen pushes; slow: a whole screen growing out of a
      button or a thumbnail)
- [ ] Nothing jumps: content that comes or goes animates its size or has its place reserved; no control moves under
      the finger that pressed it
- [ ] State swaps in place crossfade (`StateCrossfade`); lazy items, headers and footers included, use
      `animateItem(scope, motion)`
- [ ] Animated values are read in the layout or draw phase; a `RecompositionCounter` test covers each new animation
      on a scrolling or always-visible surface
- [ ] Gestures: the release passes its velocity; a new touch cancels a running settle; the outcome and its haptic
      happen at the release
- [ ] Back inside a screen previews with the gesture where §5 asks for it
- [ ] Reduced motion: loops stop, morphs and container transforms snap, and a test runs the screen with the animator
      duration scale at 0

## 9. The owner's decisions (2026-11-11)

The owner took the audit's recommendation for each of the 26 questions, after a check against Material's rule of speed
by size and against what material3's own components do in the pinned version (audit §6). That check changed four of
them: Q-03, Q-04, Q-17 and Q-22. The decisions are normative in `docs/DESIGN_SYSTEM.md` §5.1. Nothing waits for the
owner any more; items the decisions leave as they are need no pull request.

| # | Decision | Lands in |
|---|---|---|
| Q-01 | A bar or block that grew in shrinks away on `defaultSpatialSpec`; fast exits stay only where §6 names them (sticker suggestions, the floating navigation bar) | A-5 (M-041), A-8 (M-027), B-6 (M-066), B-9 (M-087), B-10 (M-126) |
| Q-02 | Fades come in on `defaultEffectsSpec` and leave on `fastEffectsSpec`, everywhere | S-5 (M-005), A-8 (M-022), B-6 (M-066) |
| Q-03 | A whole screen growing out of a button or a thumbnail moves on `slowSpatialSpec` on its way in and on `defaultSpatialSpec` on its way back. Shared-axis pushes keep `defaultSpatialSpec` | S-5 |
| Q-04 | Panes move on `defaultSpatialSpec`, read from the scheme: bounds, enter and exit, and the third pane's handle | S-3 (M-001), S-6 (M-011, M-098, M-121) |
| Q-05 | The collapsing header keeps material3's own settle; §5 no longer says slow | closed by `docs/DESIGN_SYSTEM.md` §5.1 (M-007) |
| Q-06 | Signing in and out fade through; §5 says so | closed by `docs/DESIGN_SYSTEM.md` §5.1 (M-012) |
| Q-07 | A committed folder swipe lands on `defaultSpatialSpec`, as a tab tap | A-1 (M-015) |
| Q-08 | Step-backs inside a screen preview with the back gesture, through one helper | C-9 (M-028, M-051, M-078, M-103, M-122) |
| Q-09 | Scroll to top on tab reselect runs on `defaultSpatialSpec`; other programmatic scrolls keep Compose's | S-5 (M-003); M-021, M-026 and M-050 stay as they are |
| Q-10 | Chat rows and sessions keep `SwipeToDismissBox`'s own settle | M-016 stays as it is; C-6 does the reveal's icon (M-119) |
| Q-11 | The emoji panel and a bot keyboard grow in and shrink away on `defaultSpatialSpec` when no keyboard is involved, and back previews it | A-2 (M-029) |
| Q-12 | A reaction badge added to a bubble scales in on `fastSpatialSpec` in its draw phase; no burst | A-9 (M-036) |
| Q-13 | A new message fades in place while its neighbours slide; §5 says so | closed by `docs/DESIGN_SYSTEM.md` §5.1 (M-044) |
| Q-14 | Floating reactions rise on `slowSpatialSpec` and fade on `slowEffectsSpec`, and stop with power saving's "Stop call screen animations" | B-2 |
| Q-15 | Video tiles fade in and out and the voice and video layouts crossfade, on the effects springs; tiles' bounds do not animate | B-6 (M-059), B-7 (M-057) |
| Q-16 | The message menu waits for the lifted message's return only when the message had been moved to fit | A-6 (M-033) |
| Q-17 | The record pill springs back on `fastSpatialSpec` after a release, a cancel or a lock | A-4 (M-032) |
| Q-18 | New story's two steps move on the shared axis X; the shared media tabs' content crossfades on the effects springs | B-9 (M-078), C-7 (M-096) |
| Q-19 | With animations off, auth's back gesture previews nothing: the step changes when the gesture commits | C-8 |
| Q-20 | Progress while Telegram answers sits in a reserved slot above the buttons; auth keeps its progress in the button | C-1, C-3, C-4 (M-090, M-091, M-099, M-106, M-114) |
| Q-21 | Rows that depend on a switch grow in and shrink away inside `SettingsGroup` | C-5 (M-111, M-113) |
| Q-22 | A cancelled back gesture in the viewers returns on `defaultSpatialSpec`, and a new gesture cancels the return | B-8 (M-082) |
| Q-23 | Story segments and the video seek bar keep their 32 ms steps, to be looked at on the owner's 120 Hz phone before any change | nothing |
| Q-24 | Pinch zoom keeps its hard stops at 1× and 5×; the stories viewer closes on a swipe down only | B-9 |
| Q-25 | The fast-scroll index's dot jumps between letters; the session swipe's haptic fires only for the user's own crossing | C-6 |
| Q-26 | The motion lint rule covers `:app` as it covers the features; `core:ui` is checked for literal tweens, durations and easings only | S-1, S-8 |

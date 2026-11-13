package dev.kyralabs.pingogram.core.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.NavigatorState
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.get
import dev.kyralabs.pingogram.core.ui.trace.OverlayTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Overlay destinations: full-window screens that Navigation shows above the screen below without leaving it, as it
 * does with dialog destinations, but in the app's own window, inside its `SharedTransitionLayout`, so that content
 * can grow out of the screen below and shrink back into it (the media viewer, `docs/DESIGN_SYSTEM.md` §5).
 *
 * The screen below stays composed, laid out and started (an overlay destination is a [FloatingWindow]): coming back
 * to it composes nothing anew, so the transition back runs on frames that do nothing else (`docs/PERFORMANCE.md` §4:
 * coming back from a pushed screen composes it anew). [OverlayHost] draws the overlays above it, each fading in and
 * out on the default effects spring; it is registered with the `NavController` by [rememberOverlayNavigator].
 */
@Navigator.Name(OverlayNavigator.NAME)
class OverlayNavigator : Navigator<OverlayNavigator.Destination>() {
    // The NavController attaches the navigator when its NavHost sets the graph, after OverlayHost first composes.
    private val attached = MutableStateFlow<NavigatorState?>(null)

    /** Navigation's state of the overlays, once the `NavController` attached the navigator. */
    internal val navigatorState: StateFlow<NavigatorState?> = attached

    override fun onAttach(state: NavigatorState) {
        super.onAttach(state)
        attached.value = state
    }

    override fun createDestination(): Destination = Destination(this) {}

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?,
    ) {
        entries.forEach { state.pushWithTransition(it) }
    }

    override fun popBackStack(
        popUpTo: NavBackStackEntry,
        savedState: Boolean,
    ) {
        state.popWithTransition(popUpTo, savedState)
    }

    /** [entry] finished coming in (it is resumed) or going out (it is destroyed). */
    internal fun onTransitionComplete(entry: NavBackStackEntry) = state.markTransitionComplete(entry)

    /** An overlay destination: its [content], in the [AnimatedVisibilityScope] it fades in and out with. */
    class Destination(
        navigator: OverlayNavigator,
        internal val content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator),
        FloatingWindow

    companion object {
        /** The navigator's name, on the back stack Navigation saves. */
        const val NAME = "overlay"
    }
}

/** Builds an [OverlayNavigator.Destination] for the type-safe route [route]. */
class OverlayNavigatorDestinationBuilder(
    private val overlayNavigator: OverlayNavigator,
    route: KClass<*>,
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>>,
    private val content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) : NavDestinationBuilder<OverlayNavigator.Destination>(overlayNavigator, route, typeMap) {
    override fun instantiateDestination(): OverlayNavigator.Destination =
        OverlayNavigator.Destination(overlayNavigator, content)
}

/**
 * Adds an overlay destination of route [T] ([OverlayNavigator]): [content] above the screen below, which stays.
 * The `NavController` must have the navigator ([rememberOverlayNavigator]).
 */
inline fun <reified T : Any> NavGraphBuilder.overlay(
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    destination(OverlayNavigatorDestinationBuilder(provider[OverlayNavigator::class], T::class, typeMap, content))
}

/**
 * The [OverlayNavigator] of [navController], added to it the first time: before its `NavHost` sets the graph, which
 * needs the navigator for its overlay destinations, also when it restores a saved back stack.
 */
@Composable
fun rememberOverlayNavigator(navController: NavHostController): OverlayNavigator =
    remember(navController) {
        val provider = navController.navigatorProvider
        // getNavigator throws while the provider has none of that name.
        runCatching { provider.getNavigator<OverlayNavigator>(OverlayNavigator.NAME) }.getOrNull()
            ?: OverlayNavigator().also { provider.addNavigator(it) }
    }

/**
 * [below], the app's screens and its `NavHost`, with the overlay destinations of [navigator] above them
 * ([OverlayNavigator]). Each overlay fades in and out on the default effects spring, in an [AnimatedVisibilityScope]
 * its content shares elements with (`ProvideNavAnimatedVisibilityScope`), and keeps its saved state while it is on the
 * back stack. While one shows, what is below takes no touches, no keyboard focus and is hidden from screen readers, as
 * below a dialog; it stays composed and drawn.
 */
@Composable
fun OverlayHost(
    navigator: OverlayNavigator,
    modifier: Modifier = Modifier,
    below: @Composable () -> Unit,
) {
    val navigatorState by navigator.navigatorState.collectAsState()
    val backStack =
        navigatorState
            ?.backStack
            ?.collectAsState()
            ?.value
            .orEmpty()
    val transitions =
        navigatorState
            ?.transitionsInProgress
            ?.collectAsState()
            ?.value
            .orEmpty()
    val saveableStateHolder = rememberSaveableStateHolder()
    // The overlays still to draw: those on the back stack, and above them those going out.
    val shown = remember(backStack, transitions) { backStack + transitions.filter { it !in backStack } }
    Box(modifier) {
        Box(Modifier.fillMaxSize().then(if (backStack.isEmpty()) Modifier else Covered)) { below() }
        shown.forEach { entry ->
            key(entry.id) {
                OverlayEntry(
                    navigator = navigator,
                    entry = entry,
                    visible = entry in backStack,
                    restored = entry !in transitions,
                    saveableStateHolder = saveableStateHolder,
                )
            }
        }
    }
}

/**
 * One overlay, [visible] while on the back stack, fading in (at once when [restored] with the back stack) and out on
 * the default effects spring; Navigation hears when it came in or went out.
 *
 * Its content is composed hidden first (the transition at [EnterExitState.PreEnter]), and comes in once it is ready: as
 * soon as nothing in it holds the way in back ([PostponeOverlayEnter]), a frame after it first composed, or after
 * [OVERLAY_ENTER_TIMEOUT_MILLIS] at most. Content shared with the screen below (a container transform) has its own
 * bounds then, instead of growing into a page that has nothing on it yet.
 */
@Composable
private fun OverlayEntry(
    navigator: OverlayNavigator,
    entry: NavBackStackEntry,
    visible: Boolean,
    restored: Boolean,
    saveableStateHolder: SaveableStateHolder,
) {
    val destination = entry.destination as OverlayNavigator.Destination
    val gate = remember { OverlayEnterGate() }
    val started = rememberEnterStarted(gate, restored)
    val target =
        when {
            !visible -> EnterExitState.PostExit
            started -> EnterExitState.Visible
            else -> EnterExitState.PreEnter
        }
    val state = remember { MutableTransitionState(if (restored) EnterExitState.Visible else EnterExitState.PreEnter) }
    state.targetState = target
    val transition = rememberTransition(state, label = "overlay")
    val motionScheme = MaterialTheme.motionScheme
    // In on the default effects spring, out on the fast one, as every fade (docs/DESIGN_SYSTEM.md §5.1, Q-02).
    val alpha =
        transition.animateFloat(
            {
                if (targetState == EnterExitState.Visible) {
                    motionScheme.defaultEffectsSpec()
                } else {
                    motionScheme.fastEffectsSpec()
                }
            },
            label = "overlayAlpha",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
    val scope = remember(transition) { OverlayVisibility(transition) }
    val gone = !visible && state.isIdle && state.currentState == EnterExitState.PostExit
    OverlayTransitionTrace(state)
    if (!gone) {
        // The overlay's fade multiplies each of its draws' alpha (ModulateAlpha) instead of rendering the whole window
        // into an offscreen buffer first in every frame of the fade, as a plain alpha layer does: its content is a
        // scrim and what is over it, and the media a container transform shares is drawn in the transform's own overlay
        // with its own crossfade (docs/audit/performance-review-2026-11.md, M1).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.then(TouchBlocker),
        ) {
            CompositionLocalProvider(LocalOverlayEnterGate provides gate) {
                entry.LocalOwnersProvider(saveableStateHolder) { destination.content(scope, entry) }
            }
        }
    }
    val settled = state.isIdle && (state.currentState == EnterExitState.Visible || gone)
    if (settled) {
        LaunchedEffect(visible) { completeTransition(navigator, entry, visible, saveableStateHolder) }
    }
    DisposableEffect(entry) {
        // Left before its transition ended (e.g. the whole back stack replaced): Navigation still hears of it.
        onDispose {
            val onBackStack =
                navigator.navigatorState.value
                    ?.backStack
                    ?.value
                    .orEmpty()
            if (entry !in onBackStack) navigator.onTransitionComplete(entry)
        }
    }
}

/**
 * Tells Navigation that [entry]'s transition ended. Once it is gone (not [visible]), its content left the composition
 * in this frame, and Navigation destroys the entry (clearing its view models) in the next one, so the frame that
 * uncovers the screen below does not do both (docs/audit/performance-review-2026-11.md, M2); its saved state goes too.
 */
private suspend fun completeTransition(
    navigator: OverlayNavigator,
    entry: NavBackStackEntry,
    visible: Boolean,
    saveableStateHolder: SaveableStateHolder,
) {
    if (visible) {
        navigator.onTransitionComplete(entry)
    } else {
        withFrameNanos {}
        navigator.onTransitionComplete(entry)
        saveableStateHolder.removeState(entry.id)
    }
}

/**
 * [OverlayTrace]'s sections of [state]'s transitions: `overlayEnter` from the frame the overlay starts coming in until
 * it settled, `overlayExit` from the frame it starts going out until it is gone (`docs/PERFORMANCE.md` §9). Nothing
 * for an overlay restored visible.
 */
@Composable
private fun OverlayTransitionTrace(state: MutableTransitionState<EnterExitState>) {
    val target = state.targetState
    val settled = state.isIdle
    LaunchedEffect(target, settled) {
        when {
            target == EnterExitState.Visible && !settled -> OverlayTrace.begin(OverlayTrace.ENTER)
            target == EnterExitState.Visible -> OverlayTrace.end(OverlayTrace.ENTER)
            target == EnterExitState.PostExit && !settled -> OverlayTrace.begin(OverlayTrace.EXIT)
            target == EnterExitState.PostExit -> OverlayTrace.end(OverlayTrace.EXIT)
        }
    }
}

/**
 * Whether the overlay may come in: at once when [restored], else once nothing holds [gate], a frame after its content
 * first composed, or after [OVERLAY_ENTER_TIMEOUT_MILLIS] at most.
 */
@Composable
private fun rememberEnterStarted(
    gate: OverlayEnterGate,
    restored: Boolean,
): Boolean {
    var started by remember { mutableStateOf(restored) }
    if (!started) {
        LaunchedEffect(gate) {
            withTimeoutOrNull(OVERLAY_ENTER_TIMEOUT_MILLIS) {
                // The content's holds come with its first composition, applied before the next frame.
                withFrameNanos {}
                snapshotFlow { gate.isHeld }.first { !it }
            }
            started = true
        }
    }
    return started
}

/** An overlay's [AnimatedVisibilityScope]: its own [transition], which its content stays composed through. */
private class OverlayVisibility(
    override val transition: Transition<EnterExitState>,
) : AnimatedVisibilityScope

/**
 * Holds the way in of the overlay destination this is composed in ([OverlayHost]) while [ready] is false: its content
 * stays hidden, composed, until it has what it shows (at most [OVERLAY_ENTER_TIMEOUT_MILLIS] after it first composed).
 * Nothing outside an overlay destination.
 */
@Composable
fun PostponeOverlayEnter(ready: Boolean) {
    val gate = LocalOverlayEnterGate.current ?: return
    if (!ready) {
        DisposableEffect(gate) {
            gate.hold()
            onDispose { gate.release() }
        }
    }
}

/** How many parts of an overlay's content hold its way in ([PostponeOverlayEnter]); main thread only. */
private class OverlayEnterGate {
    private var holds by mutableIntStateOf(0)

    val isHeld: Boolean get() = holds > 0

    fun hold() {
        holds++
    }

    fun release() {
        holds--
    }
}

private val LocalOverlayEnterGate = staticCompositionLocalOf<OverlayEnterGate?> { null }

/** The longest an overlay's content may hold its way in ([PostponeOverlayEnter]): it then comes in as it is. */
const val OVERLAY_ENTER_TIMEOUT_MILLIS = 500L

/** Content below an overlay: no touches reach it through the overlay, and no focus or screen reader enters it. */
private val Covered =
    Modifier
        .clearAndSetSemantics {}
        .focusProperties { canFocus = false }
        .focusGroup()
        .focusProperties { onEnter = { cancelFocusChange() } }

/** Takes every touch an overlay's content leaves, so none reaches the screen below. */
private val TouchBlocker =
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent()
        }
    }

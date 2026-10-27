package dev.kyralabs.pingogram.core.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        provider.navigators[OverlayNavigator.NAME] as? OverlayNavigator
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
 * One overlay, [visible] while on the back stack, fading in (at once when [restored] with the back stack) and out;
 * Navigation hears when it came in or went out.
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
    val state = remember { MutableTransitionState(restored) }
    state.targetState = visible
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(motionScheme.defaultEffectsSpec()),
        exit = fadeOut(motionScheme.defaultEffectsSpec()),
    ) {
        Box(Modifier.fillMaxSize().then(TouchBlocker)) {
            entry.LocalOwnersProvider(saveableStateHolder) { destination.content(this@AnimatedVisibility, entry) }
        }
    }
    val settled = state.isIdle && state.currentState == visible
    if (settled) {
        LaunchedEffect(visible) {
            navigator.onTransitionComplete(entry)
            if (!visible) saveableStateHolder.removeState(entry.id)
        }
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

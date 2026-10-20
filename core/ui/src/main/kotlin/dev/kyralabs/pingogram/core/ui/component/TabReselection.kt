package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * Taps on the tab that is already selected in the app's navigation (`docs/DESIGN_SYSTEM.md` §6.1): the floating
 * navigation bar or the rail report them with [reselect], naming the tab by its route, and the tab's screen scrolls
 * back to its top ([ScrollToTopOnReselect]). The app provides one through [LocalTabReselection].
 *
 * Nothing is kept for later: a tap reaches only the screens composed at that moment, and only those of its route.
 */
@Stable
class TabReselection {
    private val reselected =
        MutableSharedFlow<Any>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The selected tab, of top-level route [route], was tapped again. */
    fun reselect(route: Any) {
        reselected.tryEmit(route)
    }

    /** Each tap on the tab of [route] while it was selected. */
    fun reselections(route: Any): Flow<Any> = reselected.filter { it == route }
}

/**
 * The [TabReselection] the app's navigation reports to. The default belongs to nobody: without the app (previews, a
 * screen's own tests), no tab is ever tapped again.
 */
val LocalTabReselection = staticCompositionLocalOf { TabReselection() }

/**
 * Scrolls [listState] back to its first item, on the list's own animation, whenever the tab of [route] is tapped while
 * selected ([TabReselection]), as Material's navigation does; a tap during the scroll starts it again. Not [enabled],
 * e.g. on a screen pushed above the tab, it does nothing.
 */
@Composable
fun ScrollToTopOnReselect(
    route: Any,
    listState: LazyListState,
    enabled: Boolean = true,
) {
    if (!enabled) return
    val reselection = LocalTabReselection.current
    LaunchedEffect(reselection, route, listState) {
        reselection.reselections(route).collectLatest { listState.animateScrollToItem(0) }
    }
}

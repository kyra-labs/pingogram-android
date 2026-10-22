package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable

/**
 * The state of a long list that the user flings through, the chat list and the conversation (`docs/PERFORMANCE.md`
 * §4): a [LazyListState] with a cache window ([ScrollingListCacheWindow]). Items within half a screen beyond the
 * viewport compose ahead while the list scrolls, in the frames' idle time, rather than one at a time as they come into
 * view, and those it scrolled past stay composed for a screen behind it, so a fling back does not compose them again.
 * Saved like [rememberLazyListState].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberScrollingListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState =
    rememberLazyListState(
        cacheWindow = ScrollingListCacheWindow,
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
    )

/** Half a viewport ahead, one behind: [rememberScrollingListState]. */
@OptIn(ExperimentalFoundationApi::class)
private val ScrollingListCacheWindow =
    LazyLayoutCacheWindow(aheadFraction = CACHE_AHEAD_FRACTION, behindFraction = CACHE_BEHIND_FRACTION)

private const val CACHE_AHEAD_FRACTION = 0.5f
private const val CACHE_BEHIND_FRACTION = 1f

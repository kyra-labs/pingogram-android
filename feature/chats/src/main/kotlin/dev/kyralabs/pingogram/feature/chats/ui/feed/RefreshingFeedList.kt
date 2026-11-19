package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The feed's list, which reads the posts on screen again (`REQUIREMENTS.md` FR-X5, owner decision 2026-11-19) through
 * [onRefresh] with the keys of the cards shown: when it comes back into view (back from a channel, the app opened
 * again; its first start reads the newest posts already), and when pulled to refresh, Material's `PullToRefreshBox`
 * with the expressive loading indicator showing while [isRefreshing]. Never on a timer. The cards are [items], their
 * layout bounds held in [viewport] for the cards that report being seen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RefreshingFeedList(
    isRefreshing: Boolean,
    onRefresh: (keys: List<String>, pulled: Boolean) -> Unit,
    viewport: LayoutBoundsHolder,
    contentPadding: PaddingValues,
    items: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val shown = { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
    val starts = remember { intArrayOf(0) }
    LifecycleStartEffect(listState) {
        if (starts[0]++ > 0) currentOnRefresh(shown(), false)
        onStopOrDispose {}
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { currentOnRefresh(shown(), true) },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().layoutBounds(viewport),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = items,
        )
    }
}

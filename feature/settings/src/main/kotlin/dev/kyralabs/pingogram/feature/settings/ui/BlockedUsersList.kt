package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The list of the Blocked users page: its rows, drawn as one card, and loading more as its end comes near.

/** The rows under their count, then the next page loading or failing; more load as the end comes near. */
@Composable
internal fun BlockedUsersList(
    state: BlockedUsersUiState.Content,
    onEvent: (BlockedUsersEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style = RowStyle(MaterialTheme.sizes.contentMaxWidth, spacing.windowMarginCompact, MaterialTheme.motionScheme)
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.settings_blocked_users_header,
                        state.totalCount,
                        numbers.format(state.totalCount),
                    ),
                modifier = Modifier.row(this, style),
            )
        }
        itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, _ -> ROW }) { index, item ->
            BlockedSenderRow(
                item = item,
                onUnblock = { onEvent(BlockedUsersEvent.Unblock(item.sender)) },
                modifier = Modifier.row(this, style).cardRow(cardRowShape(index, state.items.size), cardColor),
            )
        }
        more(state.more, style, onRetry = { onEvent(BlockedUsersEvent.LoadMore) })
    }
    LoadMoreEffect(
        listState = listState,
        canLoadMore = state.more == MoreBlockedSenders.Available,
        onLoadMore = { onEvent(BlockedUsersEvent.LoadMore) },
    )
}

/**
 * The end of the list: the next page loading, or why it failed with "Try again" ([onRetry]); nothing when everything
 * is loaded or the next page waits for the end to come near.
 */
private fun LazyListScope.more(
    more: MoreBlockedSenders,
    style: RowStyle,
    onRetry: () -> Unit,
) {
    when (more) {
        MoreBlockedSenders.Loading -> {
            item(key = MORE, contentType = MORE) {
                SettingsLoading(stringResource(R.string.settings_blocked_users_loading), Modifier.row(this, style))
            }
        }

        is MoreBlockedSenders.Failed -> {
            item(key = MORE, contentType = MORE) {
                SettingsError(more.error, onRetry = onRetry, modifier = Modifier.row(this, style))
            }
        }

        MoreBlockedSenders.None, MoreBlockedSenders.Available -> {}
    }
}

/**
 * While [canLoadMore], calls [onLoadMore] when the end of the list comes within [LOAD_MORE_THRESHOLD] items, and again
 * whenever items were added and the end is still near.
 */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // The item count, so that a page that leaves the end near asks again.
            layout.totalItemsCount.takeIf { it > 0 && lastVisible >= it - 1 - LOAD_MORE_THRESHOLD }
        }.filterNotNull()
            .distinctUntilChanged()
            .collect { currentOnLoadMore() }
    }
}

/** A blocked user or chat: its avatar, name and detail, and a button that unblocks it, or progress while it does. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlockedSenderRow(
    item: BlockedSenderItem,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = item.name.resolve()
    ListItem(
        modifier = modifier,
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = name,
                id = item.avatarId,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = item.placeholderIcon,
            )
        },
        trailingContent = {
            if (item.isUnblocking) {
                val unblocking = stringResource(R.string.settings_unblocking, name)
                LoadingIndicator(Modifier.semantics { contentDescription = unblocking })
            } else {
                IconButton(onClick = onUnblock) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.settings_unblock, name),
                    )
                }
            }
        },
        supportingContent = item.detail?.let { detail -> { Text(detail.resolve()) } },
        colors = settingsItemColors(),
    ) {
        Text(name)
    }
}

/** How rows are laid out and animated: at most [maxWidth] wide with [margin]s, on the [motionScheme]'s springs. */
private class RowStyle(
    val maxWidth: Dp,
    val margin: Dp,
    val motionScheme: MotionScheme,
)

/** A row of the list: as wide as the settings pages' column, with their margins, sliding on the motion scheme. */
private fun Modifier.row(
    scope: LazyItemScope,
    style: RowStyle,
): Modifier =
    with(scope) {
        this@row
            .animateItem(
                fadeInSpec = style.motionScheme.defaultEffectsSpec(),
                placementSpec = style.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = style.motionScheme.fastEffectsSpec(),
            ).widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .padding(horizontal = style.margin)
    }

/**
 * A row drawn as part of one card: clipped to its part of the card's [shape], on the card's [color], which also fills
 * the corners of the list item's own shape, as the card does in a [SettingsSection].
 */
private fun Modifier.cardRow(
    shape: Shape,
    color: Color,
): Modifier = clip(shape).background(color)

private const val HEADER = "header"
private const val ROW = "row"
private const val MORE = "more"

/** How close to the end of the list the next page is asked for, as in search. */
private const val LOAD_MORE_THRESHOLD = 5

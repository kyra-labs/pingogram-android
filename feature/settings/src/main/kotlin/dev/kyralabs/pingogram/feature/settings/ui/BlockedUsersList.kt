package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.phoneNumberToShow
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.text.NumberFormat

// The list of the Blocked users page: its rows, one segmented group, and loading more as its end comes near.

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
    val style =
        SettingsListRowStyle(
            MaterialTheme.sizes.contentMaxWidth,
            windowMargin(),
            MaterialTheme.motionScheme,
        )
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
        // The gap between segmented rows; the heading and the end keep their own padding besides.
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.settings_blocked_users_header,
                        state.totalCount,
                        numbers.format(state.totalCount),
                    ),
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        itemsIndexed(state.items, key = { _, item -> item.key }, contentType = { _, _ -> ROW }) { index, item ->
            BlockedSenderRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, state.items.size),
                onUnblock = { onEvent(BlockedUsersEvent.Unblock(item.sender)) },
                modifier = Modifier.settingsListRow(this, style),
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
    style: SettingsListRowStyle,
    onRetry: () -> Unit,
) {
    when (more) {
        MoreBlockedSenders.Loading -> {
            item(key = MORE, contentType = MORE) {
                SettingsLoading(
                    stringResource(R.string.settings_blocked_users_loading),
                    Modifier.settingsListRow(this, style),
                )
            }
        }

        is MoreBlockedSenders.Failed -> {
            item(key = MORE, contentType = MORE) {
                SettingsError(more.error, onRetry = onRetry, modifier = Modifier.settingsListRow(this, style))
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

/**
 * A blocked user or chat, a row of the list's segmented group ([shapes]): its avatar, name and detail (the username, or
 * the phone number formatted for its country), and an "Unblock" text button whose shape morphs when pressed, or
 * progress while it unblocks. Screen readers hear the button with the name ("Unblock Ada Lovelace").
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BlockedSenderRow(
    item: BlockedSenderItem,
    shapes: ListItemShapes,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = item.name.resolve()
    SegmentedListItem(
        shapes = shapes,
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
                val unblock = stringResource(R.string.settings_unblock, name)
                TextButton(
                    onClick = onUnblock,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = unblock },
                ) {
                    Text(stringResource(R.string.settings_unblock_button))
                }
            }
        },
        supportingContent = blockedSenderDetail(item),
        colors = settingsRowColors(),
    ) {
        Text(text = name, style = LocalTextStyle.current.userContent)
    }
}

/** The row's detail: the username as user content, or the phone number formatted, left to right in any layout. */
private fun blockedSenderDetail(item: BlockedSenderItem): (@Composable () -> Unit)? {
    val detail = item.detail
    val phoneNumber = item.phoneNumber
    return when {
        detail != null -> {
            { Text(text = detail.resolve(), style = LocalTextStyle.current.userContent) }
        }

        phoneNumber != null -> {
            {
                val country = LocalConfiguration.current.locales[0].country
                val formatted = remember(phoneNumber, country) { formatPhoneNumber(phoneNumber, country) }
                Text(text = phoneNumberToShow(formatted), style = LocalTextStyle.current.numericContent)
            }
        }

        else -> {
            null
        }
    }
}

private const val HEADER = "header"
private const val ROW = "row"
private const val MORE = "more"

/** How close to the end of the list the next page is asked for, as in search. */
private const val LOAD_MORE_THRESHOLD = 5

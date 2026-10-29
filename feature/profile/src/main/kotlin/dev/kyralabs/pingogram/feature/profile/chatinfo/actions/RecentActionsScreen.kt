package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.BottomAndTopOf
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.chatinfo.LoadMoreEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageError
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageLoading
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors

/** The recent actions page: hoists [RecentActionsViewModel]; a tap on a person opens their profile. */
@Composable
internal fun RecentActionsRoute(
    onBack: () -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenProfile by rememberUpdatedState(onOpenProfile)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecentActionsEffect.OpenProfile -> currentOnOpenProfile(effect.userId)
            }
        }
    }
    RecentActionsScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * The recent actions (`docs/DESIGN_SYSTEM.md` §6, "Recent actions"), a settings-style page: the kinds of action as
 * `FilterChip`s in a row that scrolls sideways (none picked: every kind), a `LinearWavyProgressIndicator` while the
 * picked ones load, then the actions, newest first, as one segmented group in a keyed lazy list (the 40 dp avatar,
 * the name as user content, what they did, the message it concerns in one line, the time at the end); more load as
 * the end comes near. None found is a line of text, a failure the page's error with "Try again".
 */
@Composable
internal fun RecentActionsScreen(
    state: RecentActionsUiState,
    onEvent: (RecentActionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    InfoPageScaffold(
        title = stringResource(R.string.recent_actions),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        when (state) {
            RecentActionsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is RecentActionsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(RecentActionsEvent.Retry) } }
            }

            is RecentActionsUiState.Content -> {
                InfoPageBody(innerPadding) { contentPadding ->
                    ActionsList(state, onEvent, contentPadding, listState)
                }
            }
        }
    }
}

@Composable
private fun ActionsList(
    state: RecentActionsUiState.Content,
    onEvent: (RecentActionsEvent) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    listState: LazyListState,
) {
    val spacing = MaterialTheme.spacing
    val rowModifier =
        Modifier
            .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = windowMargin())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            remember(contentPadding, spacing) {
                contentPadding.withTop(spacing.small).plusBottom(spacing.extraLarge).let(::BottomAndTopOf)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            Column(
                rowModifier.padding(bottom = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                FilterRow(state, onEvent)
                Text(
                    text = stringResource(R.string.recent_actions_footer),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.isLoading) LoadingLine()
            }
        }
        if (state.actions.isEmpty() && !state.isLoading) {
            item(key = EMPTY, contentType = EMPTY) {
                Text(
                    text = stringResource(R.string.recent_actions_none),
                    modifier = rowModifier.padding(vertical = spacing.medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        itemsIndexed(state.actions, key = { _, item -> item.id }, contentType = { _, _ -> ROW }) { index, item ->
            ActionRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, state.actions.size),
                onClick = { onEvent(RecentActionsEvent.Open(item.userId)) },
                modifier = rowModifier.animateItem(),
            )
        }
        when {
            state.loadingMore -> item(key = MORE, contentType = MORE) { MoreLoading() }
            state.pageError != null -> item(key = MORE, contentType = MORE) { MoreFailed(onEvent, rowModifier) }
        }
    }
    LoadMoreEffect(listState, canLoadMore = state.canLoadMore && state.pageError == null) {
        onEvent(RecentActionsEvent.LoadMore)
    }
}

@Composable
private fun FilterRow(
    state: RecentActionsUiState.Content,
    onEvent: (RecentActionsEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        RecentActionsFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter in state.filters,
                onClick = { onEvent(RecentActionsEvent.ToggleFilter(filter)) },
                label = { Text(stringResource(filter.label)) },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** An action: who did what, the message it concerns, and when; a person's row opens their profile. */
@Composable
private fun ActionRow(
    item: RecentActionItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val name = item.name.resolve()
    SegmentedListItem(
        onClick = onClick,
        enabled = item.userId != 0L,
        shapes = shapes,
        modifier = modifier,
        colors = profileRowColors(),
        leadingContent = {
            Avatar(photo = item.photo, name = name, id = item.avatarId, size = MaterialTheme.sizes.avatarSmall)
        },
        supportingContent = {
            Column {
                Text(item.text.resolve(), style = MaterialTheme.typography.bodyMedium.userContent)
                item.message?.let { message ->
                    Text(
                        text = message.resolve(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.userContent,
                    )
                }
            }
        },
        trailingContent = {
            Text(item.time, style = MaterialTheme.typography.labelSmall.userContent)
        },
    ) {
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingLine() {
    val description = stringResource(R.string.recent_actions_loading)
    LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreLoading() {
    val description = stringResource(R.string.recent_actions_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun MoreFailed(
    onEvent: (RecentActionsEvent) -> Unit,
    modifier: Modifier,
) {
    Row(modifier.padding(vertical = MaterialTheme.spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.recent_actions_more_error),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { onEvent(RecentActionsEvent.LoadMore) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.recent_actions_retry))
        }
    }
}

private const val HEADER = "header"
private const val EMPTY = "empty"
private const val ROW = "row"
private const val MORE = "more"

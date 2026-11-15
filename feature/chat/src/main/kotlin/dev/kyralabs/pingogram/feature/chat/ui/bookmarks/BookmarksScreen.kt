package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Bookmarks page: hoists [BookmarksViewModel], opens a bookmark's message with [onOpenMessage] and offers "Undo"
 * after a removal; [onBack] leaves it.
 */
@Composable
internal fun BookmarksRoute(
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val details = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookmarksEffect.OpenMessage -> {
                    onOpenMessage(effect.chatId, effect.messageId)
                }

                // Snackbars queue up; later effects must not wait for them.
                BookmarksEffect.Removed -> {
                    launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.bookmark_removed),
                                actionLabel = resources.getString(R.string.bookmarks_undo),
                                duration = SnackbarDuration.Long,
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(BookmarksEvent.UndoRemove)
                    }
                }

                is BookmarksEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, details)) }
                }
            }
        }
    }
    BookmarksScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The Bookmarks page (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34b): a small `TopAppBar` with back and
 * "Bookmarks" on `surfaceContainer`, then on a `ContentSheet` the tag filters ("All 27", then each tag with a dot of
 * its hue and its count) and the bookmarks, the newest first ([BookmarkCard]); nothing bookmarked yet its empty state
 * (board 34d).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksScreen(
    state: BookmarksUiState,
    onEvent: (BookmarksEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bookmarks_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.bookmarks_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { padding ->
        ContentSheet(innerPadding = padding) { contentPadding ->
            when (state) {
                BookmarksUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
                }

                BookmarksUiState.Empty -> {
                    BookmarksEmpty()
                }

                is BookmarksUiState.Content -> {
                    BookmarksList(state, onEvent, contentPadding)
                }
            }
        }
    }
}

@Composable
private fun BookmarksList(
    state: BookmarksUiState.Content,
    onEvent: (BookmarksEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            item(key = FILTERS, contentType = FILTERS) { TagFilters(state, onEvent) }
            items(state.items, key = { "${it.chatId}:${it.messageId}" }, contentType = { CARD }) { item ->
                BookmarkCard(
                    item = item,
                    onOpen = { onEvent(BookmarksEvent.Open(item.chatId, item.messageId)) },
                    onRemove = { onEvent(BookmarksEvent.Remove(item.chatId, item.messageId)) },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
                )
            }
        }
    }
}

/** "All 27", then each tag with a dot of its hue and its count; one choice at a time. */
@Composable
private fun TagFilters(
    state: BookmarksUiState.Content,
    onEvent: (BookmarksEvent) -> Unit,
) {
    val resources = LocalResources.current
    val hues = MaterialTheme.pingogramColors.senderHues
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(state.filters, key = { it.tagId ?: ALL }) { filter ->
            val name = if (filter.tagId == null) resources.getString(R.string.bookmarks_all) else filter.name
            FilterChip(
                selected = state.selectedTagId == filter.tagId,
                onClick = { onEvent(BookmarksEvent.SelectTag(filter.tagId)) },
                label = {
                    Text(
                        text = resources.getString(R.string.bookmarks_filter, name, filter.count),
                        style = MaterialTheme.typography.labelLarge.userContent,
                    )
                },
                leadingIcon =
                    filter.tagId?.let {
                        {
                            Box(
                                Modifier
                                    .size(MaterialTheme.sizes.badgeIcon)
                                    .background(hues[filter.color].ink, CircleShape),
                            )
                        }
                    },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** Nothing bookmarked yet (board 34d): the bookmark on `Cookie9Sided`, what bookmarks are and how to save one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookmarksEmpty() {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_bookmark_filled,
        title = stringResource(R.string.bookmarks_empty_title),
        body = stringResource(R.string.bookmarks_empty_body),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val FILTERS = "filters"
private const val CARD = "card"
private const val ALL = "all"

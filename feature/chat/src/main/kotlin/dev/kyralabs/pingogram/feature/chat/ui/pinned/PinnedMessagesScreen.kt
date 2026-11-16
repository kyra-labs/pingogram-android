package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchTopBar
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The pinned messages page (the owner's design "Pinned messages page", `docs/DESIGN_SYSTEM.md` §6 "Pinned messages
 * bar"), on `surfaceContainer`: a small top bar with back, search and More ("Unpin all" where the user may unpin),
 * which
 * the search bar replaces while searching; then [PinnedPageList]: the title, the filter chips and the pins under their
 * months, or a loading indicator once loading takes a moment, the empty state, or an error with "Try again".
 */
@Composable
internal fun PinnedMessagesScreen(
    state: PinnedPageUiState,
    onEvent: (PinnedPageEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberScrollingListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val query = rememberTextFieldState(state.query)
    LaunchedEffect(query) { snapshotFlow { query.text.toString() }.collect { onEvent(PinnedPageEvent.Search(it)) } }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { PinnedTopBar(state, query, onEvent, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PinnedBody(state, onEvent, listState, innerPadding)
    }
}

/** The top bar, or the search bar in its place while searching. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedTopBar(
    state: PinnedPageUiState,
    query: TextFieldState,
    onEvent: (PinnedPageEvent) -> Unit,
    onBack: () -> Unit,
) {
    if (state.searching) {
        ChatSearchTopBar(
            query = query,
            height = TopAppBarDefaults.TopAppBarExpandedHeight,
            onClose = {
                query.edit { replace(0, length, "") }
                onEvent(PinnedPageEvent.CloseSearch)
            },
        )
        return
    }
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.pinned_back))
            }
        },
        actions = {
            IconButton(onClick = { onEvent(PinnedPageEvent.OpenSearch) }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_search), stringResource(R.string.pinned_search))
            }
            if (state.canUnpin && state.total > 0) MoreMenu(onEvent)
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/** More: an expressive menu with "Unpin all". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreMenu(onEvent: (PinnedPageEvent) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(UiR.drawable.ic_more_vert), stringResource(R.string.pinned_more))
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pinned_unpin_all)) },
                onClick = {
                    open = false
                    onEvent(PinnedPageEvent.UnpinAll)
                },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_keep_off), contentDescription = null) },
            )
        }
    }
}

/** The list with the content's months, or with its state in their place. */
@Composable
private fun PinnedBody(
    state: PinnedPageUiState,
    onEvent: (PinnedPageEvent) -> Unit,
    listState: LazyListState,
    innerPadding: PaddingValues,
) {
    val margin = windowMargin()
    val content = state.content
    val months = (content as? PinnedPageContent.Content)?.months ?: persistentListOf()
    val states: (LazyListScope.() -> Unit)? =
        when (content) {
            is PinnedPageContent.Content -> {
                null
            }

            else -> {
                { item(key = "state", contentType = "state") { PinnedState(content, onEvent) } }
            }
        }
    PinnedPageList(state, months, listState, innerPadding, margin, onEvent, states)
}

/** Loading after a moment, the empty states, or the error with "Try again", in the rest of the viewport. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LazyItemScope.PinnedState(
    content: PinnedPageContent,
    onEvent: (PinnedPageEvent) -> Unit,
) {
    val modifier = Modifier.fillMaxWidth().fillParentMaxHeight(STATE_HEIGHT)
    when (content) {
        PinnedPageContent.Loading -> {
            Box(modifier, contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        is PinnedPageContent.Empty -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_push_pin,
                title =
                    stringResource(
                        if (content.filtered) R.string.pinned_none_found_title else R.string.pinned_empty_title,
                    ),
                body =
                    stringResource(
                        if (content.filtered) R.string.pinned_none_found_body else R.string.pinned_empty_body,
                    ),
                modifier = modifier,
            )
        }

        is PinnedPageContent.Error -> {
            EmptyState(
                illustration = MaterialShapes.Cookie4Sided,
                icon = UiR.drawable.ic_error,
                title = stringResource(R.string.pinned_error_title),
                body = errorText(content.error),
                modifier = modifier,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    Button(onClick = { onEvent(PinnedPageEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                        Text(
                            stringResource(R.string.scheduled_retry),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                },
            )
        }

        is PinnedPageContent.Content -> {}
    }
}

/** The share of the viewport the states take below the title and chips. */
private const val STATE_HEIGHT = 0.7f

@PingogramPreviews
@Composable
private fun PinnedMessagesScreenPreview(
    @PreviewParameter(PinnedPagePreviewStates::class) state: PinnedPageUiState,
) {
    PingogramPreview { PinnedMessagesScreen(state = state, onEvent = {}, onBack = {}) }
}

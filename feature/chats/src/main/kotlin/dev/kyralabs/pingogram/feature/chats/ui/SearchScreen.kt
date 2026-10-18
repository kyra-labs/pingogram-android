package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewStates
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Global search (`REQUIREMENTS.md` FR-L6; `docs/DESIGN_SYSTEM.md` §6), a full-screen destination pushed above the tabs:
 * the expanded full-screen search bar, its field at the top and the results below.
 *
 * - **Search bar**: Material 3 Expressive's expanded full-screen search bar, from its parts: the search bar's
 *   [SearchBarDefaults.InputField] (on [textFieldState], with an expanded search bar state), with a back arrow that
 *   leaves ([onBack]) and a clear button while there is text, over the search bar's container color and divider.
 *   material3 1.5's `ExpandedFullScreenSearchBar` shows them in a dialog window that expands from a collapsed
 *   `SearchBar`: as a navigation destination, that window would stay above the conversation a result pushes until
 *   search left the composition, and would sit outside Navigation's predictive back. Every edit of the field goes to
 *   [SearchEvent.QueryChanged]; the keyboard closes on the search action and once the results are scrolled.
 * - **Results** ([SearchResultsList]): the suggestions, or the sections of the query's results; an empty state before
 *   anything was ever searched or opened ([SearchContent.Suggestions.isEmpty]), and when the query found nothing.
 * - **Screen readers** hear the number of results once every section has searched (the divider's polite live region).
 * - **Layout**: edge to edge, the list ending above the keyboard; on medium and wider windows the field and the list
 *   keep a comfortable width ([sizes][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth]), centred.
 *
 * Stateless: [SearchRoute] hoists the view model and the field's state, and focuses the field on entry through
 * [focusRequester].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    textFieldState: TextFieldState,
    onEvent: (SearchEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = SearchBarDefaults.colors()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { currentOnEvent(SearchEvent.QueryChanged(it)) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { keyboard?.hide() }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                SearchField(
                    textFieldState = textFieldState,
                    colors = colors,
                    focusRequester = focusRequester,
                    onBack = onBack,
                    onClear = {
                        textFieldState.clearText()
                        onEvent(SearchEvent.ClearQuery)
                    },
                )
                ResultCountDivider(state.content, colors.dividerColor)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.containerColor,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        SearchBody(state.content, onEvent, listState, innerPadding)
    }
}

/**
 * The search bar's input field: the back arrow, the query with its placeholder, and the clear button while there is
 * text, at most [contentMaxWidth][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth] wide and below
 * the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    textFieldState: TextFieldState,
    colors: SearchBarColors,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    // Always expanded: the field is the expanded search bar's, full screen from the start.
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val keyboard = LocalSoftwareKeyboardController.current
    val hasText = textFieldState.text.isNotEmpty()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    SearchBarDefaults.fullScreenWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        contentAlignment = Alignment.TopCenter,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { keyboard?.hide() },
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.search_back),
                    )
                }
            },
            trailingIcon =
                if (hasText) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            colors = colors.inputFieldColors,
        )
    }
}

/**
 * The search bar's divider below the field, which tells screen readers how many results the query found once every
 * section has searched, in a polite live region: a node on screen, as live regions must be, and named only then.
 */
@Composable
private fun ResultCountDivider(
    content: SearchContent,
    color: Color,
) {
    val resources = LocalResources.current
    val count = (content as? SearchContent.Results)?.resultCount
    val announcement =
        when (count) {
            null -> null
            0 -> resources.getString(R.string.search_a11y_no_results)
            else -> resources.getQuantityString(R.plurals.search_a11y_results, count, count)
        }
    HorizontalDivider(
        modifier =
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                if (announcement != null) contentDescription = announcement
            },
        color = color,
    )
}

/** The list, or an empty state: the hint before anything was searched, and a query that found nothing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchBody(
    content: SearchContent,
    onEvent: (SearchEvent) -> Unit,
    listState: LazyListState,
    innerPadding: PaddingValues,
) {
    val bodyModifier =
        Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding)
    when {
        content is SearchContent.Suggestions && content.isEmpty -> {
            EmptyState(
                illustration = MaterialShapes.Cookie9Sided,
                icon = UiR.drawable.ic_search,
                title = stringResource(R.string.search_hint_title),
                body = stringResource(R.string.search_hint_body),
                modifier = bodyModifier.padding(innerPadding),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        content is SearchContent.Results && content.isEmpty -> {
            EmptyState(
                illustration = MaterialShapes.Sunny,
                icon = UiR.drawable.ic_search,
                title = stringResource(R.string.search_no_results_title, content.query),
                body = stringResource(R.string.search_no_results_body),
                modifier = bodyModifier.padding(innerPadding),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        else -> {
            SearchResultsList(
                content = content,
                onEvent = onEvent,
                listState = listState,
                contentPadding = innerPadding,
                modifier = bodyModifier,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun SearchScreenPreview(
    @PreviewParameter(SearchPreviewStates::class) state: SearchUiState,
) {
    PingogramPreview {
        SearchScreen(
            state = state,
            textFieldState = remember { TextFieldState(SearchPreviewData.queryOf(state)) },
            onEvent = {},
            onBack = {},
        )
    }
}

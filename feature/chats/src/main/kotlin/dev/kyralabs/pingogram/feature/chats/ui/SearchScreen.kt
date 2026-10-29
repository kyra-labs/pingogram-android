package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Constraints
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewStates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Global search (`REQUIREMENTS.md` FR-L6; `docs/DESIGN_SYSTEM.md` §6, "Global search"): the expanded search bar, its
 * field at the top, the filter chips under it, and the results below. Full screen on compact and medium windows, a
 * destination pushed above the tabs; docked in the Chats tab's list pane on expanded ones (`SearchPane`).
 *
 * - **Search bar**: Material 3 Expressive's expanded search bar, from its parts: the search bar's
 *   [SearchBarDefaults.InputField] (on [textFieldState], with an expanded search bar state), with a back arrow that
 *   leaves ([onBack]) and a clear button while there is text, over the search bar's container color and divider.
 *   material3 1.5's `ExpandedFullScreenSearchBar` shows them in a dialog window that expands from a collapsed
 *   `SearchBar`: as a navigation destination, that window would stay above the conversation a result pushes until
 *   search left the composition, and would sit outside Navigation's predictive back. Every edit of the field goes to
 *   [SearchEvent.QueryChanged]; the keyboard closes on the search action and once the results are scrolled.
 * - **Filters**: a row of [FilterChip]s under the field ([SearchFilter]: All, Chats, Contacts, Messages, Media, Links)
 *   with the chips' expressive press shapes, scrolling sideways where they do not fit; a chip sends
 *   [SearchEvent.FilterChanged].
 * - **Progress**: while anything searches ([SearchContent.isSearching]), one `LinearWavyProgressIndicator` over the
 *   divider below the chips, after [LoadingIndicatorDelay] so that fast answers never flash it, fading in on the
 *   default effects spring. It overlaps the divider rather than taking space, so the list never moves for it.
 * - **Results** ([SearchResultsList]): the suggestions, or the sections of the query's results; an empty state before
 *   anything was ever searched or opened ([SearchContent.Suggestions.isEmpty]), and when the query (or, without one,
 *   the Media or Links filter) found nothing.
 * - **Screen readers** hear the number of results once every section has searched (the divider's polite live region).
 * - **Layout**: edge to edge, the list ending above the keyboard; on wide windows the field, the chips and the list
 *   keep a comfortable width ([sizes][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth]), centred.
 *
 * Stateless: [SearchRoute] hoists the view model and the field's state, and focuses the field on entry through
 * [focusRequester].
 */
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
            // Opaque: the results scroll under the top bar, which the Scaffold places above them.
            Column(Modifier.background(colors.containerColor)) {
                SearchHeader(
                    textFieldState = textFieldState,
                    filter = state.filter,
                    focusRequester = focusRequester,
                    onBack = onBack,
                    onEvent = onEvent,
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
 * The search field, then the filter chips with [filter] selected, at most
 * [contentMaxWidth][dev.kyralabs.pingogram.core.ui.theme.PingogramSizes.contentMaxWidth] wide and below the status bar.
 */
@Composable
private fun SearchHeader(
    textFieldState: TextFieldState,
    filter: SearchFilter,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onEvent: (SearchEvent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    SearchBarDefaults.fullScreenWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val width =
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
        SearchField(
            textFieldState = textFieldState,
            colors = SearchBarDefaults.colors(),
            onBack = onBack,
            onClear = {
                textFieldState.clearText()
                onEvent(SearchEvent.ClearQuery)
            },
            modifier = width.focusRequester(focusRequester),
        )
        SearchFilters(filter, onSelect = { onEvent(SearchEvent.FilterChanged(it)) }, modifier = width)
    }
}

/**
 * The search bar's input field: the back arrow, the query with its placeholder, and the clear button while there is
 * text.
 */
@Composable
private fun SearchField(
    textFieldState: TextFieldState,
    colors: SearchBarColors,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    // Always expanded: the field is the expanded search bar's, full screen from the start.
    val searchBarState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val keyboard = LocalSoftwareKeyboardController.current
    val hasText = textFieldState.text.isNotEmpty()
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = { keyboard?.hide() },
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.search_back),
                )
            }
        },
        trailingIcon =
            if (hasText) {
                {
                    IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes()) {
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

/**
 * The filter chips (`docs/DESIGN_SYSTEM.md` §6, "Global search"): one [FilterChip] per [SearchFilter], [selected]
 * selected, each morphing its shape when pressed ([FilterChipDefaults.shapes]), in a row that scrolls sideways where
 * they do not fit, bringing the selected one into view; choosing one calls [onSelect].
 */
@Composable
private fun SearchFilters(
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
    modifier: Modifier,
) {
    val spacing = MaterialTheme.spacing
    // The chosen chip scrolls into view, e.g. Links restored after process death on a narrow phone.
    val selectedChip = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { selectedChip.bringIntoView() }
    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        SearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label)) },
                shapes = FilterChipDefaults.shapes(),
                modifier = if (filter == selected) Modifier.bringIntoViewRequester(selectedChip) else Modifier,
            )
        }
    }
}

/**
 * The search bar's divider below the chips, with the progress indicator over it while anything searches
 * ([SearchProgress]). It tells screen readers how many results the query found once every section has searched, in
 * a polite live region: a node on screen, as live regions must be, and named only then.
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
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (announcement != null) contentDescription = announcement
                },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = color)
        SearchProgress(content.isSearching)
    }
}

/**
 * Search's one progress indicator (`docs/DESIGN_SYSTEM.md` §5, §6): a `LinearWavyProgressIndicator` once [searching]
 * has lasted [LoadingIndicatorDelay], fading in on the default effects spring (read in the draw phase), named for
 * screen readers. It takes no height of its own ([overlapping]): it sits across the divider.
 */
@Composable
private fun SearchProgress(searching: Boolean) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(searching) {
        if (searching) delay(LoadingIndicatorDelay)
        shown = searching
    }
    if (!shown) return
    val alpha = remember { Animatable(0f) }
    val fadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(alpha) { alpha.animateTo(1f, fadeIn) }
    val description = stringResource(R.string.search_searching)
    LinearWavyProgressIndicator(
        modifier =
            Modifier
                .fillMaxWidth()
                .overlapping()
                .graphicsLayer { this.alpha = alpha.value }
                .semantics { contentDescription = description },
    )
}

/** Lays this content out with no height of its own, centred on where it is placed, so it overlaps what is around it. */
private fun Modifier.overlapping(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
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
            val (title, body) =
                when {
                    content.query.isNotEmpty() -> {
                        stringResource(R.string.search_no_results_title, content.query.bidiIsolated()).bidiIsolated() to
                            stringResource(R.string.search_no_results_body)
                    }

                    content.filter == SearchFilter.Links -> {
                        stringResource(R.string.search_no_links_title) to stringResource(R.string.search_no_links_body)
                    }

                    else -> {
                        stringResource(R.string.search_no_media_title) to stringResource(R.string.search_no_media_body)
                    }
                }
            EmptyState(
                illustration = MaterialShapes.Sunny,
                icon = UiR.drawable.ic_search,
                title = title,
                body = body,
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
@PingogramVariantPreviews
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

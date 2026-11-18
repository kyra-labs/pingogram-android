package dev.kyralabs.pingogram.feature.settings.ui.snippets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.Snippet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.dragAfterLongPress
import dev.kyralabs.pingogram.core.ui.component.dragHandle
import dev.kyralabs.pingogram.core.ui.component.rememberListReorderState
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsBody
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageFrame
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** [SnippetsScreen] with its [SnippetsViewModel]; its errors show as snackbars. */
@Composable
internal fun SnippetsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SnippetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SnippetsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    SnippetsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState = snackbarHostState)
}

/**
 * Snippets (`REQUIREMENTS.md` FR-X2, `docs/DESIGN_SYSTEM.md` §6 "Snippets"): the saved texts in the order the composer
 * offers them, each "/" and its name over the start of its text, moved by dragging its handle or after a long press
 * (screen readers: "Move up", "Move down"), opened by a tap ([onOpen]); without any, an [EmptyState] says what they
 * are for.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SnippetsScreen(
    state: SnippetsUiState,
    onEvent: (SnippetsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (id: Long) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_snippets),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when {
                state is SnippetsUiState.Loading -> {
                    SettingsLoading(
                        description = stringResource(R.string.settings_snippets_loading),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                state is SnippetsUiState.Content && state.snippets.isEmpty() -> {
                    EmptyState(
                        illustration = MaterialShapes.Cookie9Sided,
                        icon = UiR.drawable.ic_description,
                        title = stringResource(R.string.settings_snippets_empty_title),
                        body = stringResource(R.string.settings_snippets_empty_body),
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                state is SnippetsUiState.Content -> {
                    SnippetsList(state, onEvent, onOpen, listState, contentPadding)
                }
            }
        }
    }
}

/**
 * The snippets in their order, one segmented group, dragged into another place by a handle or after a long press as
 * Chat folders' are, then a footer saying how to insert them and that they stay on this device.
 */
@Composable
internal fun SnippetsList(
    state: SnippetsUiState.Content,
    onEvent: (SnippetsEvent) -> Unit,
    onOpen: (id: Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style = SettingsListRowStyle(MaterialTheme.sizes.contentMaxWidth, windowMargin(), MaterialTheme.motionScheme)
    val reorder =
        rememberListReorderState(listState, state.snippets.map { it.id }, ::snippetRowKey) {
            onEvent(SnippetsEvent.Reorder(it))
        }
    val byId = state.snippets.associateBy { it.id }
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
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        val order = reorder.order
        itemsIndexed(order, key = { _, id -> snippetRowKey(id) }, contentType = { _, _ -> ROW }) { index, id ->
            val snippet = byId[id] ?: return@itemsIndexed
            val dragged = reorder.draggedId == id
            SnippetRow(
                snippet = snippet,
                shapes = ListItemDefaults.segmentedShapes(index, order.size),
                dragged = dragged,
                onClick = { onOpen(id) },
                moves =
                    SnippetMoves(canMoveUp = index > 0, canMoveDown = index < order.lastIndex) { up ->
                        onEvent(SnippetsEvent.Move(id, up))
                    },
                modifier =
                    Modifier
                        .settingsListRow(this, style, animatePlacement = !dragged)
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) reorder.offset else 0f }
                        .dragAfterLongPress(reorder, id),
                handleModifier = Modifier.dragHandle(reorder, id),
            )
        }
        item(key = FOOTER, contentType = FOOTER) {
            SettingsFooter(
                pluralStringResource(R.plurals.settings_snippets_footer, Snippet.MAX_SNIPPETS, Snippet.MAX_SNIPPETS),
                Modifier.settingsListRow(this, style),
            )
        }
    }
}

/** Where a snippet can move one place, and [onMove] that moves it up (true) or down. */
internal class SnippetMoves(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (up: Boolean) -> Unit,
)

/**
 * A snippet: "/" and its name (user content) over the first line of its text, and a drag handle at the end
 * ([handleModifier] starts the drag, 48 dp wide), in the list item's dragged shape while [dragged]; a tap
 * ([onClick]) opens it, and screen readers move it with "Move up" and "Move down" ([moves]).
 */
@Composable
private fun SnippetRow(
    snippet: Snippet,
    shapes: ListItemShapes,
    dragged: Boolean,
    onClick: () -> Unit,
    moves: SnippetMoves,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val moveUp = stringResource(R.string.settings_snippets_move_up)
    val moveDown = stringResource(R.string.settings_snippets_move_down)
    SegmentedListItem(
        onClick = onClick,
        shapes = if (dragged) shapes.copy(shape = shapes.draggedShape) else shapes,
        modifier =
            modifier.semantics {
                customActions =
                    buildList {
                        if (moves.canMoveUp) add(CustomAccessibilityAction(moveUp) { true.also { moves.onMove(true) } })
                        if (moves.canMoveDown) {
                            add(CustomAccessibilityAction(moveDown) { true.also { moves.onMove(false) } })
                        }
                    }
            },
        supportingContent = {
            Text(
                text = snippet.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.userContent,
            )
        },
        trailingContent = {
            // Screen readers move the snippet with the row's custom actions instead.
            Box(modifier = handleModifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(UiR.drawable.ic_drag_indicator), contentDescription = null)
            }
        },
        colors = settingsRowColors(),
    ) {
        Text(
            text = stringResource(R.string.settings_snippets_name, snippet.name),
            style = LocalTextStyle.current.userContent,
        )
    }
}

private fun snippetRowKey(id: Long): Any = "snippet-$id"

private const val ROW = "row"
private const val FOOTER = "footer"

/** The states of the Snippets page for previews and screenshot tests. */
internal object SnippetsPreviewStates {
    val content =
        SnippetsUiState.Content(
            persistentListOf(
                Snippet(1, "address", "12 Crescent Road, Flat 4, Marylebone, London NW1 5LR"),
                Snippet(2, "thanks", "Thank you so much! I'll get back to you by the end of the day."),
                Snippet(3, "iban", "GB33 BUKB 2020 1555 5555 55"),
            ),
        )

    val empty = SnippetsUiState.Content(persistentListOf())
}

@Composable
private fun SnippetsPreview(state: SnippetsUiState) {
    PingogramPreview { SnippetsScreen(state, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun SnippetsScreenPreview() {
    SnippetsPreview(SnippetsPreviewStates.content)
}

@PingogramPreviews
@Composable
private fun SnippetsScreenEmptyPreview() {
    SnippetsPreview(SnippetsPreviewStates.empty)
}

@PingogramPreviews
@Composable
private fun SnippetsScreenLoadingPreview() {
    SnippetsPreview(SnippetsUiState.Loading)
}

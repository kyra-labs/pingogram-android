package dev.kyralabs.pingogram.feature.chats.ui.drafts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageRow
import dev.kyralabs.pingogram.feature.chats.ui.preview.DraftsPreviewStates
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Drafts page (`REQUIREMENTS.md` FR-X16, `docs/DESIGN_SYSTEM.md` §6 "Drafts"), pushed above the Chats tab: a
 * small [TopAppBar] with back ([onBack]) and the title on `surfaceContainer`, and below it on a rounded `surface`
 * sheet ([ContentSheet]) one segmented group of global search's message rows ([SearchMessageRow]), the draft as the
 * message, each opening its chat ([DraftsEvent.Open]). While the chats load the sheet shows the delayed loading
 * indicator; without any draft, an empty state.
 */
@Composable
internal fun DraftsScreen(
    state: DraftsUiState,
    onEvent: (DraftsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The sheet paints the top bar's container around itself (ContentSheet), so nothing lies under it.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = { DraftsTopBar(onBack) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            val body = Modifier.fillMaxSize().padding(contentPadding)
            when (state) {
                DraftsUiState.Loading -> DraftsLoading(body)
                DraftsUiState.Empty -> NoDrafts(body)
                is DraftsUiState.Content -> DraftsList(state, onEvent, Modifier.fillMaxSize(), contentPadding)
            }
        }
    }
}

/** The small top bar: back and "Drafts" in `titleLargeEmphasized`, on the page's `surfaceContainer`. */
@Composable
private fun DraftsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.drafts_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.drafts_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/**
 * The drafts in one `LazyColumn`, keyed by their chat and typed, at most `contentMaxWidth` wide and as far in from
 * the sheet's sides as global search's rows, moving on the motion scheme's springs as drafts come and go.
 */
@Composable
private fun DraftsList(
    content: DraftsUiState.Content,
    onEvent: (DraftsEvent) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val margin = MaterialTheme.spacing.windowMarginCompact
    val motionScheme = MaterialTheme.motionScheme
    val drafts = content.drafts
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items = drafts, key = { _, item -> item.chatId }, contentType = { _, _ -> DRAFT_ROW }) {
            index,
            item,
            ->
            SearchMessageRow(
                item = item,
                highlight = MatchHighlight.None,
                shapes = ListItemDefaults.segmentedShapes(index, drafts.size),
                onClick = { onEvent(DraftsEvent.Open(item.chatId)) },
                modifier =
                    Modifier
                        .animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            placementSpec = motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                        ).widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = margin),
            )
        }
    }
}

/** The delayed loading indicator in the middle of the sheet, named for screen readers. */
@Composable
private fun DraftsLoading(modifier: Modifier) {
    val description = stringResource(R.string.drafts_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** No chat has a draft: an empty state on `MaterialShapes.Clover4Leaf`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoDrafts(modifier: Modifier) {
    EmptyState(
        illustration = MaterialShapes.Clover4Leaf,
        icon = UiR.drawable.ic_description,
        title = stringResource(R.string.drafts_empty_title),
        body = stringResource(R.string.drafts_empty_body),
        modifier = modifier,
    )
}

/** The content type of the list's rows. */
private const val DRAFT_ROW = "draft"

@PingogramPreviews
@Composable
private fun DraftsScreenPreview(
    @PreviewParameter(DraftsPreviewStates::class) state: DraftsUiState,
) {
    PingogramPreview { DraftsScreen(state = state, onEvent = {}, onBack = {}) }
}

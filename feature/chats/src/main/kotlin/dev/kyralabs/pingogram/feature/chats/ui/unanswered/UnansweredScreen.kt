package dev.kyralabs.pingogram.feature.chats.ui.unanswered

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageRow
import dev.kyralabs.pingogram.feature.chats.ui.preview.UnansweredPreviewStates
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Unanswered page (`REQUIREMENTS.md` FR-X22, `docs/DESIGN_SYSTEM.md` §6 "Unanswered"), pushed above the Chats tab,
 * as Drafts: a small [TopAppBar] with back ([onBack]) and the title on `surfaceContainer`, and on a rounded `surface`
 * sheet ([ContentSheet]) one segmented group of global search's message rows, the person's last message as the
 * message. A row replies ([UnansweredEvent.Reply], "Reply" for screen readers); the close button at its end dismisses
 * it ([UnansweredEvent.Dismiss]). While the chats load the sheet shows the delayed loading indicator; with none, an
 * empty state. [snackbarHostState] shows a dismissal not saved.
 */
@Composable
internal fun UnansweredScreen(
    state: UnansweredUiState,
    onEvent: (UnansweredEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        // The sheet paints the top bar's container around itself (ContentSheet), so nothing lies under it.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = { UnansweredTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            val body = Modifier.fillMaxSize().padding(contentPadding)
            when (state) {
                UnansweredUiState.Loading -> UnansweredLoading(body)
                UnansweredUiState.Empty -> NoneUnanswered(body)
                is UnansweredUiState.Content -> UnansweredList(state, onEvent, Modifier.fillMaxSize(), contentPadding)
            }
        }
    }
}

/** The small top bar: back and "Unanswered" in `titleLargeEmphasized`, on the page's `surfaceContainer`. */
@Composable
private fun UnansweredTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.unanswered_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.unanswered_back),
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
 * The chats in one `LazyColumn`, keyed by chat and typed, at most `contentMaxWidth` wide and as far in from the
 * sheet's sides as global search's rows, moving on the motion scheme's springs as chats come and go.
 */
@Composable
private fun UnansweredList(
    content: UnansweredUiState.Content,
    onEvent: (UnansweredEvent) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val margin = MaterialTheme.spacing.windowMarginCompact
    val motionScheme = MaterialTheme.motionScheme
    val chats = content.chats
    val replyLabel = stringResource(R.string.unanswered_reply)
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items = chats, key = { _, item -> item.chatId }, contentType = { _, _ -> UNANSWERED_ROW }) {
            index,
            item,
            ->
            SearchMessageRow(
                item = item,
                highlight = MatchHighlight.None,
                shapes = ListItemDefaults.segmentedShapes(index, chats.size),
                onClick = { onEvent(UnansweredEvent.Reply(item.chatId)) },
                modifier =
                    Modifier
                        .animateItem(
                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                            placementSpec = motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = motionScheme.fastEffectsSpec(),
                        ).widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = margin)
                        .semantics { onClick(label = replyLabel, action = null) },
                trailingContent = {
                    DismissButton(item) { onEvent(UnansweredEvent.Dismiss(item.chatId, item.messageId)) }
                },
            )
        }
    }
}

/** The close button at a row's end: "Dismiss Ada" for screen readers. */
@Composable
private fun DismissButton(
    item: SearchMessageItem,
    onDismiss: () -> Unit,
) {
    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_close),
            contentDescription = stringResource(R.string.unanswered_dismiss, item.chatTitle.resolve()),
        )
    }
}

/** The delayed loading indicator in the middle of the sheet, named for screen readers. */
@Composable
private fun UnansweredLoading(modifier: Modifier) {
    val description = stringResource(R.string.unanswered_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** No chat is waiting: an empty state on `MaterialShapes.Sunny`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoneUnanswered(modifier: Modifier) {
    EmptyState(
        illustration = MaterialShapes.Sunny,
        icon = UiR.drawable.ic_check,
        title = stringResource(R.string.unanswered_empty_title),
        body = stringResource(R.string.unanswered_empty_body),
        modifier = modifier,
    )
}

/** The content type of the list's rows. */
private const val UNANSWERED_ROW = "unanswered"

@PingogramPreviews
@Composable
private fun UnansweredScreenPreview(
    @PreviewParameter(UnansweredPreviewStates::class) state: UnansweredUiState,
) {
    PingogramPreview { UnansweredScreen(state = state, onEvent = {}, onBack = {}) }
}

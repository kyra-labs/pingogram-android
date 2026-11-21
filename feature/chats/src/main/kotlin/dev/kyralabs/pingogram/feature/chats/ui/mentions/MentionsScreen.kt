package dev.kyralabs.pingogram.feature.chats.ui.mentions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageRow
import dev.kyralabs.pingogram.feature.chats.ui.SectionHeader
import dev.kyralabs.pingogram.feature.chats.ui.preview.MentionsPreviewStates
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Mentions & replies page (`REQUIREMENTS.md` FR-X1, `docs/DESIGN_SYSTEM.md` §6 "Mentions & replies"), pushed
 * above the Chats tab: a small [TopAppBar] with back ([onBack]) and the title on `surfaceContainer`, and below it on a
 * rounded `surface` sheet ([ContentSheet]) the unread messages, then the earlier ones, each group under its header and
 * each message as global search's row ([SearchMessageRow]), which opens its chat at it ([MentionsEvent.Open]). While
 * they load the sheet shows the delayed loading indicator; without any, an empty state; after a failure, its error
 * with "Try again".
 */
@Composable
internal fun MentionsScreen(
    state: MentionsUiState,
    onEvent: (MentionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // The sheet paints the top bar's container around itself (ContentSheet), so nothing lies under it; the content
        // keeps the color it had on that container.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = { MentionsTopBar(onBack) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            val body = Modifier.fillMaxSize().padding(contentPadding)
            when (state) {
                MentionsUiState.Loading -> MentionsLoading(body)
                MentionsUiState.Empty -> NoMentions(body)
                is MentionsUiState.Error -> MentionsError(state, onRetry = { onEvent(MentionsEvent.Retry) }, body)
                is MentionsUiState.Content -> MentionsList(state, onEvent, Modifier.fillMaxSize(), contentPadding)
            }
        }
    }
}

/** The small top bar: back and "Mentions & replies" in `titleLargeEmphasized`, on the page's `surfaceContainer`. */
@Composable
private fun MentionsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.mentions_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.mentions_back),
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
 * The unread messages, then the earlier ones, in one `LazyColumn`, every entry keyed by its chat and message and
 * typed, at most `contentMaxWidth` wide, moving on the motion scheme's springs as a refresh changes them.
 */
@Composable
private fun MentionsList(
    content: MentionsUiState.Content,
    onEvent: (MentionsEvent) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
) {
    val maxWidth = MaterialTheme.sizes.contentMaxWidth
    val margin = MaterialTheme.spacing.windowMarginCompact
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val style = RowStyle(maxWidth, margin, motionScheme)
        group(R.string.mentions_unread, UNREAD, content.unread, onEvent, style)
        group(R.string.mentions_earlier, EARLIER, content.earlier, onEvent, style)
    }
}

/** A group of [items] under its header [title], left out while empty, each entry placed as [style] says. */
private fun LazyListScope.group(
    @StringRes title: Int,
    name: String,
    items: ImmutableList<SearchMessageItem>,
    onEvent: (MentionsEvent) -> Unit,
    style: RowStyle,
) {
    if (items.isEmpty()) return
    item(key = "$name:$HEADER", contentType = HEADER) {
        SectionHeader(stringResource(title), Modifier.row(this, style))
    }
    itemsIndexed(
        items = items,
        key = { _, item -> "$name:${item.chatId}:${item.messageId}" },
        contentType = { _, _ -> MESSAGE_ROW },
    ) { index, item ->
        SearchMessageRow(
            item = item,
            highlight = MatchHighlight.None,
            shapes = ListItemDefaults.segmentedShapes(index, items.size),
            onClick = { onEvent(MentionsEvent.Open(item.chatId, item.messageId)) },
            modifier = Modifier.row(this, style),
        )
    }
}

/** How the list's entries sit: at most [maxWidth] wide, [margin] in from the sheet's sides, on [motionScheme]. */
private class RowStyle(
    val maxWidth: Dp,
    val margin: Dp,
    val motionScheme: MotionScheme,
)

/**
 * An entry of [scope] at most [RowStyle.maxWidth] wide, [RowStyle.margin] in from the sheet's sides as global
 * search's are, moving on the motion scheme's springs as the list changes.
 */
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

/** The delayed loading indicator in the middle of the sheet, named for screen readers. */
@Composable
private fun MentionsLoading(modifier: Modifier) {
    val description = stringResource(R.string.mentions_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** Nothing mentions the user or replies to them: an empty state on `MaterialShapes.Sunny`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoMentions(modifier: Modifier) {
    EmptyState(
        illustration = MaterialShapes.Sunny,
        icon = UiR.drawable.ic_alternate_email,
        title = stringResource(R.string.mentions_empty_title),
        body = stringResource(R.string.mentions_empty_body),
        modifier = modifier,
    )
}

/** Reading them failed: the error on `MaterialShapes.Cookie4Sided`, with "Try again" ([onRetry]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MentionsError(
    state: MentionsUiState.Error,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.mentions_error_title),
        body = errorText(state.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.mentions_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

// Keys and content types of the list's entries; a message's key is its group's, its chat's and its own id.
private const val UNREAD = "unread"
private const val EARLIER = "earlier"
private const val HEADER = "header"
private const val MESSAGE_ROW = "message"

@PingogramPreviews
@Composable
private fun MentionsScreenPreview(
    @PreviewParameter(MentionsPreviewStates::class) state: MentionsUiState,
) {
    PingogramPreview { MentionsScreen(state = state, onEvent = {}, onBack = {}) }
}

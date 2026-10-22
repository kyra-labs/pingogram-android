package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatTopBar
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.TopicsPreviewStates
import dev.kyralabs.pingogram.feature.chat.ui.senderPreview
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The topic list of a forum (`REQUIREMENTS.md` FR-C9, `docs/DESIGN_SYSTEM.md` §6 "Forum topics"): the conversation's
 * top bar with the forum ([ChatTopBar]) on `surfaceContainer`, the topics on a rounded `surface` sheet below
 * ([ContentSheet]), rows inset like the chat list's ([TopicRowItem]).
 *
 * - **States**: a loading indicator once loading takes a moment ([DelayedLoadingIndicator]), an empty state, an error
 *   with "Try again" ([ForumTopicsEvent.Retry]).
 * - **Pages**: the list reports when its last topic comes near ([ForumTopicsEvent.NearEndChanged]); below the last
 *   topic, a loading indicator while the next page loads, or its error with "Try again".
 * - A tap opens the topic's conversation through [onOpenTopic]; [onBack] is the top bar's back button, none beside the
 *   chat list.
 */
@Composable
internal fun ForumTopicsScreen(
    state: ForumTopicsUiState,
    onEvent: (ForumTopicsEvent) -> Unit,
    onOpenTopic: (topicId: Int) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { ChatTopBar(header = state.header, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            TopicsBody(state.content, onEvent, onOpenTopic, listState, contentPadding)
        }
    }
}

/** What the sheet shows for [content]: progress, an empty or error state, or the topics. */
@Composable
private fun TopicsBody(
    content: TopicsContent,
    onEvent: (ForumTopicsEvent) -> Unit,
    onOpenTopic: (topicId: Int) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val bodyModifier = Modifier.fillMaxSize().padding(contentPadding)
    when (content) {
        TopicsContent.Loading -> {
            Box(bodyModifier, contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        TopicsContent.Empty -> {
            TopicsEmpty(bodyModifier)
        }

        is TopicsContent.Error -> {
            TopicsError(content, onRetry = { onEvent(ForumTopicsEvent.Retry) }, modifier = bodyModifier)
        }

        is TopicsContent.Content -> {
            TopicList(content, onEvent, onOpenTopic, listState, contentPadding)
        }
    }
}

/** The topics, keyed by id, then the state of the next page; reports when the last topic comes near. */
@Composable
private fun TopicList(
    content: TopicsContent.Content,
    onEvent: (ForumTopicsEvent) -> Unit,
    onOpenTopic: (topicId: Int) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        items(content.topics, key = { it.id }, contentType = { CONTENT_TYPE_TOPIC }) { topic ->
            TopicRowItem(
                topic = topic,
                onClick = { onOpenTopic(topic.id) },
                modifier = Modifier.animateItem(placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
            )
        }
        if (content.isLoadingMore || content.moreError != null) {
            item(key = KEY_MORE, contentType = KEY_MORE) {
                MoreTopics(content, onRetry = { onEvent(ForumTopicsEvent.Retry) })
            }
        }
    }
    ReportNearEnd(listState, onEvent)
}

/** The next page loading, or its error with a retry, below the last topic. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreTopics(
    content: TopicsContent.Content,
    onRetry: () -> Unit,
) {
    val error = content.moreError
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
        if (error == null) {
            LoadingIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.topics_more_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.chat_retry)) }
            }
        }
    }
}

/** No topics yet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopicsEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_mode_comment_filled,
        title = stringResource(R.string.topics_empty_title),
        body = stringResource(R.string.topics_empty_body),
        modifier = modifier,
    )
}

/** The first page failed: the error with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopicsError(
    content: TopicsContent.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.topics_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** Reports whether the last topics are on screen ([ForumTopicsEvent.NearEndChanged]), read from the list's layout. */
@Composable
private fun ReportNearEnd(
    listState: LazyListState,
    onEvent: (ForumTopicsEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        try {
            snapshotFlow {
                val layout = listState.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
            }.distinctUntilChanged()
                .collect { currentOnEvent(ForumTopicsEvent.NearEndChanged(it)) }
        } finally {
            currentOnEvent(ForumTopicsEvent.NearEndChanged(false))
        }
    }
}

/** How close to the last topic the next page starts loading: about a screen. */
private const val LOAD_MORE_THRESHOLD = 8

private const val CONTENT_TYPE_TOPIC = "topic"
private const val KEY_MORE = "more"

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ForumTopicsScreenPreview(
    @PreviewParameter(TopicsPreviewStates::class) state: ForumTopicsUiState,
) {
    PingogramPreview { ForumTopicsScreen(state = state, onEvent = {}, onOpenTopic = {}, onBack = {}) }
}

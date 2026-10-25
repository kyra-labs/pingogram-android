package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BubbleResources
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.DateSeparatorRow
import dev.kyralabs.pingogram.feature.chat.ui.MessageBubbleRow
import dev.kyralabs.pingogram.feature.chat.ui.MessageShortcuts
import dev.kyralabs.pingogram.feature.chat.ui.ServiceMessageRow
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.rememberComposerFocusState
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.contentType
import dev.kyralabs.pingogram.feature.chat.ui.entry
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelEvent
import dev.kyralabs.pingogram.feature.chat.ui.panel.EmojiPanelUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.ScheduledPreviewStates
import dev.kyralabs.pingogram.feature.chat.ui.rememberBubbleResources
import dev.kyralabs.pingogram.feature.chat.ui.rememberMessageJumpState
import dev.kyralabs.pingogram.feature.chat.ui.topSpacing
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The scheduled messages of a chat (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6 "Scheduled messages"), pushed
 * above the conversation: a small top bar with back ([onBack]) and "Scheduled messages" in `titleLargeEmphasized` on
 * `surfaceContainer`, and below it on a rounded `surface` sheet ([ContentSheet]) the messages as the conversation's
 * bubbles ([MessageBubbleRow]), laid out as the conversation is: a reverse-layout `LazyColumn`, the latest send time at
 * the bottom, each day under its date pill ("Today", "Tomorrow"), those sent when the other user comes online last,
 * each bubble with its send time and the schedule-send symbol in its meta. Every entry is keyed and typed by its kind,
 * and animates to its place on the motion scheme's springs as messages are sent, rescheduled or deleted; items run no
 * effects (`docs/PERFORMANCE.md` §4).
 *
 * - **Actions**: a tap or a long press on a bubble (and screen readers' actions) opens its actions sheet
 *   ([ScheduledActionsBottomSheet], shown by the route): send now, reschedule, edit, copy, delete.
 * - **Edit**: the conversation's composer ([ChatBottomBar]) in edit mode at the bottom, its bar showing the message,
 *   its check saving the edit ([ScheduledEvent.SaveEdit]), its close button ending it; [composerText] is the field's
 *   text, [composerFocus] its focus, the emoji panel ([emojiPanel], [onEmojiPanelEvent]) offers emoji only while
 *   editing. Nothing new is sent from here.
 * - **States**: a loading indicator once loading takes a moment ([DelayedLoadingIndicator]), an empty state once none
 *   is left, an error with "Try again" ([ScheduledEvent.Retry]).
 * - Snackbars show in [snackbarHostState], above the composer.
 */
@Suppress("LongParameterList") // The state, back, the list and snackbars, the composer's text, focus and panel.
@Composable
internal fun ScheduledMessagesScreen(
    state: ScheduledMessagesUiState,
    onEvent: (ScheduledEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberScrollingListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    composerText: ComposerText = remember { ComposerText() },
    composerFocus: ComposerFocusState = rememberComposerFocusState(),
    emojiPanel: EmojiPanelUiState = EmojiPanelUiState(),
    onEmojiPanelEvent: (EmojiPanelEvent) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        // The top bar's container; the messages sit on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { ScheduledTopBar(onBack) },
        bottomBar = {
            ChatBottomBar(
                composer = state.composer,
                text = composerText,
                onSend = { _: SendOptions -> onEvent(ScheduledEvent.SaveEdit) },
                onFocusChanged = {},
                focus = composerFocus,
                onCloseBar = { onEvent(ScheduledEvent.CancelEdit) },
                panel = emojiPanel,
                onPanelEvent = onEmojiPanelEvent,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            ScheduledBody(state, onEvent, listState, contentPadding)
        }
    }
}

/** The small top bar: back and "Scheduled messages" in `titleLargeEmphasized`, on the screen's `surfaceContainer`. */
@Composable
private fun ScheduledTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.scheduled_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scheduled_back),
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

/** What the sheet shows for [state]'s content: progress, the empty or error state, or the messages. */
@Composable
private fun ScheduledBody(
    state: ScheduledMessagesUiState,
    onEvent: (ScheduledEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val bodyModifier = Modifier.fillMaxSize().padding(contentPadding)
    when (val content = state.content) {
        ScheduledContent.Loading -> {
            Box(bodyModifier, contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
        }

        ScheduledContent.Empty -> {
            ScheduledEmpty(bodyModifier)
        }

        is ScheduledContent.Error -> {
            ScheduledError(content, onRetry = { onEvent(ScheduledEvent.Retry) }, modifier = bodyModifier)
        }

        is ScheduledContent.Content -> {
            ScheduledList(content, state.canCopy, onEvent, listState, contentPadding)
        }
    }
}

/**
 * The messages of [content], latest send time first in a reverse layout, as the conversation lays them out: the
 * conversation's bubbles, which the bubbles' own events reach as scheduled-message events ([toScheduledEvent]).
 */
@Composable
private fun ScheduledList(
    content: ScheduledContent.Content,
    canCopy: Boolean,
    onEvent: (ScheduledEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val spacing = MaterialTheme.spacing
    val motionScheme = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val onBubbleEvent: (ChatEvent) -> Unit = remember { { event -> event.toScheduledEvent()?.let(currentOnEvent) } }
    // No replies from here; copying where the chat lets its content be saved.
    val resources: BubbleResources =
        rememberBubbleResources(rememberMessageJumpState(), remember(canCopy) { MessageShortcuts(canCopy = canCopy) })
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + spacing.medium,
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection) + spacing.medium,
            bottom = contentPadding.calculateBottomPadding() + spacing.small,
        )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = listPadding,
        reverseLayout = true,
    ) {
        items(content.items, key = { it.key }, contentType = { it.contentType }) { item ->
            val itemModifier = Modifier.entry(this, motionScheme, item.topSpacing(spacing.small, spacing.extraSmall))
            when (item) {
                is ChatItem.Bubble -> {
                    MessageBubbleRow(
                        bubble = item,
                        fileState = null,
                        playback = Playback.Stopped,
                        resources = resources,
                        onEvent = onBubbleEvent,
                        modifier = itemModifier,
                    )
                }

                is ChatItem.DateSeparator -> {
                    DateSeparatorRow(item, itemModifier)
                }

                is ChatItem.Service -> {
                    ServiceMessageRow(item, itemModifier, canCallBack = false)
                }

                is ChatItem.UnreadDivider, is ChatItem.Sponsored -> {
                    // Never among scheduled messages.
                }
            }
        }
    }
}

/**
 * The bubbles' events as the list of scheduled messages takes them: a long press, a tap and screen readers' actions
 * open the message's actions; tapped spoilers show; a screen reader's "Copy" copies. The rest (playing, opening media,
 * replying) is not offered here.
 */
private fun ChatEvent.toScheduledEvent(): ScheduledEvent? =
    when (this) {
        is ChatEvent.ShowMessageActions -> ScheduledEvent.ShowActions(messageId)
        is ChatEvent.ToggleTime -> ScheduledEvent.ShowActions(messageId)
        is ChatEvent.RevealSpoilers -> ScheduledEvent.RevealSpoilers(messageId)
        is ChatEvent.Copy -> ScheduledEvent.Act(messageId, ScheduledAction.Copy)
        else -> null
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduledEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = R.drawable.ic_schedule_send,
        title = stringResource(R.string.scheduled_empty_title),
        body = stringResource(R.string.scheduled_empty_body),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduledError(
    content: ScheduledContent.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.scheduled_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scheduled_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun ScheduledMessagesScreenPreview(
    @PreviewParameter(ScheduledPreviewStates::class) state: ScheduledMessagesUiState,
) {
    PingogramPreview { PreviewImages { ScheduledMessagesScreen(state = state, onEvent = {}, onBack = {}) } }
}

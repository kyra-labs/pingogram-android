package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.MessageShortcuts
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import kotlinx.collections.immutable.toImmutableList

/**
 * The share button beside channel posts for previews and screenshot tests (`docs/DESIGN_SYSTEM.md` §6 "Share
 * button"): the channel with a one-line post on top of its posts, each with its share button.
 */
internal object ShareButtonPreviewData {
    val channel: ChatUiState
        get() {
            val channel = ChatPreviewData.channel
            val posts = channel.content as ChatContent.Content
            val newest =
                bubble(
                    NEWEST_POST,
                    "New icons are out.",
                    time = "8:30 AM",
                    sender = UiText.Plain("Material Design"),
                    senderId = CHANNEL_ID,
                )
            val items =
                (listOf(newest) + posts.items).map { item ->
                    if (item is ChatItem.Bubble) item.copy(hasShareButton = true) else item
                }
            return channel.copy(
                content = posts.copy(items = items.toImmutableList()),
                shortcuts = MessageShortcuts(canForward = true),
            )
        }

    /** The one-line post's message id. */
    const val NEWEST_POST = 33L

    private const val CHANNEL_ID = -200L
}

package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ThreadLink
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import kotlinx.collections.immutable.toImmutableList

/**
 * Comment threads for previews and screenshot tests (`REQUIREMENTS.md` FR-C9): the channel with its posts' links to
 * their comments ("12 comments", "Leave a comment"), and the conversation of the first post's comments, which starts
 * with the post (its head) as the discussion group has it.
 */
internal object ThreadPreviewData {
    /** The channel, its newest post with 12 comments, the one before without any yet. */
    val channelWithComments: ChatUiState
        get() {
            val channel = ChatPreviewData.channel
            val posts = channel.content as ChatContent.Content
            val items =
                posts.items.map { item ->
                    if (item is ChatItem.Bubble) {
                        val count = if (item.id == NEWEST_POST) COMMENTS else 0
                        item.copy(thread = ThreadLink(item.id, count, isComments = true))
                    } else {
                        item
                    }
                }
            return channel.copy(content = posts.copy(items = items.toImmutableList()))
        }

    /** The comments of the channel's newest post: the post first, then three comments, the last the user's. */
    val comments: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header =
                    ChatHeader(
                        chatId = DISCUSSION,
                        title = UiText.Plain("Material Design Chat"),
                        photo = null,
                        placeholderIcon = null,
                        subtitle = ChatSubtitle.Status(UiText.plural(R.plurals.thread_comments, COMMENTS, COMMENTS)),
                    ),
                content =
                    content(
                        bubble(
                            HEAD + 3,
                            "Can't wait to try it in our app.",
                            time = "8:12 AM",
                            isOutgoing = true,
                            status = OutgoingStatus.Read,
                        ),
                        comment(
                            HEAD + 2,
                            "The new shape morphs look great",
                            "Grace Hopper",
                            GRACE,
                            GroupPosition.Last,
                        ),
                        comment(
                            HEAD + 1,
                            "Is the loading indicator in the stable release?",
                            "Grace Hopper",
                            GRACE,
                            GroupPosition.First,
                        ),
                        bubble(
                            HEAD,
                            "Material 3 Expressive brings new shapes, motion and type to every component.",
                            time = "8:00 AM",
                            sender = UiText.Plain("Material Design"),
                            senderId = CHANNEL,
                            groupChat = true,
                            forwardedFrom =
                                UiText.resource(
                                    R.string.chat_forwarded_from,
                                    UiText.Plain("Material Design").bidiIsolated(),
                                ),
                        ),
                        ChatItem.DateSeparator(epochDay = 2, date = today),
                    ),
            )

    private fun comment(
        id: Long,
        text: String,
        name: String,
        senderId: Long,
        position: GroupPosition,
    ) = bubble(
        id,
        text,
        time = "8:10 AM",
        sender = UiText.Plain(name),
        senderId = senderId,
        groupChat = true,
        position = position,
    )

    private const val NEWEST_POST = 32L
    private const val COMMENTS = 12
    private const val DISCUSSION = -400L
    private const val CHANNEL = -200L
    private const val GRACE = 3L
    private const val HEAD = 900L
}

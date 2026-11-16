package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.content.PaidPostSamples
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Paid posts for previews and screenshot tests (`REQUIREMENTS.md` FR-C4, the owner's board 2): a channel's conversation
 * with a post paid for (its album shown, "Bought · 25 ★") over one not paid for (blurred, the price and the note), and
 * a message this version cannot show, with and without "Update Pingogram".
 */
internal object PaidMediaPreviewData {
    private val header =
        ChatHeader(
            chatId = PaidPostSamples.CHANNEL,
            title = UiText.Plain("Lens & Light"),
            photo = null,
            placeholderIcon = null,
            subtitle =
                ChatSubtitle.Status(
                    UiText.plural(R.plurals.chat_subtitle_subscribers, SUBSCRIBERS, SUBSCRIBERS),
                ),
        )

    /** The post paid for over the locked one. */
    val posts: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        post(
                            3,
                            PaidPostSamples.paidMedia(
                                unlocked = true,
                                caption = "Monsoon in Coorg. Thanks for supporting the channel!",
                            ),
                            "Yesterday",
                        ),
                        post(2, PaidPostSamples.paidMedia(unlocked = false), "9:20 AM"),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** A message this version cannot show, with "Update Pingogram" ([showsUpdateLink]) or without it. */
    fun unsupported(showsUpdateLink: Boolean): ChatUiState =
        ChatUiState(
            composer = ChatPreviewData.canWrite,
            header = header,
            showsUpdateLink = showsUpdateLink,
            content =
                content(
                    bubble(
                        id = 4,
                        text = "",
                        time = "11:02 AM",
                        sender = UiText.Plain("Lens & Light"),
                        senderId = PaidPostSamples.CHANNEL,
                        position = GroupPosition.Single,
                        content = MessageContent.Unsupported,
                        contentLabel = UiText.resource(UiR.string.preview_unsupported),
                    ).copy(isChannelPost = true),
                    ChatItem.DateSeparator(epochDay = 1, date = today),
                ),
        )

    private fun post(
        id: Long,
        content: MessageContent.PaidMedia,
        time: String,
    ): ChatItem.Bubble =
        bubble(
            id = id,
            text = content.caption.text,
            time = time,
            sender = UiText.Plain("Lens & Light"),
            senderId = PaidPostSamples.CHANNEL,
            position = GroupPosition.Single,
            content = content,
        ).copy(isChannelPost = true, views = VIEWS)

    private const val SUBSCRIBERS = 12_480
    private const val VIEWS = 4_100
}

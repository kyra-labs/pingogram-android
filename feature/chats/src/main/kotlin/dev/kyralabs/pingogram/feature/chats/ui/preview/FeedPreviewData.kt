package dev.kyralabs.pingogram.feature.chats.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedPost
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedReaction
import kotlinx.collections.immutable.persistentListOf

/** Channel feed posts for previews and tests, unread and read. */
internal object FeedPreviewData {
    private fun formatted(value: String) = FormattedText(value, persistentListOf())

    private fun post(
        chatId: Long,
        id: Long,
        title: String,
        isUnread: Boolean,
        text: String? = null,
    ) = FeedPost(
        chatId = chatId,
        messageIds = persistentListOf(id),
        channelTitle = title,
        channelPhoto = null,
        time = "9:41 AM",
        isUnread = isUnread,
        text = text?.let(::formatted),
        media = null,
        reactions =
            persistentListOf(
                FeedReaction("👍", count = 128, isChosen = true),
                FeedReaction("🔥", count = 42, isChosen = false),
            ),
        viewCount = 3_400,
    )

    val text =
        post(
            1,
            10,
            "Kotlin Weekly",
            isUnread = true,
            text = "Coroutines 2.1 is out: structured concurrency for everyone.",
        )
    val read =
        post(
            4,
            40,
            "Cooking Club",
            isUnread = false,
            text = "Bread in ten minutes, and the oven does the rest.",
        )

    val posts = persistentListOf(text, read)
}

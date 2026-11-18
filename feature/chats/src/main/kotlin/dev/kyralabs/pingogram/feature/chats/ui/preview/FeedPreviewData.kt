package dev.kyralabs.pingogram.feature.chats.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedAd
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedMedia
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedPollOption
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedPost
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedReaction
import kotlinx.collections.immutable.persistentListOf

/** Channel feed posts for previews and tests: one of each kind of content, unread and read. */
internal object FeedPreviewData {
    private fun formatted(value: String) = FormattedText(value, persistentListOf())

    private fun post(
        chatId: Long,
        id: Long,
        title: String,
        isUnread: Boolean,
        text: String? = null,
        media: FeedMedia? = null,
    ) = FeedPost(
        chatId = chatId,
        messageIds = persistentListOf(id),
        channelTitle = title,
        channelPhoto = null,
        time = "9:41 AM",
        isUnread = isUnread,
        text = text?.let(::formatted),
        media = media,
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
    val photo =
        post(
            2,
            20,
            "Astronomy Daily",
            isUnread = true,
            "The Pillars of Creation, again.",
            FeedMedia.Photo(1, 1280, 720),
        )
    val album =
        post(3, 30, "Travel Notes", isUnread = true, media = FeedMedia.Album(persistentListOf(1, 2, 3, 4), more = 3))
    val video = post(4, 40, "Cooking Club", isUnread = false, "Bread in ten minutes.", FeedMedia.Video(null, 151))
    val file =
        post(
            5,
            50,
            "Design Library",
            isUnread = false,
            media = FeedMedia.File("Expressive-components-2026-final-v3.pdf", 38_200_000),
        )
    val poll =
        post(
            6,
            60,
            "Weekend Plans",
            isUnread = false,
            media =
                FeedMedia.Poll(
                    question = formatted("Where to this weekend?"),
                    options =
                        persistentListOf(
                            FeedPollOption(formatted("The mountains"), 64, isChosen = true),
                            FeedPollOption(formatted("The sea"), 36, isChosen = false),
                        ),
                    voterCount = 1_284,
                    isAnonymous = true,
                ),
        )

    /** Kotlin Weekly's sponsored message, as a text with a button. */
    val ad =
        FeedAd(
            chatId = 1,
            id = 1L shl 30,
            isRecommended = false,
            title = "Trip Planner",
            text = formatted("Plan trips with friends, share the costs and keep every booking in one place."),
            media = null,
            sponsorPhoto = null,
            buttonText = "Open Trip Planner",
        )

    val posts = persistentListOf(text, photo, album, video, file, poll)
}

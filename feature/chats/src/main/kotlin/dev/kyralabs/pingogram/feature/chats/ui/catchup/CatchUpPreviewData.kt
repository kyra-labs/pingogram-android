package dev.kyralabs.pingogram.feature.chats.ui.catchup

import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf

/** Catch-up's states for previews and screenshot tests: a group's card with a mention, "All caught up". */
internal object CatchUpPreviewData {
    val trip =
        CatchUpCard(
            chatId = 101,
            title = "Trip — Coorg 2026",
            photo = null,
            avatarShape = AvatarShape.Group,
            unreadCount = 9,
            mentionCount = 1,
            since = "9:02 AM",
            messages =
                persistentListOf(
                    message(1, "Meera", "Okay final plan: Saturday, 6:00 AM from Yeshwantpur", "9:02 AM"),
                    message(2, "Rahul", "I’ll bring the speaker 🔊", "9:05 AM"),
                    message(3, "Aisha", "Can someone book the homestay? The good one fills up fast", "9:11 AM"),
                    message(4, "Meera", "@you can you check the price for 4 people?", "9:14 AM"),
                ),
            earlierCount = 5,
            replyTo = message(4, "Meera", "@you can you check the price for 4 people?", "9:14 AM"),
        )

    private val beta =
        CatchUpCard(
            chatId = 102,
            title = "Pingogram Beta",
            photo = null,
            avatarShape = AvatarShape.Group,
            unreadCount = 14,
            mentionCount = 0,
            since = "8:12 AM",
        )

    val cards =
        CatchUpUiState(
            stage = CatchUpStage.Cards(position = 3, total = 12, minutesLeft = 4, current = trip, next = beta),
        )

    /** The card with the reply field open, answering the mention. */
    val replying =
        CatchUpUiState(
            stage =
                CatchUpStage.Cards(
                    position = 3,
                    total = 12,
                    minutesLeft = 4,
                    current = trip,
                    next = beta,
                    reply = CatchUpReplyState(),
                ),
        )

    val done =
        CatchUpUiState(stage = CatchUpStage.Done(chats = 12, minutes = 4, read = 9, replied = 2, skipped = 1))

    private fun message(
        id: Long,
        sender: String,
        text: String,
        time: String,
    ) = CatchUpMessage(id, UiText.Plain(sender), UiText.Plain(text), time, senderHue = sender.length)
}

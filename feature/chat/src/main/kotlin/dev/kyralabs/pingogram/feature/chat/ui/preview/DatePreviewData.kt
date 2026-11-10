package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.dates.DateTimeSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today

/**
 * Dates in messages for previews and screenshot tests (td_api.tl `textEntityTypeDateTime`): a private chat with dates
 * as the bubble mapping words them for the reader (the mapping is the view model's, so the texts are given worded),
 * and the sheet a tap on one opens.
 */
internal object DatePreviewData {
    private const val ADA = 2L

    /** Ada's invitation with a date and a relative one, and the user's answer. */
    val messages: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = ChatPreviewData.privateChat.header,
                content =
                    content(
                        dated(3, "Booked! See you Sunday, December 6, 2026, 6:00 PM.", isOutgoing = true),
                        dated(2, "The meetup starts in 3 days, at the café. Bring your laptop!", "in 3 days"),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** The sheet of the date of [messages]' answer. */
    val sheet =
        DateTimeSheet(
            unixTime = 1_796_580_000,
            text = "Sunday, December 6, 2026, 6:00 PM",
            zoneName = "Central European Time",
        )

    private fun dated(
        id: Long,
        text: String,
        date: String = "Sunday, December 6, 2026, 6:00 PM",
        isOutgoing: Boolean = false,
    ): ChatItem.Bubble =
        bubble(
            id = id,
            text = text,
            time = "10:4$id AM",
            isOutgoing = isOutgoing,
            sender = if (isOutgoing) UiText.Plain("You") else UiText.Plain("Ada"),
            senderId = if (isOutgoing) 1 else ADA,
            status = if (isOutgoing) OutgoingStatus.Read else null,
            entities =
                listOf(
                    TextEntity(text.indexOf(date), date.length, TextEntityType.DateTime(1_796_580_000, format = null)),
                ),
        )
}

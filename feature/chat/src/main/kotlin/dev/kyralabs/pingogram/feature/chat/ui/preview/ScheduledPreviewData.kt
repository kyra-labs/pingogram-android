package dev.kyralabs.pingogram.feature.chat.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.BubbleSchedule
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledAction
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledActionsSheet
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledBar
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledContent
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledDeleteDialog
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ScheduledMessagesUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Scheduled messages for previews and screenshot tests, in the private chat with Ada: three today (two of them a
 * group), one tomorrow that repeats daily, and one sent when Ada comes online; the bar over the conversation; the
 * actions sheet and the delete confirmation of one of them, and editing it.
 */
internal object ScheduledPreviewData {
    private val tomorrow = UiText.resource(R.string.chat_date_tomorrow)
    private val ada = UiText.Plain("Ada Lovelace").bidiIsolated()
    private val whenOnline = UiText.resource(R.string.scheduled_when_user_online, ada)

    val bar =
        ScheduledBar(
            count = 3,
            next = UiText.resource(R.string.scheduled_bar_next, dayTime(ChatPreviewData.today, "6:30 PM")),
        )

    val barOne = ScheduledBar(count = 1, next = UiText.resource(R.string.scheduled_when_online))

    /** Newest send time first, as the list's reverse layout takes them. */
    private val items: List<ChatItem> =
        listOf(
            scheduled(
                15,
                "Happy birthday! 🎂",
                "",
                BubbleSchedule.WhenOnline(UiText.Plain("Ada Lovelace")),
                whenOnline,
            ),
            ChatItem.DateSeparator(Long.MAX_VALUE, whenOnline),
            scheduled(14, "Daily stand-up in 10 minutes", "8:50 AM", BubbleSchedule.At(DAY), tomorrow),
            ChatItem.DateSeparator(TOMORROW, tomorrow),
            scheduled(
                13,
                "And bring the slides, please.",
                "6:31 PM",
                BubbleSchedule.At(),
                ChatPreviewData.today,
                GroupPosition.Last,
            ),
            scheduled(
                12,
                "Don't forget the meetup at 7!",
                "6:30 PM",
                BubbleSchedule.At(),
                ChatPreviewData.today,
                GroupPosition.First,
            ),
            scheduled(
                11,
                "Reminder: the tickets go on sale at noon",
                "11:45 AM",
                BubbleSchedule.At(),
                ChatPreviewData.today,
            ),
            ChatItem.DateSeparator(TODAY, ChatPreviewData.today),
        )

    val content = ScheduledMessagesUiState(content = ScheduledContent.Content(items.toImmutableList()), canCopy = true)

    /** A single message, which repeats daily. */
    val one =
        ScheduledMessagesUiState(
            content =
                ScheduledContent.Content(
                    persistentListOf(
                        scheduled(14, "Daily stand-up in 10 minutes", "8:50 AM", BubbleSchedule.At(DAY), tomorrow),
                        ChatItem.DateSeparator(TOMORROW, tomorrow),
                    ),
                ),
            canCopy = true,
        )

    val loading = ScheduledMessagesUiState()

    val empty = ScheduledMessagesUiState(content = ScheduledContent.Empty)

    val error = ScheduledMessagesUiState(content = ScheduledContent.Error(DomainError.Network))

    val actions =
        ScheduledActionsSheet(
            messageId = 14,
            sendsAt = UiText.resource(R.string.scheduled_sends_at, dayTime(tomorrow, "8:50 AM")),
            actions = ScheduledAction.entries.toImmutableList(),
            repeatPeriod = DAY,
        )

    val withActions = content.copy(actions = actions)

    val deleting = content.copy(deleteDialog = ScheduledDeleteDialog(14))

    /** Editing message 12, whose text fills the composer. */
    val editing =
        content.copy(
            composer =
                ComposerUiState.Available(
                    canSchedule = false,
                    bar = ComposerBar.Edit(12, UiText.Plain(EDITED_TEXT), canBeEmpty = false),
                ),
        )

    /** The private conversation with [bar] over its messages. */
    val conversation: ChatUiState get() = ChatPreviewData.privateChat.copy(scheduledBar = bar)

    const val EDITED_TEXT = "Don't forget the meetup at 7!"

    private const val DAY = 86_400
    private const val TODAY = 20_833L
    private const val TOMORROW = TODAY + 1

    private fun dayTime(
        day: UiText,
        time: String,
    ) = UiText.resource(R.string.scheduled_day_time, day, time)

    @Suppress("LongParameterList") // What a scheduled bubble shows, the place in its group defaulted.
    private fun scheduled(
        id: Long,
        text: String,
        time: String,
        schedule: BubbleSchedule,
        date: UiText,
        position: GroupPosition = GroupPosition.Single,
    ): ChatItem.Bubble =
        ChatPreviewData
            .bubble(id, text, time = time, isOutgoing = true, position = position, date = date)
            .copy(showsTime = true, schedule = schedule)
}

/** Every state of the list of scheduled messages, for its previews. */
internal class ScheduledPreviewStates : PreviewParameterProvider<ScheduledMessagesUiState> {
    override val values: Sequence<ScheduledMessagesUiState> =
        with(ScheduledPreviewData) { sequenceOf(content, one, editing, loading, empty, error) }
}

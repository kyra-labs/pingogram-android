package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.GROUP_GAP_SECONDS
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.ItemInputs
import dev.kyralabs.pingogram.feature.chat.ui.ItemSources
import dev.kyralabs.pingogram.feature.chat.ui.ItemUnit
import dev.kyralabs.pingogram.feature.chat.ui.MessageState
import dev.kyralabs.pingogram.feature.chat.ui.NO_UNREAD_DIVIDER
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.ReplyState
import dev.kyralabs.pingogram.feature.chat.ui.fullNameOf
import dev.kyralabs.pingogram.feature.chat.ui.inputsOf
import dev.kyralabs.pingogram.feature.chat.ui.itemUnits
import dev.kyralabs.pingogram.feature.chat.ui.toItem
import dev.kyralabs.pingogram.feature.chat.ui.userId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

/**
 * What the items of the list of scheduled messages are made from: the [chat], its [messages] (earliest send time
 * first, as the repository gives them), the messages they reply to that were fetched ([replies], by the replying
 * message's id), those whose spoilers were revealed, who they name, and the day and formatter of the times.
 */
@Suppress("LongParameterList") // The chat, its messages, what they refer to and were tapped, and the times.
internal class ScheduledSources(
    val chat: Chat,
    val messages: List<ScheduledMessage>,
    val replies: Map<Long, ReplyState>,
    val revealedSpoilers: Set<Long>,
    val people: ChatPeople,
    val today: LocalDate,
    val timeFormatter: ChatTimeFormatter,
)

/**
 * Maps scheduled messages to the conversation's [ChatItem]s, latest send time first for the list's reverse layout
 * (`REQUIREMENTS.md` FR-C5): the bubbles the conversation would show for them ([inputsOf], [toItem]), grouped by sender
 * as there, under a date separator per send day ("Today", "Tomorrow", "Friday, October 30"), those that wait for the
 * other user to come online last, under "When Ada is online". Each bubble shows its send time in its meta with the
 * schedule's mark ([ChatItem.Bubble.schedule]); no read state, as nobody has them yet, but the "Not sent" label of one
 * that failed to reach Telegram.
 *
 * As [ChatItemMapper][dev.kyralabs.pingogram.feature.chat.ui.ChatItemMapper] does, a message is mapped again only when
 * what it shows changed. Not thread-safe: one flow collection uses it at a time.
 */
internal class ScheduledItemMapper {
    private var previous: Map<Any, MappedItem> = emptyMap()

    fun map(sources: ScheduledSources): ImmutableList<ChatItem> {
        if (sources.messages.isEmpty()) {
            previous = emptyMap()
            return persistentListOf()
        }
        // The conversation's mapping reads a message's date as its time: the send time here, none for "when online".
        val dated = sources.messages.asReversed().map { it.message.copy(date = it.schedule.sendDate ?: 0) }
        val schedules = sources.messages.associate { it.message.id to it.schedule }
        val units = itemUnits(dated)
        val sections = units.map { unit -> sectionOf(schedules.getValue(unit.newest.id), unit.newest, sources) }
        val itemSources = sources.toItemSources(dated)
        val labels = HashMap<Long, UiText>()
        val mapped = HashMap<Any, MappedItem>(units.size * 2)
        val items = ArrayList<ChatItem>(units.size + units.size / 2)
        for (index in units.indices) {
            val unit = units[index]
            val section = sections[index]
            val label = labels.getOrPut(section) { sources.labelOf(section) }
            val older = units.getOrNull(index + 1)
            val newer = units.getOrNull(index - 1)
            val position =
                positionOf(
                    joinsPrevious = older != null && sections[index + 1] == section && joins(older.newest, unit.oldest),
                    joinsNext = newer != null && sections[index - 1] == section && joins(unit.newest, newer.oldest),
                )
            val schedule = schedules.getValue(unit.newest.id).toBubbleSchedule(sources)
            val inputs = itemSources.inputsOf(unit, position, label)
            val item =
                previous[unit.key]?.takeIf { it.inputs == inputs && it.schedule == schedule }
                    ?: MappedItem(inputs, schedule, inputs.toItem().scheduled(unit, schedule, sources.timeFormatter))
            mapped[unit.key] = item
            items += item.item
            if (older == null || sections[index + 1] != section) items += ChatItem.DateSeparator(section, label)
        }
        previous = mapped
        return items.toImmutableList()
    }

    private class MappedItem(
        val inputs: ItemInputs,
        val schedule: BubbleSchedule,
        val item: ChatItem,
    )
}

/** The section of a message, which its date separator heads: the epoch day it goes out, or [WHEN_ONLINE]. */
private fun sectionOf(
    schedule: MessageSchedule,
    message: Message,
    sources: ScheduledSources,
): Long = if (schedule.sendDate == null) WHEN_ONLINE else sources.timeFormatter.day(message.date).toEpochDay()

/** The date separator's label of [section]: the day, or "When Ada is online". */
private fun ScheduledSources.labelOf(section: Long): UiText =
    if (section == WHEN_ONLINE) {
        otherUserName()?.let { UiText.resource(R.string.scheduled_when_user_online, it.bidiIsolated()) }
            ?: UiText.resource(R.string.scheduled_when_online)
    } else {
        timeFormatter.dateLabel(LocalDate.ofEpochDay(section), today)
    }

/** The other user of a private chat, by name; null for other chats, and while not known. */
private fun ScheduledSources.otherUserName(): UiText? = chat.userId?.let(people.users::get)?.let(::fullNameOf)

private fun MessageSchedule.toBubbleSchedule(sources: ScheduledSources): BubbleSchedule =
    when (this) {
        is MessageSchedule.At -> BubbleSchedule.At(repeatPeriod)
        is MessageSchedule.WhenVideoProcessed -> BubbleSchedule.At()
        MessageSchedule.WhenOnline -> BubbleSchedule.WhenOnline(sources.otherUserName())
    }

/**
 * This item as a scheduled message's: a bubble with its send time always shown, the schedule's mark, no read state
 * (a failure to reach Telegram stays, without a retry), and none of what only sent messages have: reactions, a thread,
 * a bot's buttons.
 */
private fun ChatItem.scheduled(
    unit: ItemUnit,
    schedule: BubbleSchedule,
    formatter: ChatTimeFormatter,
): ChatItem {
    if (this !is ChatItem.Bubble) return this
    val failed = unit.messages.any { it.sendingState is SendingState.Failed }
    return copy(
        time = if (schedule is BubbleSchedule.At) formatter.time(unit.newest.date) else "",
        showsTime = true,
        status = if (failed) OutgoingStatus.Failed else null,
        canResend = false,
        reactions = persistentListOf(),
        thread = null,
        keyboard = null,
        schedule = schedule,
    )
}

/**
 * Whether [newer] continues the group of [older], in the same section: both bubbles, from the same sender, going out
 * at most [GROUP_GAP_SECONDS] apart.
 */
private fun joins(
    older: Message,
    newer: Message,
): Boolean =
    older.content !is MessageContent.Service &&
        newer.content !is MessageContent.Service &&
        older.sender == newer.sender &&
        older.isOutgoing == newer.isOutgoing &&
        newer.date - older.date <= GROUP_GAP_SECONDS

private fun positionOf(
    joinsPrevious: Boolean,
    joinsNext: Boolean,
): GroupPosition =
    when {
        joinsPrevious && joinsNext -> GroupPosition.Middle
        joinsPrevious -> GroupPosition.Last
        joinsNext -> GroupPosition.First
        else -> GroupPosition.Single
    }

/** The conversation's item sources for [dated] (newest first), without an unread divider. */
private fun ScheduledSources.toItemSources(dated: List<Message>): ItemSources =
    ItemSources(
        chat = chat,
        messages =
            MessageState(
                history = ChatHistory(dated.toImmutableList(), reachedStart = true),
                replies = replies,
                revealedSpoilers = revealedSpoilers,
            ),
        people = people,
        unreadAfterId = NO_UNREAD_DIVIDER,
        today = today,
        timeFormatter = timeFormatter,
    )

/** When [this] sends, in Unix time; null for "when online", which has no time. */
internal val MessageSchedule.sendDate: Long?
    get() =
        when (this) {
            is MessageSchedule.At -> sendDate
            is MessageSchedule.WhenVideoProcessed -> sendDate
            MessageSchedule.WhenOnline -> null
        }

/** The section of the messages that go out when the other user comes online, after every day. */
private const val WHEN_ONLINE = Long.MAX_VALUE

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate

// `updatePoll` in the history windows ([HistoryChange.ReplacePoll]): the poll it carries replaces the poll of every
// message that shows it.

/** [this] with [poll] in place of the poll of the messages that show it; the same instance where none does. */
internal fun PersistentList<Message>.replacingPoll(poll: Poll): PersistentList<Message> {
    if (none { it.showsPoll(poll.id) }) return this
    return mutate { list -> list.replaceAll { it.withPoll(poll) } }
}

internal fun PersistentMap<Long, Message>.replacingPoll(poll: Poll): PersistentMap<Long, Message> {
    if (values.none { it.showsPoll(poll.id) }) return this
    return mutate { map -> map.replaceAll { _, message -> message.withPoll(poll) } }
}

private fun Message.showsPoll(pollId: Long): Boolean = (content as? MessageContent.Poll)?.poll?.id == pollId

/** [this] with [poll] as its poll if it shows that poll; the same instance otherwise. */
private fun Message.withPoll(poll: Poll): Message {
    val content = content as? MessageContent.Poll
    return if (content == null || content.poll.id != poll.id) this else copy(content = content.copy(poll = poll))
}

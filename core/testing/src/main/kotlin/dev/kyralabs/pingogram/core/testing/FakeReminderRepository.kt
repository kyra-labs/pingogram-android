package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** [ReminderRepository] in memory: [saved], the soonest first; [failure], where set, makes every change fail. */
class FakeReminderRepository : ReminderRepository {
    val saved = MutableStateFlow<List<MessageReminder>>(emptyList())

    var failure: Outcome.Failure? = null

    override val reminders: Flow<ImmutableList<MessageReminder>> =
        saved.map { list -> list.sortedBy { it.at }.toImmutableList() }.distinctUntilChanged()

    override fun reminders(chatId: Long): Flow<ImmutableMap<Long, MessageReminder>> =
        saved
            .map { list -> list.filter { it.chatId == chatId }.associateBy { it.messageId }.toImmutableMap() }
            .distinctUntilChanged()

    override suspend fun set(reminder: MessageReminder): Outcome<Unit> {
        failure?.let { return it }
        saved.update { list ->
            list.filterNot { it.chatId == reminder.chatId && it.messageId == reminder.messageId } + reminder
        }
        return Outcome.Success(Unit)
    }

    override suspend fun delete(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> {
        failure?.let { return it }
        saved.update { list -> list.filterNot { it.chatId == chatId && it.messageId == messageId } }
        return Outcome.Success(Unit)
    }
}

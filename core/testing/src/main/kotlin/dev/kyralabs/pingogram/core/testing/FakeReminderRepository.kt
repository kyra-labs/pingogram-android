package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [ReminderRepository] in memory: the [saved] reminders; writes fail with [failure] while it is set. */
class FakeReminderRepository : ReminderRepository {
    val saved = MutableStateFlow<List<Reminder>>(emptyList())
    var failure: DomainError? = null

    override val reminders: Flow<ImmutableList<Reminder>> =
        saved.map { all -> all.sortedBy { it.at }.toImmutableList() }.distinctUntilChanged()

    override fun reminder(
        chatId: Long,
        messageId: Long,
    ): Flow<Reminder?> =
        saved.map { all -> all.firstOrNull { it.chatId == chatId && it.messageId == messageId } }.distinctUntilChanged()

    override suspend fun save(reminder: Reminder): Outcome<Unit> =
        write {
            saved.value =
                saved.value.filterNot { it.chatId == reminder.chatId && it.messageId == reminder.messageId } + reminder
        }

    override suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> =
        write { saved.value = saved.value.filterNot { it.chatId == chatId && it.messageId == messageId } }

    override suspend fun setDone(
        chatId: Long,
        messageId: Long,
        done: Boolean,
    ): Outcome<Unit> =
        write {
            saved.value =
                saved.value.map { if (it.chatId == chatId && it.messageId == messageId) it.copy(done = done) else it }
        }

    private inline fun write(change: () -> Unit): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        change()
        return Outcome.Success(Unit)
    }
}

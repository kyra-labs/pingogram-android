package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.repository.BirthdayReminderRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [BirthdayReminderRepository] in memory: [failure] makes every change fail. */
class FakeBirthdayReminderRepository(
    initial: ImmutableList<BirthdayReminder> = persistentListOf(),
) : BirthdayReminderRepository {
    val all = MutableStateFlow(initial)

    /** What every change fails with; null takes it. */
    var failure: DomainError? = null

    override val reminders: Flow<ImmutableList<BirthdayReminder>> = all

    override suspend fun set(reminder: BirthdayReminder): Outcome<Unit> =
        change { list -> list.filterNot { it.userId == reminder.userId } + reminder }

    override suspend fun delete(userId: Long): Outcome<Unit> = change { list -> list.filterNot { it.userId == userId } }

    private fun change(edit: (List<BirthdayReminder>) -> List<BirthdayReminder>): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        all.value = edit(all.value).sortedBy { it.userId }.toImmutableList()
        return Outcome.Success(Unit)
    }
}

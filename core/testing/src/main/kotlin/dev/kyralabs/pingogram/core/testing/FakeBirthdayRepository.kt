package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.domain.repository.BirthdayRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [BirthdayRepository] in memory: tests set [announced] to play Telegram's update. */
class FakeBirthdayRepository(
    initial: ImmutableList<CloseBirthday> = persistentListOf(),
) : BirthdayRepository {
    val announced = MutableStateFlow(initial)

    override val closeBirthdays: StateFlow<ImmutableList<CloseBirthday>> = announced
}

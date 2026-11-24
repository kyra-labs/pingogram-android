package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** The contacts whose birthday is close (`REQUIREMENTS.md` FR-X18), as Telegram tells the account. */
interface BirthdayRepository {
    /** The contacts with a close birthday, in Telegram's order; empty until Telegram names any. */
    val closeBirthdays: StateFlow<ImmutableList<CloseBirthday>>
}

package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether Telegram tells the user when one of their contacts joins Telegram ("Ada joined Telegram", a service message
 * in a new chat with them). The setting is the account's, so the user's other apps follow it.
 */
interface ContactJoinedRepository {
    /** Whether those notices come, then every change while collected. */
    val notifies: Flow<Boolean>

    suspend fun setNotifies(notifies: Boolean): Outcome<Unit>
}

package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * "Notifications from all accounts" (Settings → Notifications, `docs/adr/0031-multiple-accounts.md`): whether the
 * accounts the screens do not show post their notifications too, as Telegram's apps offer. On by default; kept on this
 * device. Calls of every account ring either way.
 */
interface AccountNotificationsRepository {
    /** Whether every account's notifications show, then every change. A setting that cannot be read counts as on. */
    val fromAllAccounts: Flow<Boolean>

    /** Saves it; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setFromAllAccounts(enabled: Boolean): Outcome<Unit>
}

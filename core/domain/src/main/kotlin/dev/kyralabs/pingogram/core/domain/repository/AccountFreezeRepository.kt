package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether Telegram froze the account (`docs/audit/feature-gaps-telegram-2026.md`, "Safety"). TDLib announces it only
 * with `updateFreezeState`, at its start and as it changes, so this is a cache.
 */
interface AccountFreezeRepository {
    /** The account's freeze as TDLib last announced it; null while it is not frozen. */
    val freeze: StateFlow<AccountFreeze?>
}

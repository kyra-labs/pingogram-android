package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMuteChange
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationException
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettingsChange
import kotlinx.coroutines.flow.Flow

/**
 * The account's notification settings (`REQUIREMENTS.md` FR-S1, Notifications): the defaults of each kind of chat and
 * the chats that differ from them. They belong to the Telegram account, so they follow it to every device; TDLib keeps
 * them and reports their changes. TDLib's notification engine applies them (`NotificationRepository`).
 *
 * Every request fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time,
 * `NotFound` for a chat TDLib does not know, or another one when TDLib refuses it.
 */
interface NotificationSettingsRepository {
    /** The defaults of [scope] now. */
    suspend fun scopeSettings(scope: NotificationScope): Outcome<ScopeNotificationSettings>

    /**
     * Every change of a scope's defaults TDLib reports from now on, made here or on another device. No replay: collect
     * it before asking for [scopeSettings], so no change in between is missed.
     */
    val scopeChanges: Flow<ScopeNotificationSettingsChange>

    /**
     * Makes [settings] the defaults of [scope]. What Pingogram does not change (sounds, stories) stays as it is.
     * TDLib then reports the new defaults through [scopeChanges].
     */
    suspend fun setScopeSettings(
        scope: NotificationScope,
        settings: ScopeNotificationSettings,
    ): Outcome<Unit>

    /** The chats of [scope] whose notifications differ from its defaults, as TDLib lists them. */
    suspend fun exceptions(scope: NotificationScope): Outcome<List<NotificationException>>

    /** Every change of a chat's own notification settings TDLib reports from now on; no replay. */
    val chatMuteChanges: Flow<ChatMuteChange>

    /** Applies [change] to chat [chatId]; every other setting of the chat stays. Saved Messages refuses it. */
    suspend fun setChatMute(
        chatId: Long,
        change: MuteChange,
    ): Outcome<Unit>

    /**
     * Resets every notification setting of the account: the defaults of every scope, and every chat's own settings,
     * so no exception is left (`resetAllNotificationSettings`).
     */
    suspend fun resetAll(): Outcome<Unit>
}

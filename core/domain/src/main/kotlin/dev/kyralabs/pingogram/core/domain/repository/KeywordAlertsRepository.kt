package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import kotlinx.coroutines.flow.Flow

/**
 * Keyword alerts' settings (board 30, Settings → Notifications → Keyword alerts), kept on this phone across logouts and
 * never sent to Telegram. A change that cannot be saved fails with `DomainError.Unknown`.
 */
interface KeywordAlertsRepository {
    /** The settings, then every change. Never fails: settings that cannot be read are the defaults. */
    val settings: Flow<KeywordAlertSettings>

    /**
     * Adds [word] (trimmed) at the end. Nothing changes for a blank word, one already kept (in any case, with or
     * without accents) or once there are [KeywordAlertSettings.MAX_WORDS]; a word longer than
     * [KeywordAlertSettings.MAX_WORD_LENGTH] is cut there.
     */
    suspend fun addWord(word: String): Outcome<Unit>

    suspend fun removeWord(word: String): Outcome<Unit>

    suspend fun setWhere(where: KeywordScope): Outcome<Unit>

    suspend fun setAlertWhenMuted(alert: Boolean): Outcome<Unit>

    suspend fun setShowInChatList(show: Boolean): Outcome<Unit>
}

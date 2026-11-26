package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import kotlinx.coroutines.flow.Flow

/**
 * Muted words' settings (`REQUIREMENTS.md` FR-X21, Settings → Chats → Muted words), kept on this phone across logouts
 * and never sent to Telegram. A change that cannot be saved fails with `DomainError.Unknown`.
 */
interface MutedWordsRepository {
    /** The settings, then every change. Never fails: settings that cannot be read are the defaults. */
    val settings: Flow<MutedWordSettings>

    suspend fun setEnabled(enabled: Boolean): Outcome<Unit>

    /**
     * Adds [word] (trimmed) at the end. Nothing changes for a blank word, one already kept (in any case, with or
     * without accents) or once there are [MutedWordSettings.MAX_WORDS]; a word longer than
     * [MutedWordSettings.MAX_WORD_LENGTH] is cut there.
     */
    suspend fun addWord(word: String): Outcome<Unit>

    suspend fun removeWord(word: String): Outcome<Unit>
}

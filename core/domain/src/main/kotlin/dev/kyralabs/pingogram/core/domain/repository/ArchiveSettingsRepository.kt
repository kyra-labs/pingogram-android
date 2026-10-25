package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettingsAvailability
import dev.kyralabs.pingogram.core.domain.model.ChatListType

/**
 * What applies to a whole chat list rather than to one chat (`REQUIREMENTS.md` FR-L3, FR-L9): the archive's settings,
 * which belong to the Telegram account, and marking every chat of a list as read. Nothing is kept: TDLib answers from
 * memory, and sends no update for the archive's settings.
 *
 * Requests fail with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, `Unknown` for a
 * refusal (e.g. archiving new chats where Telegram does not offer it).
 */
interface ArchiveSettingsRepository {
    /** The archive's settings, with whether the account may archive new chats from non-contacts. */
    suspend fun archiveSettings(): Outcome<ArchiveSettingsAvailability>

    /** Replaces the archive's settings with [settings]. */
    suspend fun setArchiveSettings(settings: ArchiveSettings): Outcome<Unit>

    /**
     * Marks every chat in [list] as read, as TDLib's `readChatList` does; the unread counts and rows change through
     * TDLib's updates.
     */
    suspend fun readChatList(list: ChatListType): Outcome<Unit>
}

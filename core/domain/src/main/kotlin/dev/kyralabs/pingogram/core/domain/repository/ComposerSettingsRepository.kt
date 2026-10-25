package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import kotlinx.coroutines.flow.Flow

/**
 * How the composer takes Enter ([SendKeys]), and what its record button records: kept on this device across logouts,
 * like the Appearance settings, and set in Appearance → Chats (the record button by a tap on it in a conversation).
 */
interface ComposerSettingsRepository {
    /** Which Enter sends, then every change. Never fails: settings that cannot be read count as the defaults. */
    val sendKeys: Flow<SendKeys>

    /** Saves [keys]; fails with `DomainError.Unknown` if they cannot be written (a full disk). */
    suspend fun setSendKeys(keys: SendKeys): Outcome<Unit>

    /**
     * Whether picked photos and videos go without compression by default (`AttachmentKind.Original`: as files, their
     * metadata removed), then every change; the attach preview can still change it per send. Off when unreadable.
     */
    val sendPhotosInOriginalQuality: Flow<Boolean>

    /** Saves [sendPhotosInOriginalQuality]; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setSendPhotosInOriginalQuality(enabled: Boolean): Outcome<Unit>

    /**
     * Whether the composer's record button records round video messages rather than voice messages
     * (`docs/adr/0032-round-video-messages.md`), then every change; a tap on the button switches it. Off (voice) when
     * unreadable.
     */
    val recordsVideoMessages: Flow<Boolean>

    /** Saves [recordsVideoMessages]; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setRecordsVideoMessages(enabled: Boolean): Outcome<Unit>
}

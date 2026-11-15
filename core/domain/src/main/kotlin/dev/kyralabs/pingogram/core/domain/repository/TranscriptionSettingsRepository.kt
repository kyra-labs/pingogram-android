package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import kotlinx.coroutines.flow.Flow

/** Voice message transcription's settings (Settings → Appearance → Chats), kept on this device across logouts. */
interface TranscriptionSettingsRepository {
    /** The settings, then every change. Never fails: settings that cannot be read are the defaults. */
    val settings: Flow<TranscriptionSettings>

    /** Saves whether others' messages are transcribed as they show; fails with `DomainError.Unknown` if it cannot. */
    suspend fun setAutomatic(enabled: Boolean): Outcome<Unit>

    /** Notes that a transcript said "On this phone", so later ones do not; fails as [setAutomatic]. */
    suspend fun markNoticeShown(): Outcome<Unit>
}

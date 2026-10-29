package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How voice messages play (Settings → Appearance → Chats): raise to listen, kept on this device across logouts, like
 * the Appearance settings.
 */
interface PlaybackSettingsRepository {
    /** Raise to listen's settings, then every change. Never fails: settings that cannot be read are the defaults. */
    val raiseToListen: Flow<RaiseToListenSettings>

    /** Saves whether raise to listen is on; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setRaiseToListen(enabled: Boolean): Outcome<Unit>

    /** Saves whether lowering the phone pauses; fails as [setRaiseToListen]. */
    suspend fun setPauseWhenLowered(enabled: Boolean): Outcome<Unit>
}

/**
 * Raise to listen: while a voice message plays, the phone held to the ear plays it through the earpiece, the screen
 * off ([enabled], on by default). Lowered, it plays on through the speaker, or pauses ([pauseWhenLowered], off by
 * default) until the phone is raised again.
 */
data class RaiseToListenSettings(
    val enabled: Boolean = true,
    val pauseWhenLowered: Boolean = false,
)

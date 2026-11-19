package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How voice messages play (Settings → Chats → Voice messages): raise to listen and skipping silences, and how fast
 * voice and round video messages play, kept on this device across logouts, like the Appearance settings.
 */
interface PlaybackSettingsRepository {
    /** Raise to listen's settings, then every change. Never fails: settings that cannot be read are the defaults. */
    val raiseToListen: Flow<RaiseToListenSettings>

    /** Saves whether raise to listen is on; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setRaiseToListen(enabled: Boolean): Outcome<Unit>

    /** Saves whether lowering the phone pauses; fails as [setRaiseToListen]. */
    suspend fun setPauseWhenLowered(enabled: Boolean): Outcome<Unit>

    /**
     * Whether voice messages skip their silences (`REQUIREMENTS.md` FR-X4), off by default, then every change. Never
     * fails, as [raiseToListen].
     */
    val skipSilence: Flow<Boolean>

    /** Saves whether voice messages skip their silences; fails as [setRaiseToListen]. */
    suspend fun setSkipSilence(enabled: Boolean): Outcome<Unit>

    /**
     * How fast voice and round video messages play (`REQUIREMENTS.md` FR-X4), 1× by default, then every change. Never
     * fails, as [raiseToListen].
     */
    val playbackSpeed: Flow<PlaybackSpeed>

    /** Saves how fast voice and round video messages play; fails as [setRaiseToListen]. */
    suspend fun setPlaybackSpeed(speed: PlaybackSpeed): Outcome<Unit>
}

/** How fast voice and round video messages play: the speeds their speed chip goes through, in order. */
enum class PlaybackSpeed(
    val factor: Float,
) {
    Normal(NORMAL_FACTOR),
    Faster(FASTER_FACTOR),
    Fastest(FASTEST_FACTOR),
    ;

    /** The speed after this one on the chip: 1× again after 2×. */
    val next: PlaybackSpeed get() = entries[(ordinal + 1) % entries.size]
}

private const val NORMAL_FACTOR = 1f
private const val FASTER_FACTOR = 1.5f
private const val FASTEST_FACTOR = 2f

/**
 * Raise to listen: while a voice message plays, the phone held to the ear plays it through the earpiece, the screen
 * off ([enabled], on by default). Lowered, it plays on through the speaker, or pauses ([pauseWhenLowered], off by
 * default) until the phone is raised again.
 */
data class RaiseToListenSettings(
    val enabled: Boolean = true,
    val pauseWhenLowered: Boolean = false,
)

package dev.kyralabs.pingogram.core.domain.model

/**
 * What went wrong in a call, as the user rates it (td_api.tl `CallProblem`). The video problems apply to video calls
 * only ([isVideo]).
 */
enum class CallProblem(
    val isVideo: Boolean = false,
) {
    /** The user heard their own voice back. */
    Echo,

    /** Background noise. */
    Noise,

    /** The other side kept going silent. */
    Interruptions,

    /** The speech was distorted. */
    DistortedSpeech,

    /** The user could not hear the other side. */
    SilentLocal,

    /** The other side could not hear the user. */
    SilentRemote,

    /** The call ended by itself. */
    Dropped,

    DistortedVideo(isVideo = true),

    PixelatedVideo(isVideo = true),
}

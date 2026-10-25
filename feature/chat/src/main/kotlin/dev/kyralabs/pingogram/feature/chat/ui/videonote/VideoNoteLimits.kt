package dev.kyralabs.pingogram.feature.chat.ui.videonote

/** The limits of a round video message (`docs/adr/0032-round-video-messages.md`). */
internal object VideoNoteLimits {
    /** A minute, Telegram's limit for a video note; the recording is sent once it is reached. */
    const val MAX_DURATION_MILLIS = 60_000L
}

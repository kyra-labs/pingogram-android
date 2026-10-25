package dev.kyralabs.pingogram.feature.chat.ui

/** One-off effects of the composer's round video messages (`docs/adr/0032-round-video-messages.md`): snackbars. */
sealed interface VideoNoteEffect : ChatEffect {
    /** The camera or the microphone could not be used, or the recording could not be finished: a snackbar says so. */
    data object RecordingFailed : VideoNoteEffect

    /** The other camera could not be opened; the recording goes on with the one before. */
    data object FlipFailed : VideoNoteEffect
}

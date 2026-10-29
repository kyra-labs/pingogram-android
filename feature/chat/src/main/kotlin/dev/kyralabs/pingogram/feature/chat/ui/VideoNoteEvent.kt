package dev.kyralabs.pingogram.feature.chat.ui

/**
 * What the user does with the composer's round video messages (`docs/adr/0032-round-video-messages.md`), sent to
 * [ChatViewModel.onEvent]: switching the record button between voice and video, the permissions recording video needs,
 * and flipping the camera. Recording itself goes through the [VoiceEvent]s, whichever the button records.
 */
sealed interface VideoNoteEvent : ChatEvent {
    /**
     * A tap on the record button: it records video messages from now on, or voice messages again. The screen sends it
     * only with the camera's and the microphone's permissions for video, else [PermissionNeeded].
     */
    data object SwitchMode : VideoNoteEvent

    /**
     * Recording video needs the camera's or the microphone's permission: the rationale sheet explains why, before the
     * system asks; [switching] when the user was switching the button to video, which happens once they are granted.
     */
    data class PermissionNeeded(
        val switching: Boolean,
    ) : VideoNoteEvent

    /**
     * The system's request was answered: both permissions [granted], or one refused; [canAskAgain] false once the
     * system no longer asks, when the sheet offers the app's settings instead.
     */
    data class PermissionAnswered(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : VideoNoteEvent

    /** "Not now", the scrim, dragging or back on the rationale sheet: the button stays as it was. */
    data object DismissRationale : VideoNoteEvent

    /** "Flip camera" while a video message records: the other camera from now on, in the same recording. */
    data object FlipCamera : VideoNoteEvent
}

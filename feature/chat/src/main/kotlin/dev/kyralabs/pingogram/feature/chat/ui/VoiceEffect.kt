package dev.kyralabs.pingogram.feature.chat.ui

/** One-off effects of the composer's voice recording (`REQUIREMENTS.md` FR-C5): snackbars, some with an action. */
sealed interface VoiceEffect : ChatEffect {
    /**
     * The microphone was pressed without the permission: a snackbar says why voice messages need it, with an action
     * that asks the system for it (answered by [VoiceEvent.PermissionAnswered]).
     */
    data object ExplainMicrophone : VoiceEffect

    /** The permission was refused: a snackbar says so, with an action that opens the app's settings. */
    data object MicrophoneDenied : VoiceEffect

    /** A recording too short to send, or the permission just granted: a snackbar says to hold to record. */
    data object HoldToRecord : VoiceEffect

    /** The microphone could not be used, or the recording could not be finished: a snackbar says so. */
    data object RecordingFailed : VoiceEffect
}

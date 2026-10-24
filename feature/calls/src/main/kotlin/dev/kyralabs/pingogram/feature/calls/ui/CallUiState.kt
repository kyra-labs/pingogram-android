package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.CallEndReason
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the call screen shows. */
@Immutable
sealed interface CallUiState {
    /** The call is being set up (the first frames after "Call"): nothing to show yet. */
    data object Loading : CallUiState

    /** No call: the screen closes. */
    data object Closed : CallUiState

    /**
     * Before calling or answering: the microphone permission is missing. The screen says why it is needed, then asks
     * Android ([canAsk]) or, after a final refusal, offers the app's settings.
     */
    data class NeedsMicrophone(
        val person: CallPerson,
        val isAnswering: Boolean,
        val canAsk: Boolean,
    ) : CallUiState

    data class InCall(
        val person: CallPerson,
        val isOutgoing: Boolean,
        val stage: CallStage,
        /** "01:23" once connected. */
        val duration: String?,
        val emojis: ImmutableList<String> = persistentListOf(),
        val isMuted: Boolean = false,
        val isOnHold: Boolean = false,
        val route: AudioRoute? = null,
        val routes: ImmutableList<AudioRoute> = persistentListOf(),
        /** Made as a video call, or the other person turned video on: an incoming one says so. */
        val isVideoCall: Boolean = false,
        val video: CallVideoUi = CallVideoUi(),
        /** Ended, and Telegram asks the user to rate it: "Rate this call" shows over the ended call. */
        val rating: CallRatingPrompt? = null,
    ) : CallUiState {
        /** The incoming call rings: answer or decline. */
        val isIncomingRinging: Boolean get() = !isOutgoing && stage == CallStage.Ringing

        /** Mute, the route and hold apply: the call is under way. */
        val hasControls: Boolean get() = !isIncomingRinging && stage !is CallStage.Ended
    }
}

/** The call's video as the screen shows it (`docs/adr/0019-video-and-group-calls-scope.md`). */
@Immutable
data class CallVideoUi(
    /** The camera the user sends (the self-view), or null with it off. */
    val camera: CameraFacing? = null,
    val canSwitchCamera: Boolean = false,
    val remote: RemoteVideo = RemoteVideo.Off,
    /** The device has a camera: the panel offers one. */
    val hasCamera: Boolean = false,
    /** The device can share its screen: the top bar offers it while the call is connected. */
    val canShareScreen: Boolean = false,
    /** The user shares the screen (`docs/adr/0026-screen-sharing.md`): the "You're sharing your screen" pill shows. */
    val isSharingScreen: Boolean = false,
    /** The other side's video is their shared screen: fitted to the window, not cropped. */
    val remoteIsScreen: Boolean = false,
) {
    /** Either side's video shows: the screen lays out as a video call. */
    val shows: Boolean get() = camera != null || remote != RemoteVideo.Off
}

/** "Rate this call" over an ended call; [isVideo] offers the video problems too. */
@Immutable
data class CallRatingPrompt(
    val isVideo: Boolean,
)

/** The other person as the call screen shows them. */
@Immutable
data class CallPerson(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
)

/** The call's stage in words; [CallStage.Ended] carries why. */
@Immutable
sealed interface CallStage {
    data object Requesting : CallStage

    /** Outgoing, before the other person's phone rings; incoming, it rings here. */
    data object Ringing : CallStage

    /** Outgoing: the other person's phone rings. */
    data object RingingThere : CallStage

    data object Connecting : CallStage

    data object Connected : CallStage

    data object Reconnecting : CallStage

    data class Ended(
        val reason: CallEndReason,
    ) : CallStage
}

/** What the user does on the call screen. */
sealed interface CallEvent {
    data object Answer : CallEvent

    data object HangUp : CallEvent

    data object ToggleMute : CallEvent

    data object ToggleHold : CallEvent

    data class SelectRoute(
        val route: AudioRoute,
    ) : CallEvent

    /** Turns the camera on (asking for the permission first where needed) or off. */
    data object ToggleCamera : CallEvent

    /** The other camera: front after back, and back. */
    data object SwitchCamera : CallEvent

    /** Shares the screen (Android's consent dialog first) or stops sharing it. */
    data object ToggleScreenSharing : CallEvent

    /** Android's consent dialog closed: the user agreed ([granted]; its result reached the engine) or not. */
    data class ScreenSharingResult(
        val granted: Boolean,
    ) : CallEvent

    /** Android answered the camera's permission request, as [MicrophoneResult] the microphone's. */
    data class CameraResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : CallEvent

    /** Android answered the permission request: [granted], and whether it would ask again ([canAskAgain]). */
    data class MicrophoneResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : CallEvent

    /** "Not now" before the permission request: an outgoing call is not made, an incoming one is declined. */
    data object DismissMicrophone : CallEvent

    /** "Send" in "Rate this call": [stars] from 1 to 5, and what went wrong. */
    data class Rate(
        val stars: Int,
        val problems: Set<CallProblem>,
    ) : CallEvent

    /** "Rate this call" closed without rating: "Not now", the scrim, back, or picture-in-picture. */
    data object DismissRating : CallEvent
}

/** What the call screen does once, outside its state. */
sealed interface CallEffect {
    /** Ask Android for the camera. */
    data object RequestCamera : CallEffect

    /** Show Android's consent dialog for capturing the screen. */
    data object RequestScreenCapture : CallEffect

    /** Android no longer asks for the camera: open the app's settings, where the user can allow it. */
    data object OpenSettings : CallEffect
}

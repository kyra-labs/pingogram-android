@file:Suppress("TooManyFunctions") // One preview per state of the call screen.

package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CallEndReason
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import kotlinx.collections.immutable.persistentListOf

/** The states of the call screen, shared by the previews and the screenshot tests. */
internal object CallPreviewStates {
    private val ada = CallPerson(userId = 42, name = "Ada Lovelace", photo = null)
    private val earpiece = AudioRoute("1", AudioRouteType.Earpiece)
    private val speaker = AudioRoute("2", AudioRouteType.Speaker)
    private val headphones = AudioRoute("3", AudioRouteType.Bluetooth, "Pixel Buds Pro")

    val incoming = CallUiState.InCall(ada, isOutgoing = false, stage = CallStage.Ringing, duration = null)

    val incomingVideo = incoming.copy(isVideoCall = true)

    val ringing = CallUiState.InCall(ada, isOutgoing = true, stage = CallStage.RingingThere, duration = null)

    val connected =
        CallUiState.InCall(
            person = ada,
            isOutgoing = true,
            stage = CallStage.Connected,
            duration = "12:34",
            emojis = persistentListOf("😀", "🐶", "🍎", "🚗"),
            route = earpiece,
            routes = persistentListOf(earpiece, speaker),
            video = CallVideoUi(hasCamera = true),
        )

    /** A video call: the other person's video, the self-view from the front camera, on the speaker. */
    val video =
        connected.copy(
            isVideoCall = true,
            route = speaker,
            video =
                CallVideoUi(
                    camera = CameraFacing.Front,
                    canSwitchCamera = true,
                    remote = RemoteVideo.Active,
                    hasCamera = true,
                ),
        )

    /** Only the user's camera on: it fills the screen. */
    val videoSelfOnly = video.copy(video = video.video.copy(remote = RemoteVideo.Off))

    /** The other person's video held back (their app in the background, a weak connection). */
    val videoPaused = video.copy(video = video.video.copy(remote = RemoteVideo.Paused))

    val reconnecting =
        connected.copy(
            stage = CallStage.Reconnecting,
            isMuted = true,
            route = headphones,
            routes = persistentListOf(earpiece, speaker, headphones),
        )

    val ended = connected.copy(stage = CallStage.Ended(CallEndReason.Failed(DomainError.CallPrivacyRestricted)))

    val microphone = CallUiState.NeedsMicrophone(ada, isAnswering = false, canAsk = true)
}

@Composable
private fun Preview(state: CallUiState) {
    PingogramPreview { CallScreen(state, {}, {}, {}, {}) }
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun CallIncomingPreview() = Preview(CallPreviewStates.incoming)

@PingogramPreviews
@Composable
private fun CallIncomingVideoPreview() = Preview(CallPreviewStates.incomingVideo)

@PingogramPreviews
@Composable
private fun CallRingingPreview() = Preview(CallPreviewStates.ringing)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun CallConnectedPreview() = Preview(CallPreviewStates.connected)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun CallVideoPreview() = Preview(CallPreviewStates.video)

@PingogramPreviews
@Composable
private fun CallVideoSelfOnlyPreview() = Preview(CallPreviewStates.videoSelfOnly)

@PingogramPreviews
@Composable
private fun CallVideoPausedPreview() = Preview(CallPreviewStates.videoPaused)

@PingogramPreviews
@Composable
private fun CallReconnectingPreview() = Preview(CallPreviewStates.reconnecting)

@PingogramPreviews
@Composable
private fun CallEndedPreview() = Preview(CallPreviewStates.ended)

@PingogramPreviews
@Composable
private fun CallMicrophonePreview() = Preview(CallPreviewStates.microphone)

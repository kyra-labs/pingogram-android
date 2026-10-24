@file:Suppress("TooManyFunctions") // One preview per state of the voice chat's screen.

package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.GroupCallEndReason
import kotlinx.collections.immutable.persistentListOf

/** The states of the voice chat's screen, shared by the previews and the screenshot tests. */
internal object GroupCallPreviewStates {
    private val chat = GroupCallChat(chatId = -100, title = "Book club", photo = null)
    private val earpiece = AudioRoute("1", AudioRouteType.Earpiece)
    private val speaker = AudioRoute("2", AudioRouteType.Speaker)

    private fun participant(
        id: Long,
        name: String,
        state: ParticipantState,
        isCurrentUser: Boolean = false,
    ) = GroupCallParticipantUi("user:$id", id, name, null, isCurrentUser, state)

    private fun video(
        id: Long,
        name: String,
        isPaused: Boolean = false,
        isPinned: Boolean = false,
        isSpeaking: Boolean = false,
    ) = ParticipantVideoUi("user:$id", "endpoint-$id", name, isPaused, isPinned, isSpeaking)

    private val participants =
        persistentListOf(
            participant(1, "Grace Hopper", ParticipantState.Speaking),
            participant(2, "Alan Turing", ParticipantState.HandRaised),
            participant(3, "Ada Lovelace", ParticipantState.Muted, isCurrentUser = true),
            participant(4, "Katherine Johnson", ParticipantState.Listening),
            participant(5, "Margaret Hamilton", ParticipantState.Muted),
        )

    val joining = GroupCallUiState.InCall(chat, GroupCallStage.Joining)

    val connected =
        GroupCallUiState.InCall(
            chat = chat,
            stage = GroupCallStage.Connected,
            duration = "4:05",
            participantCount = 12,
            participants = participants,
            isMuted = true,
            route = speaker,
            routes = persistentListOf(earpiece, speaker),
        )

    /** Telegram allows video: the panel offers the camera, off (`docs/adr/0024-video-in-voice-chats.md`). */
    val cameraOffered = connected.copy(video = GroupCallVideoUi(canSend = true))

    /** The user sends the front camera: the self-view above the participants. */
    val camera =
        connected.copy(video = GroupCallVideoUi(canSend = true, camera = CameraFacing.Front, canSwitchCamera = true))

    /** Others send their video: Grace's pinned across the width, Katherine's paused, the user's camera last. */
    val videos =
        camera.copy(
            video =
                camera.video.copy(
                    remote =
                        persistentListOf(
                            video(1, "Grace Hopper", isPinned = true, isSpeaking = true),
                            video(2, "Alan Turing"),
                            video(4, "Katherine Johnson", isPaused = true),
                        ),
                ),
        )

    /** One other video, unpinned: it alone takes the width; no camera of the user's. */
    val video =
        cameraOffered.copy(
            video =
                cameraOffered.video.copy(
                    remote =
                        persistentListOf(
                            video(1, "Grace Hopper"),
                        ),
                ),
        )

    /** An administrator keeps the user muted: Raise hand in Mute's place, raised. */
    val raiseHand = connected.copy(canUnmute = false, isHandRaised = true)

    val stream =
        connected.copy(
            isStream = true,
            participants = persistentListOf(),
            participantCount = 1_284,
            canUnmute = false,
        )

    val ended = connected.copy(stage = GroupCallStage.Ended(GroupCallEndReason.Ended))

    val microphone = GroupCallUiState.NeedsMicrophone(chat, canAsk = true)

    val unavailable = GroupCallUiState.Unavailable(chat, isBusy = true)
}

@Composable
private fun Preview(state: GroupCallUiState) {
    PingogramPreview { GroupCallScreen(state, {}, {}, {}, {}) }
}

@PingogramPreviews
@Composable
private fun GroupCallJoiningPreview() = Preview(GroupCallPreviewStates.joining)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun GroupCallConnectedPreview() = Preview(GroupCallPreviewStates.connected)

@PingogramPreviews
@Composable
private fun GroupCallCameraPreview() = Preview(GroupCallPreviewStates.camera)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun GroupCallVideosPreview() = Preview(GroupCallPreviewStates.videos)

@PingogramPreviews
@Composable
private fun GroupCallVideoPreview() = Preview(GroupCallPreviewStates.video)

@PingogramPreviews
@Composable
private fun GroupCallRaiseHandPreview() = Preview(GroupCallPreviewStates.raiseHand)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun GroupCallStreamPreview() = Preview(GroupCallPreviewStates.stream)

@PingogramPreviews
@Composable
private fun GroupCallEndedPreview() = Preview(GroupCallPreviewStates.ended)

@PingogramPreviews
@Composable
private fun GroupCallMicrophonePreview() = Preview(GroupCallPreviewStates.microphone)

@PingogramPreviews
@Composable
private fun GroupCallUnavailablePreview() = Preview(GroupCallPreviewStates.unavailable)

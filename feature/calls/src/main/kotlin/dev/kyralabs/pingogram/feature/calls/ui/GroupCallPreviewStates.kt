@file:Suppress("TooManyFunctions") // One preview per state of the voice chat's screen.

package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
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

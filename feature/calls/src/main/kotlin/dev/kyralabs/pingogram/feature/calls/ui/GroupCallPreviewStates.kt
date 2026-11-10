@file:Suppress("TooManyFunctions") // One preview per state of the voice chat's screen.

package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.GroupCallEndReason
import dev.kyralabs.pingogram.feature.calls.ui.comments.CommentUi
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsHost
import dev.kyralabs.pingogram.feature.calls.ui.comments.GroupCallCommentsUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

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
        isScreen: Boolean = false,
    ) = ParticipantVideoUi("user:$id", "endpoint-$id", name, isPaused, isPinned, isSpeaking, isScreen)

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

    /** The user shares their screen: "You're sharing your screen" with "Stop" under the header (ADR 0026). */
    val sharingScreen =
        connected.copy(video = GroupCallVideoUi(canSend = true, canShareScreen = true, isSharingScreen = true))

    /** Grace shares her screen beside her camera; the screen comes first. */
    val screens =
        connected.copy(
            video =
                GroupCallVideoUi(
                    canSend = true,
                    canShareScreen = true,
                    remote =
                        persistentListOf(
                            video(1, "Grace Hopper", isScreen = true),
                            video(1, "Grace Hopper", isSpeaking = true),
                            video(2, "Alan Turing"),
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

    val confirmConference = GroupCallUiState.ConfirmConference

    val conference =
        connected.copy(
            chat = GroupCallChat(chatId = 0, title = "", photo = null),
            isConference = true,
            emojis = persistentListOf("🐝", "🌵", "🎈", "🦊"),
            canInvite = true,
        )

    private fun comment(
        id: Int,
        senderId: Long,
        name: String,
        text: String,
        paidStarCount: Long = 0,
        isOwn: Boolean = false,
        isFailed: Boolean = false,
    ) = CommentUi(
        id = id,
        senderKey = "user:$senderId",
        senderId = senderId,
        name = name,
        photo = null,
        text = text,
        paidStarCount = paidStarCount,
        isOwn = isOwn,
        canDelete = isOwn,
        isFailed = isFailed,
    )

    /** Comments on, newest at the bottom: one paid ("★ 50"), one of the user's that Telegram refused (board 9a). */
    val comments =
        GroupCallCommentsUiState.Available(
            isShown = true,
            areAllowed = true,
            canComment = true,
            received = 214,
            comments =
                persistentListOf(
                    comment(1, 5, "Margaret Hamilton", "Is the recording going up on the channel later?"),
                    comment(2, 4, "Katherine Johnson", "Grace, which emulator image are you using?"),
                    comment(3, 2, "Alan Turing", "Can someone drop the PR link?", paidStarCount = 50),
                    comment(4, 1, "Grace Hopper", "Linked it in the group, #338"),
                    comment(5, 3, "Ada Lovelace", "Thanks!", isOwn = true, isFailed = true),
                ),
            // No floating reactions: they rise on a spring, which a golden would capture at an arbitrary point.
        )

    /** Comments are off: an administrator sees the menu to turn them on, others only that they are off. */
    val commentsOff =
        comments.copy(
            areAllowed = false,
            canComment = false,
            canToggle = true,
            comments = persistentListOf(),
        )

    /** Comments cost Telegram Stars: the field says Pingogram can't pay. */
    val commentsPaid = comments.copy(isPaid = true)

    /** A conference call with its video tiles and its "Invite link" chip beside the comments (board 9b). */
    val conferenceVideos =
        videos.copy(
            chat = GroupCallChat(chatId = 0, title = "", photo = null),
            isConference = true,
            inviteLink = "https://t.me/call/abc",
            participants = participants.take(4).toPersistentList(),
        )
}

/** A comments host showing [state], for previews and goldens. */
internal fun previewComments(state: GroupCallCommentsUiState) =
    GroupCallCommentsHost(mutableStateOf(state), mutableStateOf(null), onEvent = {})

@Composable
private fun Preview(
    state: GroupCallUiState,
    comments: GroupCallCommentsUiState = GroupCallCommentsUiState.Unavailable,
) {
    PingogramPreview { GroupCallScreen(state, {}, {}, {}, {}, comments = previewComments(comments)) }
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
@PingogramVariantPreviews
@Composable
private fun GroupCallSharingScreenPreview() = Preview(GroupCallPreviewStates.sharingScreen)

@PingogramPreviews
@Composable
private fun GroupCallScreensPreview() = Preview(GroupCallPreviewStates.screens)

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

@PingogramPreviews
@Composable
private fun GroupCallConfirmConferencePreview() = Preview(GroupCallPreviewStates.confirmConference)

@PingogramPreviews
@Composable
private fun GroupCallConferencePreview() = Preview(GroupCallPreviewStates.conference)

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun GroupCallCommentsPreview() = Preview(GroupCallPreviewStates.connected, GroupCallPreviewStates.comments)

@PingogramPreviews
@Composable
private fun GroupCallCommentsOffPreview() =
    Preview(GroupCallPreviewStates.connected, GroupCallPreviewStates.commentsOff)

@PingogramPreviews
@Composable
private fun GroupCallCommentsPaidPreview() =
    Preview(GroupCallPreviewStates.connected, GroupCallPreviewStates.commentsPaid)

@PingogramPreviews
@Composable
private fun GroupCallConferenceCommentsPreview() =
    Preview(GroupCallPreviewStates.conferenceVideos, GroupCallPreviewStates.comments)

@PingogramPreviews
@Composable
private fun GroupCallCommentsHiddenPreview() =
    Preview(GroupCallPreviewStates.connected, GroupCallPreviewStates.comments.copy(isShown = false))

@file:Suppress("TooManyFunctions") // One composable per part of the voice chat's screen.

package dev.kyralabs.pingogram.feature.calls.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.GroupCallEndReason
import dev.kyralabs.pingogram.feature.calls.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/** A live stream's video, which [GroupCallActivity] draws with WebRTC's renderer and previews as a placeholder. */
typealias StreamVideo = @Composable (modifier: Modifier) -> Unit

/** A tonal placeholder for the stream's video in previews and screenshot tests, which cannot draw with OpenGL. */
private val PlaceholderStream: StreamVideo = { modifier ->
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

/**
 * The voice chat's screen (`docs/DESIGN_SYSTEM.md` §6, "Voice chat screen"), in the call screen's style: on
 * `surfaceContainer`, "Back to the app" and the chat's title with where the voice chat is at the top, the participants
 * in the middle (a live stream's video in their place), and the call screen's panel docked at the bottom: Mute (or
 * Raise hand while an administrator keeps the user muted), the audio route, and "Leave" (with "End voice chat" in a
 * menu for those who may).
 */
@Composable
fun GroupCallScreen(
    state: GroupCallUiState,
    onEvent: (GroupCallEvent) -> Unit,
    onMinimize: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    video: StreamVideo = PlaceholderStream,
    selfVideo: CallVideo = PlaceholderVideo,
    participantVideo: ParticipantVideo = PlaceholderParticipantVideo,
    pictureInPicture: Boolean = false,
    onAddPeople: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    // A Surface, so text and icons take the window's content color.
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            GroupCallContent(
                state,
                onEvent,
                onMinimize,
                onRequestMicrophone,
                onOpenSettings,
                GroupCallVideos(video, selfVideo, participantVideo),
                pictureInPicture,
                GroupCallExtras(onAddPeople, snackbarHostState),
            )
        }
    }
}

/** "Add people" in a conference call, and where its results show. */
private class GroupCallExtras(
    val onAddPeople: () -> Unit,
    val snackbarHostState: SnackbarHostState?,
)

/** What draws a voice chat's videos: a live stream's, the user's own camera (the self-view) and the others'. */
private class GroupCallVideos(
    val stream: StreamVideo,
    val self: CallVideo,
    val participants: ParticipantVideo,
)

@Composable
@Suppress("LongParameterList") // The screen's state, its events and the activity's callbacks.
private fun BoxScope.GroupCallContent(
    state: GroupCallUiState,
    onEvent: (GroupCallEvent) -> Unit,
    onMinimize: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
    videos: GroupCallVideos,
    pictureInPicture: Boolean,
    extras: GroupCallExtras,
) {
    when (state) {
        GroupCallUiState.Loading -> {
            DelayedLoadingIndicator(Modifier.align(Alignment.Center))
        }

        GroupCallUiState.Closed -> {
            Unit
        }

        is GroupCallUiState.NeedsMicrophone -> {
            MicrophoneStep(state, onEvent, onRequestMicrophone, onOpenSettings)
        }

        GroupCallUiState.ConfirmConference -> {
            ConferenceStep(onEvent)
        }

        is GroupCallUiState.Unavailable -> {
            Message(
                title = state.chat.title,
                text =
                    stringResource(
                        if (state.isBusy) R.string.group_call_busy else R.string.group_call_no_voice_chat,
                    ),
            )
        }

        is GroupCallUiState.InCall -> {
            val first = state.video.remote.firstOrNull()
            if (pictureInPicture && state.isStream) {
                videos.stream(Modifier.fillMaxSize())
            } else if (pictureInPicture && first != null) {
                // The pinned video, or the first: the one the grid shows first.
                videos.participants(first.endpointId, first.isScreen, Modifier.fillMaxSize())
            } else {
                InGroupCall(state, onEvent, onMinimize, videos, extras)
            }
        }
    }
}

@Composable
private fun InGroupCall(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
    onMinimize: () -> Unit,
    videos: GroupCallVideos,
    extras: GroupCallExtras,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxSize()) {
        Header(state, onEvent, onMinimize)
        AnimatedVisibility(
            visible = state.video.isSharingScreen,
            enter =
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit =
                shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            ScreenSharingPill(
                onStop = { onEvent(GroupCallEvent.ToggleScreenSharing) },
                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
            )
        }
        val camera = state.video.camera
        if (camera != null && !state.isStream && state.video.remote.isEmpty()) {
            // The user's own camera above the participants (docs/adr/0024-video-in-voice-chats.md); with the others'
            // videos it joins their grid.
            SelfView(
                mirror = camera == CameraFacing.Front,
                canSwitch = state.video.canSwitchCamera,
                aspectRatio = SELF_VIEW_ASPECT_LANDSCAPE,
                video = videos.self,
                onSwitch = { onEvent(GroupCallEvent.SwitchCamera) },
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = spacing.small)
                        .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                        .fillMaxWidth(SELF_VIEW_WIDTH),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.isStream) {
                videos.stream(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.medium, vertical = spacing.small)
                        .clip(MaterialTheme.shapes.large),
                )
            } else {
                Participants(state, videos, onEvent, extras.onAddPeople)
            }
            extras.snackbarHostState?.let { SnackbarHost(it, Modifier.align(Alignment.BottomCenter)) }
        }
        if (state.hasControls) Controls(state, onEvent)
    }
}

/**
 * "Back to the app", the chat's title and where the voice chat is, inside the safe area; "Share screen" at the end
 * where the user may share (`docs/adr/0026-screen-sharing.md`).
 */
@Composable
private fun Header(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
    onMinimize: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ).padding(horizontal = spacing.small, vertical = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderRow(state, onEvent, onMinimize)
        }
        // A conference call's emojis, once Telegram verified its state (docs/adr/0025-conference-calls.md).
        AnimatedVisibility(
            visible = state.emojis.isNotEmpty(),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            KeyEmojis(state.emojis, stringResource(R.string.group_call_key_explanation))
        }
    }
}

/** Back to the app, the title over the status, and screen sharing. */
@Composable
private fun RowScope.HeaderRow(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
    onMinimize: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    MinimizeButton(onMinimize)
    Column(Modifier.weight(1f).padding(horizontal = spacing.small)) {
        Text(
            text = state.chat.displayTitle(),
            style = MaterialTheme.typography.titleLarge.userContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.hasControls && (state.video.canShareScreen || state.video.isSharingScreen)) {
        ShareScreenButton(
            sharing = state.video.isSharingScreen,
            onToggle = { onEvent(GroupCallEvent.ToggleScreenSharing) },
        )
    }
}

/** The chat's title; "Group call" for a conference call (chat 0), "Voice chat" while the chat is not known. */
@Composable
private fun GroupCallChat.displayTitle(): String =
    when {
        chatId == 0L -> stringResource(R.string.group_call)
        else -> title.ifBlank { stringResource(R.string.voice_chat) }
    }

/** "Connecting…", "Reconnecting…", why it ended, or once connected "12 participants · 4:05". */
@Composable
private fun statusText(state: GroupCallUiState.InCall): String {
    val count =
        state.participantCount?.let {
            pluralStringResource(
                if (state.isStream) UiR.plurals.voice_chat_bar_watching else UiR.plurals.voice_chat_bar_participants,
                it,
                NumberFormat.getIntegerInstance().format(it),
            )
        }
    return when (val stage = state.stage) {
        GroupCallStage.Joining -> stringResource(R.string.group_call_joining)
        GroupCallStage.Reconnecting -> stringResource(R.string.call_status_reconnecting)
        GroupCallStage.Connected -> listOfNotNull(count, state.duration).joinToString(" · ")
        is GroupCallStage.Ended -> endText(stage.reason)
    }
}

@Composable
private fun endText(reason: GroupCallEndReason): String =
    when (reason) {
        GroupCallEndReason.Left -> stringResource(R.string.group_call_left)
        GroupCallEndReason.Ended -> stringResource(R.string.group_call_ended)
        GroupCallEndReason.Disconnected -> stringResource(R.string.call_end_disconnected)
        is GroupCallEndReason.Failed -> errorText(reason.error)
    }

/** The participants: their videos first where any are received, then a keyed list of rows, the user's among them. */
@Composable
private fun Participants(
    state: GroupCallUiState.InCall,
    videos: GroupCallVideos,
    onEvent: (GroupCallEvent) -> Unit,
    onAddPeople: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        modifier =
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        contentPadding = PaddingValues(horizontal = spacing.small, vertical = spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.video.remote.isNotEmpty()) {
            item(key = "videos", contentType = "videos") {
                VideoGrid(
                    state,
                    videos.participants,
                    videos.self,
                    onEvent,
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                        .padding(horizontal = spacing.small, vertical = spacing.small),
                )
            }
        }
        if (state.canInvite) {
            item(key = "add", contentType = "add") {
                AddPeopleRow(onAddPeople, Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).animateItem())
            }
        }
        items(state.participants, key = { it.key }, contentType = { "participant" }) { participant ->
            ParticipantRow(participant, Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).animateItem())
        }
    }
}

/**
 * A participant: their avatar, ringed in `primary` while they speak (the ring fades in the draw phase, at once while
 * power saving holds the call screens still, [LocalStillCallAnimations]), their name and what they are doing, and a
 * symbol of it at the end. Screen readers hear one item.
 */
@Composable
private fun ParticipantRow(
    participant: GroupCallParticipantUi,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val speaking = participant.state == ParticipantState.Speaking
    val ringSpec = if (LocalStillCallAnimations.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val ring by animateFloatAsState(if (speaking) 1f else 0f, ringSpec)
    val ringWidth = MaterialTheme.sizes.avatarRing
    val name =
        participant.name.ifBlank { stringResource(R.string.call_someone) }.let {
            if (participant.isCurrentUser) stringResource(R.string.group_call_you, it) else it
        }
    val stateText = stringResource(participant.state.label())
    ListItem(
        headlineContent = {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge.userContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(stateText, color = if (speaking) colors.primary else colors.onSurfaceVariant, maxLines = 1)
        },
        leadingContent = {
            Avatar(
                photo = participant.photo,
                name = participant.name,
                id = participant.id,
                size = MaterialTheme.sizes.avatarSmall,
                modifier =
                    Modifier.drawBehind {
                        if (ring > 0f) {
                            drawCircle(
                                color = colors.primary.copy(alpha = ring),
                                radius = size.minDimension / 2 + ringWidth.toPx(),
                                style = Stroke(ringWidth.toPx()),
                            )
                        }
                    },
            )
        },
        trailingContent = {
            Icon(
                painterResource(participant.state.icon()),
                contentDescription = null,
                tint = if (speaking) colors.primary else colors.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name, $stateText" },
    )
}

/** "Add people": the add-person symbol in a `primaryContainer` circle where the avatars sit; opens the picker. */
@Composable
private fun AddPeopleRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        onClick = onClick,
        leadingContent = {
            Box(
                Modifier
                    .size(MaterialTheme.sizes.avatarSmall)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_person_add),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = colors.surfaceContainer),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.group_call_add_people), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun ParticipantState.label(): Int =
    when (this) {
        ParticipantState.Speaking -> R.string.group_call_speaking
        ParticipantState.Listening -> R.string.group_call_listening
        ParticipantState.Muted -> R.string.group_call_muted
        ParticipantState.HandRaised -> R.string.group_call_hand_raised
    }

@DrawableRes
private fun ParticipantState.icon(): Int =
    when (this) {
        ParticipantState.Speaking, ParticipantState.Listening -> UiR.drawable.ic_mic_filled
        ParticipantState.Muted -> R.drawable.ic_mic_off_filled
        ParticipantState.HandRaised -> R.drawable.ic_back_hand
    }

/** The call screen's panel, docked at the bottom: Mute or Raise hand, the audio route, and Leave. */
@Composable
private fun Controls(
    state: GroupCallUiState.InCall,
    onEvent: (GroupCallEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .windowInsetsPadding(bottomInsets(docked = true))
                    .padding(horizontal = spacing.large, vertical = spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val motion = MaterialTheme.motionScheme
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!state.isStream) {
                    AnimatedContent(
                        targetState = state.canUnmute,
                        transitionSpec = {
                            fadeIn(motion.defaultEffectsSpec())
                                .togetherWith(fadeOut(motion.fastEffectsSpec()))
                                .using(SizeTransform(clip = false))
                        },
                        label = "microphone",
                    ) { canUnmute ->
                        if (canUnmute) {
                            LabeledButton(R.string.call_mute) {
                                Toggle(
                                    checked = state.isMuted,
                                    icon =
                                        if (state.isMuted) R.drawable.ic_mic_off_filled else UiR.drawable.ic_mic_filled,
                                    label = R.string.call_mute,
                                ) { onEvent(GroupCallEvent.ToggleMute) }
                            }
                        } else {
                            LabeledButton(R.string.group_call_raise_hand) {
                                Toggle(
                                    checked = state.isHandRaised,
                                    icon = R.drawable.ic_back_hand,
                                    label = R.string.group_call_raise_hand,
                                ) { onEvent(GroupCallEvent.ToggleHand) }
                            }
                        }
                    }
                }
                RouteButton(state.route, state.routes) { onEvent(GroupCallEvent.SelectRoute(it)) }
                if (state.video.canSend) CameraButton(on = state.video.camera != null, onEvent = onEvent)
            }
            Spacer(Modifier.height(spacing.extraLarge))
            LeaveButton(canEnd = state.canEnd, isStream = state.isStream, onEvent = onEvent)
        }
    }
}

/** The call screen's Camera toggle over its label (`docs/adr/0024-video-in-voice-chats.md`). */
@Composable
private fun CameraButton(
    on: Boolean,
    onEvent: (GroupCallEvent) -> Unit,
) {
    LabeledButton(R.string.call_camera) {
        Toggle(
            checked = on,
            icon = if (on) R.drawable.ic_videocam_filled else R.drawable.ic_videocam_off_filled,
            label = R.string.call_camera,
        ) { onEvent(GroupCallEvent.ToggleCamera) }
    }
}

/**
 * "Leave": the call screen's red pill. For one who may end the voice chat, it opens a menu with "Leave" and "End voice
 * chat" (in `error`), which asks first.
 */
@Composable
private fun LeaveButton(
    canEnd: Boolean,
    isStream: Boolean,
    onEvent: (GroupCallEvent) -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    Box {
        HangUpButton(label = R.string.group_call_leave) {
            if (canEnd) menu = true else onEvent(GroupCallEvent.Leave)
        }
        DropdownMenuPopup(expanded = menu && canEnd, onDismissRequest = { menu = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_call_leave)) },
                    onClick = {
                        menu = false
                        onEvent(GroupCallEvent.Leave)
                    },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_call_end_filled), contentDescription = null) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (isStream) R.string.group_call_end_stream else R.string.group_call_end),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menu = false
                        confirmEnd = true
                    },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                )
            }
        }
    }
    if (confirmEnd) {
        EndDialog(isStream, onDismiss = { confirmEnd = false }) { onEvent(GroupCallEvent.EndForEveryone) }
    }
}

/** Asks before ending the voice chat (or live stream) for everyone. */
@Composable
private fun EndDialog(
    isStream: Boolean,
    onDismiss: () -> Unit,
    onEnd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isStream) R.string.group_call_end_stream_title else R.string.group_call_end_title,
                ),
            )
        },
        text = { Text(stringResource(R.string.group_call_end_text)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onEnd()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.group_call_end_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.group_call_cancel))
            }
        },
    )
}

/** A conference call's link: what joining means, "Not now" and "Join" (`docs/adr/0025-conference-calls.md`). */
@Composable
private fun ConferenceStep(onEvent: (GroupCallEvent) -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_call_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            text = stringResource(R.string.group_call_confirm_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
        )
        Spacer(Modifier.height(spacing.extraLarge))
        Row(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { onEvent(GroupCallEvent.DeclineConference) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.call_not_now))
            }
            Button(onClick = { onEvent(GroupCallEvent.JoinConference) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.group_call_join), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/**
 * Before joining without the microphone: why it is needed, "Not now" and "Continue" (Android's request) or "Open
 * settings".
 */
@Composable
private fun MicrophoneStep(
    state: GroupCallUiState.NeedsMicrophone,
    onEvent: (GroupCallEvent) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.chat.displayTitle(),
            style = MaterialTheme.typography.headlineSmall.userContent,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(spacing.medium))
        Text(
            text = stringResource(R.string.group_call_microphone_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
        )
        Spacer(Modifier.height(spacing.extraLarge))
        Row(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { onEvent(GroupCallEvent.DismissMicrophone) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.call_not_now))
            }
            Button(
                onClick = if (state.canAsk) onRequestMicrophone else onOpenSettings,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(if (state.canAsk) R.string.call_continue else R.string.call_open_settings),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** A short message in the middle of the screen, before it closes. */
@Composable
private fun Message(
    title: String,
    text: String,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.userContent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.medium))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The self-view's share of the window's width (at most `sizes.illustrationMaxSize`), leaving room below it. */
private const val SELF_VIEW_WIDTH = 0.6f

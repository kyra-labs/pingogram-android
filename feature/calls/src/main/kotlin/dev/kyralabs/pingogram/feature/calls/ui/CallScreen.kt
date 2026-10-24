@file:Suppress("TooManyFunctions") // The call screen's parts: the person, the key, the controls, the permission step.

package dev.kyralabs.pingogram.feature.calls.ui

import android.view.accessibility.AccessibilityManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CallEndReason
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The call screen (`REQUIREMENTS.md` FR-V1, `docs/DESIGN_SYSTEM.md` §6 "Call screen"), laid out as Android's own Phone
 * app: the call's stage or its timer, the other person's name and the key's emojis at the top, their avatar in a
 * [MaterialShapes] cookie that turns while the call rings, then the controls. An incoming call is answered or declined
 * by sliding the handle of a pill (two buttons with TalkBack), or declined to write instead ("Message"); during the
 * call a panel holds mute, the audio route and hold over a wide hang-up button.
 *
 * Phones in landscape (a compact height) put the person at the start and the controls at the end.
 */
@Composable
fun CallScreen(
    state: CallUiState,
    onEvent: (CallEvent) -> Unit,
    onMinimize: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: (userId: Long) -> Unit = {},
    video: CallVideo = PlaceholderVideo,
    pictureInPicture: Boolean = false,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        when (state) {
            CallUiState.Loading, CallUiState.Closed -> {
                Unit
            }

            is CallUiState.NeedsMicrophone -> {
                MicrophoneStep(state, onEvent, onRequestMicrophone, onOpenSettings)
            }

            is CallUiState.InCall -> {
                if (pictureInPicture) {
                    PictureInPicture(state, video)
                } else if (state.video.shows) {
                    VideoInCall(state, onEvent, onMinimize, video)
                } else {
                    InCall(state, onEvent, onMinimize, onMessage)
                }
                // Over the ended call, never in picture-in-picture, whose window has no room for it.
                val rating = state.rating
                if (rating != null && !pictureInPicture) {
                    CallRatingSheet(
                        isVideo = rating.isVideo,
                        onRate = { stars, problems -> onEvent(CallEvent.Rate(stars, problems)) },
                        onDismiss = { onEvent(CallEvent.DismissRating) },
                    )
                }
            }
        }
    }
}

/** Which video a [CallVideo] draws: the other person's, or the user's own camera (the self-view). */
enum class CallVideoSlot { Remote, Self }

/**
 * Draws a call's video in its bounds, filling them: the call activity's renderer ([CallVideoView]), a tonal
 * placeholder in previews and screenshot tests, which cannot draw with OpenGL.
 */
typealias CallVideo = @Composable (slot: CallVideoSlot, mirror: Boolean, modifier: Modifier) -> Unit

internal val PlaceholderVideo: CallVideo = { slot, _, modifier ->
    val color =
        when (slot) {
            CallVideoSlot.Remote -> MaterialTheme.colorScheme.surfaceContainerHighest
            CallVideoSlot.Self -> MaterialTheme.colorScheme.secondaryContainer
        }
    Box(modifier.background(color))
}

@Composable
private fun InCall(
    state: CallUiState.InCall,
    onEvent: (CallEvent) -> Unit,
    onMinimize: () -> Unit,
    onMessage: (userId: Long) -> Unit,
) {
    CallLayout(
        top = { MinimizeButton(onMinimize) },
        header = {
            CallHeader(
                name = state.person.name,
                status = { CallStatus(state) },
                subtitle = {
                    AnimatedVisibility(
                        visible = state.emojis.isNotEmpty(),
                        enter =
                            expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit =
                            shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) { KeyEmojis(state.emojis, state.person.name) }
                },
            )
        },
        avatar = { size ->
            CallAvatar(state.person, size, ringing = state.stage.isRinging(), large = state.isIncomingRinging)
        },
        controls = { docked ->
            val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val resize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            AnimatedContent(
                targetState = state.controls(),
                transitionSpec = {
                    (fadeIn(fade) togetherWith fadeOut(fade)).using(SizeTransform { _, _ -> resize })
                },
                contentAlignment = Alignment.BottomCenter,
                label = "callControls",
            ) { controls ->
                when (controls) {
                    Controls.Incoming -> IncomingControls(docked, onEvent) { onMessage(state.person.userId) }
                    Controls.InCall -> CallControlsPanel(state, docked, onEvent)
                    Controls.None -> Spacer(Modifier.fillMaxWidth())
                }
            }
        },
    )
}

/** Which controls the call shows. */
private enum class Controls { Incoming, InCall, None }

private fun CallUiState.InCall.controls(): Controls =
    when {
        isIncomingRinging -> Controls.Incoming
        stage is CallStage.Ended -> Controls.None
        else -> Controls.InCall
    }

/**
 * The top row, the header and the avatar above the controls, or, in a window of compact height, the controls beside
 * them. [avatar] gets the size it may take; [controls] whether it is docked to the window's bottom edge.
 */
@Composable
private fun CallLayout(
    top: @Composable () -> Unit,
    header: @Composable () -> Unit,
    avatar: @Composable (size: Dp) -> Unit,
    controls: @Composable (docked: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones in landscape: the same height below which the message actions move into a sheet.
        if (maxHeight < MaterialTheme.sizes.messageMenuMinWindowHeight) {
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(MaterialTheme.spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    PersonColumn(top, header, avatar)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { controls(false) }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_SIDES))
                        .padding(horizontal = MaterialTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { PersonColumn(top, header, avatar) }
                Box(Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)) { controls(true) }
            }
        }
    }
}

@Composable
private fun ColumnScope.PersonColumn(
    top: @Composable () -> Unit,
    header: @Composable () -> Unit,
    avatar: @Composable (size: Dp) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) { top() }
    header()
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        avatar(minOf(maxWidth * AVATAR_WIDTH_FRACTION, maxHeight, MaterialTheme.sizes.illustrationMaxSize))
    }
}

/** The call's stage above the name, as the system's dialer places it, and [subtitle] below. */
@Composable
private fun CallHeader(
    name: String,
    status: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        status()
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
            name.ifBlank { stringResource(R.string.call_someone) },
            style = MaterialTheme.typography.displaySmall.userContent,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(MaterialTheme.spacing.medium))
        subtitle()
    }
}

/**
 * A video call (`docs/adr/0019-video-and-group-calls-scope.md`): the other person's video fills the window (the user's
 * own camera while theirs is off), the call's stage and the name on a [pingogramColors] media overlay pill at the top,
 * the self-view in a `large`-rounded card at the end above the controls, with "Switch camera" on it, and the same
 * controls panel at the bottom (at the end in a window of compact height).
 */
@Composable
private fun VideoInCall(
    state: CallUiState.InCall,
    onEvent: (CallEvent) -> Unit,
    onMinimize: () -> Unit,
    video: CallVideo,
) {
    val remote = state.video.remote != RemoteVideo.Off
    val front = state.video.camera == CameraFacing.Front
    val name = state.person.name.ifBlank { stringResource(R.string.call_someone) }
    val remoteDescription = stringResource(R.string.call_remote_video, name)
    val selfDescription = stringResource(R.string.call_self_view)
    Box(Modifier.fillMaxSize()) {
        video(
            if (remote) CallVideoSlot.Remote else CallVideoSlot.Self,
            !remote && front,
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = if (remote) remoteDescription else selfDescription },
        )
        if (state.video.remote == RemoteVideo.Paused) {
            OverlayPill(Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.call_video_paused), style = MaterialTheme.typography.bodyMedium)
            }
        }
        VideoOverlay(state, onEvent, onMinimize, video, name)
    }
}

/** Over the video: the header at the top, the self-view and the controls at the bottom (at the end in landscape). */
@Composable
private fun VideoOverlay(
    state: CallUiState.InCall,
    onEvent: (CallEvent) -> Unit,
    onMinimize: () -> Unit,
    video: CallVideo,
    name: String,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < MaterialTheme.sizes.messageMenuMinWindowHeight
        val selfView: @Composable (Modifier) -> Unit = { modifier ->
            if (state.video.remote != RemoteVideo.Off && state.video.camera != null) {
                SelfView(
                    mirror = state.video.camera == CameraFacing.Front,
                    canSwitch = state.video.canSwitchCamera,
                    aspectRatio = if (compactHeight) SELF_VIEW_ASPECT_LANDSCAPE else SELF_VIEW_ASPECT,
                    video = video,
                    onSwitch = { onEvent(CallEvent.SwitchCamera) },
                    modifier = modifier.width(minOf(maxWidth, maxHeight) * SELF_VIEW_FRACTION),
                )
            }
        }
        val top: @Composable () -> Unit = {
            Box(Modifier.fillMaxWidth()) { MinimizeButton(onMinimize, overlay = true) }
            VideoHeader(state, name)
        }
        if (compactHeight) {
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(MaterialTheme.spacing.large),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    top()
                    Spacer(Modifier.weight(1f))
                    selfView(Modifier.align(Alignment.End))
                }
                Spacer(Modifier.width(MaterialTheme.spacing.large))
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    CallControlsPanel(state, docked = false, onEvent)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(TOP_SIDES))
                        .padding(horizontal = MaterialTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { top() }
                Spacer(Modifier.weight(1f))
                selfView(
                    Modifier
                        .align(Alignment.End)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(MaterialTheme.spacing.large),
                )
                Box(Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)) {
                    CallControlsPanel(state, docked = true, onEvent)
                }
            }
        }
    }
}

/**
 * The call in picture-in-picture: only its video (the other person's, the user's own while theirs is off), or the
 * avatar once neither camera sends; the window's own controls hang up and return to the screen.
 */
@Composable
private fun PictureInPicture(
    state: CallUiState.InCall,
    video: CallVideo,
) {
    val remote = state.video.remote != RemoteVideo.Off
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.video.shows) {
            video(
                if (remote) CallVideoSlot.Remote else CallVideoSlot.Self,
                !remote && state.video.camera == CameraFacing.Front,
                Modifier.fillMaxSize(),
            )
        } else {
            CallAvatar(state.person, minOf(maxWidth, maxHeight) * AVATAR_WIDTH_FRACTION, ringing = false, large = true)
        }
    }
}

/** The stage and the name over the video, on a media overlay pill, with the key's emojis below once agreed. */
@Composable
private fun VideoHeader(
    state: CallUiState.InCall,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OverlayPill {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CallStatus(state, color = MaterialTheme.pingogramColors.onMediaOverlay)
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge.userContent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        if (state.emojis.isNotEmpty()) {
            Spacer(Modifier.height(MaterialTheme.spacing.small))
            KeyEmojis(state.emojis, state.person.name)
        }
    }
}

/** Text on the video: the media overlay's colors in a `large`-rounded pill. */
@Composable
private fun OverlayPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.pingogramColors.mediaOverlay,
        contentColor = MaterialTheme.pingogramColors.onMediaOverlay,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)) {
            content()
        }
    }
}

/**
 * The user's own camera in a `large`-rounded card of [aspectRatio] (the call screen's 3:4, 4:3 in landscape), mirrored
 * for the front camera as a mirror shows; "Switch camera" at its bottom end where the device has a front and a back
 * camera.
 */
@Composable
internal fun SelfView(
    mirror: Boolean,
    canSwitch: Boolean,
    aspectRatio: Float,
    video: CallVideo,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.call_self_view)
    Box(
        modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.large),
    ) {
        video(CallVideoSlot.Self, mirror, Modifier.fillMaxSize().semantics { contentDescription = description })
        if (canSwitch) {
            FilledTonalIconButton(
                onClick = onSwitch,
                shapes = IconButtonDefaults.shapes(),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.pingogramColors.mediaOverlay,
                        contentColor = MaterialTheme.pingogramColors.onMediaOverlay,
                    ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(MaterialTheme.spacing.extraSmall),
            ) {
                Icon(
                    painterResource(R.drawable.ic_cameraswitch),
                    contentDescription = stringResource(R.string.call_switch_camera),
                )
            }
        }
    }
}

/**
 * The four emojis of the call's key in a pill; tapping it says what they are for. Read as one item: "Encryption key:
 * 😀 🐶 🍎 🚗".
 */
@Composable
private fun KeyEmojis(
    emojis: List<String>,
    name: String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.call_key_description, emojis.joinToString(" "))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceBright)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.call_key_explain)) {
                        expanded = !expanded
                    }.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_encrypted),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
            Text(emojis.joinToString(" "), style = MaterialTheme.typography.titleLarge)
        }
        if (expanded) {
            Text(
                stringResource(R.string.call_key_explanation, name.ifBlank { stringResource(R.string.call_someone) }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                        .padding(top = MaterialTheme.spacing.small),
            )
        }
    }
}

/**
 * The avatar clipped to a cookie (`docs/DESIGN_SYSTEM.md` §4): [MaterialShapes.Cookie9Sided] while the call rings,
 * turning a lobe at a time on the default spatial spring with the photo kept upright, morphing to the calmer
 * [MaterialShapes.Cookie12Sided] once it is answered. An incoming call shows it at [size]; otherwise it shrinks to
 * [IN_CALL_SCALE] of it, in its graphics layer, so nothing is measured again. Still with reduced motion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CallAvatar(
    person: CallPerson,
    size: Dp,
    ringing: Boolean,
    large: Boolean,
) {
    val shape = rememberMorphShape(MaterialShapes.Cookie12Sided, MaterialShapes.Cookie9Sided, morphed = ringing)
    val turn = rememberRingTurn(ringing)
    val scale = animateFloatAsState(if (large) 1f else IN_CALL_SCALE, MaterialTheme.motionScheme.defaultSpatialSpec())
    val name = person.name.ifBlank { stringResource(R.string.call_someone) }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = turn()
            }.clip(shape),
    ) {
        Avatar(
            photo = person.photo,
            name = name,
            id = person.userId,
            size = size,
            modifier = Modifier.graphicsLayer { rotationZ = -turn() },
        )
    }
}

/**
 * The avatar's angle while [ringing]: a ninth of a turn (one lobe of the cookie) every [RING_TURN_MILLIS], read in the
 * draw phase. It stays where it is once the call is answered, at 0 with reduced motion, and where infinite animations
 * are off (UI tests).
 */
@Composable
private fun rememberRingTurn(ringing: Boolean): () -> Float {
    val rotation = remember { Animatable(0f) }
    val animate = ringing && !isReducedMotion()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(animate, spec) {
        if (!animate) return@LaunchedEffect
        val turn: suspend () -> Unit = {
            while (true) {
                delay(RING_TURN_MILLIS)
                // Kept within a turn, so the angle never grows without bound.
                rotation.snapTo(rotation.value % FULL_TURN)
                rotation.animateTo(rotation.value + FULL_TURN / RING_LOBES, spec)
            }
        }
        val policy = coroutineContext[InfiniteAnimationPolicy]
        if (policy != null) policy.onInfiniteOperation(turn) else turn()
    }
    return remember(rotation) { { rotation.value } }
}

/** The call's stage, or its timer and the connection's quality once connected; a polite live region. */
@Composable
private fun CallStatus(
    state: CallUiState.InCall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val stage = state.stage
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stage == CallStage.Connected || stage == CallStage.Reconnecting) {
            val weak = stage == CallStage.Reconnecting
            Icon(
                painterResource(
                    if (weak) R.drawable.ic_signal_cellular_alt_1_bar else R.drawable.ic_signal_cellular_alt,
                ),
                contentDescription = stringResource(if (weak) R.string.call_signal_weak else R.string.call_signal_good),
                tint = if (weak) MaterialTheme.colorScheme.error else color,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
            Spacer(Modifier.size(MaterialTheme.spacing.small))
        }
        val text =
            when {
                stage == CallStage.Connected && state.isOnHold -> stringResource(R.string.call_status_on_hold)
                stage == CallStage.Connected -> state.duration.orEmpty()
                else -> stageText(stage, state.isOutgoing, state.isVideoCall)
            }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun stageText(
    stage: CallStage,
    isOutgoing: Boolean,
    isVideo: Boolean,
): String =
    when (stage) {
        CallStage.Requesting -> {
            stringResource(R.string.call_status_requesting)
        }

        CallStage.Ringing -> {
            stringResource(
                when {
                    isOutgoing -> R.string.call_status_calling
                    isVideo -> R.string.call_status_incoming_video
                    else -> R.string.call_status_incoming
                },
            )
        }

        CallStage.RingingThere -> {
            stringResource(R.string.call_status_ringing)
        }

        CallStage.Connecting -> {
            stringResource(R.string.call_status_connecting)
        }

        CallStage.Connected -> {
            stringResource(R.string.call_status_connected)
        }

        CallStage.Reconnecting -> {
            stringResource(R.string.call_status_reconnecting)
        }

        is CallStage.Ended -> {
            endText(stage.reason, isOutgoing)
        }
    }

@Composable
private fun endText(
    reason: CallEndReason,
    isOutgoing: Boolean,
): String =
    when (reason) {
        CallEndReason.HungUp -> {
            stringResource(R.string.call_status_ended)
        }

        CallEndReason.Missed -> {
            stringResource(
                if (isOutgoing) R.string.call_end_no_answer else R.string.call_end_missed,
            )
        }

        CallEndReason.Declined -> {
            stringResource(if (isOutgoing) R.string.call_end_busy else R.string.call_end_declined)
        }

        CallEndReason.Disconnected -> {
            stringResource(R.string.call_end_disconnected)
        }

        is CallEndReason.Failed -> {
            errorText(reason.error)
        }
    }

/**
 * An incoming call: "Message" (decline and write instead), then the slider that answers toward its end and declines
 * toward its start. Screen readers, which cannot slide, get the two as buttons.
 */
@Composable
private fun IncomingControls(
    docked: Boolean,
    onEvent: (CallEvent) -> Unit,
    onMessage: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val answer = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onEvent(CallEvent.Answer)
    }
    val decline = { onEvent(CallEvent.HangUp) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(bottomInsets(docked))
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssistChip(
            onClick = onMessage,
            label = { Text(stringResource(R.string.call_message)) },
            leadingIcon = {
                Icon(
                    painterResource(UiR.drawable.ic_chat),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            border = null,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
        if (rememberTouchExploration()) {
            AnswerButtons(onAnswer = answer, onDecline = decline)
        } else {
            AnswerSlider(onAnswer = answer, onDecline = decline)
        }
    }
}

/**
 * A pill with "Decline" at its start and "Answer" at its end and a handle between them, which the user slides toward
 * either (mirrored right to left), on the gesture in the layout phase. Past [COMMIT_FRACTION] of the way a
 * `TextHandleMove` haptic says it will take; releasing there answers or declines, anywhere else the handle springs back
 * on the default spatial spring. Its phone turns into a hang-up as it nears "Decline". A tap nudges it toward "Answer",
 * as a hint. Screen readers hear one item with "Answer" and "Decline" as custom actions (Switch Access too).
 */
@Composable
private fun AnswerSlider(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val handleWidth = with(LocalDensity.current) { SliderHandleSize.width.toPx() }
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val slider = remember(direction) { SliderState(direction) }
    val dragState =
        rememberDraggableState { delta ->
            if (slider.drag(delta)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    Box(
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.illustrationMaxSize * SLIDER_WIDTH_SCALE)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(MaterialTheme.spacing.small)
                .onSizeChanged { slider.maxOffset = ((it.width - handleWidth) / 2f).coerceAtLeast(0f) }
                .answerActions(onAnswer, onDecline),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .graphicsLayer { alpha = 1f - abs(slider.progress()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.call_decline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.call_answer), style = MaterialTheme.typography.bodyLarge)
        }
        SliderHandle(
            slider = slider,
            dragState = dragState,
            onRelease = {
                when (slider.release(fast, settle)) {
                    SliderOutcome.Answer -> {
                        onAnswer()
                    }

                    SliderOutcome.Decline -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        onDecline()
                    }

                    null -> {
                        Unit
                    }
                }
            },
            onTap = { scope.launch { slider.nudge(fast, settle) } },
        )
    }
}

/** The slider's semantics: one item, answered and declined through its custom actions. */
@Composable
private fun Modifier.answerActions(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
): Modifier {
    val answer = stringResource(R.string.call_answer)
    val decline = stringResource(R.string.call_decline)
    val description = stringResource(R.string.call_slide_description)
    return clearAndSetSemantics {
        contentDescription = description
        customActions =
            listOf(
                CustomAccessibilityAction(answer) {
                    onAnswer()
                    true
                },
                CustomAccessibilityAction(decline) {
                    onDecline()
                    true
                },
            )
    }
}

/** The handle, offset by the drag in the layout phase, its phone turning toward a hang-up near "Decline". */
@Composable
private fun SliderHandle(
    slider: SliderState,
    dragState: DraggableState,
    onRelease: suspend () -> Unit,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .offset { IntOffset(slider.offset.roundToInt(), 0) }
            .size(SliderHandleSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .draggable(dragState, Orientation.Horizontal, onDragStopped = { onRelease() })
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(UiR.drawable.ic_call_filled),
            contentDescription = null,
            tint = MaterialTheme.pingogramColors.online,
            modifier =
                Modifier
                    .size(IconButtonDefaults.mediumIconSize)
                    .graphicsLayer { rotationZ = HANG_UP_ROTATION * (-slider.progress()).coerceAtLeast(0f) },
        )
    }
}

/** The handle: a medium wide icon button's size. */
private val SliderHandleSize: DpSize
    @Composable get() = IconButtonDefaults.mediumContainerSize(IconButtonWidthOption.Wide)

private enum class SliderOutcome { Answer, Decline }

/**
 * Where the slider's handle is, in pixels from the middle (physical: right is positive), and how far it may go;
 * [direction] is -1 right to left, where "Answer" is on the left.
 */
private class SliderState(
    private val direction: Float,
) {
    var offset by mutableFloatStateOf(0f)
    var maxOffset by mutableFloatStateOf(0f)
    private var committed = false
    private var pastThreshold = false

    /** -1 at "Decline", 1 at "Answer", whatever the layout's direction. */
    fun progress(): Float = if (maxOffset > 0f) offset * direction / maxOffset else 0f

    /** Moves the handle by [delta]; true as it passes [COMMIT_FRACTION] toward either side. */
    fun drag(delta: Float): Boolean {
        if (committed) return false
        offset = (offset + delta).coerceIn(-maxOffset, maxOffset)
        val past = abs(progress()) >= COMMIT_FRACTION
        val crossed = past && !pastThreshold
        pastThreshold = past
        return crossed
    }

    /** Past the threshold, slides to its end and answers or declines; anywhere else, springs back. */
    suspend fun release(
        fast: FiniteAnimationSpec<Float>,
        settle: FiniteAnimationSpec<Float>,
    ): SliderOutcome? {
        val outcome =
            when {
                committed -> null
                progress() >= COMMIT_FRACTION -> SliderOutcome.Answer
                progress() <= -COMMIT_FRACTION -> SliderOutcome.Decline
                else -> null
            }
        if (outcome == null) {
            if (!committed) animateTo(0f, settle)
            return null
        }
        committed = true
        animateTo(if (outcome == SliderOutcome.Answer) maxOffset * direction else -maxOffset * direction, fast)
        return outcome
    }

    /** A tap's hint: a little toward "Answer" and back. */
    suspend fun nudge(
        fast: FiniteAnimationSpec<Float>,
        settle: FiniteAnimationSpec<Float>,
    ) {
        if (committed) return
        animateTo(maxOffset * NUDGE_FRACTION * direction, fast)
        animateTo(0f, settle)
    }

    private suspend fun animateTo(
        target: Float,
        spec: FiniteAnimationSpec<Float>,
    ) {
        animate(offset, target, animationSpec = spec) { value, _ -> offset = value }
    }
}

/** Decline at the start, answer at the end, as round buttons: what screen readers get instead of the slider. */
@Composable
private fun AnswerButtons(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LabeledButton(R.string.call_decline) {
            HeroButton(
                icon = R.drawable.ic_call_end_filled,
                label = R.string.call_decline,
                container = MaterialTheme.colorScheme.error,
                content = MaterialTheme.colorScheme.onError,
                onClick = onDecline,
            )
        }
        LabeledButton(R.string.call_answer) {
            HeroButton(
                icon = UiR.drawable.ic_call_filled,
                label = R.string.call_answer,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = onAnswer,
            )
        }
    }
}

/** Whether a screen reader explores by touch (TalkBack), following its changes. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.run { isEnabled && isTouchExplorationEnabled } == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * The call's controls on a `surfaceContainerHigh` panel, docked to the window's bottom with `extraLarge` top corners (a
 * card of its own in landscape): mute, the audio route and hold as labelled toggle buttons, then a wide hang-up pill.
 */
@Composable
private fun CallControlsPanel(
    state: CallUiState.InCall,
    docked: Boolean,
    onEvent: (CallEvent) -> Unit,
) {
    val canHold = state.stage == CallStage.Connected || state.stage == CallStage.Reconnecting
    val shape =
        if (docked) {
            MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        } else {
            MaterialTheme.shapes.extraLarge
        }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .windowInsetsPadding(bottomInsets(docked))
                    .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LabeledButton(R.string.call_mute) {
                    Toggle(
                        checked = state.isMuted,
                        icon = if (state.isMuted) R.drawable.ic_mic_off_filled else UiR.drawable.ic_mic_filled,
                        label = R.string.call_mute,
                    ) { onEvent(CallEvent.ToggleMute) }
                }
                RouteButton(state.route, state.routes) { onEvent(CallEvent.SelectRoute(it)) }
                if (state.video.hasCamera) {
                    LabeledButton(R.string.call_camera) {
                        Toggle(
                            checked = state.video.camera != null,
                            icon =
                                if (state.video.camera != null) {
                                    R.drawable.ic_videocam_filled
                                } else {
                                    R.drawable.ic_videocam_off_filled
                                },
                            label = R.string.call_camera,
                            enabled = !state.isOnHold,
                        ) { onEvent(CallEvent.ToggleCamera) }
                    }
                }
                LabeledButton(R.string.call_hold) {
                    Toggle(
                        checked = state.isOnHold,
                        icon = UiR.drawable.ic_pause_filled,
                        label = R.string.call_hold,
                        enabled = canHold,
                    ) { onEvent(CallEvent.ToggleHold) }
                }
            }
            Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
            HangUpButton { onEvent(CallEvent.HangUp) }
        }
    }
}

/** A large round button in [container], with the press morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
        modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(IconButtonDefaults.largeIconSize),
        )
    }
}

/** Why the microphone is needed, before Android asks (or once it no longer does, the app's settings). */
@Composable
private fun MicrophoneStep(
    state: CallUiState.NeedsMicrophone,
    onEvent: (CallEvent) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    CallLayout(
        top = {},
        header = { CallHeader(state.person.name, status = {}, subtitle = {}) },
        avatar = { size -> CallAvatar(state.person, size, ringing = false, large = false) },
        controls = { docked ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(bottomInsets(docked))
                    .padding(MaterialTheme.spacing.large),
            ) {
                Text(
                    stringResource(
                        if (state.canAsk) R.string.call_microphone_rationale else R.string.call_microphone_settings,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MaterialTheme.spacing.extraLarge))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEvent(CallEvent.DismissMicrophone) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(if (state.isAnswering) R.string.call_decline else R.string.call_not_now))
                    }
                    Spacer(Modifier.weight(1f))
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
        },
    )
}

private val TOP_SIDES = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

private fun CallStage.isRinging() =
    this == CallStage.Requesting || this == CallStage.Ringing || this == CallStage.RingingThere

/** The self-view's width against the window's shorter side, and its shape (portrait; landscape in landscape). */
private const val SELF_VIEW_FRACTION = 0.3f
private const val SELF_VIEW_ASPECT = 3f / 4f
internal const val SELF_VIEW_ASPECT_LANDSCAPE = 4f / 3f

/** The avatar's largest width against the window's, as the system's dialer shows an incoming caller. */
private const val AVATAR_WIDTH_FRACTION = 0.6f

/** The avatar during a call and while calling, against an incoming call's. */
private const val IN_CALL_SCALE = 0.72f

/** How long the avatar rests between its turns while ringing; the spring of a turn takes about half of it. */
private const val RING_TURN_MILLIS = 1_200L

/** The lobes of the ringing cookie ([MaterialShapes.Cookie9Sided]): each turn moves one lobe on. */
private const val RING_LOBES = 9

private const val FULL_TURN = 360f

/** How far the handle goes before releasing answers or declines, and how far a tap nudges it. */
private const val COMMIT_FRACTION = 0.7f
private const val NUDGE_FRACTION = 0.25f

/** The phone's turn into a hang-up at "Decline". */
private const val HANG_UP_ROTATION = 135f

/** The slider's widest, against the illustrations': about the width of a phone's content. */
private const val SLIDER_WIDTH_SCALE = 1.4f

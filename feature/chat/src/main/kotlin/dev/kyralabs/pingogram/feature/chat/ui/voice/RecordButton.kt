package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VideoNoteEvent
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.composerActionShapes
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The composer's microphone in the send button's place while the field is empty (`docs/DESIGN_SYSTEM.md` §6
 * Composer): the composer's 56 dp round button (`IconButtonDefaults.mediumContainerSize`) in `tertiaryContainer`,
 * which morphs into a wide `primary` pill while it records, its icon giving way to a
 * [LinearWavyProgressIndicator] whose waves rise and fall with the voice ([RecordingMeter.level]); locked, the pill
 * sends. Its colors cross-fade on the motion scheme's effects spring. Switched to round video messages
 * ([RecordModeUiState.video], `docs/adr/0032-round-video-messages.md`), it shows a camera instead of the microphone and
 * records video with the same gestures.
 *
 * - **Tap to switch**: where the device has a camera ([RecordModeUiState.canSwitch]), a tap switches the button between
 *   voice and video ([VideoNoteEvent.SwitchMode], a segment tick), as Telegram's apps do, and its tooltip says what it
 *   records now; switching to video without the camera's or the microphone's permission opens their rationale first
 *   ([VideoNoteEvent.PermissionNeeded]). A press becomes a hold after [HOLD_TO_RECORD_MILLIS]; without a camera, a
 *   press records at once.
 * - **Hold to record**: holding it starts recording ([VoiceEvent.Start]) with a long-press haptic, if the permissions
 *   are granted (the microphone, and the camera for video); else it asks for them ([VoiceEvent.PermissionNeeded],
 *   [VideoNoteEvent.PermissionNeeded]). The pill follows the finger toward the start ([drag]); past
 *   `recordingCancelDistance` the recording is cancelled ([VoiceEvent.Cancel]), past `recordingLockDistance` up it
 *   locks ([VoiceEvent.Lock]), both with the threshold haptic; releasing sends ([VoiceEvent.Send]) with a confirming
 *   haptic, or a rejecting one when it is too short to send. The gesture takes every touch of the press, so the
 *   button's own click never fires for it.
 * - **Without touch** (a screen reader, a keyboard), activating it records locked at once: holding is a touch gesture.
 *   Screen readers hear "Record voice message" or "Record video message", and switch through a custom action.
 * - **Locked**: the pill is the send button.
 */
@OptIn(ExperimentalMaterial3Api::class) // TooltipBox
@Composable
internal fun RecordButton(
    recording: VoiceRecordingUiState?,
    drag: RecordDrag,
    onEvent: (VoiceEvent) -> Unit,
    modifier: Modifier = Modifier,
    mode: RecordModeUiState = RecordModeUiState(),
    onVideoEvent: (VideoNoteEvent) -> Unit = {},
) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val actions = rememberRecordActions(recording, mode, onEvent, onVideoEvent) { scope.launch { tooltip.show() } }
    val uniform = IconButtonDefaults.mediumContainerSize()
    val wide = IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
    val width =
        animateDpAsState(
            targetValue = if (recording != null) wide.width else uniform.width,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "recordButtonWidth",
        )
    val shapes = composerActionShapes()
    val video = recording?.let { it.video != null } ?: mode.video
    val input = recordInputOf(video, canSwitch = mode.canSwitch && recording == null)
    val idle = if (video) ButtonFace.Camera else ButtonFace.Mic
    val face = recording?.let { if (it.isLocked) ButtonFace.Send else ButtonFace.Level } ?: idle
    val colors = recordButtonColors(mic = face == ButtonFace.Mic || face == ButtonFace.Camera)
    // Shown after a switch only: touch would show it on the long press that records.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(input.label) } },
        state = tooltip,
        modifier = modifier,
        enableUserInput = false,
    ) {
        FilledIconButton(
            onClick = actions::sendLocked,
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .animatedWidth(width, uniform.height)
                    .recordInput(input, drag, actions, isLocked = face == ButtonFace.Send),
            // A pill while it records, the press morph only while it is the microphone or camera, or sends.
            shapes = if (face == ButtonFace.Level) IconButtonShapes(shapes.shape, shapes.shape) else shapes,
            colors = colors,
        ) {
            Crossfade(
                targetState = face,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "recordButtonFace",
            ) { RecordButtonFace(it, recording?.meter, video) }
        }
    }
}

/** What the record button's input needs, for a button that records [video] and, where [canSwitch], switches. */
@Composable
private fun recordInputOf(
    video: Boolean,
    canSwitch: Boolean,
): RecordInput {
    val sizes = MaterialTheme.sizes
    return RecordInput(
        layoutDirection = LocalLayoutDirection.current,
        cancelDistance = sizes.recordingCancelDistance,
        lockDistance = sizes.recordingLockDistance,
        label = stringResource(if (video) R.string.video_note_record else R.string.voice_record),
        actionLabel = stringResource(R.string.voice_record_action),
        switchLabel =
            stringResource(if (video) R.string.video_note_switch_to_voice else R.string.video_note_switch_to_video)
                .takeIf { canSwitch },
    )
}

/**
 * The record button's colors: `tertiaryContainer` while it is the microphone or the camera ([mic]), `primary` while it
 * records, cross-fading on the motion scheme's effects spring.
 */
@Composable
private fun recordButtonColors(mic: Boolean): IconButtonColors {
    val colorScheme = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue = if (mic) colorScheme.tertiaryContainer else colorScheme.primary,
        animationSpec = effects,
        label = "recordButtonContainer",
    )
    val content by animateColorAsState(
        targetValue = if (mic) colorScheme.onTertiaryContainer else colorScheme.onPrimary,
        animationSpec = effects,
        label = "recordButtonContent",
    )
    return IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content)
}

/** What the record button shows: the microphone or the camera, the voice's level while held, or send while locked. */
private enum class ButtonFace {
    Mic,
    Camera,
    Level,
    Send,
}

/** The microphone or camera icon, the wavy level of the voice in [meter], or the send icon of a [video] message. */
@Composable
private fun RecordButtonFace(
    face: ButtonFace,
    meter: RecordingMeter?,
    video: Boolean,
) {
    when (face) {
        ButtonFace.Mic, ButtonFace.Camera -> {
            Icon(
                painter =
                    painterResource(
                        if (face ==
                            ButtonFace.Camera
                        ) {
                            R.drawable.ic_videocam_filled
                        } else {
                            UiR.drawable.ic_mic_filled
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }

        ButtonFace.Level -> {
            LinearWavyProgressIndicator(
                progress = { 1f },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(MaterialTheme.sizes.waveformHeight)
                        .padding(horizontal = MaterialTheme.spacing.small),
                color = LocalContentColor.current,
                trackColor = Color.Transparent,
                stopSize = 0.dp,
                amplitude = { meter?.level ?: 0f },
                wavelength = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
                waveSpeed = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
            )
        }

        ButtonFace.Send -> {
            Icon(
                painter = painterResource(UiR.drawable.ic_send_filled),
                contentDescription = stringResource(if (video) R.string.video_note_send else R.string.voice_send),
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
    }
}

/**
 * What the record button does, with the latest recording, mode and callbacks: start (checking the permissions), switch
 * between voice and video, and the hold gesture's press, threshold and release, each with its haptic.
 */
@Suppress("LongParameterList") // What it reads from the composition, the recording, the mode and its callbacks.
private class RecordActions(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val focusManager: FocusManager,
    private val recording: State<VoiceRecordingUiState?>,
    private val mode: State<RecordModeUiState>,
    private val onEvent: State<(VoiceEvent) -> Unit>,
    private val onVideoEvent: State<(VideoNoteEvent) -> Unit>,
    private val onSwitched: () -> Unit,
) {
    val isRecording: Boolean get() = recording.value != null
    val isLocked: Boolean get() = recording.value?.isLocked == true

    /** Whether a tap switches between voice and video: the device has a camera, and nothing records. */
    val canSwitch: Boolean get() = mode.value.canSwitch && !isRecording

    /** Starts recording, [locked] or held, if the permissions are granted (true); else asks for them (false). */
    fun start(locked: Boolean): Boolean {
        val video = mode.value.video
        val granted = if (video) context.hasVideoPermissions() else context.has(Manifest.permission.RECORD_AUDIO)
        if (granted) {
            focusManager.clearFocus()
            onEvent.value(VoiceEvent.Start(locked))
        } else if (video) {
            onVideoEvent.value(VideoNoteEvent.PermissionNeeded(switching = false))
        } else {
            onEvent.value(VoiceEvent.PermissionNeeded)
        }
        return granted
    }

    /** A tap: switches between voice and video, asking for video's permissions first where they are missing. */
    fun switch() {
        if (!canSwitch) return
        val toVideo = !mode.value.video
        if (toVideo && !context.hasVideoPermissions()) {
            onVideoEvent.value(VideoNoteEvent.PermissionNeeded(switching = true))
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onVideoEvent.value(VideoNoteEvent.SwitchMode)
        onSwitched()
    }

    fun press(): Boolean =
        start(locked = false).also { started ->
            if (started) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

    fun threshold(event: VoiceEvent) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onEvent.value(event)
    }

    fun release() {
        // A video message that reached its limit was sent already: nothing to confirm.
        recording.value?.let { current ->
            val longEnough = current.meter.elapsedMillis >= ChatRecorder.MIN_DURATION_MILLIS
            haptics.performHapticFeedback(if (longEnough) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        }
        onEvent.value(VoiceEvent.Send)
    }

    /** The button's click: sends a locked recording; nothing otherwise (holding records). */
    fun sendLocked() {
        if (!isLocked) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onEvent.value(VoiceEvent.Send)
    }
}

@Composable
private fun rememberRecordActions(
    recording: VoiceRecordingUiState?,
    mode: RecordModeUiState,
    onEvent: (VoiceEvent) -> Unit,
    onVideoEvent: (VideoNoteEvent) -> Unit,
    onSwitched: () -> Unit,
): RecordActions {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val currentRecording = rememberUpdatedState(recording)
    val currentMode = rememberUpdatedState(mode)
    val currentOnEvent = rememberUpdatedState(onEvent)
    val currentOnVideoEvent = rememberUpdatedState(onVideoEvent)
    val currentOnSwitched = rememberUpdatedState(onSwitched)
    return remember(context, haptics, focusManager) {
        RecordActions(
            context = context,
            haptics = haptics,
            focusManager = focusManager,
            recording = currentRecording,
            mode = currentMode,
            onEvent = currentOnEvent,
            onVideoEvent = currentOnVideoEvent,
            onSwitched = { currentOnSwitched.value() },
        )
    }
}

/** What the record button's input needs from the composition; [switchLabel] is the switch's action, null for none. */
private class RecordInput(
    val layoutDirection: LayoutDirection,
    val cancelDistance: Dp,
    val lockDistance: Dp,
    val label: String,
    val actionLabel: String,
    val switchLabel: String?,
)

/**
 * The record button's input: the hold gesture ([recordGesture]), outside the layer that moves the button with the
 * finger, so the finger is measured from where it pressed; and while not [isLocked], recording locked at once with
 * Enter or Space and through the semantics' click, which screen readers use.
 */
private fun Modifier.recordInput(
    input: RecordInput,
    drag: RecordDrag,
    actions: RecordActions,
    isLocked: Boolean,
): Modifier {
    val gesture =
        recordGesture(input, drag, actions).graphicsLayer {
            val offset = drag.towardStart.coerceAtMost(input.cancelDistance.toPx())
            translationX = if (input.layoutDirection == LayoutDirection.Ltr) -offset else offset
        }
    if (isLocked) return gesture
    return gesture
        .onPreviewKeyEvent { event ->
            val activates = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
            if (activates && event.type == KeyEventType.KeyUp && !actions.isRecording) actions.start(locked = true)
            activates
        }.clearAndSetSemantics {
            role = Role.Button
            contentDescription = input.label
            onClick(input.actionLabel) {
                if (!actions.isRecording) actions.start(locked = true)
                true
            }
            input.switchLabel?.let { switchLabel ->
                customActions =
                    listOf(
                        CustomAccessibilityAction(switchLabel) {
                            actions.switch()
                            true
                        },
                    )
            }
        }
}

/**
 * The hold gesture: a press that [RecordActions.press] accepts records until it is released (send), slides
 * `cancelDistance` toward the start (cancel) or `lockDistance` up (lock); after a cancel or a lock the rest of the
 * press does nothing. Where a tap switches between voice and video ([RecordActions.canSwitch]), a press released
 * within [HOLD_TO_RECORD_MILLIS] is that tap, and only a longer one records. It takes every change of the press in
 * the initial pass, before the button's click sees it. While locked, presses go to the button.
 */
private fun Modifier.recordGesture(
    input: RecordInput,
    drag: RecordDrag,
    actions: RecordActions,
): Modifier =
    pointerInput(input.layoutDirection, input.cancelDistance, input.lockDistance) {
        val cancelPx = input.cancelDistance.toPx()
        val lockPx = input.lockDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (actions.isLocked) return@awaitEachGesture
            down.consume()
            if (actions.canSwitch && releasedWithin(down.id, HOLD_TO_RECORD_MILLIS)) {
                actions.switch()
                return@awaitEachGesture
            }
            var active = actions.press()
            do {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                change?.consume()
                val pressed = change?.pressed == true
                if (active && !pressed) {
                    actions.release()
                } else if (active && change != null) {
                    val dx = change.position.x - down.position.x
                    drag.towardStart = (if (input.layoutDirection == LayoutDirection.Ltr) -dx else dx).coerceAtLeast(0f)
                    drag.up = (down.position.y - change.position.y).coerceAtLeast(0f)
                    val threshold =
                        when {
                            drag.towardStart > cancelPx -> VoiceEvent.Cancel
                            drag.up > lockPx -> VoiceEvent.Lock
                            else -> null
                        }
                    if (threshold != null) {
                        active = false
                        drag.reset()
                        actions.threshold(threshold)
                    }
                }
            } while (pressed)
            drag.reset()
        }
    }

/** [width] (animated) by [height], read in the layout phase so the animation only lays the button out again. */
private fun Modifier.animatedWidth(
    width: State<Dp>,
    height: Dp,
): Modifier =
    layout { measurable, constraints ->
        val widthPx = constraints.constrainWidth(width.value.roundToPx())
        val heightPx = constraints.constrainHeight(height.roundToPx())
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

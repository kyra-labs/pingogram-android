package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The composer's microphone in the send button's place while the field is empty (`docs/DESIGN_SYSTEM.md` §6
 * Composer): a small [FilledIconButton] (`IconButtonDefaults.smallContainerSize`, in a 48 dp touch target) that
 * morphs into a wide pill while it records, its icon giving way to a [LinearWavyProgressIndicator] whose waves rise and
 * fall with the voice ([RecordingMeter.level]); locked, the pill sends.
 *
 * - **Hold to record**: pressing it starts recording ([VoiceEvent.Start]) with a long-press haptic, if the microphone
 *   permission is granted; else it asks for it ([VoiceEvent.PermissionNeeded]). The pill follows the finger toward
 *   the start ([drag]); past `recordingCancelDistance` the recording is cancelled ([VoiceEvent.Cancel]), past
 *   `recordingLockDistance` up it locks ([VoiceEvent.Lock]), both with the threshold haptic; releasing sends
 *   ([VoiceEvent.Send]) with a confirming haptic, or a rejecting one when it is too short to send. The gesture takes
 *   every touch of the press, so the button's own click never fires for it.
 * - **Without touch** (a screen reader, a keyboard), activating it records locked at once: holding is a touch gesture.
 * - **Locked**: the pill is the send button.
 */
@Composable
internal fun RecordButton(
    recording: VoiceRecordingUiState?,
    drag: RecordDrag,
    onEvent: (VoiceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = rememberRecordActions(recording, onEvent)
    val uniform = IconButtonDefaults.smallContainerSize()
    val wide = IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
    val width =
        animateDpAsState(
            targetValue = if (recording != null) wide.width else uniform.width,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "recordButtonWidth",
        )
    val shapes = IconButtonDefaults.shapes()
    val sizes = MaterialTheme.sizes
    val input =
        RecordInput(
            layoutDirection = LocalLayoutDirection.current,
            cancelDistance = sizes.recordingCancelDistance,
            lockDistance = sizes.recordingLockDistance,
            label = stringResource(R.string.voice_record),
            actionLabel = stringResource(R.string.voice_record_action),
        )
    val face = recording?.let { if (it.isLocked) ButtonFace.Send else ButtonFace.Level } ?: ButtonFace.Mic
    FilledIconButton(
        onClick = actions::sendLocked,
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .animatedWidth(width, uniform.height)
                .recordInput(input, drag, actions, isLocked = face == ButtonFace.Send),
        // A pill while it records, the press morph only while it is the microphone or sends.
        shapes = if (face == ButtonFace.Level) IconButtonShapes(shapes.shape, shapes.shape) else shapes,
    ) {
        Crossfade(
            targetState = face,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "recordButtonFace",
        ) { RecordButtonFace(it, recording?.meter) }
    }
}

/** What the record button shows: the microphone, the voice's level while held, or send while locked. */
private enum class ButtonFace {
    Mic,
    Level,
    Send,
}

/** The microphone icon, the wavy level of the voice in [meter], or the send icon. */
@Composable
private fun RecordButtonFace(
    face: ButtonFace,
    meter: RecordingMeter?,
) {
    when (face) {
        ButtonFace.Mic -> {
            Icon(
                painter = painterResource(UiR.drawable.ic_mic_filled),
                contentDescription = null,
                modifier = Modifier.size(IconButtonDefaults.smallIconSize),
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
                contentDescription = stringResource(R.string.voice_send),
                modifier = Modifier.size(IconButtonDefaults.smallIconSize),
            )
        }
    }
}

/**
 * What the record button does, with the latest recording and callback: start (checking the permission), and the hold
 * gesture's press, threshold and release, each with its haptic.
 */
private class RecordActions(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val focusManager: FocusManager,
    private val recording: State<VoiceRecordingUiState?>,
    private val onEvent: State<(VoiceEvent) -> Unit>,
) {
    val isRecording: Boolean get() = recording.value != null
    val isLocked: Boolean get() = recording.value?.isLocked == true

    /** Starts recording, [locked] or held, if the permission is granted (true); else asks for it (false). */
    fun start(locked: Boolean): Boolean {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            focusManager.clearFocus()
            onEvent.value(VoiceEvent.Start(locked))
        } else {
            onEvent.value(VoiceEvent.PermissionNeeded)
        }
        return granted
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
        val longEnough = (recording.value?.meter?.elapsedMillis ?: 0L) >= ChatRecorder.MIN_DURATION_MILLIS
        haptics.performHapticFeedback(if (longEnough) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
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
    onEvent: (VoiceEvent) -> Unit,
): RecordActions {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val currentRecording = rememberUpdatedState(recording)
    val currentOnEvent = rememberUpdatedState(onEvent)
    return remember(context, haptics, focusManager) {
        RecordActions(context, haptics, focusManager, currentRecording, currentOnEvent)
    }
}

/** What the record button's input needs from the composition. */
private class RecordInput(
    val layoutDirection: LayoutDirection,
    val cancelDistance: Dp,
    val lockDistance: Dp,
    val label: String,
    val actionLabel: String,
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
        }
}

/**
 * The hold gesture: a press that [RecordActions.press] accepts records until it is released (send), slides
 * `cancelDistance` toward the start (cancel) or `lockDistance` up (lock); after a cancel or a lock the rest of the
 * press does nothing. It takes every change of the press in the initial pass, before the button's click sees it.
 * While locked, presses go to the button.
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

package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The buttons of the composer: sending, with its menu of other ways to send, saving an edit, and those in the pill.

/**
 * The send button, the composer's 56 dp round button (`IconButtonDefaults.mediumContainerSize`, `primary`) as Google
 * Messages' (`docs/DESIGN_SYSTEM.md` §6 Composer): a tap sends, a long press (with its haptic) opens the menu of other
 * ways to send below it (without sound; at a time, where [canSchedule]), which screen readers reach as the button's
 * custom actions. Its shape morphs while pressed. Not [enabled], it is disabled, e.g. while picked files are being
 * prepared.
 */
@Composable
internal fun SendButton(
    canSchedule: Boolean,
    onSend: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: () -> Unit,
    enabled: Boolean = true,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val silentLabel = stringResource(R.string.composer_send_silently)
    val scheduleLabel = stringResource(R.string.composer_schedule)
    // The same lambda across recompositions, so the long-press gesture is not restarted.
    val openMenu =
        remember(haptics) {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            }
        }
    Box {
        FilledIconButton(
            onClick = onSend,
            modifier =
                Modifier
                    .size(IconButtonDefaults.mediumContainerSize())
                    .testTag(SEND_BUTTON_TEST_TAG)
                    .longPress(enabled, openMenu)
                    .semantics {
                        customActions =
                            listOfNotNull(
                                CustomAccessibilityAction(silentLabel) {
                                    onSendSilently()
                                    true
                                },
                                if (canSchedule) {
                                    CustomAccessibilityAction(scheduleLabel) {
                                        onSchedule()
                                        true
                                    }
                                } else {
                                    null
                                },
                            )
                    },
            enabled = enabled,
            shapes = composerActionShapes(),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_send_filled),
                contentDescription = stringResource(R.string.composer_send),
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
        SendOptionsMenu(
            expanded = menuOpen,
            canSchedule = canSchedule,
            onDismiss = { menuOpen = false },
            onSendSilently = onSendSilently,
            onSchedule = onSchedule,
        )
    }
}

/** The shapes of the composer's round button: a circle at rest, morphing to the medium pressed shape. */
@Composable
internal fun composerActionShapes(): IconButtonShapes =
    IconButtonDefaults.shapes(
        shape = IconButtonDefaults.mediumRoundShape,
        pressedShape = IconButtonDefaults.mediumPressedShape,
    )

/**
 * Calls [onLongPress] when a press lasts the platform's long-press time while [enabled]; that press's later changes
 * are then taken in the initial pass, before the button's click sees them, so it does not also click.
 */
private fun Modifier.longPress(
    enabled: Boolean,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    do {
                        val change =
                            awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    } while (change != null && change.pressed)
                }
            if (released != null) return@awaitEachGesture
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * The attach button at the start of the composer's pill: a "+" in a 48 dp touch target, as Google Messages' composer
 * has it, whose state layer morphs while pressed. It opens the attach menu ([onAttach]).
 */
@Composable
internal fun AttachButton(onAttach: () -> Unit) {
    IconButton(
        onClick = onAttach,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(R.string.attach),
        )
    }
}

/**
 * The gallery button at the end of the composer's pill while the field is empty, as Google Messages': it opens the
 * system photo picker ([onGallery]) without the attach menu.
 */
@Composable
internal fun GalleryButton(onGallery: () -> Unit) {
    IconButton(
        onClick = onGallery,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_image),
            contentDescription = stringResource(R.string.composer_gallery),
        )
    }
}

/**
 * The button that saves an edit, in the send button's place and as large: a round filled icon button with a check, in
 * the Expressive shapes that morph as it is pressed.
 */
@Composable
internal fun SaveEditButton(onSave: () -> Unit) {
    FilledIconButton(
        onClick = onSave,
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        shapes = composerActionShapes(),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_check),
            contentDescription = stringResource(R.string.composer_edit_save),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/**
 * The other ways to send, under the send button (and an inline bot's result): without sound, and at a time where
 * [canSchedule].
 */
@Composable
internal fun SendOptionsMenu(
    expanded: Boolean,
    canSchedule: Boolean,
    onDismiss: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.composer_send_silently)) },
            onClick = {
                onDismiss()
                onSendSilently()
            },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_notifications_off), null) },
        )
        if (canSchedule) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.composer_schedule)) },
                onClick = {
                    onDismiss()
                    onSchedule()
                },
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_schedule), null) },
            )
        }
    }
}

/** The send button, for `benchmark/`'s journeys. */
internal const val SEND_BUTTON_TEST_TAG = "composer_send"

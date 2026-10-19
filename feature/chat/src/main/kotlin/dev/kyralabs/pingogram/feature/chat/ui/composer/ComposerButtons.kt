package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The buttons beside the composer's text field: sending, with its menu of other ways to send, and saving an edit.

/**
 * The send button, a [SplitButtonLayout] as tall as the text field: the leading button sends, the trailing one opens
 * the menu of other ways to send (without sound; at a time, where [canSchedule]). The trailing button's corners morph
 * to a full circle while the menu is open, and its arrow turns on the motion scheme's spatial spring. Not [enabled],
 * both parts are disabled, e.g. while picked files are being prepared.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SendButton(
    canSchedule: Boolean,
    onSend: () -> Unit,
    onSendSilently: () -> Unit,
    onSchedule: () -> Unit,
    enabled: Boolean = true,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val height = ButtonDefaults.MediumContainerHeight
    val sendLabel = stringResource(R.string.composer_send)
    val optionsLabel = stringResource(R.string.composer_send_options)
    val arrowTurn by animateFloatAsState(
        targetValue = if (menuOpen) HALF_TURN else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "sendOptionsArrow",
    )
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onSend,
                modifier = Modifier.heightIn(min = height).semantics { contentDescription = sendLabel },
                enabled = enabled,
                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_send_filled),
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)),
                )
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.heightIn(min = height).semantics { contentDescription = optionsLabel },
                    enabled = enabled,
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(SplitButtonDefaults.trailingButtonIconSizeFor(height))
                                .graphicsLayer { rotationZ = arrowTurn },
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
        },
    )
}

/**
 * The button that saves an edit, in the send button's place and as tall: a filled icon button with a check, in the
 * Expressive shapes that morph as it is pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SaveEditButton(onSave: () -> Unit) {
    FilledIconButton(
        onClick = onSave,
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_check),
            contentDescription = stringResource(R.string.composer_edit_save),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/** The other ways to send, under the send button's trailing part: without sound, and at a time where [canSchedule]. */
@Composable
private fun SendOptionsMenu(
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

/** How far the send menu's arrow turns while the menu is open, in degrees. */
private const val HALF_TURN = 180f

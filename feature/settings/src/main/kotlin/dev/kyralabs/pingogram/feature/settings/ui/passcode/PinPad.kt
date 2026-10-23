package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The keypad for a PIN: the digits in a phone's order, then delete, 0 and [submitLabel]'s check, at most
 * `pinPadMaxWidth` wide. Each row is a Material 3 Expressive [ButtonGroup] of medium (56 dp) buttons: a pressed key
 * grows and squeezes its neighbours (`animateWidth`) and morphs its shape (`ButtonDefaults.shapesFor`), both on the
 * motion scheme's springs. Digits and delete are tonal, the check is the filled primary action, showing a
 * [LoadingIndicator] while [isChecking]. Keys tick under the finger ([HapticFeedbackType.KeyboardTap]).
 *
 * With a hardware keyboard, digits, Backspace and Enter work while a key has the focus. Nothing has a fixed height,
 * so the digits never clip at large font scales.
 */
@Composable
internal fun PinPad(
    input: PasscodeInput,
    onSubmit: () -> Unit,
    submitLabel: String,
    enabled: Boolean,
    isChecking: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val digit = { key: Char ->
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        input.append(key, PasscodeType.Pin)
    }
    val delete = {
        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        input.delete()
    }
    Column(
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.pinPadMaxWidth)
                .fillMaxWidth()
                .onKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val char = event.utf16CodePoint.toChar()
                    when {
                        char in '0'..'9' -> digit(char)
                        event.key == Key.Backspace -> delete()
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> onSubmit()
                        else -> return@onKeyEvent false
                    }
                    true
                },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        DigitRows.forEach { row ->
            KeyRow {
                row.forEach { key -> digitKey(key, enabled, digit) }
            }
        }
        KeyRow {
            deleteKey(enabled, delete)
            digitKey('0', enabled, digit)
            submitKey(submitLabel, enabled, isChecking, onSubmit)
        }
    }
}

/** The delete key: the backspace icon on a tonal medium button. */
private fun ButtonGroupScope.deleteKey(
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            FilledTonalButton(
                onClick = onDelete,
                shapes = ButtonDefaults.shapesFor(KeyHeight),
                modifier = Modifier.key(this@deleteKey, interactionSource),
                enabled = enabled,
                contentPadding = KeyPadding,
                interactionSource = interactionSource,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_backspace),
                    contentDescription = stringResource(R.string.settings_passcode_delete),
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(KeyHeight)),
                )
            }
        },
        menuContent = {},
    )
}

/** The submit key: a check on the filled primary button, named [label]; a [LoadingIndicator] while [isChecking]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.submitKey(
    label: String,
    enabled: Boolean,
    isChecking: Boolean,
    onSubmit: () -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            Button(
                onClick = { if (!isChecking) onSubmit() },
                shapes = ButtonDefaults.shapesFor(KeyHeight),
                modifier = Modifier.key(this@submitKey, interactionSource).semantics { contentDescription = label },
                enabled = enabled || isChecking,
                contentPadding = KeyPadding,
                interactionSource = interactionSource,
            ) {
                val iconModifier = Modifier.size(ButtonDefaults.iconSizeFor(KeyHeight))
                if (isChecking) {
                    LoadingIndicator(modifier = iconModifier, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(painterResource(UiR.drawable.ic_check), contentDescription = null, modifier = iconModifier)
                }
            }
        },
        menuContent = {},
    )
}

/** One row of keys: a [ButtonGroup] of equal keys `spacing.small` apart, which never overflows into a menu. */
@Composable
private fun KeyRow(content: ButtonGroupScope.() -> Unit) {
    ButtonGroup(
        // Never shown: three weighted keys always fit.
        overflowIndicator = {},
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        content = content,
    )
}

/** A digit key: [key] in `headlineSmall` on a tonal medium button. */
private fun ButtonGroupScope.digitKey(
    key: Char,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
) {
    customItem(
        buttonGroupContent = {
            val interactionSource = remember { MutableInteractionSource() }
            FilledTonalButton(
                onClick = { onDigit(key) },
                shapes = ButtonDefaults.shapesFor(KeyHeight),
                modifier = Modifier.key(this@digitKey, interactionSource),
                enabled = enabled,
                contentPadding = KeyPadding,
                interactionSource = interactionSource,
            ) {
                Text(text = key.toString(), style = MaterialTheme.typography.headlineSmall)
            }
        },
        menuContent = {},
    )
}

/**
 * A key's size in its row ([scope]): an equal share of the width, at least a medium button tall, growing when pressed.
 */
private fun Modifier.key(
    scope: ButtonGroupScope,
    interactionSource: MutableInteractionSource,
): Modifier =
    with(scope) {
        this@key
            .weight(1f)
            .animateWidth(interactionSource)
            .heightIn(min = KeyHeight)
    }

/** No padding of their own: every key is as tall as the row, its digit or icon centered, whatever the font scale. */
private val KeyPadding = PaddingValues(0.dp)

/** The keys' height: Material 3 Expressive's medium button. */
private val KeyHeight = ButtonDefaults.MediumContainerHeight

private val DigitRows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))

/**
 * The entry's submit button for a password: a default-height filled button with the Expressive press morph and a
 * `labelLargeEmphasized` label (`docs/DESIGN_SYSTEM.md` §3.3), showing a [LoadingIndicator] while [isChecking].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SubmitButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isChecking: Boolean,
) {
    Button(
        onClick = { if (!isChecking) onClick() },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.semantics { if (isChecking) contentDescription = label },
        enabled = enabled || isChecking,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Hidden rather than removed while checking, so the button keeps its width.
            Text(
                text = label,
                modifier = if (isChecking) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
            if (isChecking) {
                LoadingIndicator(
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

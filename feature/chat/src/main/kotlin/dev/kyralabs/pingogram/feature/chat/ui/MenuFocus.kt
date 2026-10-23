package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager

// The floating message menu with a hardware keyboard and a mouse, for MessageActionsMenu.

/**
 * Where the keyboard's focus goes in the floating menu (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"): nowhere when a
 * touch opened it; when a key or a mouse's secondary button did ([MessageLiftState.takesFocus]), the menu takes it as
 * it opens, so that Esc closes it, and with the keyboard moves on to its first control, the first quick reaction.
 * Decided once as the menu opens.
 */
@Composable
internal fun rememberMenuFocus(lift: MessageLiftState): FocusRequester? {
    val requester = remember { if (lift.takesFocus) FocusRequester() else null }
    val inputModes = LocalInputModeManager.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(requester) {
        if (requester != null && requester.requestFocus(FocusDirection.Enter)) {
            if (inputModes.inputMode == InputMode.Keyboard) focusManager.moveFocus(FocusDirection.Enter)
        }
    }
    return requester
}

/**
 * The floating menu's keys: Esc closes it ([onClose]) wherever its focus is. With a [focus] requester the menu holds
 * the focus while it shows: it takes it as a whole, and Tab and the arrows move through its reactions and actions
 * without leaving it for the conversation behind.
 */
internal fun Modifier.menuFocus(
    focus: FocusRequester?,
    onClose: () -> Unit,
): Modifier {
    val keys =
        onKeyEvent { event ->
            if (event.key != Key.Escape) return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown) onClose()
            true
        }
    return if (focus == null) {
        keys
    } else {
        keys
            .focusRequester(focus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusable()
    }
}

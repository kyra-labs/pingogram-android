package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * The conversation's keys on a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"), for whatever has the
 * focus in it, after the focused control had its turn: Ctrl+F opens the search in the chat where the top bar offers it
 * ([ChatSearchEvent.Open]), and Esc leaves selection mode ([SelectionEvent.Clear]) or else closes the search
 * ([ChatSearchEvent.Close], while [searching]), as back does. With nothing to close, Esc goes on to what holds the
 * conversation (beside the chat list, the list takes the focus back).
 */
@Composable
internal fun Modifier.conversationKeys(
    state: ChatUiState,
    searching: Boolean,
    onEvent: (ChatEvent) -> Unit,
): Modifier {
    val selecting = state.selection != null
    // The top bar offers search once the chat is known, outside selection mode.
    val canSearch = state.header != null && !selecting
    val handler =
        remember(canSearch, searching, selecting, onEvent) {
            { event: KeyEvent -> conversationKey(event, canSearch, searching, selecting, onEvent) }
        }
    return onKeyEvent(handler)
}

/** What [conversationKeys] does with [event]; true where it took it. */
private fun conversationKey(
    event: KeyEvent,
    canSearch: Boolean,
    searching: Boolean,
    selecting: Boolean,
    onEvent: (ChatEvent) -> Unit,
): Boolean {
    val down = event.type == KeyEventType.KeyDown
    return when {
        event.isFindShortcut() && canSearch -> {
            if (down && !searching) onEvent(ChatSearchEvent.Open)
            true
        }

        event.key == Key.Escape && (selecting || searching) -> {
            if (down) onEvent(if (selecting) SelectionEvent.Clear else ChatSearchEvent.Close)
            true
        }

        else -> {
            false
        }
    }
}

/** Ctrl+F, without another modifier. */
private fun KeyEvent.isFindShortcut(): Boolean =
    key == Key.F && isCtrlPressed && !isShiftPressed && !isAltPressed && !isMetaPressed

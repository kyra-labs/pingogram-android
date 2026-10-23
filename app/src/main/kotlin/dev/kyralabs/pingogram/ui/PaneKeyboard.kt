package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * The Chats tab's list-detail layout with a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"), as
 * Google Messages behaves on tablets:
 *
 * - The list pane is one focus group ([listPaneFocus]) that remembers the row that had the focus, so the focus comes
 *   back to it.
 * - A chat opened from the list pane while the list has the keyboard's focus ([opening]) gives its composer the focus
 *   once it shows ([focusesComposerOf]); opened by touch, or with one pane, it does not.
 * - Esc in the conversation, once nothing in it took the key, gives the focus back to the list ([escapeToList]),
 *   without closing the chat.
 *
 * Not saved: after a rotation the composer does not ask for the focus again.
 */
@Stable
internal class PaneKeyboard(
    private val inputModes: InputModeManager,
) {
    /** The list pane's focus group. */
    val listFocus = FocusRequester()

    /** Whether the list pane holds the focus; read in callbacks only. */
    var listHasFocus = false

    /** The chat whose composer takes the focus as it shows; null for none. */
    private var composerChatId by mutableStateOf<Long?>(null)

    /** Chat [chatId] opens from the list pane ([open]); with [twoPanes], beside it. */
    fun opening(
        chatId: Long,
        twoPanes: Boolean,
        open: () -> Unit,
    ) {
        composerChatId = chatId.takeIf { twoPanes && listHasFocus && inputModes.inputMode == InputMode.Keyboard }
        open()
    }

    /** Whether the composer of chat [chatId] takes the focus as the chat shows. */
    fun focusesComposerOf(chatId: Long): Boolean = composerChatId == chatId

    /** The focus back to the list pane, at the row that had it last. */
    fun focusList(): Boolean = listFocus.requestFocus(FocusDirection.Enter)
}

/** The layout's [PaneKeyboard], following the window's input mode. */
@Composable
internal fun rememberPaneKeyboard(): PaneKeyboard {
    val inputModes = LocalInputModeManager.current
    return remember(inputModes) { PaneKeyboard(inputModes) }
}

/** The list pane as [keyboard]'s focus group, which restores the row that last had the focus. */
internal fun Modifier.listPaneFocus(keyboard: PaneKeyboard): Modifier =
    onFocusChanged { keyboard.listHasFocus = it.hasFocus }
        .focusRequester(keyboard.listFocus)
        .focusRestorer()
        .focusGroup()

/**
 * Esc that nothing in this conversation took gives the focus back to the list, where the list shows beside it
 * ([enabled]).
 */
internal fun Modifier.escapeToList(
    keyboard: PaneKeyboard,
    enabled: Boolean,
): Modifier =
    onKeyEvent { event ->
        if (!enabled || event.key != Key.Escape) return@onKeyEvent false
        if (event.type == KeyEventType.KeyDown) keyboard.focusList()
        true
    }

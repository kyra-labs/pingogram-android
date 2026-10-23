package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Whether the user put the bot's keyboard of message [hiddenFor] away (0 for none): it then stays away until they ask
 * for it with the toggle, or a new keyboard comes. Kept across rotations and process death.
 */
@Stable
internal class BotKeyboardState(
    hiddenFor: Long = 0,
) {
    var hiddenFor by mutableLongStateOf(hiddenFor)
        private set

    fun isHidden(keyboard: ReplyKeyboardUiState): Boolean = hiddenFor == keyboard.messageId

    /** [keyboard] stays away until asked for. */
    fun hide(keyboard: ReplyKeyboardUiState) {
        hiddenFor = keyboard.messageId
    }

    /** The keyboard was asked for. */
    fun show() {
        hiddenFor = 0
    }

    companion object {
        val Saver = Saver<BotKeyboardState, Long>(save = { it.hiddenFor }, restore = { BotKeyboardState(it) })
    }
}

@Composable
internal fun rememberBotKeyboardState(): BotKeyboardState =
    rememberSaveable(saver = BotKeyboardState.Saver) { BotKeyboardState() }

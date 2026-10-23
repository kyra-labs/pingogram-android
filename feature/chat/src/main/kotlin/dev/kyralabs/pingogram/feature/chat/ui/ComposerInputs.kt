package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandSuggestions
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotReplyUiState
import dev.kyralabs.pingogram.feature.chat.ui.bots.keyboardPresses
import dev.kyralabs.pingogram.feature.chat.ui.composer.BotInput
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotInput
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState

// What the composer gets of the bots, for the conversation's bottom bar.

/**
 * The bots' part of the composer: the [commandSuggestions] and the bot's keyboard ([botReply]), whose sends report
 * through [onSent], as the commands it sends do; the menu and "Start" go to [onEvent].
 */
@Composable
internal fun rememberBotInput(
    commandSuggestions: CommandSuggestions?,
    botReply: BotReplyUiState,
    onEvent: (ChatEvent) -> Unit,
    onSent: (SendOptions) -> Unit,
): BotInput =
    remember(commandSuggestions, botReply, onEvent, onSent) {
        BotInput(
            suggestions = commandSuggestions,
            reply = botReply,
            onKeyboardPress = keyboardPresses(botReply, onEvent, onSent),
            onSendCommand = { command ->
                onEvent(BotEvent.SendCommand(command))
                onSent(SendOptions())
            },
            onInsertCommand = { onEvent(BotEvent.InsertCommand(it)) },
            onMenu = { onEvent(BotEvent.OpenMenu) },
            onStart = { onEvent(BotEvent.Start) },
        )
    }

/** An inline bot's [inlineResults] above the [composer], a result sent reporting through [onSent]. */
@Composable
internal fun rememberInlineInput(
    inlineResults: InlineBotUiState?,
    composer: ComposerUiState,
    onEvent: (ChatEvent) -> Unit,
    onSent: (SendOptions) -> Unit,
): InlineBotInput {
    val canSchedule = (composer as? ComposerUiState.Available)?.canSchedule == true
    return remember(inlineResults, canSchedule, onEvent, onSent) {
        InlineBotInput(inlineResults, canSchedule) { event ->
            onEvent(event)
            if (event is InlineBotEvent.Send) onSent(event.options)
        }
    }
}

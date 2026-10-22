package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialogHost
import dev.kyralabs.pingogram.feature.chat.ui.bots.SwitchInlineChatsSheet
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsBottomSheet

/**
 * The sheets [viewModel] shows over the conversation: forwarding, send-as, a secret chat's timer and key, the pinned
 * messages, the bots' commands, and what follows a tap on a bot's button (its alert or question, the chat
 * picker of an inline query).
 */
@Composable
internal fun ChatSheets(viewModel: ChatViewModel) {
    val forwardSheet by viewModel.forwardSheet.collectAsStateWithLifecycle()
    forwardSheet?.let { ForwardBottomSheet(it, viewModel.forwardComment, viewModel::onEvent) }
    val sendAsSheet by viewModel.sendAsSheet.collectAsStateWithLifecycle()
    sendAsSheet?.let { SendAsBottomSheet(it, viewModel::onEvent) }
    val secretChatSheet by viewModel.secretChatSheet.collectAsStateWithLifecycle()
    secretChatSheet?.let { SecretChatBottomSheet(it, viewModel::onEvent) }
    val pinnedSheet by viewModel.pinnedSheet.collectAsStateWithLifecycle()
    pinnedSheet?.let { PinnedMessagesSheet(it, viewModel::onEvent) }
    val botButtonDialog by viewModel.botButtonDialog.collectAsStateWithLifecycle()
    botButtonDialog?.let { BotButtonDialogHost(it, viewModel.botButtonPassword, viewModel::onEvent) }
    val switchInlineSheet by viewModel.switchInlineSheet.collectAsStateWithLifecycle()
    switchInlineSheet?.let { SwitchInlineChatsSheet(it, viewModel::onEvent) }
    val botCommandsSheet by viewModel.botCommandsSheet.collectAsStateWithLifecycle()
    botCommandsSheet?.let { BotCommandsBottomSheet(it, viewModel::onEvent) }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialogHost
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardDialogHost
import dev.kyralabs.pingogram.feature.chat.ui.bots.KeyboardShareBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.SwitchInlineChatsSheet
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesSheet
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVotersBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.poll.StopPollAlert
import dev.kyralabs.pingogram.feature.chat.ui.reply.ReplyChatsBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchSenderSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsBottomSheet

/**
 * The sheets [viewModel] shows over the conversation: forwarding, send-as, a secret chat's timer and key, the pinned
 * messages, the bots' commands, what follows a tap on a bot's button (its alert or question, the chat picker of an
 * inline query) or on a button of a bot's keyboard (the phone number question, the users or chat picker), whether to
 * share the location with an inline bot, the members whose messages the search in the chat finds, who voted in a
 * public poll, and the question before a poll is stopped.
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
    val botKeyboardDialog by viewModel.botKeyboardDialog.collectAsStateWithLifecycle()
    botKeyboardDialog?.let { BotKeyboardDialogHost(it, viewModel::onEvent) }
    val keyboardShareSheet by viewModel.keyboardShareSheet.collectAsStateWithLifecycle()
    keyboardShareSheet?.let { KeyboardShareBottomSheet(it, viewModel::onEvent) }
    val inlineLocationSheet by viewModel.inlineLocationSheet.collectAsStateWithLifecycle()
    inlineLocationSheet?.let { InlineLocationBottomSheet(it, viewModel::onEvent) }
    val searchSenderSheet by viewModel.searchSenderSheet.collectAsStateWithLifecycle()
    searchSenderSheet?.let { SearchSenderSheet(it, viewModel.searchSenderQuery, viewModel::onEvent) }
    MessageSheets(viewModel)
}

/** The sheets of a message's actions: the chats to reply in, who voted in a poll, and the question before a stop. */
@Composable
private fun MessageSheets(viewModel: ChatViewModel) {
    val replyChatsSheet by viewModel.replyChatsSheet.collectAsStateWithLifecycle()
    replyChatsSheet?.let { ReplyChatsBottomSheet(it, viewModel::onEvent) }
    val pollVotersSheet by viewModel.pollVotersSheet.collectAsStateWithLifecycle()
    pollVotersSheet?.let { PollVotersBottomSheet(it, viewModel::onEvent) }
    val stopPollDialog by viewModel.stopPollDialog.collectAsStateWithLifecycle()
    if (stopPollDialog != null) StopPollAlert(viewModel::onEvent)
}

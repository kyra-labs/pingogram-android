package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsBottomSheet

/** The sheets [viewModel] shows over the conversation: forwarding, send-as, and a secret chat's timer and key. */
@Composable
internal fun ChatSheets(viewModel: ChatViewModel) {
    val forwardSheet by viewModel.forwardSheet.collectAsStateWithLifecycle()
    forwardSheet?.let { ForwardBottomSheet(it, viewModel.forwardComment, viewModel::onEvent) }
    val sendAsSheet by viewModel.sendAsSheet.collectAsStateWithLifecycle()
    sendAsSheet?.let { SendAsBottomSheet(it, viewModel::onEvent) }
    val secretChatSheet by viewModel.secretChatSheet.collectAsStateWithLifecycle()
    secretChatSheet?.let { SecretChatBottomSheet(it, viewModel::onEvent) }
}

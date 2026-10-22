package dev.kyralabs.pingogram.feature.chat.ui.share

import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChatAttachments
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * What was shared into chat [chatId] (`REQUIREMENTS.md` FR-N2, direct share), for `ChatViewModel`: a share
 * [ShareInbox] delivers to the chat (a Direct Share target, the one chat picked in the share sheet) goes into the
 * composer while the conversation is on screen ([onScreen]), for the user to send: a text into the field, after what it
 * holds ([ChatComposer.addShared]); files into the attach preview, the text as their caption
 * ([ChatAttachments.addShared]). Nothing is sent here. Runs in [scope], the view model's, from [start] on.
 */
internal class ChatShares(
    private val chatId: Long,
    private val inbox: ShareInbox,
    private val onScreen: StateFlow<Boolean>,
    private val composer: ChatComposer,
    private val attachments: ChatAttachments,
    private val scope: CoroutineScope,
) {
    /** Starts taking what is shared into the chat, once. */
    fun start() {
        scope.launch {
            combine(inbox.deliveredTo(chatId), onScreen) { content, shown -> content?.takeIf { shown } }
                .filterNotNull()
                .collect { content -> if (inbox.claim(chatId, content)) add(content) }
        }
    }

    private fun add(content: SharedContent) {
        if (content.files.isEmpty()) {
            composer.addShared(content.text)
        } else {
            attachments.addShared(content.files, content.kind, content.text)
        }
    }
}

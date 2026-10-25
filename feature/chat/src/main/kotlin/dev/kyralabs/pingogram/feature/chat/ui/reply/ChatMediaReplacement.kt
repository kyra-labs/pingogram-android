package dev.kyralabs.pingogram.feature.chat.ui.reply

import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ReplyEffect
import dev.kyralabs.pingogram.feature.chat.ui.ReplyEvent
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChatAttachments
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Replacing the media of the message being edited (`REQUIREMENTS.md` FR-C6, td_api.tl `editMessageMedia`), for
 * `ChatViewModel`: the edit bar's "Replace" opens the picker of the message's kind of media ([ReplyEffect.Pick]), only
 * while [composer] edits a message whose media TDLib lets be replaced ([ComposerMode.Editing.replace]); what it picks
 * is prepared as for sending, in the attach preview of its own ([ChatAttachments.pickReplacement]); the preview's
 * "Replace" leaves the edit as saving does ([ChatComposer.takeMediaEdit]) and sends the new media with the edit's text
 * as its caption ([AttachmentRepository.editMedia]). The bubble changes through TDLib's update once Telegram has the
 * upload; a refusal brings the edit back with a snackbar. Runs in [scope], the view model's.
 */
internal class ChatMediaReplacement(
    private val chatId: Long,
    private val repository: AttachmentRepository,
    private val composer: ChatComposer,
    private val attachments: ChatAttachments,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    fun onEvent(event: ReplyEvent) {
        when (event) {
            ReplyEvent.ReplaceMedia -> pick()
            is ReplyEvent.ReplacementPicked -> picked(event.uri)
            ReplyEvent.SaveReplacement -> save()
            else -> Unit
        }
    }

    private fun pick() {
        val kind = editing()?.replace
        onEffect(if (kind == null) ChatEffect.ActionUnavailable else ReplyEffect.Pick(kind))
    }

    private fun picked(uri: String) {
        val editing = editing() ?: return
        val kind = editing.replace ?: return
        attachments.pickReplacement(uri, kind.attachmentKind, editing.messageId)
    }

    private fun save() {
        val replacement = attachments.takeReplacement() ?: return
        // Only while the composer still edits that message: an edit cancelled meanwhile replaces nothing.
        val edit = if (editing()?.messageId == replacement.messageId) composer.takeMediaEdit() else null
        if (edit == null) {
            repository.discard(listOf(replacement.attachment))
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        scope.launch {
            val (messageId, attachment) = replacement.messageId to replacement.attachment
            repository
                .editMedia(chatId, messageId, attachment, edit.caption, edit.showCaptionAboveMedia)
                .onFailure { error ->
                    composer.restoreMediaEdit(edit)
                    onEffect(ChatEffect.ShowError(error))
                }
        }
    }

    private fun editing(): ComposerMode.Editing? = composer.mode.value as? ComposerMode.Editing
}

/** The kind of attachment the picked content is prepared as. */
private val ReplaceMedia.attachmentKind: AttachmentKind
    get() =
        when (this) {
            ReplaceMedia.Media -> AttachmentKind.Media
            ReplaceMedia.Document -> AttachmentKind.Document
        }

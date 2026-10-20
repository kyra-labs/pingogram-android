package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.restoredText
import dev.kyralabs.pingogram.feature.chat.ui.composer.savedEntities
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the composer's attach menu does (`REQUIREMENTS.md` FR-C5), for `ChatViewModel`: picked photos, videos and files
 * in a preview before they are sent, a photo from the camera, a contact, the device's location, a poll, and cancelling
 * uploads.
 *
 * - **Preview** ([preview]): picked items show at once, each being prepared ([AttachmentRepository.prepare]) one after
 *   the other, then ready; an item that fails is dropped with a snackbar. Items can be removed; closing the preview
 *   ([dismissPreview]) stops what is being prepared and deletes the copies. More picked from the same kind of picker
 *   join the preview; from the other kind, they replace it. [caption] is its caption, with the composer's formatting.
 * - **Sending** ([sendPreview], [sendContact], [sendLocation], [sendPoll]) closes what was open at once, takes the composer's reply
 *   along ([ReplyHandoff]), and sends; a refusal gives the reply back with a snackbar ([ChatEffect.ShowError]), a
 *   scheduled message is confirmed ([ChatEffect.MessageScheduled]). A refused poll opens again as it was.
 * - **Camera** ([takePhoto]): a new file for the camera app ([ChatEffect.LaunchCamera]), whose photo joins the preview
 *   ([photoTaken]); not taken, the file is deleted.
 * - **Process death**: the preview's ready attachments, the caption, the poll form and the camera's file are kept in
 *   [savedState]; items still being prepared are dropped, as their picker's grant does not survive.
 *
 * Runs in [scope], the view model's, on the main thread like the text fields, keeping the caption and the poll form
 * in [savedState] from [start] on. [release] deletes what was never sent when the conversation is left.
 */
@Suppress("TooManyFunctions") // An entry point per thing the attach menu does, as in ChatComposer.
internal class ChatAttachments(
    private val chatId: Long,
    private val repository: AttachmentRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val replies: ReplyHandoff,
    private val onEffect: (ChatEffect) -> Unit,
) {
    /** The caption of the preview's photos, videos or files. */
    val caption =
        ComposerText(restoredText(savedState[KEY_CAPTION], savedState[KEY_CAPTION_ENTITIES]) ?: FormattedText.Empty)

    /** The form of a new poll. */
    val poll = PollEditor(savedState.restoredPoll())

    private var nextKey = 0L

    private val mutablePreview =
        MutableStateFlow(
            savedState.restoredPreview()?.let { (kind, attachments) ->
                PreviewDraft(kind, attachments.map { PreviewItem.Ready(nextKey++, it) }.toPersistentList())
            },
        )

    /** The picked items before they are sent; null while there is no preview. */
    val preview: StateFlow<PreviewDraft?> = mutablePreview.asStateFlow()

    private val mutablePollOpen = MutableStateFlow(savedState.restoredPollOpen())

    /** Whether the poll form is open. */
    val isPollOpen: StateFlow<Boolean> = mutablePollOpen.asStateFlow()

    /** The jobs preparing picked files, which closing the preview stops. */
    private val preparing = mutableSetOf<Job>()

    /** Starts keeping the caption and the poll form in [savedState], once. */
    fun start() {
        scope.launch {
            snapshotFlow { caption.content }.collect { content ->
                savedState[KEY_CAPTION] = content.text
                savedState[KEY_CAPTION_ENTITIES] = content.savedEntities()
            }
        }
        scope.launch { snapshotFlow { poll.draft }.collect { savedState.savePoll(isPollOpen.value, it) } }
    }

    /** Photos and videos from the Photo Picker, by their URIs. */
    fun pickMedia(uris: List<String>) = pick(uris, AttachmentKind.Media)

    /** Files from the document picker, by their URIs. */
    fun pickFiles(uris: List<String>) = pick(uris, AttachmentKind.Document)

    /** Asks the camera app for a photo, into a new file of the app's cache. */
    fun takePhoto() {
        scope.launch {
            when (val file = repository.newCameraFile()) {
                is Outcome.Success -> {
                    savedState[KEY_CAMERA_FILE] = file.value
                    onEffect(ChatEffect.LaunchCamera(file.value))
                }

                is Outcome.Failure -> {
                    onEffect(ChatEffect.ShowError(file.error))
                }
            }
        }
    }

    /** The camera app returned: with a photo if [taken], which joins the preview; else its file is deleted. */
    fun photoTaken(taken: Boolean) {
        val path = savedState.remove<String>(KEY_CAMERA_FILE) ?: return
        if (!taken) {
            repository.discardCameraFile(path)
            return
        }
        val key = startItems(AttachmentKind.Media, count = 1).single()
        track(scope.launch { finish(key, repository.prepareCameraPhoto(path)) })
    }

    /** Removes item [key] from the preview, deleting its copy; the preview closes with its last item. */
    fun remove(key: Long) {
        val draft = mutablePreview.value ?: return
        val item = draft.items.firstOrNull { it.key == key } ?: return
        (item as? PreviewItem.Ready)?.let { repository.discard(listOf(it.attachment)) }
        val items = draft.items.removing(item)
        if (items.isEmpty()) close() else setPreview(draft.copy(items = items))
    }

    /** Closes the preview without sending: what is being prepared stops, and the copies are deleted. */
    fun dismissPreview() {
        val draft = mutablePreview.value ?: return
        repository.discard(draft.attachments)
        close()
    }

    /** Sends the preview's items with its caption as [options] say, once all are ready. */
    fun sendPreview(options: SendOptions) {
        val draft = mutablePreview.value?.takeIf { it.isReady } ?: return
        val text = caption.content
        setPreview(null)
        caption.setContent(FormattedText.Empty)
        val replyTo = replies.takeReply()
        scope.launch {
            val result = repository.send(chatId, draft.attachments, text, options, replyTo)
            report(result, options, replyTo)
        }
    }

    /** Sends the contact the contact picker returned at [uri], read while its grant lasts. */
    fun sendContact(uri: String) {
        scope.launch {
            when (val contact = repository.readContact(uri)) {
                is Outcome.Success -> {
                    val replyTo = replies.takeReply()
                    val sent = repository.sendContact(chatId, contact.value, replyToMessageId = replyTo)
                    report(sent, SendOptions(), replyTo)
                }

                is Outcome.Failure -> {
                    onEffect(ChatEffect.ShowError(contact.error))
                }
            }
        }
    }

    /** Sends [location], the device's, as [options] say, with the composer's reply. */
    fun sendLocation(
        location: DeviceLocation,
        options: SendOptions,
    ) {
        val replyTo = replies.takeReply()
        scope.launch { report(repository.sendLocation(chatId, location, options, replyTo), options, replyTo) }
    }

    fun openPoll() = setPollOpen(true)

    /** Closes the poll form, emptying it. */
    fun dismissPoll() {
        setPollOpen(false)
        poll.clear()
    }

    /**
     * Sends the poll as [options] say, anonymous in a channel ([inChannel]), if it is complete: the form closes at
     * once, and empties once the poll is sent; a refused poll opens again as it was.
     */
    fun sendPoll(
        options: SendOptions,
        inChannel: Boolean,
    ) {
        val newPoll = poll.poll(forceAnonymous = inChannel) ?: return
        setPollOpen(false)
        val replyTo = replies.takeReply()
        scope.launch {
            val result = repository.sendPoll(chatId, newPoll, options, replyTo)
            if (result is Outcome.Success) poll.clear() else setPollOpen(true)
            report(result, options, replyTo)
        }
    }

    /** Stops sending the messages [messageIds], still being sent, which deletes them. */
    fun cancelUpload(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        scope.launch {
            repository.cancelUpload(chatId, messageIds).onFailure { onEffect(ChatEffect.ShowError(it)) }
        }
    }

    /** The conversation is left: the copies of a preview never sent, and a camera file never used, are deleted. */
    fun release() {
        mutablePreview.value?.let { repository.discard(it.attachments) }
        savedState.get<String>(KEY_CAMERA_FILE)?.let(repository::discardCameraFile)
    }

    private fun pick(
        uris: List<String>,
        kind: AttachmentKind,
    ) {
        if (uris.isEmpty()) return
        val keys = startItems(kind, uris.size)
        track(
            scope.launch {
                // One at a time: preparing photos decodes large bitmaps.
                keys.zip(uris).forEach { (key, uri) -> finish(key, repository.prepare(uri, kind)) }
            },
        )
    }

    /**
     * Adds [count] items being prepared to the preview, opening it if needed, and returns their keys. A preview of
     * the other [kind] is replaced, its copies deleted.
     */
    private fun startItems(
        kind: AttachmentKind,
        count: Int,
    ): List<Long> {
        val current = mutablePreview.value?.takeIf { it.kind == kind }
        if (current == null) dismissPreview()
        val keys = List(count) { nextKey++ }
        val items = (current?.items ?: persistentListOf()).addingAll(keys.map { PreviewItem.Preparing(it) })
        setPreview(PreviewDraft(kind, items))
        return keys
    }

    /**
     * Item [key] is prepared: ready with its attachment, or dropped with a snackbar. A removed item, or a closed
     * preview, deletes the copy instead.
     */
    private fun finish(
        key: Long,
        result: Outcome<Attachment>,
    ) {
        val draft = mutablePreview.value
        val index = draft?.items?.indexOfFirst { it.key == key } ?: -1
        when {
            result is Outcome.Failure -> {
                onEffect(ChatEffect.ShowError(result.error))
                if (draft != null && index >= 0) remove(key)
            }

            draft == null || index < 0 -> {
                repository.discard(listOf((result as Outcome.Success).value))
            }

            else -> {
                val ready = PreviewItem.Ready(key, (result as Outcome.Success).value)
                setPreview(draft.copy(items = draft.items.replacingAt(index, ready)))
            }
        }
    }

    private fun track(job: Job) {
        preparing += job
        job.invokeOnCompletion { preparing -= job }
    }

    private fun close() {
        preparing.toList().forEach(Job::cancel)
        setPreview(null)
        caption.setContent(FormattedText.Empty)
    }

    /** What came of a send: a scheduled message confirmed, or the error with the reply given back. */
    private fun report(
        result: Outcome<Unit>,
        options: SendOptions,
        replyTo: Long?,
    ) {
        when (result) {
            is Outcome.Success -> {
                options.scheduledAt?.let { onEffect(ChatEffect.MessageScheduled(it)) }
            }

            is Outcome.Failure -> {
                replyTo?.let(replies::restoreReply)
                onEffect(ChatEffect.ShowError(result.error))
            }
        }
    }

    private fun setPreview(draft: PreviewDraft?) {
        mutablePreview.value = draft
        savedState.savePreview(draft?.kind, draft?.attachments.orEmpty())
    }

    private fun setPollOpen(open: Boolean) {
        mutablePollOpen.value = open
        savedState.savePoll(open, poll.draft)
    }

    private companion object {
        /** The caption's text and formatting ([savedEntities]), for process death. */
        const val KEY_CAPTION = "chat.attachCaption"
        const val KEY_CAPTION_ENTITIES = "chat.attachCaptionEntities"
    }
}

/** The picked items of the preview, of one [kind], in their order. */
internal data class PreviewDraft(
    val kind: AttachmentKind,
    val items: PersistentList<PreviewItem>,
) {
    val isReady: Boolean get() = items.isNotEmpty() && items.none { it is PreviewItem.Preparing }

    val attachments: List<Attachment> get() = items.mapNotNull { (it as? PreviewItem.Ready)?.attachment }
}

/**
 * How a message sent from elsewhere than the composer's text field (an attachment, a contact, a poll) takes the
 * composer's reply along, as a text message does: `ChatComposer`.
 */
internal interface ReplyHandoff {
    /** The message the composer replies to, which ends the reply; null if it replies to nothing. */
    fun takeReply(): Long?

    /** Replies to [messageId] again after the message that took it was refused, unless the composer moved on. */
    fun restoreReply(messageId: Long)
}

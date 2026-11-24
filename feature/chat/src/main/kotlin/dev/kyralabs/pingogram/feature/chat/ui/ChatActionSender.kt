package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshotFlow
import dev.kyralabs.pingogram.core.domain.model.ChatAction
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Tells the chat what the user is doing in it (`REQUIREMENTS.md` FR-X17): typing while they write, recording a voice or
 * round video message, uploading a photo or a file. The others see an action for a few seconds, so it is sent again,
 * at most every [RESEND_MILLIS], while it goes on: typing on the next edit after that, a recording or an upload when
 * the time comes. It is ended when it stops. Recording and uploading come before typing. Nothing at all is sent, an
 * ending included, while "Send typing status" is off ([enabled]).
 *
 * Runs in [scope], the view model's; [send] goes out one at a time, in order.
 */
internal class ChatActionSender(
    private val enabled: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val send: suspend (ChatAction?) -> Unit,
) {
    private val queue = Channel<ChatAction?>(Channel.UNLIMITED)

    /** What was sent last and not ended, and when; null after an ending. */
    private var sent: ChatAction? = null
    private var sentAt = 0L

    /** The recording or upload going on, which typing does not replace. */
    private var ongoing: ChatAction? = null

    /** Sends [ongoing] again each time the last one is [RESEND_MILLIS] old, while there is one. */
    private var resending: Job? = null

    /**
     * Follows [writing] (true on each edit that leaves text to send, false once there is none) and [ongoing], each of
     * whose changes is a recording or an upload going on, or none.
     */
    fun start(
        writing: Flow<Boolean>,
        ongoing: Flow<ChatAction?>,
    ) {
        scope.launch { for (action in queue) send(action) }
        scope.launch { ongoing.collect(::onOngoing) }
        scope.launch { writing.collect(::onWriting) }
    }

    private fun onWriting(isWriting: Boolean) {
        if (ongoing != null) return
        if (isWriting) act(ChatAction.Typing) else end()
    }

    private fun onOngoing(action: ChatAction?) {
        val was = ongoing
        ongoing = action
        if (action == null) {
            resending?.cancel()
            resending = null
            if (was != null) end()
            return
        }
        act(action)
        if (resending == null) resending = scope.launch { resendOngoing() }
    }

    private suspend fun resendOngoing() {
        while (true) {
            // Nothing was sent while the switch is off: then it checks again a whole interval later.
            val wait = sentAt + RESEND_MILLIS - now()
            delay(if (wait > 0) wait else RESEND_MILLIS)
            ongoing?.let(::act)
        }
    }

    private fun act(action: ChatAction) {
        if (!enabled.value) return
        val last = sent
        val time = now()
        // An upload's progress changes the action, not what the others see: resent at the same pace.
        if (last != null && last::class == action::class && time - sentAt < RESEND_MILLIS) return
        sent = action
        sentAt = time
        queue.trySend(action)
    }

    private fun end() {
        if (sent == null) return
        sent = null
        if (enabled.value) queue.trySend(null)
    }

    companion object {
        /** How often an action going on is sent again: TDLib's peers show one for about five seconds. */
        const val RESEND_MILLIS = 5_000L
    }
}

/**
 * True on each of the user's edits that leaves something to send in a new message (not an edit of a sent one), false
 * on the others and whenever the field is left blank, sending included.
 */
internal fun ComposerText.writing(mode: StateFlow<ComposerMode>): Flow<Boolean> =
    merge(
        snapshotFlow { userEdits }.drop(1).map { !isBlank && mode.value !is ComposerMode.Editing },
        snapshotFlow { isBlank }.filter { it }.map { false },
    )

/**
 * The recording or upload going on, on each change of [recording] or [uploads]: a voice or round video message being
 * recorded (not while paused), else a photo or a file of [history] being sent from this device; null for none.
 */
internal fun ongoingAction(
    recording: Flow<VoiceRecordingUiState?>,
    history: Flow<ChatHistory>,
    uploads: Flow<ImmutableMap<Long, MessageUpload>>,
): Flow<ChatAction?> =
    combine(recording, history, uploads) { recorded, loaded, uploading ->
        recorded?.action ?: uploading.firstNotNullOfOrNull { (messageId, upload) ->
            loaded.messages
                .findById(messageId)
                ?.content
                ?.uploading(upload)
        }
    }

private val VoiceRecordingUiState.action: ChatAction?
    get() =
        when {
            isPaused -> null
            video != null -> ChatAction.RecordingVideoNote
            else -> ChatAction.RecordingVoiceNote
        }

private fun MessageContent.uploading(upload: MessageUpload): ChatAction? {
    val percent = ((upload.progress ?: 0f) * PERCENT).roundToInt()
    return when (this) {
        is MessageContent.Photo -> ChatAction.UploadingPhoto(percent)
        is MessageContent.Document -> ChatAction.UploadingDocument(percent)
        else -> null
    }
}

private const val PERCENT = 100

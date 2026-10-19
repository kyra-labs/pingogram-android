package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import dev.kyralabs.pingogram.feature.chat.ui.attach.ReplyHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the conversation's composer does (`REQUIREMENTS.md` FR-C5, FR-C6), for `ChatViewModel`: it owns the [text] the
 * field edits and its [mode], sends the text, replies, edits messages, and keeps the chat's draft in step.
 *
 * - **Drafts**: the chat's draft ([drafts], TDLib's `Chat.draftMessage`) fills an empty field when the chat opens, with
 *   the message it replies to. A draft changed on another device replaces the text only while the field is empty and
 *   unfocused, never what the user is typing. The text and its reply go back as the draft [DRAFT_SAVE_DELAY_MILLIS]
 *   after the last change, and at once when the screen stops ([flush]); only when they differ from the chat's draft,
 *   so a voice note draft from another app stays until the user types. TDLib echoes each saved draft back; echoes are
 *   recognized by their text and reply, so a late one cannot refill a field emptied meanwhile.
 * - **Sending** ([send]) empties the field at once and sends the text, as a reply if the composer replies
 *   ([ComposerMode.Replying]), which clears the chat's draft; the message shows through the history. A refused message
 *   comes back into the field, if it is still empty, with its reply and a snackbar ([ChatEffect.ShowError]); a
 *   scheduled one is confirmed with [ChatEffect.MessageScheduled].
 * - **Replying** ([reply], [cancelReply]) changes what the next message replies to, and the draft with it. A message
 *   sent from the attach menu takes the reply along ([takeReply]) and gives it back if it is refused ([restoreReply]).
 * - **Editing** ([edit]) saves the draft as it is, keeps the composer's text and reply aside ([ComposerStash]), and
 *   fills the field with the message's text or caption. Drafts are left alone meanwhile. [send] then leaves the edit
 *   at once, giving the composer back what it held, and sends the edit ([MessageActionRepository.editText] or
 *   `editCaption`, from [messageOf]'s message); nothing when the text did not change. A refused edit comes back, if
 *   the composer is still as the edit left it, with a snackbar. [cancelEdit] gives the composer back without editing.
 * - **Process death**: the text, its formatting and the mode are kept in [savedState] on every change.
 *
 * Runs in [scope], the view model's, on the main thread like the text field, following the text and the drafts from
 * [start] on.
 */
@Suppress(
    "LongParameterList", // The chat, where it sends to and keeps state, what it follows, looks up and tells.
    "TooManyFunctions", // An entry point per thing the composer does (reply, edit, send), and the draft they share.
)
internal class ChatComposer(
    private val chatId: Long,
    private val repository: MessageRepository,
    private val actions: MessageActionRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val drafts: Flow<ChatDraft?>,
    private val messageOf: (Long) -> Message?,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
) : ReplyHandoff {
    /** The text the composer edits. */
    val text = ComposerText(restoredText(savedState[KEY_TEXT], savedState[KEY_ENTITIES]) ?: FormattedText.Empty)

    private val mutableMode = MutableStateFlow(savedState.restoredMode())

    /** Whether the composer writes a new message, replies or edits; then every change. */
    val mode: StateFlow<ComposerMode> = mutableMode.asStateFlow()

    /** Whether the field has the focus ([setFocused]), which keeps drafts from other devices out of it. */
    private var isFocused = false

    /** The chat's draft as TDLib last reported it, or as last saved; null until TDLib reported it. */
    private var chatDraft: ComposerDraft? = null

    /** The drafts saved and not echoed back yet, oldest first, by [echoKey]. */
    private val echoes = ArrayDeque<Pair<String, Long?>>()

    private var pendingSave: Job? = null

    /** Starts following the text (for [savedState] and the draft) and the chat's drafts, once. */
    fun start() {
        scope.launch {
            snapshotFlow { text.content }.collect { content ->
                savedState[KEY_TEXT] = content.text
                savedState[KEY_ENTITIES] = content.savedEntities()
                scheduleSave()
            }
        }
        scope.launch { drafts.collect(::onChatDraft) }
    }

    /** The field gained or lost the focus. */
    fun setFocused(focused: Boolean) {
        isFocused = focused
    }

    /** The next message replies to message [messageId]; an edit in progress is cancelled first. */
    fun reply(messageId: Long) {
        cancelEdit()
        setMode(ComposerMode.Replying(messageId))
        scheduleSave()
    }

    /**
     * The message the next message replies to, for a message sent from the attach menu, which takes the reply along:
     * the reply ends, as sending a text ends it, and the draft follows. Null when the composer replies to nothing.
     */
    override fun takeReply(): Long? {
        val replying = mode.value as? ComposerMode.Replying ?: return null
        setMode(ComposerMode.Writing)
        scheduleSave()
        return replying.messageId
    }

    /** Replies to [messageId] again after what [takeReply] gave it to was refused, unless a reply or edit began. */
    override fun restoreReply(messageId: Long) {
        if (mode.value != ComposerMode.Writing) return
        setMode(ComposerMode.Replying(messageId))
        scheduleSave()
    }

    /** The next message replies to nothing. */
    fun cancelReply() {
        if (mode.value !is ComposerMode.Replying) return
        setMode(ComposerMode.Writing)
        scheduleSave()
    }

    /**
     * Edits [message]: saves the draft now if it changed, keeps the composer's text and reply aside (those of the edit
     * it replaces, if one is in progress), and fills the field with the message's text or caption.
     */
    fun edit(message: Message) {
        val stash =
            when (val current = mode.value) {
                is ComposerMode.Editing -> {
                    current.stash
                }

                else -> {
                    pendingSave?.cancel()
                    // Undispatched: the draft is read before the field changes below.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { saveDraft() }
                    ComposerStash(text.content, current.replyToMessageId)
                }
            }
        setMode(ComposerMode.Editing(message.id, stash))
        text.setContent(message.editableText)
    }

    /** Leaves the edit in progress without sending it, giving the composer back what it held; nothing otherwise. */
    fun cancelEdit() {
        val editing = mode.value as? ComposerMode.Editing ?: return
        restore(editing.stash)
    }

    /** Message [messageId] is gone (deleted): a reply to it, or its edit, ends. */
    fun onMessageGone(messageId: Long) {
        if (mode.value.messageId != messageId) return
        if (mode.value is ComposerMode.Editing) cancelEdit() else cancelReply()
    }

    /**
     * Sends the text as [options] say, if there is more than white space, or the edit in progress. Empties the field
     * before the request is sent, so the next message can be typed at once.
     */
    fun send(options: SendOptions) {
        when (val current = mode.value) {
            is ComposerMode.Editing -> saveEdit(current)
            else -> sendMessage(options, current.replyToMessageId)
        }
    }

    /**
     * Saves the draft now, if it changed: the screen stops or leaves. Undispatched, so the request is sent before this
     * returns, as the view model's scope may be cancelled right after.
     */
    fun flush() {
        pendingSave?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) { saveDraft() }
    }

    private fun sendMessage(
        options: SendOptions,
        replyTo: Long?,
    ) {
        val content = text.content
        if (content.text.isBlank()) return
        pendingSave?.cancel()
        // Sending clears the chat's draft, and TDLib reports that too, if there was one.
        if (chatDraft?.isEmpty == false) remember(ComposerDraft.Empty)
        chatDraft = ComposerDraft.Empty
        setMode(ComposerMode.Writing)
        text.setContent(FormattedText.Empty)
        scope.launch {
            when (val result = repository.sendText(chatId, content, options, replyTo)) {
                is Outcome.Success -> {
                    options.scheduledAt?.let { onEffect(ChatEffect.MessageScheduled(it)) }
                }

                is Outcome.Failure -> {
                    if (text.isEmpty && mode.value == ComposerMode.Writing) {
                        text.setContent(content)
                        replyTo?.let { setMode(ComposerMode.Replying(it)) }
                    }
                    onEffect(ChatEffect.ShowError(result.error))
                }
            }
        }
    }

    /**
     * Sends the edit of [editing]'s message: a text message needs more than white space. Leaves the edit first; an
     * unchanged text sends nothing. A failure brings the edit back while the composer holds what the edit gave back.
     */
    private fun saveEdit(editing: ComposerMode.Editing) {
        val content = text.content
        val message = messageOf(editing.messageId)
        val target = message?.editTarget
        if (message == null || target == null) {
            onEffect(ChatEffect.ShowError(DomainError.NotFound))
        } else if (target != EditTarget.Text || content.text.isNotBlank()) {
            val restored = restore(editing.stash)
            if (content != message.editableText) sendEdit(editing, message.id, target, content, restored)
        }
    }

    /** Sends [content] as the edit of [editing]'s message; a failure brings the edit back if [restored] still shows. */
    private fun sendEdit(
        editing: ComposerMode.Editing,
        messageId: Long,
        target: EditTarget,
        content: FormattedText,
        restored: Restored,
    ) {
        scope.launch {
            val result =
                when (target) {
                    EditTarget.Text -> actions.editText(chatId, messageId, content)
                    is EditTarget.Caption -> actions.editCaption(chatId, messageId, content, target.showAboveMedia)
                }
            result.onFailure { error ->
                if (mode.value == restored.mode && text.content == restored.text) {
                    setMode(editing)
                    text.setContent(content)
                }
                onEffect(ChatEffect.ShowError(error))
            }
        }
    }

    /**
     * Gives the composer back [stash]: its text and reply. An empty stash takes the chat's draft instead, which may
     * have changed on another device meanwhile and would otherwise be removed.
     */
    private fun restore(stash: ComposerStash): Restored {
        val draft = chatDraft
        val restored = if (stash.isEmpty && draft != null) ComposerStash(draft.text, draft.replyToMessageId) else stash
        val mode = restored.replyToMessageId?.let(ComposerMode::Replying) ?: ComposerMode.Writing
        setMode(mode)
        text.setContent(restored.text)
        return Restored(mode, restored.text)
    }

    private fun setMode(mode: ComposerMode) {
        mutableMode.value = mode
        savedState.saveMode(mode)
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave =
            scope.launch {
                delay(DRAFT_SAVE_DELAY_MILLIS)
                saveDraft()
            }
    }

    private suspend fun saveDraft() {
        val current = mode.value
        // An edit is not a draft: the draft stays as it was before the edit. Before TDLib reported the chat's draft,
        // saving could overwrite one not shown yet.
        val known = chatDraft?.takeIf { current !is ComposerMode.Editing } ?: return
        val draft = ComposerDraft(text.content, current.replyToMessageId)
        if (draft == known) return
        chatDraft = draft
        remember(draft)
        repository.setDraft(chatId, draft.text, draft.replyToMessageId).onFailure { error ->
            log.warn("setChatDraftMessage failed: ${error::class.simpleName}")
        }
    }

    private fun onChatDraft(draft: ChatDraft?) {
        val reported =
            ComposerDraft(
                text = (draft?.content as? DraftContent.Text)?.text ?: FormattedText.Empty,
                replyToMessageId = draft?.replyToMessageId,
            )
        val echo = echoes.indexOf(reported.echoKey)
        if (echo >= 0) {
            // TDLib reports the drafts in the order they were saved, and may skip one that changed nothing. Until the
            // last one is back, the chat's draft is the last one saved.
            repeat(echo + 1) { echoes.removeFirst() }
            if (echoes.isEmpty()) chatDraft = reported
            return
        }
        chatDraft = reported
        val fills = !isFocused && text.isEmpty && mode.value == ComposerMode.Writing && !reported.isEmpty
        if (fills) {
            reported.replyToMessageId?.let { setMode(ComposerMode.Replying(it)) }
            text.setContent(reported.text)
        }
    }

    private fun remember(draft: ComposerDraft) {
        echoes.addLast(draft.echoKey)
        if (echoes.size > MAX_ECHOES) echoes.removeFirst()
    }

    /** A draft as the composer compares it: the text with its formatting, and the message it replies to. */
    private data class ComposerDraft(
        val text: FormattedText,
        val replyToMessageId: Long?,
    ) {
        val isEmpty: Boolean get() = text.text.isEmpty() && replyToMessageId == null

        /** How its echo is recognized: the trimmed text (TDLib may trim a draft) and the reply. */
        val echoKey: Pair<String, Long?> get() = text.text.trim() to replyToMessageId

        companion object {
            val Empty = ComposerDraft(FormattedText.Empty, replyToMessageId = null)
        }
    }

    /** What [restore] gave the composer back. */
    private class Restored(
        val mode: ComposerMode,
        val text: FormattedText,
    )

    companion object {
        /** How long after the last edit the text is saved as the chat's draft. */
        const val DRAFT_SAVE_DELAY_MILLIS = 1_000L

        /** The composer's text, for process death. */
        const val KEY_TEXT = "chat.composerText"

        /** The composer's formatting ([savedEntities]), for process death. */
        const val KEY_ENTITIES = "chat.composerEntities"

        /** Saved drafts waiting for their echo, at most; older ones are forgotten (TDLib skipped them). */
        private const val MAX_ECHOES = 8
    }
}

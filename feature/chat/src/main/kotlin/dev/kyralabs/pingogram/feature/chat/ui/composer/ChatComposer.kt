package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * What the conversation's composer does (`REQUIREMENTS.md` FR-C5), for `ChatViewModel`: it owns the [text] the field
 * edits, sends it, and keeps the chat's draft in step with it.
 *
 * - **Drafts**: the chat's draft ([drafts], TDLib's `Chat.draftMessage`) fills an empty field when the chat opens. A
 *   draft changed on another device replaces the text only while the field is empty and unfocused, never what the
 *   user is typing. The text goes back as the draft [DRAFT_SAVE_DELAY_MILLIS] after the last edit, and at once when
 *   the screen stops ([flush]); only when it differs from the chat's draft, so a voice note draft from another app
 *   stays until the user types. TDLib echoes each saved draft back; echoes are recognized by their text, so a late one
 *   cannot refill a field emptied meanwhile.
 * - **Sending** ([send]) empties the field at once and sends the text, which clears the chat's draft; the message
 *   shows through the history. A refused message comes back into the field, if it is still empty, with a snackbar
 *   ([ChatEffect.ShowError]); a scheduled one is confirmed with [ChatEffect.MessageScheduled].
 * - **Process death**: the text and its formatting are kept in [savedState] on every change.
 *
 * Runs in [scope], the view model's, on the main thread like the text field.
 */
@Suppress("LongParameterList") // The chat, where it sends to and keeps state, what it follows and tells, and its log.
internal class ChatComposer(
    private val chatId: Long,
    private val repository: MessageRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    drafts: Flow<ChatDraft?>,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
) {
    /** The text the composer edits. */
    val text = ComposerText(restoredText(savedState[KEY_TEXT], savedState[KEY_ENTITIES]) ?: FormattedText.Empty)

    /** Whether the field has the focus ([setFocused]), which keeps drafts from other devices out of it. */
    private var isFocused = false

    /** The chat's draft as TDLib last reported it, or as last saved; null until TDLib reported it. */
    private var chatDraft: FormattedText? = null

    /** The texts of drafts saved and not echoed back yet, oldest first, trimmed: TDLib may trim a draft. */
    private val echoes = ArrayDeque<String>()

    private var pendingSave: Job? = null

    init {
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

    /**
     * Sends the text as [options] say, if there is more than white space. Empties the field before the request is
     * sent, so the next message can be typed at once.
     */
    fun send(options: SendOptions) {
        val content = text.content
        if (content.text.isBlank()) return
        pendingSave?.cancel()
        // Sending clears the chat's draft, and TDLib reports that too, if there was one.
        if (chatDraft?.text?.isNotEmpty() == true) remember(FormattedText.Empty)
        chatDraft = FormattedText.Empty
        text.setContent(FormattedText.Empty)
        scope.launch {
            when (val result = repository.sendText(chatId, content, options)) {
                is Outcome.Success -> {
                    options.scheduledAt?.let { onEffect(ChatEffect.MessageScheduled(it)) }
                }

                is Outcome.Failure -> {
                    if (text.isEmpty) text.setContent(content)
                    onEffect(ChatEffect.ShowError(result.error))
                }
            }
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

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave =
            scope.launch {
                delay(DRAFT_SAVE_DELAY_MILLIS)
                saveDraft()
            }
    }

    private suspend fun saveDraft() {
        // Before TDLib reported the chat's draft, saving could overwrite one not shown yet.
        val known = chatDraft ?: return
        val content = text.content
        if (content == known) return
        chatDraft = content
        remember(content)
        repository.setDraft(chatId, content).onFailure { error ->
            log.warn("setChatDraftMessage failed: ${error::class.simpleName}")
        }
    }

    private fun onChatDraft(draft: ChatDraft?) {
        val draftText = (draft?.content as? DraftContent.Text)?.text ?: FormattedText.Empty
        val echo = echoes.indexOf(draftText.text.trim())
        if (echo >= 0) {
            // TDLib reports the drafts in the order they were saved, and may skip one that changed nothing. Until the
            // last one is back, the chat's draft is the last one saved.
            repeat(echo + 1) { echoes.removeFirst() }
            if (echoes.isEmpty()) chatDraft = draftText
            return
        }
        chatDraft = draftText
        if (!isFocused && text.isEmpty && draftText.text.isNotEmpty()) text.setContent(draftText)
    }

    private fun remember(draft: FormattedText) {
        echoes.addLast(draft.text.trim())
        if (echoes.size > MAX_ECHOES) echoes.removeFirst()
    }

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

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Selection mode of a conversation (`REQUIREMENTS.md` FR-C6 "select"; `docs/DESIGN_SYSTEM.md` §6 "Selection mode"),
 * for `ChatViewModel`: which messages are selected and what the selection toolbar offers for them ([state]).
 *
 * - **Selecting**: "Select" in a message's actions starts it ([select]); then a tap or a long press on a message
 *   selects it or no longer ([toggle]), an album as a whole. Deselecting the last one ends it, as do the close button
 *   and back ([clear]).
 * - **Kept** in the [savedState] by message id, so selection mode survives rotation and process death. Only loaded
 *   messages show as selected: a deleted message drops out at once, and the next change keeps only loaded ones.
 * - **Toolbar** ([actionsFor]): copying where every message may be saved and one has a text, forwarding, reporting and
 *   deleting where TDLib allows it for every one of them (`getMessageProperties`, asked for all at once, from
 *   memory). While TDLib answers for a changed selection, the toolbar keeps what it offered.
 * - **Acting** ([perform]) asks TDLib again, as the properties change with time, and runs the action on the selected
 *   messages, oldest first, only if it is still allowed for all of them; else a snackbar says it is not available.
 *
 * [current] gives the latest chat and history, from the view model's flows; null while the chat is not known. Runs in
 * [scope], the view model's, on the main thread.
 */
internal class ChatSelection(
    private val chatId: Long,
    private val repository: MessageActionRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val current: suspend () -> ActionContext?,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val ids: Flow<LongArray> = savedState.getStateFlow(KEY_SELECTION, LongArray(0))

    private val selected: Set<Long>
        get() = savedState.get<LongArray>(KEY_SELECTION)?.toSet().orEmpty()

    /**
     * Selection mode with the selected messages of [history] and the toolbar's actions for them; null while none of
     * them is loaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(history: Flow<ChatHistory>): Flow<MessageSelection?> =
        flow {
            var offered: ImmutableList<MessageAction> = persistentListOf()
            val selection =
                combine(ids, history) { ids, history -> selectedIn(history, ids.toSet()) }
                    .distinctUntilChanged()
                    .transformLatest { messages ->
                        if (messages.isEmpty()) {
                            offered = persistentListOf()
                            emit(null)
                        } else {
                            val messageIds = messages.map { it.id }.toImmutableSet()
                            emit(MessageSelection(messageIds, offered))
                            offered = actionsFor(messages, reportsFailure = false)?.first ?: persistentListOf()
                            emit(MessageSelection(messageIds, offered))
                        }
                    }
            emitAll(selection)
        }.distinctUntilChanged()

    /** Starts selection mode with [messageIds] (a message, or an album's every item), or adds them to it. */
    fun select(messageIds: List<Long>) {
        scope.launch {
            val loaded = loadedIds() ?: return@launch
            write(selected.filterTo(LinkedHashSet()) { it in loaded } + messageIds.filter { it in loaded })
        }
    }

    /** Selects message [messageId] (an album's every item), or deselects it if it is; keeps only loaded messages. */
    fun toggle(messageId: Long) {
        scope.launch {
            val context = current() ?: return@launch
            val message = context.history.messages.findById(messageId) ?: return@launch
            val unit = context.albumOf(message).map { it.id }
            val loaded = context.history.messages.mapTo(HashSet()) { it.id }
            val ids = selected.filterTo(LinkedHashSet()) { it in loaded }
            if (ids.containsAll(unit)) ids.removeAll(unit.toSet()) else ids.addAll(unit)
            write(ids)
        }
    }

    /** Ends selection mode. */
    fun clear() {
        savedState[KEY_SELECTION] = LongArray(0)
    }

    /**
     * Copies the texts of [messages] (oldest first) and ends selection mode: one text as it is, several under their
     * senders and times ([SelectionEffect.CopyMessages]).
     */
    fun copy(
        context: ActionContext,
        messages: List<Message>,
    ) {
        val withText = messages.filter { it.textOrCaption.text.isNotEmpty() }
        val single = withText.singleOrNull()
        onEffect(
            if (single != null) {
                ChatEffect.CopyText(single.textOrCaption.text)
            } else {
                val copied = withText.map { CopiedMessage(context.senderNameOf(it), it.date, it.textOrCaption.text) }
                SelectionEffect.CopyMessages(copied.toImmutableList())
            },
        )
        clear()
    }

    /**
     * Runs [block] with the selected loaded messages, oldest first, if TDLib allows [action] for all of them now; else
     * says it is not available. Nothing outside selection mode.
     */
    fun perform(
        action: MessageAction,
        block: suspend (ActionContext, List<Message>, List<MessageProperties>) -> Unit,
    ) {
        scope.launch {
            val context = current() ?: return@launch
            val messages = selectedIn(context.history, selected)
            if (messages.isEmpty()) return@launch
            val (allowed, properties) = actionsFor(messages, reportsFailure = true) ?: return@launch
            // Fresh: the selection may have changed while TDLib answered.
            val now = current() ?: return@launch
            if (action in allowed && selectedIn(now.history, selected) == messages) {
                block(now, messages, properties)
            } else {
                onEffect(ChatEffect.ActionUnavailable)
            }
        }
    }

    private fun write(ids: Set<Long>) {
        savedState[KEY_SELECTION] = ids.toLongArray()
    }

    private suspend fun loadedIds(): Set<Long>? = current()?.history?.messages?.mapTo(HashSet()) { it.id }

    /**
     * What the toolbar offers for [messages], in its order, with TDLib's properties of each; null when TDLib could not
     * tell (a snackbar says why if [reportsFailure]; the toolbar then offers nothing).
     */
    private suspend fun actionsFor(
        messages: List<Message>,
        reportsFailure: Boolean,
    ): Pair<ImmutableList<MessageAction>, List<MessageProperties>>? {
        val answers =
            coroutineScope {
                messages
                    .map { message ->
                        async { repository.properties(chatId, message.id) }
                    }.awaitAll()
            }
        val failure = answers.firstNotNullOfOrNull { it as? Outcome.Failure }
        if (failure != null) {
            if (reportsFailure) onEffect(ChatEffect.ShowError(failure.error))
            return null
        }
        val properties = answers.map { (it as Outcome.Success<MessageProperties>).value }
        val actions =
            buildList {
                if (properties.all { it.canBeSaved } && messages.any { it.textOrCaption.text.isNotEmpty() }) {
                    add(MessageAction.Copy)
                }
                if (properties.all { it.canBeForwarded }) add(MessageAction.Forward)
                if (properties.all { it.canBeReported }) add(MessageAction.Report)
                if (properties.all { it.canBeDeletedOnlyForSelf || it.canBeDeletedForAllUsers }) {
                    add(
                        MessageAction.Delete,
                    )
                }
            }
        return actions.toImmutableList() to properties
    }

    internal companion object {
        /** The selected message ids, which survive process death. */
        const val KEY_SELECTION = "chat.selection"
    }
}

/** The full name of [message]'s sender, or the chat's title for the chat itself; "Someone" while unknown. */
private fun ActionContext.senderNameOf(message: Message): UiText =
    when (val sender = message.sender) {
        is MessageSender.User -> {
            fullNameOf(users[sender.userId])
        }

        is MessageSender.Chat -> {
            fullNameOf(
                if (sender.chatId ==
                    chat.id
                ) {
                    ChatRef(chat.id, chat.title, chat.photo)
                } else {
                    null
                },
            )
        }
    }

/** The messages of [history] among [ids], oldest first. */
private fun selectedIn(
    history: ChatHistory,
    ids: Set<Long>,
): List<Message> = if (ids.isEmpty()) emptyList() else history.messages.filter { it.id in ids }.sortedBy { it.id }

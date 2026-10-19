package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.editTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The message actions of `ChatViewModel` (`REQUIREMENTS.md` FR-C6, FR-C7): the actions [sheet] of a long-pressed
 * message, the [deleteDialog], and the actions themselves.
 *
 * - **Sheet** ([show]): TDLib's properties of the message ([MessageActionRepository.properties], from memory) and its
 *   available reactions, both asked at once; it opens once the properties are there, with the reactions if TDLib gave
 *   them, and only the actions TDLib allows ([actionsOf]). A failure is a snackbar instead.
 * - **Actions**: each asks for the properties again, as they change with time and screen readers start actions
 *   without the sheet, and does nothing but say so ([ChatEffect.ActionUnavailable]) where TDLib no longer allows it.
 *   Replying and editing go to the [composer], which takes the focus ([ChatEffect.FocusComposer]); copying goes out as
 *   [ChatEffect.CopyText]; pinning, deleting (after the [deleteDialog]) and resending go to TDLib, whose updates then
 *   change the history. An album is deleted, and its failed items resent, as a whole.
 * - **Reactions** ([toggleReaction]) go on or come off the message as the user had chosen them, without asking:
 *   TDLib refuses a reaction it does not offer.
 * - Failures go out as [ChatEffect.ShowError] snackbars.
 *
 * [current] gives the latest chat, history and composer state, from the view model's flows; null while the chat is
 * not known. Runs in [scope], the view model's, on the main thread.
 */
@Suppress("TooManyFunctions") // One function per action of the sheet, and the two that every action runs through.
internal class ChatMessageActions(
    private val chatId: Long,
    private val repository: MessageActionRepository,
    private val composer: ChatComposer,
    private val scope: CoroutineScope,
    private val current: suspend () -> ActionContext?,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val mutableSheet = MutableStateFlow<MessageActionsSheet?>(null)
    private val mutableDeleteDialog = MutableStateFlow<DeleteMessagesDialog?>(null)

    /** The open actions sheet; null while none is. */
    val sheet: StateFlow<MessageActionsSheet?> = mutableSheet.asStateFlow()

    /** The delete confirmation shown; null while none is. */
    val deleteDialog: StateFlow<DeleteMessagesDialog?> = mutableDeleteDialog.asStateFlow()

    /** The sheet being prepared, which a new long press replaces. */
    private var opening: Job? = null

    /** Opens the actions sheet of message [messageId] once TDLib told what it allows. */
    fun show(messageId: Long) {
        opening?.cancel()
        opening =
            scope.launch {
                val context = current() ?: return@launch
                val message = context.history.messages.findById(messageId) ?: return@launch
                val reactionsMessage = context.reactionsMessageOf(message)
                val reactions = async { repository.availableReactions(chatId, reactionsMessage.id) }
                when (val properties = repository.properties(chatId, messageId)) {
                    is Outcome.Failure -> {
                        reactions.cancel()
                        onEffect(ChatEffect.ShowError(properties.error))
                    }

                    is Outcome.Success -> {
                        val top =
                            reactions
                                .await()
                                .getOrNull()
                                ?.top
                                .orEmpty()
                        mutableSheet.value =
                            MessageActionsSheet(
                                messageId = messageId,
                                reactionsMessageId = reactionsMessage.id,
                                reactions = quickReactions(top, reactionsMessage),
                                actions = context.actionsOf(message, properties.value).toImmutableList(),
                            )
                    }
                }
            }
    }

    fun dismiss() {
        opening?.cancel()
        mutableSheet.value = null
    }

    fun reply(messageId: Long) =
        act(messageId, MessageAction.Reply) { _, _, _ ->
            composer.reply(messageId)
            onEffect(ChatEffect.FocusComposer)
        }

    fun copy(messageId: Long) =
        act(messageId, MessageAction.Copy) { _, message, _ ->
            onEffect(ChatEffect.CopyText(message.textOrCaption.text))
        }

    fun edit(messageId: Long) =
        act(messageId, MessageAction.Edit) { _, message, _ ->
            composer.edit(message)
            onEffect(ChatEffect.FocusComposer)
        }

    fun pin(
        messageId: Long,
        onlyForSelf: Boolean,
    ) = act(messageId, if (onlyForSelf) MessageAction.PinForSelf else MessageAction.Pin) { _, _, _ ->
        request { repository.pin(chatId, messageId, onlyForSelf) }
    }

    fun unpin(messageId: Long) =
        act(messageId, MessageAction.Unpin) { _, _, _ -> request { repository.unpin(chatId, messageId) } }

    /** Asks before deleting message [messageId], an album's every item, for whom TDLib allows. */
    fun delete(messageId: Long) =
        act(messageId, MessageAction.Delete) { context, message, properties ->
            val ids = context.albumOf(message).map { it.id }.sorted()
            mutableDeleteDialog.value = DeleteMessagesDialog(ids.toImmutableList(), context.deleteScope(properties))
        }

    /** Deletes what the dialog asked about: for everyone where it always is, or where its check box asked for it. */
    fun confirmDelete(forEveryone: Boolean) {
        val dialog = mutableDeleteDialog.value ?: return
        mutableDeleteDialog.value = null
        val revoke =
            when (dialog.scope) {
                DeleteScope.ForMe -> false
                DeleteScope.ForEveryone -> true
                is DeleteScope.Choice -> forEveryone
            }
        request { repository.delete(chatId, dialog.messageIds, revoke) }
    }

    fun dismissDelete() {
        mutableDeleteDialog.value = null
    }

    /** Sends message [messageId] again, with the album's other failed items. */
    fun resend(messageId: Long) =
        act(messageId, MessageAction.Resend) { context, message, _ ->
            val failed = context.albumOf(message).filter { it.canBeResent }.map { it.id }
            request { repository.resend(chatId, failed) }
        }

    /** Takes the user's reaction [label] off message [messageId] if they chose it, else adds it. */
    fun toggleReaction(
        messageId: Long,
        label: ReactionLabel,
    ) {
        dismiss()
        val type = label.reactionType ?: return
        scope.launch {
            val message = current()?.history?.messages?.findById(messageId) ?: return@launch
            val chosen =
                message.interactionInfo
                    ?.reactions
                    .orEmpty()
                    .any { it.type == type && it.isChosen }
            val result =
                if (chosen) {
                    repository.removeReaction(chatId, messageId, type)
                } else {
                    repository.addReaction(chatId, messageId, type)
                }
            result.onFailure { onEffect(ChatEffect.ShowError(it)) }
        }
    }

    /**
     * Runs [block] for message [messageId] if TDLib allows [action] for it now, else says it is not available. Closes
     * the sheet first; nothing for a message that is gone.
     */
    private fun act(
        messageId: Long,
        action: MessageAction,
        block: (ActionContext, Message, MessageProperties) -> Unit,
    ) {
        dismiss()
        scope.launch {
            val message = current()?.history?.messages?.findById(messageId) ?: return@launch
            when (val properties = repository.properties(chatId, messageId)) {
                is Outcome.Failure -> {
                    onEffect(ChatEffect.ShowError(properties.error))
                }

                is Outcome.Success -> {
                    // Fresh: the composer may have changed while TDLib answered.
                    val context = current() ?: return@launch
                    if (action in context.actionsOf(message, properties.value)) {
                        block(context, message, properties.value)
                    } else {
                        onEffect(ChatEffect.ActionUnavailable)
                    }
                }
            }
        }
    }

    private fun request(call: suspend () -> Outcome<Unit>) {
        scope.launch { call().onFailure { onEffect(ChatEffect.ShowError(it)) } }
    }

    /** TDLib's [top] reactions, chosen as [message]'s reactions are, at most [MAX_QUICK_REACTIONS]. */
    private fun quickReactions(
        top: List<ReactionType>,
        message: Message,
    ) = top
        .take(MAX_QUICK_REACTIONS)
        .map { type ->
            val chosen =
                message.interactionInfo
                    ?.reactions
                    .orEmpty()
                    .any { it.type == type && it.isChosen }
            QuickReaction(type.label, chosen)
        }.toImmutableList()

    private companion object {
        /** Quick reactions at most: two rows of TDLib's reaction picker. */
        const val MAX_QUICK_REACTIONS = 16
    }
}

/**
 * The latest state the actions depend on: the [chat], its loaded [history], the current user, and whether the user can
 * write in the chat ([canWrite]: the composer shows), which replying and editing need.
 */
internal class ActionContext(
    val chat: Chat,
    val history: ChatHistory,
    val myUserId: Long?,
    val canWrite: Boolean,
)

/**
 * What the actions sheet offers for [message] with TDLib's [properties], in its order: sending a failed message again,
 * replying and editing where the user can write, copying a text or caption the chat lets be saved, pinning or
 * unpinning (also for the user alone in a private chat), deleting.
 */
internal fun ActionContext.actionsOf(
    message: Message,
    properties: MessageProperties,
): List<MessageAction> =
    buildList {
        if (message.canBeResent) add(MessageAction.Resend)
        if (properties.canBeReplied && canWrite) add(MessageAction.Reply)
        if (properties.canBeSaved && message.textOrCaption.text.isNotEmpty()) add(MessageAction.Copy)
        if (properties.canBeEdited && canWrite && message.editTarget != null) add(MessageAction.Edit)
        if (properties.canBePinned) {
            if (message.isPinned) {
                add(MessageAction.Unpin)
            } else {
                add(MessageAction.Pin)
                if (pinsForSelf) add(MessageAction.PinForSelf)
            }
        }
        if (properties.canBeDeletedOnlyForSelf || properties.canBeDeletedForAllUsers) add(MessageAction.Delete)
    }

/** A failed message TDLib can send again. */
private val Message.canBeResent: Boolean get() = (sendingState as? SendingState.Failed)?.canRetry == true

/** A private chat with someone else, where a message can be pinned for the user alone; not Saved Messages. */
private val ActionContext.pinsForSelf: Boolean
    get() = (chat.type as? ChatType.Private)?.let { it.userId != myUserId } == true

/** For whom [properties] let messages be deleted, and how the dialog offers it. */
private fun ActionContext.deleteScope(properties: MessageProperties): DeleteScope =
    when {
        properties.canBeDeletedOnlyForSelf && properties.canBeDeletedForAllUsers -> {
            val option =
                if (chat.type is ChatType.Private) {
                    UiText.resource(R.string.message_delete_also_for, UiText.Plain(chat.title))
                } else {
                    UiText.resource(R.string.message_delete_for_everyone)
                }
            DeleteScope.Choice(option)
        }

        properties.canBeDeletedForAllUsers -> {
            DeleteScope.ForEveryone
        }

        else -> {
            DeleteScope.ForMe
        }
    }

/** The loaded items of [message]'s album, newest first, as its bubble shows them; the message alone if in none. */
private fun ActionContext.albumOf(message: Message): List<Message> =
    itemUnits(history.messages).firstOrNull { it.contains(message.id) }?.messages ?: listOf(message)

/** The message whose reactions [message]'s bubble shows: an album's newest. */
private fun ActionContext.reactionsMessageOf(message: Message): Message = albumOf(message).first()

/** The reaction a label names; null for the paid reaction, which is neither added nor taken back here. */
internal val ReactionLabel.reactionType: ReactionType?
    get() =
        when (this) {
            is ReactionLabel.Emoji -> ReactionType.Emoji(emoji)
            is ReactionLabel.CustomEmoji -> ReactionType.CustomEmoji(customEmojiId)
            ReactionLabel.Paid -> null
        }

/** How a chip shows a reaction. */
internal val ReactionType.label: ReactionLabel
    get() =
        when (this) {
            is ReactionType.Emoji -> ReactionLabel.Emoji(emoji)
            is ReactionType.CustomEmoji -> ReactionLabel.CustomEmoji(customEmojiId)
            ReactionType.Paid -> ReactionLabel.Paid
        }

package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.editTarget
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The message actions of `ChatViewModel` (`REQUIREMENTS.md` FR-C6, FR-C7): the actions [sheet] of a long-pressed
 * message, the [deleteDialog], and the actions themselves.
 *
 * - **Sheet** ([show]): TDLib's properties of the message ([MessageActionRepository.properties], from memory) and its
 *   available reactions, both asked at once; it opens once the properties are there, with the reactions if TDLib gave
 *   them, and only the actions TDLib allows ([actionsOf]), with "Translate" or "Show original" where
 *   [translationAction] offers one (asked at the same time). A failure is a snackbar instead. The pictures of its
 *   custom emoji follow once it is open.
 * - **Reactions picker** (`REQUIREMENTS.md` FR-C7): every reaction TDLib offers by section, after the message's own
 *   reactions, where that shows more than the quick row and the bubble ([reactionPicker]). The sheet opens at it from a
 *   bubble's "+N" ([show] with `showsPicker`), and switches to it and back ([showPicker]).
 * - **Actions**: each asks for the properties again, as they change with time and screen readers start actions
 *   without the sheet, and does nothing but say so ([ChatEffect.ActionUnavailable]) where TDLib no longer allows it.
 *   Replying and editing go to the [composer], which takes the focus ([ChatEffect.FocusComposer]); copying goes out as
 *   [ChatEffect.CopyText]; forwarding opens the forward sheet through [onForward]; selecting starts selection mode
 *   through [onSelect], reporting the report sheet through [onReport], saving to Downloads goes to [onSave]; pinning,
 *   deleting (after the [deleteDialog]) and resending go to TDLib, whose updates then change the history. An album is
 *   forwarded, selected, reported, saved and deleted, and its failed items resent, as a whole. Selection mode deletes
 *   its messages through [askDelete], and ends through [onSelectionDeleted] once they go.
 * - **Reactions** ([toggleReaction]), from the quick row, the picker or a bubble, go on or come off the message as the
 *   user had chosen them, without asking: TDLib refuses a reaction it does not offer.
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
    private val onForward: (List<Message>) -> Unit = {},
    private val onSelect: (List<Long>) -> Unit = {},
    private val onReport: (List<Long>) -> Unit = {},
    private val onSave: (List<Message>) -> Unit = {},
    private val onSelectionDeleted: () -> Unit = {},
    private val translationAction: suspend (Message) -> MessageAction? = { null },
) {
    private val mutableSheet = MutableStateFlow<MessageActionsSheet?>(null)
    private val mutableDeleteDialog = MutableStateFlow<DeleteMessagesDialog?>(null)

    /** The open actions sheet; null while none is. */
    val sheet: StateFlow<MessageActionsSheet?> = mutableSheet.asStateFlow()

    /** The delete confirmation shown; null while none is. */
    val deleteDialog: StateFlow<DeleteMessagesDialog?> = mutableDeleteDialog.asStateFlow()

    /** The sheet being prepared, which a new long press replaces. */
    private var opening: Job? = null

    /**
     * Opens the actions sheet of message [messageId] once TDLib told what it allows, at its reactions picker if
     * [showsPicker] and the sheet has one; then asks for the pictures of the custom emoji it shows.
     */
    fun show(
        messageId: Long,
        showsPicker: Boolean = false,
    ) {
        opening?.cancel()
        opening =
            scope.launch {
                val context = current() ?: return@launch
                val message = context.history.messages.findById(messageId) ?: return@launch
                val reactionsMessage = context.reactionsMessageOf(message)
                val reactions = async { repository.availableReactions(chatId, reactionsMessage.id) }
                val translate = async { translationAction(message) }
                when (val properties = repository.properties(chatId, messageId)) {
                    is Outcome.Failure -> {
                        reactions.cancel()
                        translate.cancel()
                        onEffect(ChatEffect.ShowError(properties.error))
                    }

                    is Outcome.Success -> {
                        val available = reactions.await().getOrNull() ?: NO_REACTIONS
                        val quick = reactionOptions(available.top.take(MAX_QUICK_REACTIONS), reactionsMessage)
                        val picker = reactionPicker(available, quick.size, reactionsMessage)
                        val sheet =
                            MessageActionsSheet(
                                messageId = messageId,
                                reactionsMessageId = reactionsMessage.id,
                                reactions = quick,
                                actions =
                                    context
                                        .actionsOf(message, properties.value)
                                        .withTranslation(translate.await())
                                        .toImmutableList(),
                                picker = picker,
                                showsPicker = showsPicker && picker != null,
                            )
                        mutableSheet.value = sheet
                        loadCustomEmojiPictures(sheet)
                    }
                }
            }
    }

    /** Shows the open sheet's reactions picker if [shown] and it has one, else its quick reactions and actions. */
    fun showPicker(shown: Boolean) {
        mutableSheet.update { sheet -> sheet?.copy(showsPicker = shown && sheet.picker != null) }
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

    /** Opens the forward sheet for message [messageId] with the album's other items, through [onForward]. */
    fun forward(messageId: Long) =
        act(messageId, MessageAction.Forward) { context, message, _ -> onForward(context.albumOf(message)) }

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
            val ids = context.albumOf(message).map { it.id }
            askDelete(ids, context.deleteScope(listOf(properties)))
        }

    /**
     * Asks before deleting [messageIds] ([scope]: for whom TDLib allows it for every one of them), the selected
     * messages if [fromSelection].
     */
    fun askDelete(
        messageIds: List<Long>,
        scope: DeleteScope,
        fromSelection: Boolean = false,
    ) {
        mutableDeleteDialog.value = DeleteMessagesDialog(messageIds.sorted().toImmutableList(), scope, fromSelection)
    }

    /** Starts selection mode with message [messageId], an album's every item. */
    fun select(messageId: Long) =
        act(messageId, MessageAction.Select) { context, message, _ -> onSelect(context.albumOf(message).map { it.id }) }

    /** Opens the report sheet for message [messageId], an album's every item. */
    fun report(messageId: Long) =
        act(messageId, MessageAction.Report) { context, message, _ -> onReport(context.albumOf(message).map { it.id }) }

    /** Saves the file of message [messageId], an album's every file, to Downloads. */
    fun saveToDownloads(messageId: Long) =
        act(messageId, MessageAction.SaveToDownloads) { context, message, _ -> onSave(context.albumOf(message)) }

    /** Deletes what the dialog asked about: for everyone where it always is, or where its check box asked for it. */
    fun confirmDelete(forEveryone: Boolean) {
        val dialog = mutableDeleteDialog.value ?: return
        mutableDeleteDialog.value = null
        if (dialog.fromSelection) onSelectionDeleted()
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

    /**
     * The pictures of the custom emoji [sheet] shows, added to it while it is open (`getCustomEmojiStickers`, usually
     * from TDLib's memory). Without them, or when TDLib cannot give them, custom emoji show their placeholder.
     */
    private suspend fun loadCustomEmojiPictures(sheet: MessageActionsSheet) {
        val ids = sheet.customEmojiIds()
        val stickers = if (ids.isEmpty()) null else repository.customEmojiStickers(ids).getOrNull()
        val pictures =
            stickers
                .orEmpty()
                .mapNotNull { (id, sticker) -> sticker.pictureFileId?.let { id to it } }
                .toMap()
        if (pictures.isNotEmpty()) {
            mutableSheet.update { open ->
                open?.takeIf { it.messageId == sheet.messageId }?.copy(customEmojiPictures = pictures.toImmutableMap())
            }
        }
    }

    /** [types] as the sheet offers them, chosen as [message]'s reactions are. */
    private fun reactionOptions(
        types: List<ReactionType>,
        message: Message,
    ): ImmutableList<ReactionOption> {
        val chosen =
            message.interactionInfo
                ?.reactions
                .orEmpty()
                .filter { it.isChosen }
                .mapTo(mutableSetOf()) { it.type }
        return types.map { ReactionOption(it.label, isChosen = it in chosen) }.toImmutableList()
    }

    /**
     * The reactions picker of [message] with the [available] reactions: the message's own, then TDLib's sections. Null
     * where it would show nothing that the [quickCount] quick reactions and the message's bubble do not: every
     * available reaction is in the quick row, and the bubble has room for all of the message's reactions.
     */
    private fun reactionPicker(
        available: AvailableReactions,
        quickCount: Int,
        message: Message,
    ): ReactionPicker? {
        val sections =
            listOf(
                ReactionSectionKind.Top to available.top,
                ReactionSectionKind.Recent to available.recent,
                ReactionSectionKind.Popular to available.popular,
            ).filter { (_, types) -> types.isNotEmpty() }
                .map { (kind, types) -> ReactionSection(kind, reactionOptions(types, message)) }
        val onMessage = message.reactionChips()
        val offersMore = sections.sumOf { it.reactions.size } > quickCount
        val showsMore = onMessage.size > MAX_REACTION_BADGES
        return if (offersMore || showsMore) ReactionPicker(onMessage, sections.toImmutableList()) else null
    }

    private companion object {
        /** Quick reactions at most: two rows of TDLib's reaction picker. */
        const val MAX_QUICK_REACTIONS = 16

        val NO_REACTIONS = AvailableReactions(persistentListOf())
    }
}

/** The custom emoji [this] shows: in its quick row, in its picker and on its message. */
private fun MessageActionsSheet.customEmojiIds(): List<Long> {
    val labels =
        reactions.map { it.label } +
            picker
                ?.sections
                .orEmpty()
                .flatMap { section -> section.reactions.map { it.label } } +
            picker?.onMessage.orEmpty().map { it.label }
    return labels.filterIsInstance<ReactionLabel.CustomEmoji>().map { it.customEmojiId }.distinct()
}

/** The TDLib file that pictures this sticker: itself if it is a still, else its thumbnail if that is a picture. */
private val Sticker.pictureFileId: Int?
    get() =
        when (format) {
            StickerFormat.Webp -> file.id
            StickerFormat.Tgs, StickerFormat.Webm -> thumbnail?.asPicture()?.file?.id
        }

/**
 * The latest state the actions depend on: the [chat], its loaded [history], the current user, and whether the user can
 * write in the chat ([canWrite]: the composer shows), which replying and editing need; the [users] known, whose names
 * copied messages take.
 */
internal class ActionContext(
    val chat: Chat,
    val history: ChatHistory,
    val myUserId: Long?,
    val canWrite: Boolean,
    val users: Map<Long, User> = emptyMap(),
)

/**
 * What the actions sheet offers for [message] with TDLib's [properties], in its order: sending a failed message again,
 * replying and editing where the user can write, copying a text or caption the chat lets be saved, forwarding where
 * TDLib allows it (never protected content), pinning or unpinning (also for the user alone in a private chat), saving
 * its file (an album's files) to Downloads where the chat lets it be saved, selecting, reporting where TDLib allows
 * it, deleting.
 */
internal fun ActionContext.actionsOf(
    message: Message,
    properties: MessageProperties,
): List<MessageAction> =
    buildList {
        if (message.canBeResent) add(MessageAction.Resend)
        if (properties.canBeReplied && canWrite) add(MessageAction.Reply)
        if (properties.canBeSaved && message.textOrCaption.text.isNotEmpty()) add(MessageAction.Copy)
        if (properties.canBeForwarded) add(MessageAction.Forward)
        if (properties.canBeEdited && canWrite && message.editTarget != null) add(MessageAction.Edit)
        if (properties.canBePinned) addAll(pinActions(message))
        addAll(laterActions(message, properties))
    }

/** Saving [message]'s files where the chat lets it be saved, selecting, reporting and deleting where TDLib allows. */
private fun ActionContext.laterActions(
    message: Message,
    properties: MessageProperties,
): List<MessageAction> =
    buildList {
        val hasFiles = albumOf(message).any { it.downloadsFile != null }
        if (properties.canBeSaved && hasFiles) add(MessageAction.SaveToDownloads)
        add(MessageAction.Select)
        if (properties.canBeReported) add(MessageAction.Report)
        if (properties.canBeDeletedOnlyForSelf || properties.canBeDeletedForAllUsers) add(MessageAction.Delete)
    }

/**
 * The actions with [translation] ("Translate" or "Show original", `ChatTranslation`) after copying, or replying where
 * the text cannot be copied, else first; as they were without one.
 */
private fun List<MessageAction>.withTranslation(translation: MessageAction?): List<MessageAction> {
    if (translation == null) return this
    val after = indexOfLast { it == MessageAction.Copy || it == MessageAction.Reply || it == MessageAction.Resend }
    return toMutableList().apply { add(after + 1, translation) }
}

/** Unpinning a pinned [message], else pinning it, also for the user alone where that is possible. */
private fun ActionContext.pinActions(message: Message): List<MessageAction> =
    when {
        message.isPinned -> listOf(MessageAction.Unpin)
        pinsForSelf -> listOf(MessageAction.Pin, MessageAction.PinForSelf)
        else -> listOf(MessageAction.Pin)
    }

/** A failed message TDLib can send again. */
private val Message.canBeResent: Boolean get() = (sendingState as? SendingState.Failed)?.canRetry == true

/** A private chat with someone else, where a message can be pinned for the user alone; not Saved Messages. */
private val ActionContext.pinsForSelf: Boolean
    get() = (chat.type as? ChatType.Private)?.let { it.userId != myUserId } == true

/**
 * For whom messages with TDLib's [properties] can all be deleted, and how the dialog offers it: for everyone only where
 * every one of them can be; for the user alone too where every one of them can be.
 */
internal fun ActionContext.deleteScope(properties: List<MessageProperties>): DeleteScope {
    val forAll = properties.all { it.canBeDeletedForAllUsers }
    val forSelf = properties.all { it.canBeDeletedOnlyForSelf }
    return when {
        forSelf && forAll -> {
            val option =
                if (chat.type is ChatType.Private) {
                    UiText.resource(R.string.message_delete_also_for, UiText.Plain(chat.title.bidiIsolated()))
                } else {
                    UiText.resource(R.string.message_delete_for_everyone)
                }
            DeleteScope.Choice(option)
        }

        forAll -> {
            DeleteScope.ForEveryone
        }

        else -> {
            DeleteScope.ForMe
        }
    }
}

/** The loaded items of [message]'s album, newest first, as its bubble shows them; the message alone if in none. */
internal fun ActionContext.albumOf(message: Message): List<Message> =
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

package dev.kyralabs.pingogram.feature.chat.ui.forward

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardOptions
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ForwardEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Forwarding messages of chat [fromChatId] for `ChatViewModel` and `ForwardViewModel` (`REQUIREMENTS.md` FR-C6): the
 * forward [sheet], its [comment], and the forwarding itself. It takes any messages of the chat ([open]), so a
 * selection of several forwards the same way as one message or an album.
 *
 * - **Opening** ([open]): TDLib's properties of every message, asked at once; the sheet opens with those TDLib lets be
 *   forwarded (protected content never is), else a snackbar says it is not possible ([ChatEffect.ActionUnavailable]).
 *   It offers "Hide sender's name" where every message can be copied, and "Hide captions" where one has a caption.
 * - **Targets** ([ChatTargets]): without a query Saved Messages first (created on the device if needed), then the
 *   main chat list in TDLib's order, as far as it is loaded; [loadMore] loads its next page when the list's end comes
 *   near. A query ([setQuery]) searches the chats ([SearchRepository.searchChats]) once it has not changed for
 *   [DEBOUNCE_MILLIS], cancelling the previous search. Either way only chats where the user can send messages
 *   ([WriteAccess.Allowed]), and secret chats only when every message can be copied into one.
 * - **Picking**: [toggle] picks or unpicks a chat; picked chats stay picked while a search hides them. Hiding captions
 *   hides the sender's name too, and showing the sender's name shows the captions, as TDLib copies only without names.
 * - **Sending** ([send]) closes the sheet at once and forwards to each picked chat in turn, with the comment first
 *   ([MessageActionRepository.forward]). Then [ChatEffect.Forwarded] (naming the one chat, which the snackbar can
 *   open), or [ChatEffect.ForwardFailed] with the first error and how many chats it failed for. A flood wait stops
 *   it: the chats left would fail the same way.
 *
 * The sheet is mapped on [dispatcher], and followed only while the screen collects it. Runs in [scope], the view
 * model's, on the main thread; [comment] is touched there only, as its text field does. The forwarding after [send]
 * runs in [sendScope], the same unless the sheet's owner goes as the sheet closes (the forward destination, whose
 * forwarding goes on in the app's scope). [onClosed] runs whenever the sheet closes, sent or dismissed.
 */
@Suppress("LongParameterList", "TooManyFunctions") // The repositories the targets combine, and one function per event.
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ChatForwarding(
    private val fromChatId: Long,
    private val chats: ChatRepository,
    private val search: SearchRepository,
    private val users: UserRepository,
    private val groups: GroupRepository,
    private val actions: MessageActionRepository,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val sendScope: CoroutineScope = scope,
    private val onClosed: () -> Unit = {},
    targetPreferences: TargetPreferences = TargetPreferences(),
    private val onEffect: (ChatEffect) -> Unit,
) {
    /** The comment sent before the messages, formatting included; emptied whenever the sheet opens. */
    val comment = ComposerText()

    private val request = MutableStateFlow<ForwardRequest?>(null)
    private val choice = MutableStateFlow(Choice())
    private val chatTargets = ChatTargets(chats, search, users, groups, scope, preferences = targetPreferences)

    /** The sheet being prepared, which another [open] replaces. */
    private var opening: Job? = null

    /** The open forward sheet; null while none is. */
    val sheet: StateFlow<ForwardSheet?> =
        request
            .flatMapLatest { request -> if (request == null) flowOf(null) else sheetOf(request) }
            .flowOn(dispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** What runs once the open sheet sends, the selection mode it was opened from ending; nothing by default. */
    private var onSent: () -> Unit = {}

    /**
     * Opens the sheet for [messages] of the chat, those TDLib lets be forwarded, once TDLib told which they are;
     * [onSent] runs when it sends them.
     */
    fun open(
        messages: List<Message>,
        onSent: () -> Unit = {},
    ) {
        opening?.cancel()
        this.onSent = onSent
        val toForward = messages.distinctBy { it.id }.sortedBy { it.id }.take(MessageActionRepository.MAX_FORWARDED)
        if (toForward.isEmpty()) return
        opening =
            scope.launch {
                val answers =
                    toForward
                        .map { message ->
                            async { actions.properties(fromChatId, message.id) }
                        }.awaitAll()
                val failure = answers.firstNotNullOfOrNull { it as? Outcome.Failure }
                if (failure != null) {
                    onEffect(ChatEffect.ShowError(failure.error))
                    return@launch
                }
                val allowed =
                    toForward.zip(answers).mapNotNull { (message, answer) ->
                        val properties = (answer as Outcome.Success<MessageProperties>).value
                        (message to properties).takeIf { properties.canBeForwarded }
                    }
                if (allowed.isEmpty()) {
                    onEffect(ChatEffect.ActionUnavailable)
                    return@launch
                }
                comment.setContent(FormattedText.Empty)
                choice.value = Choice()
                chatTargets.setQuery("")
                request.value =
                    ForwardRequest(
                        messageIds = allowed.map { it.first.id },
                        canHideSenderName = allowed.all { it.second.canBeCopied },
                        hasCaptions = allowed.any { (message, _) -> message.hasCaption },
                        canCopyToSecretChat = allowed.all { it.second.canBeCopiedToSecretChat },
                    )
            }
    }

    /** Handles what the user does in the sheet. */
    fun onEvent(event: ForwardEvent) {
        when (event) {
            is ForwardEvent.QueryChanged -> setQuery(event.query)
            is ForwardEvent.ToggleTarget -> toggle(event.chatId)
            is ForwardEvent.SetHideSenderName -> setHideSenderName(event.hide)
            is ForwardEvent.SetHideCaptions -> setHideCaptions(event.hide)
            ForwardEvent.LoadMore -> loadMore()
            ForwardEvent.Send -> send()
            ForwardEvent.Dismiss -> dismiss()
        }
    }

    /** Closes the sheet without forwarding. */
    fun dismiss() {
        opening?.cancel()
        onSent = {}
        request.value = null
        onClosed()
    }

    /** What the search field holds now; it is searched once it has not changed for [DEBOUNCE_MILLIS]. */
    fun setQuery(text: String) {
        chatTargets.setQuery(text)
    }

    /** Picks chat [chatId], or unpicks it if it was picked. */
    fun toggle(chatId: Long) {
        choice.update { choice ->
            val selected = choice.selected
            choice.copy(selected = if (chatId in selected) selected.remove(chatId) else selected.add(chatId))
        }
    }

    /** Sends copies without the sender's name, or forwards; showing the name shows the captions again. */
    fun setHideSenderName(hide: Boolean) {
        choice.update { it.copy(hideSenderName = hide, hideCaptions = it.hideCaptions && hide) }
    }

    /** Leaves the captions out, which hides the sender's name too, or keeps them. */
    fun setHideCaptions(hide: Boolean) {
        choice.update { it.copy(hideCaptions = hide, hideSenderName = it.hideSenderName || hide) }
    }

    /**
     * The end of the chat list comes near: loads its next page, one at a time, until TDLib has no more. Not while the
     * sheet shows search results, which come whole.
     */
    fun loadMore() {
        if (request.value != null) chatTargets.loadMore()
    }

    /** Closes the sheet and forwards to every picked chat, the comment first; nothing while no chat is picked. */
    fun send() {
        val request = request.value ?: return
        val choice = choice.value
        val targets = choice.selected.toList()
        if (targets.isEmpty()) return
        val text = comment.content.takeUnless { it.text.isBlank() }
        val options =
            ForwardOptions(
                hideSenderName = request.canHideSenderName && choice.hideSenderName,
                hideCaptions = request.canHideSenderName && request.hasCaptions && choice.hideCaptions,
            )
        // The title of the one chat, for the snackbar; its target is shown unless a search hides it.
        val single = targets.singleOrNull()?.let { id -> sheet.value?.targets?.firstOrNull { it.chatId == id } }
        onSent()
        dismiss()
        sendScope.launch {
            var forwarded = 0
            var error: DomainError? = null
            for (target in targets) {
                val result = actions.forward(fromChatId, request.messageIds, target, text, options)
                if (result is Outcome.Failure) {
                    error = error ?: result.error
                    if (result.error is DomainError.FloodWait) break
                } else {
                    forwarded++
                }
            }
            onEffect(
                if (error == null) {
                    // A chat other than this one can be opened from the snackbar.
                    ChatEffect.Forwarded(
                        count = forwarded,
                        chatTitle = single?.title,
                        openChatId = single?.chatId?.takeIf { it != fromChatId },
                    )
                } else {
                    ChatEffect.ForwardFailed(error, failed = targets.size - forwarded, forwarded = forwarded)
                },
            )
        }
    }

    /** The sheet for [request]: its targets as the query and the chats change, with the user's choices. */
    private fun sheetOf(request: ForwardRequest): Flow<ForwardSheet> {
        // Only chats where the user can send messages; into a secret chat only what can be copied there.
        val found =
            chatTargets.targets { chat, access, _ ->
                access == WriteAccess.Allowed && (chat.type !is ChatType.Secret || request.canCopyToSecretChat)
            }
        return combine(found, choice) { found, choice ->
            ForwardSheet(
                messageCount = request.messageIds.size,
                targets = found.targets,
                selectedIds = choice.selected,
                isSearching = found.isSearching,
                isLoading = found.isLoading,
                searchError = found.error,
                canHideSenderName = request.canHideSenderName,
                hasCaptions = request.hasCaptions,
                hideSenderName = request.canHideSenderName && choice.hideSenderName,
                hideCaptions = request.canHideSenderName && request.hasCaptions && choice.hideCaptions,
            )
        }
    }

    /** What the sheet forwards, as far as the choices depend on it. */
    private data class ForwardRequest(
        val messageIds: List<Long>,
        val canHideSenderName: Boolean,
        val hasCaptions: Boolean,
        val canCopyToSecretChat: Boolean,
    )

    /** The user's choices in the open sheet. */
    private data class Choice(
        val selected: PersistentSet<Long> = persistentSetOf(),
        val hideSenderName: Boolean = false,
        val hideCaptions: Boolean = false,
    )

    internal companion object {
        /** How long the query must stay unchanged before it is searched. */
        const val DEBOUNCE_MILLIS = ChatTargets.DEBOUNCE_MILLIS

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** A message whose caption the copies can leave out: media with a caption; a text's text is no caption. */
private val Message.hasCaption: Boolean
    get() = content !is MessageContent.Text && textOrCaption.text.isNotEmpty()

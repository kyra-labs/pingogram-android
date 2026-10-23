package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.KeyboardButton
import dev.kyralabs.pingogram.core.domain.model.KeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEffect
import dev.kyralabs.pingogram.feature.chat.ui.BotKeyboardEffect
import dev.kyralabs.pingogram.feature.chat.ui.BotKeyboardEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.UnsupportedButton
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.findById
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatTargets
import dev.kyralabs.pingogram.feature.chat.ui.forward.TargetCandidate
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The bot's keyboard in effect in chat [chatId] (td_api.tl `chat.reply_markup_message_id`, `replyMarkupShowKeyboard`),
 * for `ChatViewModel`: which one, as the composer shows it ([state]), and what tapping its buttons does. Every request
 * is one tap of the user's, and nothing is shared before they chose it and agreed (Telegram's API terms). Only the
 * whole chat's conversation has one ([enabled] false in a topic or thread, whose composer would reply across them).
 *
 * - **Which**: the message the chat names ([Chat.replyMarkupMessageId]), as the history has it, so a bot's edit counts
 *   at once; fetched once ([MessageRepository.message]) when it is not loaded. A keyboard of [ReplyMarkup.ShowKeyboard]
 *   shows; [ReplyMarkup.RemoveKeyboard] and none show nothing. Whether the keyboard is on screen is the composer's (its
 *   one-time and persistent rules, the toggle), not this class's.
 * - **Force reply** ([ReplyMarkup.ForceReply], or a keyboard that asks for one): once the user can write ([writable]),
 *   the composer replies to the message and takes the focus, once per message (kept in [savedState]), not again after
 *   the user cancelled the reply, nor while they reply to or edit another; its
 *   placeholder is the field's hint while the composer replies to it ([BotReplyUiState.placeholder]).
 * - **Text** buttons send their text. In a group it replies to the keyboard's message, as Telegram's apps do, so a bot
 *   that sees only replies gets it; the composer's own reply goes first. The button shows its progress meanwhile.
 * - **Phone number**: "Share your phone number with @bot?" ([BotKeyboardDialog.SharePhone]), then the user's own
 *   contact ([AttachmentRepository.sendContact]: their number and name).
 * - **Location**: the attach menu's "Location" sheet ([BotKeyboardEffect.ShareLocation]), which asks for the
 *   permission and sends only once the user confirms there.
 * - **Poll**: the poll form ([onOpenPoll]), a quiz or a regular poll where the button requires one.
 * - **Users and chat**: a picker ([shareSheet], kept out of `ChatUiState` like the forward sheet) of the candidates the
 *   button's restrictions let through: users from the chat list's private chats and the contacts (only bots, or no
 *   bots; with or without Telegram Premium), or groups or channels the user is in (forum or not, with a username or
 *   not, created by the user, the user an administrator where the button asks for rights). TDLib does not filter for
 *   us; the user's exact rights and the bot's are not known here, so the sheet says Telegram checks them, and that the
 *   bot will be added or made an administrator where the button asks it to be. Then [BotButtonRepository.shareUsers]
 *   or [BotButtonRepository.shareChat] with the button's message and id; `only_check` is never used.
 * - **Mini Apps** say that Pingogram cannot open them yet ([ChatEffect.MiniAppsUnsupported]); buttons that create a
 *   bot too ([UnsupportedButton.ManagedBot]).
 *
 * Runs in [scope], the view model's, on the main thread; the keyboard and the picker are mapped on [dispatcher].
 */
@Suppress("LongParameterList", "TooManyFunctions") // What the buttons reach, and one function per kind of button.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatBotKeyboard(
    private val chatId: Long,
    private val enabled: Boolean,
    chat: Flow<Chat>,
    history: Flow<ChatHistory>,
    private val messages: MessageRepository,
    private val attachments: AttachmentRepository,
    private val repository: BotButtonRepository,
    private val users: UserRepository,
    chats: ChatRepository,
    search: SearchRepository,
    groups: GroupRepository,
    private val composer: ChatComposer,
    private val savedState: SavedStateHandle,
    private val canWrite: () -> Boolean,
    private val writable: Flow<Boolean>,
    private val onOpenPoll: (requiredQuiz: Boolean?) -> Unit,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val onEffect: (ChatEffect) -> Unit,
) {
    /** Whether the chat is a private one, where a button's message replies to nothing; set where the chat is read. */
    @Volatile
    private var isPrivateChat = true

    /**
     * The message whose markup is in effect, as the history has it now; null for none. Followed from [start] and the
     * composer's collection of [state], once the screen's first frame is done.
     */
    private val markupMessage: StateFlow<Message?> =
        chat
            .onEach { isPrivateChat = it.type is ChatType.Private }
            .map { it.replyMarkupMessageId.takeIf { enabled } ?: 0L }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == 0L) flowOf(null) else messageOf(id, history) }
            .distinctUntilChanged()
            .flowOn(dispatcher)
            .stateIn(scope, SharingStarted.Lazily, null)

    private val pendingButtons = MutableStateFlow<PersistentSet<BotButtonPosition>>(persistentSetOf())

    /** The keyboard the composer shows, and its field's hint. */
    val state: StateFlow<BotReplyUiState> =
        combine(markupMessage, pendingButtons, composer.mode) { message, pending, mode ->
            BotReplyUiState(
                keyboard = message?.replyKeyboard(pending),
                placeholder = message?.placeholder(mode).orEmpty(),
            )
        }.flowOn(dispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BotReplyUiState())

    private val openDialog = MutableStateFlow<BotKeyboardDialog?>(null)

    /** The question before the user's phone number is shared; null while there is none. */
    val dialog: StateFlow<BotKeyboardDialog?> = openDialog.asStateFlow()

    /** The button whose question or picker is open: its message, id and request. */
    private var asking: Asking? = null

    private val chatTargets = ChatTargets(chats, search, users, groups, scope)
    private val userTargets = ChatTargets(chats, search, users, groups, scope, withContacts = true)
    private val sheetRequest = MutableStateFlow<SheetRequest?>(null)

    /** The users or chat picker of a keyboard button; null while it is closed. */
    val shareSheet: StateFlow<KeyboardShareSheet?> =
        sheetRequest
            .flatMapLatest { request ->
                if (request == null) {
                    flowOf(null)
                } else {
                    val targets = if (request.kind == ShareKind.Users) userTargets else chatTargets
                    targets.candidates(request::allows).map { found ->
                        request.sheet.copy(
                            targets = found.targets,
                            isSearching = found.isSearching,
                            isLoading = found.isLoading,
                            searchError = found.error,
                        )
                    }
                }
            }.flowOn(dispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Starts the reply a bot's message asks for, once per message. */
    fun start() {
        scope.launch {
            combine(markupMessage, writable.distinctUntilChanged()) { message, canWrite -> message.takeIf { canWrite } }
                .collect(::forceReply)
        }
    }

    fun onEvent(event: BotKeyboardEvent) {
        when (event) {
            is BotKeyboardEvent.Press -> press(event.messageId, event.position)
            BotKeyboardEvent.SharePhone -> sharePhone()
            BotKeyboardEvent.DismissDialog -> closeDialog()
            is BotKeyboardEvent.SearchTargets -> targetsOf(sheetRequest.value)?.setQuery(event.query)
            BotKeyboardEvent.LoadMoreTargets -> targetsOf(sheetRequest.value)?.loadMore()
            is BotKeyboardEvent.ToggleTarget -> toggle(event.chatId)
            BotKeyboardEvent.Share -> share()
            BotKeyboardEvent.DismissShare -> closeSheet()
        }
    }

    private fun forceReply(message: Message?) {
        val asks =
            when (val markup = message?.replyMarkup) {
                is ReplyMarkup.ForceReply -> true
                is ReplyMarkup.ShowKeyboard -> markup.forceReply
                else -> false
            }
        if (message == null || !asks || savedState.get<Long>(KEY_FORCE_REPLIED) == message.id) return
        savedState[KEY_FORCE_REPLIED] = message.id
        if (composer.mode.value == ComposerMode.Writing) {
            composer.reply(message.id)
            onEffect(ChatEffect.FocusComposer)
        }
    }

    private fun press(
        messageId: Long,
        position: BotButtonPosition,
    ) {
        val message = markupMessage.value?.takeIf { it.id == messageId }
        val button = (message?.replyMarkup as? ReplyMarkup.ShowKeyboard)?.buttonAt(position)
        if (message == null || button == null || !canWrite()) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        if (position in pendingButtons.value) return
        act(Asking(message, position, button))
    }

    private fun act(target: Asking) {
        when (val type = target.button.type) {
            KeyboardButtonType.Text -> {
                sendText(target)
            }

            KeyboardButtonType.RequestPhoneNumber -> {
                askPhone(target)
            }

            KeyboardButtonType.RequestLocation -> {
                onEffect(BotKeyboardEffect.ShareLocation)
            }

            is KeyboardButtonType.RequestPoll -> {
                onOpenPoll(type.quiz)
            }

            is KeyboardButtonType.RequestUsers -> {
                openSheet(target, ShareKind.Users, type.maxQuantity, type)
            }

            is KeyboardButtonType.RequestChat -> {
                openSheet(target, chatKind(type), maxCount = 1, type)
            }

            is KeyboardButtonType.RequestManagedBot -> {
                onEffect(
                    BotButtonEffect.Unsupported(UnsupportedButton.ManagedBot),
                )
            }

            is KeyboardButtonType.WebApp -> {
                onEffect(ChatEffect.MiniAppsUnsupported)
            }

            KeyboardButtonType.Unsupported -> {
                onEffect(ChatEffect.ActionUnavailable)
            }
        }
    }

    /** The button's text as a message, replying where the composer replies, else in a group to the keyboard. */
    private fun sendText(target: Asking) {
        whilePending(target.position) {
            val own = composer.takeReply()
            val text = FormattedText(target.button.text, persistentListOf())
            val result = messages.sendText(chatId, text, replyToMessageId = own ?: groupReplyTo(target.message))
            if (result is Outcome.Failure) {
                own?.let(composer::restoreReply)
                onEffect(ChatEffect.ShowError(result.error))
            }
        }
    }

    private fun askPhone(target: Asking) {
        scope.launch {
            val me = users.myUserId.value?.let { userOf(it) }
            if (me == null || me.phoneNumber.isBlank()) {
                onEffect(ChatEffect.ActionUnavailable)
                return@launch
            }
            asking = target
            openDialog.value =
                BotKeyboardDialog.SharePhone(botName = botName(target.message), phoneNumber = me.phoneNumber)
        }
    }

    /** The user's own contact, once they agreed: their number and name, as Telegram's apps share it. */
    private fun sharePhone() {
        val dialog = (openDialog.value as? BotKeyboardDialog.SharePhone)?.takeUnless { it.isSharing } ?: return
        val target = asking ?: return
        openDialog.value = dialog.copy(isSharing = true)
        whilePending(target.position) {
            val me = users.myUserId.value?.let { userOf(it) }
            val result =
                if (me == null) {
                    null
                } else {
                    val contact = Contact(me.phoneNumber, me.firstName, me.lastName, me.id)
                    val own = composer.takeReply()
                    attachments
                        .sendContact(chatId, contact, replyToMessageId = own ?: groupReplyTo(target.message))
                        .also { if (it is Outcome.Failure) own?.let(composer::restoreReply) }
                }
            closeDialog()
            when (result) {
                null -> onEffect(ChatEffect.ActionUnavailable)
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(result.error))
                is Outcome.Success -> Unit
            }
        }
    }

    private fun closeDialog() {
        openDialog.value = null
        if (sheetRequest.value == null) asking = null
    }

    private fun openSheet(
        target: Asking,
        kind: ShareKind,
        maxCount: Int,
        type: KeyboardButtonType,
    ) {
        scope.launch {
            val bot = botName(target.message)
            val botUserId = (target.message.sender as? MessageSender.User)?.userId
            asking = target
            targetsOf(kind).setQuery("")
            sheetRequest.value =
                SheetRequest(
                    kind = kind,
                    type = type,
                    botUserId = botUserId,
                    sheet =
                        KeyboardShareSheet(
                            kind = kind,
                            botName = bot,
                            maxCount = maxCount.coerceAtLeast(1),
                            notes = notesOf(type, bot),
                        ),
                )
        }
    }

    private fun toggle(pickedChatId: Long) {
        sheetRequest.update { request ->
            val sheet = request?.sheet?.takeUnless { it.isSharing } ?: return@update request
            val selected =
                when {
                    pickedChatId in sheet.selected -> sheet.selected - pickedChatId
                    sheet.maxCount == 1 -> setOf(pickedChatId)
                    sheet.selected.size < sheet.maxCount -> sheet.selected + pickedChatId
                    else -> sheet.selected
                }
            request.copy(sheet = sheet.copy(selected = selected.toImmutableSet(), error = null))
        }
    }

    private fun share() {
        val request = sheetRequest.value?.takeIf { it.sheet.selected.isNotEmpty() && !it.sheet.isSharing }
        val target = asking
        val buttonId = request?.buttonId
        if (request == null || target == null || buttonId == null) return
        val sheet = request.sheet
        sheetRequest.value = request.copy(sheet = sheet.copy(isSharing = true, error = null))
        whilePending(target.position) {
            val messageId = target.message.id
            val result =
                if (request.kind == ShareKind.Users) {
                    // A private chat's id is its user's (TDLib), so the picked chats name the users.
                    repository.shareUsers(chatId, messageId, buttonId, sheet.selected.toImmutableList())
                } else {
                    repository.shareChat(chatId, messageId, buttonId, sheet.selected.first())
                }
            when (result) {
                is Outcome.Success -> {
                    closeSheet()
                }

                is Outcome.Failure -> {
                    sheetRequest.update { it?.copy(sheet = it.sheet.copy(isSharing = false, error = result.error)) }
                }
            }
        }
    }

    private fun closeSheet() {
        sheetRequest.value = null
        if (openDialog.value == null) asking = null
    }

    private fun targetsOf(kind: ShareKind): ChatTargets = if (kind == ShareKind.Users) userTargets else chatTargets

    private fun targetsOf(request: SheetRequest?): ChatTargets? = request?.kind?.let(::targetsOf)

    /** Runs [request] while the button at [position] shows its progress, once at a time. */
    private fun whilePending(
        position: BotButtonPosition,
        request: suspend () -> Unit,
    ) {
        pendingButtons.update { it.adding(position) }
        scope.launch { request() }.invokeOnCompletion { pendingButtons.update { it.removing(position) } }
    }

    /** The keyboard's message, in a group, where a button's message replies to it; null in a private chat. */
    private fun groupReplyTo(message: Message): Long? = message.id.takeUnless { isPrivateChat }

    private suspend fun userOf(userId: Long): User? = (users.getUser(userId) as? Outcome.Success)?.value

    /** "@bot", or the bot's name without a username. */
    private suspend fun botName(message: Message): UiText {
        val bot = (message.sender as? MessageSender.User)?.userId?.let { userOf(it) } ?: return UiText.Plain("")
        return bot.usernames.firstOrNull()?.let { UiText.Plain("@$it") } ?: bot.fullName()
    }

    /** Message [id] as [history] has it now, else as TDLib gave it once; null while it is not known or gone. */
    private fun messageOf(
        id: Long,
        history: Flow<ChatHistory>,
    ): Flow<Message?> =
        flow {
            val fetched = (messages.message(chatId, id) as? Outcome.Success)?.value
            emitAll(history.map { it.messages.findById(id) ?: fetched })
        }

    /** The button the press, question or picker is about: its [message], [position] and [button]. */
    private class Asking(
        val message: Message,
        val position: BotButtonPosition,
        val button: KeyboardButton,
    )

    /** The open picker: what it shares, the button's [type] (its restrictions), its bot and what it shows. */
    private data class SheetRequest(
        val kind: ShareKind,
        val type: KeyboardButtonType,
        val botUserId: Long?,
        val sheet: KeyboardShareSheet,
    ) {
        val buttonId: Int?
            get() =
                when (type) {
                    is KeyboardButtonType.RequestUsers -> type.id
                    is KeyboardButtonType.RequestChat -> type.id
                    else -> null
                }

        fun allows(candidate: TargetCandidate): Boolean =
            when (type) {
                is KeyboardButtonType.RequestUsers -> type.allows(candidate, botUserId)
                is KeyboardButtonType.RequestChat -> type.allows(candidate)
                else -> false
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The last message whose request for a reply was started, for process death. */
        const val KEY_FORCE_REPLIED = "chat.botForceReplied"
    }
}

/** The button of this keyboard at [position]; null where there is none (any more). */
internal fun ReplyMarkup.ShowKeyboard.buttonAt(position: BotButtonPosition): KeyboardButton? =
    rows.getOrNull(position.row)?.getOrNull(position.column)

/** This message's keyboard as the composer shows it, [pending] its buttons waiting; null for another markup. */
internal fun Message.replyKeyboard(
    pending: PersistentSet<BotButtonPosition> = persistentSetOf(),
): ReplyKeyboardUiState? {
    val markup = replyMarkup as? ReplyMarkup.ShowKeyboard ?: return null
    val sendsText = mutableSetOf<BotButtonPosition>()
    val rows =
        markup.rows.mapIndexed { row, buttons ->
            buttons
                .mapIndexed { column, button ->
                    if (button.type == KeyboardButtonType.Text) sendsText += BotButtonPosition(row, column)
                    BotKeyboardButton(
                        text = button.text,
                        tone = button.style.tone(),
                        isEnabled = button.type != KeyboardButtonType.Unsupported,
                    )
                }.toImmutableList()
        }
    return ReplyKeyboardUiState(
        buttons = BotKeyboard(id, rows.toImmutableList(), pending),
        resize = markup.resize,
        oneTime = markup.oneTime,
        persistent = markup.persistent,
        sendsText = sendsText.toImmutableSet(),
    )
}

/** The field's hint this message asks for: its keyboard's, or its reply request's while the composer replies to it. */
private fun Message.placeholder(mode: ComposerMode): String =
    when (val markup = replyMarkup) {
        is ReplyMarkup.ShowKeyboard -> markup.placeholder
        is ReplyMarkup.ForceReply -> markup.placeholder.takeIf { mode.replyToMessageId == id }.orEmpty()
        else -> ""
    }

/** Whether a chat button picks a channel or a group. */
private fun chatKind(type: KeyboardButtonType.RequestChat): ShareKind =
    if (type.isChannel) ShareKind.Channel else ShareKind.Group

/** The notes of a picker for [type]: what [botName] gets, and what Telegram checks or does when it is shared. */
private fun notesOf(
    type: KeyboardButtonType,
    botName: UiText,
) = buildList {
    // A name inside a sentence keeps its own direction (docs/DESIGN_SYSTEM.md §9).
    val bot = botName.bidiIsolated()
    when (type) {
        is KeyboardButtonType.RequestUsers -> {
            add(UiText.resource(R.string.bot_share_users_note, bot))
        }

        is KeyboardButtonType.RequestChat -> {
            add(UiText.resource(R.string.bot_share_chat_note, bot))
            if (type.userRights != null) add(UiText.resource(R.string.bot_share_rights_note))
            when {
                type.botRights != null -> add(UiText.resource(R.string.bot_share_bot_admin_note, bot))
                type.botIsMember -> add(UiText.resource(R.string.bot_share_bot_member_note, bot))
            }
        }

        else -> {}
    }
}.toImmutableList()

/**
 * Whether [candidate] is a user this button lets the user share: a private chat with someone other than the user and
 * the bot ([botUserId]), not a deleted account, a bot or not and with Telegram Premium or not as the button says.
 */
private fun KeyboardButtonType.RequestUsers.allows(
    candidate: TargetCandidate,
    botUserId: Long?,
): Boolean {
    val user = candidate.user ?: return false
    return candidate.chat.type is ChatType.Private &&
        !candidate.isSavedMessages &&
        !user.isDeleted &&
        user.id != botUserId &&
        (isBot == null || (user.type == UserType.Bot) == isBot) &&
        (isPremium == null || user.isPremium == isPremium)
}

/**
 * Whether [candidate] is a chat this button lets the user share, as far as the device knows: a channel, or a group
 * (a basic group or a supergroup), the user is in; a forum or not and with a username or not as the button says; one
 * the user created where it asks for that; one where they are an administrator where it asks for their rights, or for
 * the bot's (which they then grant). Their exact rights, and the bot's, Telegram checks when it is shared.
 */
private fun KeyboardButtonType.RequestChat.allows(candidate: TargetCandidate): Boolean {
    val status = statusIn(candidate) ?: return false
    val supergroup = candidate.supergroup
    return status.isMember &&
        (isForum == null || (supergroup?.isForum == true) == isForum) &&
        (hasUsername == null || (supergroup?.usernames?.isNotEmpty() == true) == hasUsername) &&
        allowsRole(status)
}

/** The user's membership in [candidate] where it is a chat of the kind asked for (a channel, or a live group). */
private fun KeyboardButtonType.RequestChat.statusIn(candidate: TargetCandidate): ChatMemberStatus? =
    when (val type = candidate.chat.type) {
        is ChatType.Supergroup -> candidate.supergroup?.takeIf { type.isChannel == isChannel }?.status
        is ChatType.BasicGroup -> candidate.basicGroup?.takeIf { !isChannel && it.isActive }?.status
        else -> null
    }

/** Whether a user with [status] may share the chat: its creator where asked, an administrator where rights are. */
private fun KeyboardButtonType.RequestChat.allowsRole(status: ChatMemberStatus): Boolean {
    val isAdmin = status is ChatMemberStatus.Creator || status is ChatMemberStatus.Administrator
    return (!isCreated || status is ChatMemberStatus.Creator) && ((userRights == null && botRights == null) || isAdmin)
}

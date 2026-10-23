package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackData
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.model.SwitchInlineTarget
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.isTelegramLink
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEffect
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.UnsupportedButton
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatTargets
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The buttons bots attach under the messages of chat [chatId] (td_api.tl `replyMarkupInlineKeyboard`), for
 * `ChatViewModel`: what a tap does, the progress of the buttons that wait for Telegram ([pending]), the alert or
 * question that follows ([dialog], kept out of `ChatUiState` like the forward sheet), and the chat picker of a
 * switch-inline button ([chatPicker]). Every request is one tap of the user's (Telegram's API terms). A tap looks up
 * the button in the message as it is now ([messageOf]), so a bot's edit (`updateMessageEdited`) counts at once.
 *
 * - **Callback**: the button shows its progress while the bot is asked ([BotButtonRepository.answer]); its answer is a
 *   snackbar ([BotButtonEffect.Answer]), or an alert ([BotButtonDialog.Alert]) where the bot asks for one, and its
 *   link opens. A bot that does not answer just ends the progress. A second tap meanwhile does nothing; the requests
 *   end with the view model's [scope], when the screen is left.
 * - **Callback with password**: first the 2-step verification password ([BotButtonDialog.Password], typed into
 *   [password], never kept in saved state and cleared when done), sent with the button's data; a wrong one is said in
 *   the question.
 * - **Link**: a Telegram link, or a link the button shows as its text, opens at once ([ChatEffect.OpenUrl]); any other
 *   only once the user confirmed it ([BotButtonDialog.OpenLink]), as its text may hide where it goes.
 * - **Login link**: asks Telegram what it needs ([BotButtonRepository.loginUrlInfo]) with the button's progress: a
 *   link opens as a link does; a login asks the user ([BotButtonDialog.Login]), who may open the link without logging
 *   in, or log in, letting the bot send them messages if they agree.
 * - **Switch inline**: "@bot query" (the bot that sent the message, or the one it came through) into this chat's
 *   composer, or into the composer of a chat the user picks among those the bot allows (through [ShareInbox], opening
 *   it with [ChatEffect.OpenChat]); nothing is sent.
 * - **Copy text** copies it ([ChatEffect.CopyText]); **user** opens their profile ([ChatEffect.OpenProfile]).
 * - **Mini Apps, games and payments** say that Pingogram does not offer them yet ([BotButtonEffect.Unsupported]);
 *   disabled buttons do nothing.
 *
 * Runs in [scope], the view model's, on the main thread; the chat picker is mapped on [dispatcher].
 */
@Suppress("LongParameterList", "TooManyFunctions") // What the buttons reach, and one function per kind of button.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatBotButtons(
    private val chatId: Long,
    private val repository: BotButtonRepository,
    private val users: UserRepository,
    chats: ChatRepository,
    search: SearchRepository,
    groups: GroupRepository,
    private val composer: ChatComposer,
    private val shareInbox: ShareInbox,
    private val messageOf: (messageId: Long) -> Message?,
    private val canWrite: () -> Boolean,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val pendingButtons =
        MutableStateFlow<PersistentMap<Long, PersistentSet<BotButtonPosition>>>(persistentMapOf())

    /** The buttons whose request is on its way, by message id. */
    val pending: StateFlow<PersistentMap<Long, PersistentSet<BotButtonPosition>>> = pendingButtons.asStateFlow()

    private val openDialog = MutableStateFlow<BotButtonDialog?>(null)

    /** The alert or question over the conversation; null while there is none. */
    val dialog: StateFlow<BotButtonDialog?> = openDialog.asStateFlow()

    /** The password the password question asks for, for its field; only on the main thread. */
    val password = TextFieldState()

    /** What the open question acts on: the button and its message. */
    private var asking: Asking? = null

    /** The open question's request (checking a password, making a login link), which dismissing it cancels. */
    private var answering: Job? = null

    private val chatTargets = ChatTargets(chats, search, users, groups, scope)
    private val pickerRequest = MutableStateFlow<PickerRequest?>(null)

    /** The chat picker of a switch-inline button; null while it is closed. */
    val chatPicker: StateFlow<SwitchInlineSheet?> =
        pickerRequest
            .flatMapLatest { request ->
                if (request == null) {
                    flowOf(null)
                } else {
                    chatTargets.targets { chat, access, user -> request.allows(chat, access, user) }.map { found ->
                        SwitchInlineSheet(
                            query = request.text,
                            targets = found.targets,
                            isSearching = found.isSearching,
                            isLoading = found.isLoading,
                            searchError = found.error,
                        )
                    }
                }
            }.flowOn(dispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onEvent(event: BotButtonEvent) {
        when (event) {
            is BotButtonEvent.Press -> press(event.messageId, event.position)
            BotButtonEvent.OpenLink -> confirmLink()
            is BotButtonEvent.Login -> login(event.logIn, event.allowWriteAccess)
            BotButtonEvent.SubmitPassword -> submitPassword()
            BotButtonEvent.DismissDialog -> dismissDialog()
            is BotButtonEvent.SearchChats -> chatTargets.setQuery(event.query)
            BotButtonEvent.LoadMoreChats -> chatTargets.loadMore()
            is BotButtonEvent.PickChat -> pickChat(event.chatId)
            BotButtonEvent.DismissChats -> pickerRequest.value = null
        }
    }

    private fun press(
        messageId: Long,
        position: BotButtonPosition,
    ) {
        val message = messageOf(messageId)
        val button = (message?.replyMarkup as? ReplyMarkup.InlineKeyboard)?.buttonAt(position)
        if (message == null || button == null) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        if (position !in pendingButtons.value[messageId].orEmpty()) act(ButtonTarget(message, position), button)
    }

    /** What tapping [button] of [target] does; the buttons that only show something, through [effectOf]. */
    private fun act(
        target: ButtonTarget,
        button: InlineKeyboardButton,
    ) {
        when (val type = button.type) {
            is InlineKeyboardButtonType.Url -> openLink(type.url, shownText = button.text)
            is InlineKeyboardButtonType.LoginUrl -> loginUrl(target, type)
            is InlineKeyboardButtonType.Callback -> callback(target, type.data)
            is InlineKeyboardButtonType.CallbackWithPassword -> askPassword(target, type.data)
            is InlineKeyboardButtonType.SwitchInline -> switchInline(target.message, type)
            else -> effectOf(type)?.let(onEffect)
        }
    }

    /** Opens [url] at once where it cannot mislead (a Telegram link, or the text it is shown as); else asks first. */
    private fun openLink(
        url: String,
        shownText: String?,
    ) {
        when {
            url.isBlank() -> onEffect(ChatEffect.ActionUnavailable)
            isTelegramLink(url) || shownText?.trim() == url.trim() -> onEffect(ChatEffect.OpenUrl(url))
            else -> openDialog.value = BotButtonDialog.OpenLink(url)
        }
    }

    private fun confirmLink() {
        val dialog = openDialog.value as? BotButtonDialog.OpenLink ?: return
        openDialog.value = null
        onEffect(ChatEffect.OpenUrl(dialog.url))
    }

    private fun callback(
        target: ButtonTarget,
        data: CallbackData,
    ) {
        whilePending(target) {
            when (val answer = repository.answer(chatId, target.message.id, CallbackPayload.Data(data))) {
                is Outcome.Success -> show(answer.value)
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(answer.error))
            }
        }
    }

    /** The bot's answer: its text as an alert or a snackbar, and its link opened; nothing for none. */
    private fun show(answer: CallbackAnswer) {
        val answered = answer as? CallbackAnswer.Answered ?: return
        when {
            answered.text.isBlank() -> Unit
            answered.showAlert -> openDialog.value = BotButtonDialog.Alert(answered.text)
            else -> onEffect(BotButtonEffect.Answer(answered.text))
        }
        if (answered.url.isNotBlank()) onEffect(ChatEffect.OpenUrl(answered.url))
    }

    private fun askPassword(
        target: ButtonTarget,
        data: CallbackData,
    ) {
        password.clearText()
        asking = Asking.Password(target, data)
        scope.launch {
            openDialog.value = BotButtonDialog.Password(botName(target.message.botUserId))
        }
    }

    private fun submitPassword() {
        val dialog = (openDialog.value as? BotButtonDialog.Password)?.takeUnless { it.isChecking }
        val request = asking as? Asking.Password
        val typed = password.text.toString()
        if (dialog == null || request == null || typed.isEmpty()) return
        openDialog.value = dialog.copy(isChecking = true, error = null)
        answering =
            scope.launch {
                val payload = CallbackPayload.DataWithPassword(typed, request.data)
                when (val answer = repository.answer(chatId, request.target.message.id, payload)) {
                    is Outcome.Success -> {
                        closeDialog()
                        show(answer.value)
                    }

                    is Outcome.Failure -> {
                        openDialog.update {
                            (it as? BotButtonDialog.Password)?.copy(
                                isChecking = false,
                                error = answer.error,
                            )
                        }
                    }
                }
            }
    }

    private fun loginUrl(
        target: ButtonTarget,
        type: InlineKeyboardButtonType.LoginUrl,
    ) {
        whilePending(target) {
            when (val info = repository.loginUrlInfo(chatId, target.message.id, type.id)) {
                is Outcome.Success -> {
                    when (val value = info.value) {
                        is LoginUrlInfo.Open -> {
                            if (value.skipConfirmation && value.url.isNotBlank()) {
                                onEffect(ChatEffect.OpenUrl(value.url))
                            } else {
                                openLink(value.url, shownText = null)
                            }
                        }

                        is LoginUrlInfo.RequestConfirmation -> {
                            asking = Asking.Login(target, type.id, value.url)
                            openDialog.value =
                                BotButtonDialog.Login(
                                    domain = value.domain,
                                    botName = botName(value.botUserId),
                                    myName = myName(),
                                    requestWriteAccess = value.requestWriteAccess,
                                )
                        }
                    }
                }

                is Outcome.Failure -> {
                    onEffect(ChatEffect.ShowError(info.error))
                }
            }
        }
    }

    /** Opens the login's link: as it is unless [logIn], else the link Telegram makes that logs the user in. */
    private fun login(
        logIn: Boolean,
        allowWriteAccess: Boolean,
    ) {
        val dialog = openDialog.value as? BotButtonDialog.Login
        val request = asking as? Asking.Login
        if (dialog == null || request == null || dialog.isLoggingIn) return
        if (logIn) openLoggedIn(dialog, request, allowWriteAccess) else openWithoutLogin(request)
    }

    private fun openWithoutLogin(request: Asking.Login) {
        closeDialog()
        if (request.url.isNotBlank()) onEffect(ChatEffect.OpenUrl(request.url))
    }

    private fun openLoggedIn(
        dialog: BotButtonDialog.Login,
        request: Asking.Login,
        allowWriteAccess: Boolean,
    ) {
        openDialog.value = dialog.copy(isLoggingIn = true)
        answering =
            scope.launch {
                val messageId = request.target.message.id
                val write = allowWriteAccess && dialog.requestWriteAccess
                when (val url = repository.loginUrl(chatId, messageId, request.buttonId, write)) {
                    is Outcome.Success -> {
                        closeDialog()
                        onEffect(ChatEffect.OpenUrl(url.value))
                    }

                    is Outcome.Failure -> {
                        openDialog.update { (it as? BotButtonDialog.Login)?.copy(isLoggingIn = false) }
                        onEffect(ChatEffect.ShowError(url.error))
                    }
                }
            }
    }

    private fun dismissDialog() {
        answering?.cancel()
        closeDialog()
    }

    private fun closeDialog() {
        openDialog.value = null
        asking = null
        password.clearText()
    }

    /** "@bot query" into this chat's composer, or the picker of the chats the bot allows. */
    private fun switchInline(
        message: Message,
        type: InlineKeyboardButtonType.SwitchInline,
    ) {
        scope.launch {
            val username = message.botUserId?.let { bot -> userOf(bot)?.usernames?.firstOrNull() }
            if (username == null) {
                onEffect(ChatEffect.ActionUnavailable)
                return@launch
            }
            val text = "@$username ${type.query}"
            when (val target = type.target) {
                SwitchInlineTarget.CurrentChat -> {
                    insertHere(text)
                }

                is SwitchInlineTarget.ChosenChat -> {
                    chatTargets.setQuery("")
                    pickerRequest.value = PickerRequest(text, target)
                }

                SwitchInlineTarget.Link -> {
                    onEffect(BotButtonEffect.Unsupported(UnsupportedButton.InlineLink))
                }
            }
        }
    }

    private fun pickChat(pickedChatId: Long) {
        val request = pickerRequest.value ?: return
        pickerRequest.value = null
        if (pickedChatId == chatId) {
            insertHere(request.text)
        } else {
            shareInbox.deliver(pickedChatId, SharedContent(text = FormattedText(request.text, persistentListOf())))
            onEffect(ChatEffect.OpenChat(pickedChatId))
        }
    }

    /** [text] in place of what the composer holds, its cursor at the end, the composer focused; nothing is sent. */
    private fun insertHere(text: String) {
        if (!canWrite()) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        composer.cancelEdit()
        composer.text.setContent(FormattedText(text, persistentListOf()))
        onEffect(ChatEffect.FocusComposer)
    }

    /** Runs [request] while the button of [target] shows its progress, once at a time. */
    private fun whilePending(
        target: ButtonTarget,
        request: suspend () -> Unit,
    ) {
        val messageId = target.message.id
        pendingButtons.update { it.putting(messageId, it[messageId].orEmpty().adding(target.position)) }
        scope
            .launch { request() }
            .invokeOnCompletion {
                pendingButtons.update { pending ->
                    val left = pending[messageId].orEmpty().removing(target.position)
                    if (left.isEmpty()) pending.removing(messageId) else pending.putting(messageId, left)
                }
            }
    }

    private suspend fun userOf(userId: Long): User? = (users.getUser(userId) as? Outcome.Success)?.value

    /** "@bot", or the bot's name without a username. */
    private suspend fun botName(botUserId: Long?): UiText {
        val bot = botUserId?.let { userOf(it) } ?: return UiText.Plain("")
        return bot.usernames.firstOrNull()?.let { UiText.Plain("@$it") } ?: bot.fullName()
    }

    private suspend fun myName(): UiText =
        users.myUserId.value
            ?.let { userOf(it) }
            ?.fullName() ?: UiText.Plain("")

    /** A button and the message it is under. */
    private class ButtonTarget(
        val message: Message,
        val position: BotButtonPosition,
    )

    /** What the open question acts on. */
    private sealed interface Asking {
        class Password(
            val target: ButtonTarget,
            val data: CallbackData,
        ) : Asking

        class Login(
            val target: ButtonTarget,
            val buttonId: Long,
            val url: String,
        ) : Asking
    }

    /** The open chat picker: [text] for the composer of the chat picked among those [target] allows. */
    private class PickerRequest(
        val text: String,
        val target: SwitchInlineTarget.ChosenChat,
    ) {
        /** Chats where the user can write, of the kinds the bot allows; never a secret chat. */
        fun allows(
            chat: Chat,
            access: WriteAccess,
            user: User?,
        ): Boolean =
            access == WriteAccess.Allowed &&
                when (val type = chat.type) {
                    is ChatType.Private -> if (user?.type == UserType.Bot) target.bots else target.users
                    is ChatType.BasicGroup -> target.groups
                    is ChatType.Supergroup -> if (type.isChannel) target.channels else target.groups
                    is ChatType.Secret -> false
                }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The snackbar or screen of a button that only shows something; null for a disabled one. */
private fun effectOf(type: InlineKeyboardButtonType): ChatEffect? =
    when (type) {
        is InlineKeyboardButtonType.CopyText -> ChatEffect.CopyText(type.text)
        is InlineKeyboardButtonType.User -> ChatEffect.OpenProfile(type.userId)
        is InlineKeyboardButtonType.WebApp -> BotButtonEffect.Unsupported(UnsupportedButton.MiniApp)
        InlineKeyboardButtonType.CallbackGame -> BotButtonEffect.Unsupported(UnsupportedButton.Game)
        InlineKeyboardButtonType.Buy -> BotButtonEffect.Unsupported(UnsupportedButton.Payment)
        else -> null
    }

/** The bot whose inline query a switch-inline button starts: the one the message came through, else its sender. */
private val Message.botUserId: Long?
    get() = viaBotUserId.takeIf { it != 0L } ?: (sender as? MessageSender.User)?.userId

/** These positions, none for null. */
private fun PersistentSet<BotButtonPosition>?.orEmpty(): PersistentSet<BotButtonPosition> = this ?: persistentSetOf()

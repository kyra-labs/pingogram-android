package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.BotCommand
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chat.ui.BotEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The bots of chat [chatId] for `ChatViewModel` (`REQUIREMENTS.md` FR-C5): their commands, a bot's menu button, its
 * description in the empty chat, and starting it.
 *
 * - **Bots** come from [BotInfoRepository.chatBots], following every change of the full info while the screen collects;
 *   a failure counts as no bots (logged without details), so the composer stays as in any chat.
 * - **Button** ([state]): where the bots offer commands or a bot has a menu button, the composer's pill starts with the
 *   bot button ([ComposerBotButton]): a bot's menu button, which opens a Mini App, takes the place of its commands'
 *   menu, as td_api.tl `botInfo.menu_button` says; without one it is "/", the commands. Not while editing.
 * - **Sheet** ([sheet], kept out of `ChatUiState`): the commands with their bots, following changes while it is open.
 * - **Suggestions** ([suggestions], kept out of `ChatUiState` as only the composer shows them): while the field holds a
 *   command being typed ([commandQuery]), and does not edit, the commands it starts.
 * - **Sending** ([BotEvent.SendCommand]): the command goes out as a text message through [messages] (the topic's, in a
 *   topic), replying where the composer replies ([ChatComposer.takeReply], given back if it is refused); a command
 *   being typed in the field goes with it. Telegram finds the command in the text itself. **Inserting**
 *   ([BotEvent.InsertCommand]) puts it into the field with a space, for its arguments, and focuses the field.
 * - **Start** ([BotEvent.Start]): a private chat with a bot whose history is empty (no last message), or that a start
 *   link opened ([BotStartInbox], its parameter kept in [savedState] across process death), shows "Start" in the
 *   composer's place ([withBots]); a tap sends `sendBotStartMessage`, once at a time, with the parameter. Never by
 *   itself.
 * - **Mini Apps** are in the backlog: the menu button says so ([ChatEffect.MiniAppsUnsupported]), never a WebView.
 *
 * Runs in [scope], the view model's, on the main thread, as the text field.
 */
@Suppress("LongParameterList") // The chat, where its bots, messages and composer come from, and what it tells.
internal class ChatBotCommands(
    private val chatId: Long,
    private val repository: BotInfoRepository,
    users: UserRepository,
    chats: ChatRepository,
    private val messages: MessageRepository,
    private val composer: ChatComposer,
    private val inbox: BotStartInbox,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
) {
    private val chatBots: SharedFlow<ChatBots> =
        repository
            .chatBots(chatId)
            .map { found ->
                when (found) {
                    is Outcome.Success -> {
                        found.value
                    }

                    is Outcome.Failure -> {
                        log.warn("Bots not loaded: ${found.error::class.simpleName}")
                        ChatBots.None
                    }
                }
            }.distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The commands with their bots' names, photos and usernames. */
    private val commands: Flow<ImmutableList<BotCommandItem>> =
        combine(chatBots, users.users) { bots, known ->
            bots.commands.map { it.toItem(inGroup = bots.bot == null, known) }.toImmutableList()
        }.distinctUntilChanged()

    private val startParameter = savedState.getStateFlow<String?>(KEY_START_PARAMETER, null)
    private val starting = MutableStateFlow(false)
    private val sheetOpen = MutableStateFlow(false)

    /**
     * What the composer and the empty chat show of the bots: none at first, so the conversation never waits for a bot's
     * full info, which TDLib may ask the server for the first time.
     */
    val state: Flow<ChatBotState> =
        combine(
            chatBots.onStart { emit(ChatBots.None) },
            chats.chat(chatId).map { it != null && it.lastMessage == null }.distinctUntilChanged(),
            startParameter,
            starting,
        ) { bots, hasNoMessages, parameter, isStarting ->
            val bot = bots.bot
            val menu = bot?.menuButton
            ChatBotState(
                button =
                    when {
                        menu != null -> ComposerBotButton.MiniApp(menu.text)
                        bots.commands.isNotEmpty() -> ComposerBotButton.Commands
                        else -> null
                    },
                intro =
                    bot?.takeIf { it.description.isNotEmpty() || it.photo != null || it.animation != null }?.let {
                        BotIntro(it.description, it.photo, it.animation)
                    },
                isBotChat = bot != null,
                hasNoMessages = hasNoMessages,
                startParameter = parameter,
                isStarting = isStarting,
            )
        }.distinctUntilChanged()

    /** The commands sheet while it is open; null while it is not. */
    val sheet: StateFlow<BotCommandsSheet?> =
        combine(sheetOpen, commands) { open, commands -> if (open) BotCommandsSheet(commands) else null }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The commands suggested for the command being typed; null while none is, or none matches. */
    val suggestions: StateFlow<CommandSuggestions?> =
        combine(
            snapshotFlow { commandQuery(composer.text.state.text) }.distinctUntilChanged(),
            composer.mode,
            commands,
        ) { query, mode, commands ->
            val matching = if (query == null || mode is ComposerMode.Editing) null else commands.startingWith(query)
            matching?.takeIf { it.isNotEmpty() }?.let { CommandSuggestions(query.orEmpty(), it) }
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Takes the start parameter of a link to this bot as it comes, also while the conversation is open. */
    fun start() {
        scope.launch {
            inbox.deliveredTo(chatId).filterNotNull().collect { parameter ->
                if (inbox.take(chatId, parameter)) savedState[KEY_START_PARAMETER] = parameter
            }
        }
    }

    fun onEvent(event: BotEvent) {
        when (event) {
            BotEvent.OpenMenu -> openMenu()
            BotEvent.DismissCommands -> sheetOpen.value = false
            is BotEvent.SendCommand -> send(event.text)
            is BotEvent.InsertCommand -> insert(event.text)
            BotEvent.Start -> startBot()
        }
    }

    private fun openMenu() {
        scope.launch {
            val bots = chatBots.first()
            when {
                bots.bot?.menuButton != null -> onEffect(ChatEffect.MiniAppsUnsupported)
                bots.commands.isNotEmpty() -> sheetOpen.value = true
            }
        }
    }

    private fun send(text: String) {
        sheetOpen.value = false
        // The command being typed is what goes out.
        if (commandQuery(composer.text.state.text) != null) composer.text.setContent(FormattedText.Empty)
        val replyTo = composer.takeReply()
        scope.launch {
            val command = FormattedText(text, persistentListOf())
            messages.sendText(chatId, command, SendOptions(), replyTo).onFailure { error ->
                replyTo?.let(composer::restoreReply)
                onEffect(ChatEffect.ShowError(error))
            }
        }
    }

    private fun insert(text: String) {
        sheetOpen.value = false
        val field = composer.text
        val command = "$text "
        if (field.isBlank || commandQuery(field.state.text) != null) {
            field.setContent(FormattedText(command, persistentListOf()))
        } else {
            field.prepend(command)
        }
        onEffect(ChatEffect.FocusComposer)
    }

    private fun startBot() {
        if (starting.value) return
        starting.value = true
        scope.launch {
            val bot = chatBots.first().bot
            if (bot == null) {
                starting.value = false
                return@launch
            }
            when (val started = repository.sendStartMessage(bot.botUserId, chatId, startParameter.value.orEmpty())) {
                is Outcome.Success -> savedState[KEY_START_PARAMETER] = null
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(started.error))
            }
            starting.value = false
        }
    }

    internal companion object {
        /** The start parameter of a link to this bot, until "Start" sent it. */
        const val KEY_START_PARAMETER = "chat.botStartParameter"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * What the composer and the empty chat show of the chat's bots: the pill's bot [button], the bot's [intro], and
 * whether "Start" shows: a private chat with a bot ([isBotChat]) without messages ([hasNoMessages], from the chat's
 * last message) or with a link's [startParameter]; [isStarting] while Telegram is asked.
 */
internal data class ChatBotState(
    val button: ComposerBotButton? = null,
    val intro: BotIntro? = null,
    val isBotChat: Boolean = false,
    val hasNoMessages: Boolean = false,
    val startParameter: String? = null,
    val isStarting: Boolean = false,
)

/**
 * This state with [bots]: "Start" in the place of a composer the user could write in, where the bot chat has no
 * messages (the history is empty, or loading and the chat has no last message) or a start link opened it; otherwise
 * the composer with the bot button, except while editing; and the bot's intro for the empty chat.
 */
internal fun ChatUiState.withBots(bots: ChatBotState): ChatUiState {
    val available = composer as? ComposerUiState.Available
    val isEmpty = content == ChatContent.Empty || (content == ChatContent.Loading && bots.hasNoMessages)
    val composer =
        when {
            available == null -> composer
            bots.isBotChat && (bots.startParameter != null || isEmpty) -> ComposerUiState.BotStart(bots.isStarting)
            else -> available.copy(bot = bots.button.takeIf { available.bar !is ComposerBar.Edit })
        }
    return copy(composer = composer, botIntro = bots.intro)
}

/**
 * The command as the sheet and the suggestions show it: in a group ([inGroup]) with its bot from [users] and addressed
 * to it ("/start@helper_bot"), so other bots of the group leave it alone; a bot without a username takes "/start".
 */
private fun BotCommand.toItem(
    inGroup: Boolean,
    users: ImmutableMap<Long, User>,
): BotCommandItem {
    if (!inGroup) return BotCommandItem(command, "/$command", description)
    val bot = users[botUserId]
    val username = bot?.usernames?.firstOrNull()
    return BotCommandItem(
        command = command,
        text = if (username != null) "/$command@$username" else "/$command",
        description = description,
        bot = CommandBot(botUserId, bot?.fullName() ?: UiText.Plain(""), bot?.profilePhoto),
    )
}

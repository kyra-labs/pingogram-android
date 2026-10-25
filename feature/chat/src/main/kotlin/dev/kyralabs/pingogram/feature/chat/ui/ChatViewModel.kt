package dev.kyralabs.pingogram.feature.chat.ui

import android.os.SystemClock
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreview
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChatAttachments
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.attach.PreviewDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.attachOptions
import dev.kyralabs.pingogram.feature.chat.ui.attach.isChannel
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsSheet
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotStartInbox
import dev.kyralabs.pingogram.feature.chat.ui.bot.ChatBotCommands
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandSuggestions
import dev.kyralabs.pingogram.feature.chat.ui.bot.withBots
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotReplyUiState
import dev.kyralabs.pingogram.feature.chat.ui.bots.ChatBotButtons
import dev.kyralabs.pingogram.feature.chat.ui.bots.ChatBotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.KeyboardShareSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.SwitchInlineSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.withPendingButtons
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerTargets
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.composerState
import dev.kyralabs.pingogram.feature.chat.ui.content.opensInViewer
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatForwarding
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheet
import dev.kyralabs.pingogram.feature.chat.ui.inline.ChatInlineBots
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotRepositories
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationSheet
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedSheet
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioPlayer
import dev.kyralabs.pingogram.feature.chat.ui.poll.ChatPolls
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVotersSheet
import dev.kyralabs.pingogram.feature.chat.ui.poll.StopPollDialog
import dev.kyralabs.pingogram.feature.chat.ui.poll.withPollChoices
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.ChatScheduledMessages
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.nextText
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearch
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchRepositories
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import dev.kyralabs.pingogram.feature.chat.ui.search.SenderSheet
import dev.kyralabs.pingogram.feature.chat.ui.search.withSearchMatch
import dev.kyralabs.pingogram.feature.chat.ui.secret.ChatSecretChat
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.ChatSendAs
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsChoice
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsSheet
import dev.kyralabs.pingogram.feature.chat.ui.share.ChatShares
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.chat.ui.thread.ChatThread
import dev.kyralabs.pingogram.feature.chat.ui.thread.ThreadState
import dev.kyralabs.pingogram.feature.chat.ui.thread.composer
import dev.kyralabs.pingogram.feature.chat.ui.thread.header
import dev.kyralabs.pingogram.feature.chat.ui.thread.inThread
import dev.kyralabs.pingogram.feature.chat.ui.translation.ChatTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslationRepositories
import dev.kyralabs.pingogram.feature.chat.ui.translation.withTranslations
import dev.kyralabs.pingogram.feature.chat.ui.voice.ChatRecorder
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecorder
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The conversation of chat [chatId] (`REQUIREMENTS.md` FR-C1–C3, FR-C5, FR-C8) as [uiState], and the text of its
 * composer as [composerText]. Events come in through [onEvent].
 *
 * - **State**: one `combine` of the chat, its history, the users and the chats the messages name, the replied messages
 *   fetched for it, the basic group or supergroup of a group or channel, what people are doing in it, the minute
 *   ([WallClock.minutes], for "last seen" and "Today"), and the paging state. Items are mapped on [dispatcher] by a
 *   [ChatItemMapper], which reuses items whose message did not change. It runs while the screen collects, and 5 s
 *   after, so a rotation does not restart it.
 * - **The chat** comes from [ChatRepository.chat], or from [ChatRepository.getChat] while TDLib has not announced it:
 *   [ChatContent.NotFound] for a chat TDLib does not know, [ChatContent.Error] with a retry for other failures.
 * - **Opening, paging and jumps** ([ChatWindow]): the conversation opens around the message search found
 *   ([initialMessageId]), else around the first unread message, else at the newest page, and shows nothing until those
 *   are loaded, so it never shows the newest messages first and then jumps; the list goes where the content's
 *   [ListAnchor] says. Older and newer pages load near the ends ([ChatEvent.NearOldestChanged],
 *   [ChatEvent.NearNewestChanged]); a tapped quote ([ChatEvent.OpenReply]) and the jump to the newest message
 *   ([ChatEvent.ShowNewest]) load the messages around their message when it is not loaded. The newest message the list
 *   shows is kept in the [SavedStateHandle], which the conversation opens around after process death.
 * - **Replies** whose replied message is not loaded fetch it once ([MessageRepository.repliedMessage]); the quote
 *   shows that it loads, or that the message was deleted.
 * - **Unread divider**: before the first incoming message after the last one read when the chat was first shown
 *   (`lastReadInboxMessageId`, if anything was unread), kept in the [SavedStateHandle], so it stays where it was
 *   while the screen lives, also across process death, whatever arrives or is read meanwhile.
 * - **Open and close**: [ChatEvent.Started] opens the chat in TDLib and [ChatEvent.Stopped] closes it, from the
 *   screen's lifecycle (started and stopped, or left), each once per change. They are sent before [onEvent] returns,
 *   so a close sent as the screen leaves is not lost with the view model's scope.
 * - **Read** ([MessageViews]): the messages the screen shows ([ChatEvent.MessagesVisible]) go to TDLib as viewed while
 *   the chat is open, which reads them; [Chat.unreadCount], the jump button's badge, follows.
 * - **Times** follow the zone, the language and the 12- or 24-hour setting ([TimePatterns]), checked at every emission.
 *   A group of messages shows its time on its last bubble; a bubble tapped shows its own ([ChatEvent.ToggleTime]).
 * - **Files** ([ChatFiles]): the files of the loaded voice messages, songs and documents are followed through
 *   [FileRepository.observe] into [ChatUiState.fileStates], on [dispatcher], for as long as they are loaded. Documents
 *   download and cancel on request. Failures go out as [ChatEffect.ShowError] snackbars.
 * - **Automatic download** ([ChatAutoDownloads], `docs/PERFORMANCE.md` §7): under the rules of the current network
 *   ([AutoDownloadPolicy]), the files of the messages on screen download by themselves, and photos download in their
 *   bubbles or wait for a tap on their download button ([ChatUiState.mediaDownloads]).
 * - **Custom emoji** ([ChatCustomEmoji]): those of the messages on screen and of the composer's text are loaded, for
 *   the bubbles, reactions and composer to draw ([customEmoji], kept out of [uiState] like the forward sheet).
 * - **Playback** ([ChatPlayback]): voice messages and songs play one at a time on the [AudioPlayer], downloading their
 *   file first if needed, into [ChatUiState.playback]; the position moves in snapshot state, so it re-emits nothing.
 *   Stopping the screen pauses, and the player is released with the view model.
 * - **Composer** ([ChatComposer]): shown where the user can write ([composerState], from the chat's permissions and the
 *   user's membership), it sends the text ([ChatEvent.Send]) and keeps the chat's draft in step with it: filled from
 *   the draft, saved a second after typing stops and when the screen stops, kept across process death. It replies and
 *   edits too, with a bar for the message ([composerBar]), fetched by [ComposerTargets] when it is not loaded.
 * - **Message actions** ([ChatMessageActions]): the actions sheet of a long-pressed message, with what TDLib allows for
 *   it ([MessageActionRepository.properties]) and its quick reactions; replying, copying, editing, pinning, deleting
 *   (after a confirmation), sending again and toggling reactions; and the [ChatUiState.shortcuts] screen readers get.
 * - **Forwarding** ([ChatForwarding]): the forward sheet ([forwardSheet], kept out of [uiState] so the chats it lists
 *   do not recompose the conversation), with its comment ([forwardComment]), and forwarding to the picked chats.
 * - **Selection mode** ([ChatSelection], [ChatUiState.selection]): the selected messages, kept across process death,
 *   and the selection toolbar's actions (copying them with their senders and times, forwarding, reporting and deleting
 *   them all); it ends once they are copied, forwarded, reported or deleted.
 * - **Reporting** ([ChatReporting], [ChatUiState.report]) a message or the selected ones, with the reasons Telegram
 *   asks for and a comment ([reportComment]); **saving** a message's files to Downloads ([ChatSaving]).
 * - **Attach menu** ([ChatAttachments]): what the composer's attach button offers in this chat
 *   ([ComposerUiState.Available.attachOptions]); picked photos, videos and files in a preview
 *   ([ChatUiState.attachPreview]) with its caption ([attachCaption]), a photo from the camera, a contact, the device's
 *   location (found by the sheet's own `ShareLocationViewModel`) and a poll ([ChatUiState.poll], [pollEditor]), each
 *   taking the composer's reply along. The uploads of the messages being sent
 *   ([ChatUiState.uploads]) come from [FileRepository.observeUpload], and can be cancelled.
 * - **Shared into the chat** ([ChatShares], `REQUIREMENTS.md` FR-N2, direct share): what another app shared to this
 *   chat, through a Direct Share target or the share sheet, goes into the composer once the conversation is on screen,
 *   a text into the field, files into the attach preview; the user sends it.
 * - **Voice messages** ([ChatRecorder]): held, locked, paused, sent or cancelled from the composer's microphone on the
 *   [VoiceRecorder], as [voiceRecording] (kept out of [uiState], like the forward sheet, as only the composer shows
 *   it); recording stops what plays, the screen stopping locks and pauses it, and clearing the view model throws it
 *   away.
 * - **Secret chats** ([ChatSecretChat], `REQUIREMENTS.md` FR-E1): the secret chat's state in the header and the
 *   composer (none while it waits for the other user or after it ended), and the sheets of its top bar's actions
 *   ([secretChatSheet], kept out of [uiState] like the forward sheet): the self-destruct timer and the encryption key.
 * - **Bots' buttons** ([ChatBotButtons]): what a tap on a button under a bot's message does (callbacks, links,
 *   logins, inline queries, copying, profiles), the progress of the buttons that wait in their bubbles, and the
 *   alerts, questions and chat picker that follow ([botButtonDialog], [switchInlineSheet], kept out of [uiState]).
 * - **Bots' keyboards** ([ChatBotKeyboard]): the keyboard in effect in the chat, for the composer ([botReply]), what
 *   its buttons send or share (the phone number after a question, [botKeyboardDialog]; the location and a poll through
 *   the attach menu's sheets; users or a chat through a picker, [keyboardShareSheet]), and a bot's request for a
 *   reply, all kept out of [uiState].
 * - **Send-as** ([ChatSendAs]): who new messages go out as in a group or channel where the user can choose, as the
 *   composer's avatar ([ComposerUiState.Available.sendAs]), and the picker that changes it ([sendAsSheet], kept out of
 *   [uiState] like the forward sheet).
 * - **Bots** ([ChatBotCommands], `REQUIREMENTS.md` FR-C5): the commands of the chat's bots, in their sheet
 *   ([botCommandsSheet]) and as suggestions while a command is typed ([commandSuggestions]), both kept out of [uiState]
 *   like the forward sheet; a bot's menu button; and in a private chat with a bot its description while the chat is
 *   empty ([ChatUiState.botIntro]) and "Start" ([ComposerUiState.BotStart]), also after a start link.
 * - **Inline bots** ([ChatInlineBots], "@gif cats"): while the field starts with an inline bot's username, its results
 *   above the composer ([inlineResults], kept out of [uiState] like the suggestions), paged, sent with the composer's
 *   reply; the question whether to share the location with a bot that asks for it ([inlineLocationSheet]).
 * - **Forum topics** ([ChatThread], `REQUIREMENTS.md` FR-C9): with a [topic], the conversation is that topic's: its
 *   history, what is sent goes into it, its name in the header, its unread state, and its pins.
 * - **Pinned messages** ([ChatPinnedMessages], `REQUIREMENTS.md` FR-C9): the bar over the messages
 *   ([ChatUiState.pinnedBar]), cycling through them as its taps show each in the list, closing (unpinning or hiding),
 *   and the sheet of them all ([pinnedSheet], kept out of [uiState] like the forward sheet).
 * - **Translation** ([ChatTranslation], `REQUIREMENTS.md` FR-C3): a message's "Translate" and "Show original", the
 *   translations in place of the bubbles' texts (copied only into the bubbles they change), and the translate bar
 *   ([ChatUiState.translateBar]) where Telegram suggests translating the chat, which translates the messages on screen.
 * - **Scheduled messages** ([ChatScheduledMessages], `REQUIREMENTS.md` FR-C5): where the chat holds messages the user
 *   scheduled, how many, for the bar over the messages ([ChatUiState.scheduledBar]), which opens their list.
 * - **Search in the chat** ([ChatSearch], `REQUIREMENTS.md` §5.9): the query, sender and results of the search, for
 *   its bars ([searchState], kept out of [uiState] like the forward sheet), the result shown in the list as a tapped
 *   quote's message, marking the query's words ([ChatItem.Bubble.searchMatch]), the members' sheet
 *   ([searchSenderSheet]) and the jump to a day.
 * - **Polls** ([ChatPolls], `REQUIREMENTS.md` FR-C4): voting (the answers checked before "Vote" copied into their
 *   bubbles only), taking a vote back and stopping a poll from the message actions, the question before a stop
 *   ([stopPollDialog]) and the voters' sheet ([pollVotersSheet]), both kept out of [uiState] like the forward sheet.
 * - **Sponsored messages** ([ChatSponsoredMessages]) of a channel or a chat with a bot (`REQUIREMENTS.md` §2): loaded
 *   once ([SponsoredMessageRepository]), placed among the items where they reach the chat's newest message, viewed once
 *   wholly on screen, opened, and "About this ad" ([ChatUiState.sponsoredSheet]) with reporting.
 * - **Starting**: the view model is created in the frame that first composes the screen; what it starts (loading the
 *   messages it opens at, following the composer's text and drafts, reporting viewed messages) runs once that frame is
 *   done, so the frame does not wait for it.
 * - **Profiles** (`REQUIREMENTS.md` FR-P2): the title of a private chat and a sender's avatar or name open the user's
 *   profile ([ChatEvent.OpenProfile]); a tapped mention opens the profile it names, or for an `@username` the profile
 *   or the group or channel Telegram says has it ([ChatEvent.OpenMention], [ChatMentions]).
 * - **Effects** ([effects]): errors, opening a photo, video or GIF in the media viewer ([ChatEvent.OpenMedia]),
 *   opening a downloaded document in another app ([ChatEvent.OpenFile]), a location in a maps app, a scheduled
 *   message's time, a copied text, the composer's focus when a reply or an edit starts, and a haptic when a message
 *   being sent fails while the screen is shown ([SendFailures]).
 *
 * It takes one repository per kind of data the screen combines or changes, plus the player, clock, dispatcher,
 * language, time patterns and log that tests replace, hence `LongParameterList` is suppressed. Each part of the
 * conversation is a class of its own (the list above); what is left here wires them into one state and one event
 * handler, which grows by a few lines with each part, hence `LargeClass` is suppressed. The conversation destination
 * and the detail pane of the Chats tab create it with their [chatId] (and the destination with its route's
 * [initialMessageId]) through [Factory].
 */
@Suppress(
    "LongParameterList",
    // One part per feature of the conversation, each its own class (ChatSendAs, ChatBotCommands, ChatBotButtons,
    // ChatInlineBots…); what is left here wires them into one state and one event handler.
    "LargeClass",
)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    messages: MessageRepository,
    messageActionRepository: MessageActionRepository,
    sponsoredMessageRepository: SponsoredMessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    chatActionRepository: ChatActionRepository,
    fileRepository: FileRepository,
    private val searchRepository: SearchRepository,
    sharedStorageRepository: SharedStorageRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    audioPlayer: AudioPlayer,
    voiceRecorder: VoiceRecorder,
    voiceNoteRepository: VoiceNoteRepository,
    sendAsRepository: SendAsRepository,
    secretChatRepository: SecretChatRepository,
    autoDownloadPolicy: AutoDownloadPolicy,
    chatJoinRepository: ChatJoinRepository,
    threadRepositories: ThreadRepositories,
    shareInbox: ShareInbox,
    private val botButtonRepository: BotButtonRepository,
    botInfoRepository: BotInfoRepository,
    botStartInbox: BotStartInbox,
    inlineBotRepositories: InlineBotRepositories,
    translationRepositories: TranslationRepositories,
    searchRepositories: ChatSearchRepositories,
    customEmojiRepository: CustomEmojiRepository,
    pollRepository: PollRepository,
    scheduledMessageRepository: ScheduledMessageRepository,
    private val log: ChatLog,
    private val initialMessageId: Long? = null,
    recordingClock: () -> Long = SystemClock::elapsedRealtime,
    private val topic: MessageTopic? = null,
    sendsPhotosInOriginalQuality: Flow<Boolean> = flowOf(false),
) : ViewModel() {
    @AssistedInject
    constructor(
        @Assisted("chatId") chatId: Long,
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        messages: MessageRepository,
        messageActionRepository: MessageActionRepository,
        sponsoredMessageRepository: SponsoredMessageRepository,
        attachmentRepository: AttachmentRepository,
        composerSettings: ComposerSettingsRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatActionRepository: ChatActionRepository,
        fileRepository: FileRepository,
        searchRepository: SearchRepository,
        sharedStorageRepository: SharedStorageRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        audioPlayer: AudioPlayer,
        voiceRecorder: VoiceRecorder,
        voiceNoteRepository: VoiceNoteRepository,
        sendAsRepository: SendAsRepository,
        secretChatRepository: SecretChatRepository,
        autoDownloadPolicy: AutoDownloadPolicy,
        chatJoinRepository: ChatJoinRepository,
        threadRepositories: ThreadRepositories,
        shareInbox: ShareInbox,
        botButtonRepository: BotButtonRepository,
        botInfoRepository: BotInfoRepository,
        botStartInbox: BotStartInbox,
        inlineBotRepositories: InlineBotRepositories,
        translationRepositories: TranslationRepositories,
        searchRepositories: ChatSearchRepositories,
        customEmojiRepository: CustomEmojiRepository,
        pollRepository: PollRepository,
        scheduledMessageRepository: ScheduledMessageRepository,
        @Assisted("messageId") messageId: Long? = null,
        @Assisted("topic") topic: MessageTopic? = null,
    ) : this(
        chatId,
        savedStateHandle,
        chatRepository,
        messages,
        messageActionRepository,
        sponsoredMessageRepository,
        attachmentRepository,
        userRepository,
        groupRepository,
        chatActionRepository,
        fileRepository,
        searchRepository,
        sharedStorageRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
        audioPlayer,
        voiceRecorder,
        voiceNoteRepository,
        sendAsRepository,
        secretChatRepository,
        autoDownloadPolicy,
        chatJoinRepository,
        threadRepositories,
        shareInbox,
        botButtonRepository,
        botInfoRepository,
        botStartInbox,
        inlineBotRepositories,
        translationRepositories,
        searchRepositories,
        customEmojiRepository,
        pollRepository,
        scheduledMessageRepository,
        AndroidChatLog,
        messageId,
        topic = topic,
        sendsPhotosInOriginalQuality = composerSettings.sendPhotosInOriginalQuality,
    )

    /**
     * Creates the view model of chat `chatId`'s conversation, opened at message `messageId` if not null, of its forum
     * topic or message thread `topic` if not null.
     */
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("chatId") chatId: Long,
            @Assisted("messageId") messageId: Long?,
            @Assisted("topic") topic: MessageTopic?,
        ): ChatViewModel
    }

    /** Each retry of finding the chat starts it again. */
    private val chatAttempts = MutableStateFlow(0)

    /** Whether the conversation shows the whole chat, a forum topic or a message thread, and what that changes. */
    private val thread =
        ChatThread(chatId, topic, threadRepositories, chatAttempts, viewModelScope) { effectChannel.trySend(it) }

    /** The history and the texts sent: the topic's, in a topic's conversation. */
    private val messageRepository: MessageRepository = thread.messages(messages)

    /** The replied messages fetched for messages whose replied message is not loaded, by replying message id. */
    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())

    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The messages whose time was tapped to show ([ChatEvent.ToggleTime]). */
    private val shownTimes = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /**
     * Whether the chat is open in TDLib ([ChatEvent.Started]): set on the main thread, read where the history is
     * watched for failed sends too ([SendFailures]).
     */
    @Volatile
    private var isOpen = false

    /** [isOpen] as it changes, for what waits for the conversation to be on screen ([ChatShares]). */
    private val onScreen = MutableStateFlow(false)

    private val sendFailures = SendFailures()

    private val mapper = ChatItemMapper()
    private var timeFormatter: ChatTimeFormatter? = null

    private val effectChannel = Channel<ChatEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: snackbars and opening a location. */
    val effects: Flow<ChatEffect> = effectChannel.receiveAsFlow()

    private val files = ChatFiles(fileRepository, viewModelScope) { effectChannel.trySend(ChatEffect.ShowError(it)) }

    private val autoDownloads = ChatAutoDownloads(fileRepository, files, autoDownloadPolicy.rules, viewModelScope)

    private val customEmojis = ChatCustomEmoji(customEmojiRepository, viewModelScope)

    /**
     * The custom emoji loaded so far, which the bubbles and the composer draw ([ChatCustomEmoji]); kept out of
     * [uiState], so a newly loaded one recomposes nothing.
     */
    val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>> get() = customEmojis.customEmoji

    private val nextChannels =
        ChatNextChannel(chatId, wholeChat = topic == null, chatRepository, viewModelScope) { effectChannel.trySend(it) }

    /**
     * The next unread channel a read channel's conversation offers, which a pull past its newest post opens
     * ([ChatNextChannel]); kept out of [uiState], as only the end of the list shows it.
     */
    val nextChannel: StateFlow<NextChannel?> get() = nextChannels.next

    private val views = MessageViews(chatId, thread.chats(chatRepository), viewModelScope, log)

    private val mentions = ChatMentions(searchRepository, viewModelScope) { effectChannel.trySend(it) }

    private val sponsored =
        ChatSponsoredMessages(
            chatId = chatId,
            repository = sponsoredMessageRepository,
            views = views,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    private val playback =
        ChatPlayback(
            player = audioPlayer,
            files = fileRepository,
            scope = viewModelScope,
            history = { messageRepository.history(chatId).first() },
            onError = { effectChannel.trySend(ChatEffect.ShowError(it)) },
            onPlaybackFailed = { effectChannel.trySend(ChatEffect.PlaybackFailed) },
            log = log,
        )

    private val composer: ChatComposer =
        ChatComposer(
            chatId = chatId,
            repository = messageRepository,
            actions = messageActionRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            drafts = thread.drafts(chatRepository.chat(chatId)),
            messageOf = { id ->
                history.replayCache
                    .firstOrNull()
                    ?.messages
                    ?.findById(id) ?: composerTargets[id]
            },
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    /** Whether picked photos and videos go without compression by default (Data and storage). */
    private val originalQualityByDefault =
        sendsPhotosInOriginalQuality.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val attachments =
        ChatAttachments(
            chatId = chatId,
            repository = thread.attachments(attachmentRepository),
            savedState = savedStateHandle,
            scope = viewModelScope,
            replies = composer,
            onEffect = { effectChannel.trySend(it) },
            originalByDefault = { originalQualityByDefault.value },
        )

    private val recorder =
        ChatRecorder(
            chatId = chatId,
            recorder = voiceRecorder,
            repository = thread.voiceNotes(voiceNoteRepository),
            scope = viewModelScope,
            replies = composer,
            clock = recordingClock,
            onStart = { playback.pause() },
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    private val shares = ChatShares(chatId, shareInbox, onScreen.asStateFlow(), composer, attachments, viewModelScope)

    /** The voice message being recorded in the composer; null while there is none. */
    val voiceRecording: StateFlow<VoiceRecordingUiState?> get() = recorder.state

    private val sendAs =
        ChatSendAs(
            chatId = chatId,
            chats = chatRepository,
            users = userRepository,
            repository = sendAsRepository,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    private val joining = ChatJoining(chatId, chatJoinRepository, viewModelScope) { effectChannel.trySend(it) }

    private val bots =
        ChatBotCommands(
            chatId = chatId,
            repository = botInfoRepository,
            users = userRepository,
            chats = chatRepository,
            messages = messageRepository,
            composer = composer,
            inbox = botStartInbox,
            savedState = savedStateHandle,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    /** The bots' commands sheet; null while it is closed. Kept out of [uiState], like the forward sheet. */
    val botCommandsSheet: StateFlow<BotCommandsSheet?> get() = bots.sheet

    /** The commands suggested above the composer while one is typed; null while none is. Kept out of [uiState]. */
    val commandSuggestions: StateFlow<CommandSuggestions?> get() = bots.suggestions

    private val inlineBots =
        ChatInlineBots(
            chatId = chatId,
            repositories = inlineBotRepositories,
            bots = thread.inlineBots(inlineBotRepositories.bots),
            composer = composer,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            log = log,
        )

    /** The inline bot and its results while the field holds an inline query; null while not. Kept out of [uiState]. */
    val inlineResults: StateFlow<InlineBotUiState?> get() = inlineBots.state

    /** Whether to share the location with an inline bot that asks for it; null while not asked. */
    val inlineLocationSheet: StateFlow<InlineLocationSheet?> get() = inlineBots.locationSheet

    /** The send-as picker; null while it is not open. Kept out of [uiState], like the forward sheet. */
    val sendAsSheet: StateFlow<SendAsSheet?> get() = sendAs.sheet

    private val botButtons =
        ChatBotButtons(
            chatId = chatId,
            repository = botButtonRepository,
            users = userRepository,
            chats = chatRepository,
            search = searchRepository,
            groups = groupRepository,
            composer = composer,
            shareInbox = shareInbox,
            messageOf = { id ->
                history.replayCache
                    .firstOrNull()
                    ?.messages
                    ?.findById(id)
            },
            canWrite = { uiState.value.composer is ComposerUiState.Available },
            scope = viewModelScope,
            dispatcher = dispatcher,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The alert or question after a bot's button was tapped; null while none shows. Kept out of [uiState]. */
    val botButtonDialog: StateFlow<BotButtonDialog?> get() = botButtons.dialog

    /** The password a bot's button asks for, for its field; never kept in the saved state. */
    val botButtonPassword: TextFieldState get() = botButtons.password

    /** The chat picker of a bot's switch-inline button; null while it is closed. Kept out of [uiState]. */
    val switchInlineSheet: StateFlow<SwitchInlineSheet?> get() = botButtons.chatPicker

    private val secret =
        ChatSecretChat(
            chatId = chatId,
            chats = chatRepository,
            users = userRepository,
            repository = secretChatRepository,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The self-destruct timer or encryption key sheet of a secret chat; null while none is open. */
    val secretChatSheet: StateFlow<SecretChatSheet?> get() = secret.sheet

    /** The messages the composer's bars show that are not loaded. */
    private val composerTargets = ComposerTargets(chatId, messageRepository, viewModelScope, composer::onMessageGone)

    /** The latest chat, history, user and composer, for the message actions; null while the chat is not known. */
    private val actionContext: suspend () -> ActionContext? = {
        (chat.first() as? ChatLookup.Found)?.chat?.let { chat ->
            ActionContext(
                chat = chat,
                history = history.first(),
                myUserId = userRepository.myUserId.first(),
                canWrite = uiState.value.composer is ComposerUiState.Available,
                users = userRepository.users.first(),
            )
        }
    }

    private val messageActions =
        ChatMessageActions(
            chatId = chatId,
            repository = messageActionRepository,
            composer = composer,
            scope = viewModelScope,
            current = actionContext,
            onEffect = { effectChannel.trySend(it) },
            onForward = { forwarding.open(it) },
            onSelect = { selection.select(it) },
            onReport = { reporting.start(it) },
            onSave = { saving.save(it) },
            onSelectionDeleted = { selection.clear() },
            translationAction = { translation.actionFor(it) },
            onRetractVote = { polls.retract(it) },
            onStopPoll = { polls.askStop(it) },
        )

    private val selection =
        ChatSelection(
            chatId = chatId,
            repository = messageActionRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            current = actionContext,
            onEffect = { effectChannel.trySend(it) },
        )

    private val reporting =
        ChatReporting(
            chatId = chatId,
            repository = messageActionRepository,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
            onReported = { selection.clear() },
        )

    private val saving =
        ChatSaving(
            files = fileRepository,
            storage = sharedStorageRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    private val forwarding =
        ChatForwarding(
            fromChatId = chatId,
            chats = chatRepository,
            search = searchRepository,
            users = userRepository,
            groups = groupRepository,
            actions = messageActionRepository,
            scope = viewModelScope,
            dispatcher = dispatcher,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The events of selection mode, reporting and saving. */
    private val selectionEvents =
        ChatSelectionEvents(selection, reporting, saving, messageActions, forwarding) {
            messageRepository.history(chatId).first()
        }

    /** The forward sheet; null while none is open. */
    val forwardSheet: StateFlow<ForwardSheet?> get() = forwarding.sheet

    /** The comment of the forward sheet, for its text field; kept like [composerText]. */
    val forwardComment: ComposerText get() = forwarding.comment

    /** The comment of a report that asks for one, for its text field; kept like [composerText]. */
    val reportComment: TextFieldState get() = reporting.comment

    /** The text the composer edits, for its text field; the view model keeps it across rotations. */
    val composerText: ComposerText get() = composer.text

    /** The caption of the attach preview, for its text field; kept like [composerText]. */
    val attachCaption: ComposerText get() = attachments.caption

    /** What the form of a new poll holds, for its fields; kept like [composerText]. */
    val pollEditor: PollEditor get() = attachments.poll

    private val chat: Flow<ChatLookup> =
        chatAttempts
            .flatMapLatest {
                chatRepository.chat(chatId).transformLatest { chat ->
                    if (chat != null) {
                        emit(ChatLookup.Found(chat))
                    } else {
                        emit(ChatLookup.Pending)
                        // Found, TDLib announces it too, and chat() follows.
                        when (val found = chatRepository.getChat(chatId)) {
                            is Outcome.Success -> emit(ChatLookup.Found(found.value))
                            is Outcome.Failure -> emit(ChatLookup.Failed(found.error))
                        }
                    }
                }
            }.combine(thread.state, ::inThread)
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val history: SharedFlow<ChatHistory> =
        messageRepository
            .history(chatId)
            .onEach(::fetchMissingReplies)
            // A `Reject` haptic when a message being sent fails while the screen is shown (SendFailures).
            .onEach { if (sendFailures.failedSince(it) && isOpen) effectChannel.trySend(ChatEffect.SendFailed) }
            .flowOn(dispatcher)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val translation =
        ChatTranslation(
            chatId = chatId,
            repositories = translationRepositories,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            history = history,
            messageOf = { id ->
                history.replayCache
                    .firstOrNull()
                    ?.messages
                    ?.findById(id)
            },
            savedState = savedStateHandle,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    private val polls =
        ChatPolls(
            chatId = chatId,
            repository = pollRepository,
            users = userRepository,
            chats = chatRepository,
            history = history,
            messageOf = { id ->
                history.replayCache
                    .firstOrNull()
                    ?.messages
                    ?.findById(id)
            },
            savedState = savedStateHandle,
            scope = viewModelScope,
            onEffect = { effectChannel.trySend(it) },
        )

    /** Who voted for each answer of a public poll; null while the sheet is closed. Kept out of [uiState]. */
    val pollVotersSheet: StateFlow<PollVotersSheet?> get() = polls.votersSheet

    /** The question before a poll is stopped; null while none shows. */
    val stopPollDialog: StateFlow<StopPollDialog?> get() = polls.stopDialog

    /** Which messages the conversation shows, where its list shows them, and how far pages have loaded. */
    private val window =
        ChatWindow(
            chatId = chatId,
            repository = messageRepository,
            savedState = savedStateHandle,
            scope = viewModelScope,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            history = history,
            content = { uiState.value.content },
            initialMessageId = initialMessageId,
            onEffect = { effectChannel.trySend(it) },
        )

    private val botKeyboard =
        ChatBotKeyboard(
            chatId = chatId,
            enabled = topic == null,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            history = history,
            messages = messageRepository,
            attachments = attachmentRepository,
            repository = botButtonRepository,
            users = userRepository,
            chats = chatRepository,
            search = searchRepository,
            groups = groupRepository,
            composer = composer,
            savedState = savedStateHandle,
            canWrite = { uiState.value.composer is ComposerUiState.Available },
            // uiState is made below: read once collected.
            writable = flowOf(Unit).flatMapLatest { uiState.map { it.composer is ComposerUiState.Available } },
            onOpenPoll = attachments::openPoll,
            scope = viewModelScope,
            dispatcher = dispatcher,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The bot's keyboard in the composer, and its field's hint. Kept out of [uiState]: only the composer shows it. */
    val botReply: StateFlow<BotReplyUiState> get() = botKeyboard.state

    /** The question before a keyboard button shares the user's phone number; null while none shows. */
    val botKeyboardDialog: StateFlow<BotKeyboardDialog?> get() = botKeyboard.dialog

    /** The users or chat picker of a keyboard button; null while it is closed. Kept out of [uiState]. */
    val keyboardShareSheet: StateFlow<KeyboardShareSheet?> get() = botKeyboard.shareSheet

    /**
     * The basic group or supergroup of a group or channel: its member count, and the user's membership; the secret
     * chat of a secret chat: its state.
     */
    private val group: Flow<ChatGroup> =
        chat
            .map { (it as? ChatLookup.Found)?.chat?.type }
            .distinctUntilChanged()
            .flatMapLatest { type ->
                when (type) {
                    is ChatType.BasicGroup -> {
                        groupRepository.basicGroup(type.basicGroupId).map { ChatGroup(basicGroup = it) }
                    }

                    is ChatType.Supergroup -> {
                        groupRepository.supergroup(type.supergroupId).map { ChatGroup(supergroup = it) }
                    }

                    is ChatType.Secret -> {
                        secret.secretChat.map { ChatGroup(secretChat = it) }
                    }

                    else -> {
                        flowOf(ChatGroup())
                    }
                }
            }.distinctUntilChanged()

    /** The chats the messages name, by id: their titles and photos, which change far less often than the chats. */
    private val referencedChats: Flow<ImmutableMap<Long, ChatRef>> =
        combine(history, replies) { history, replies -> referencedChatIds(chatId, history, replies) }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map(chatRepository::chatRef)) { refs ->
                        refs.filterNotNull().associateBy { it.id }.toImmutableMap()
                    }
                }
            }

    private val chatSources: Flow<ChatSources> =
        combine(
            chat,
            group,
            chatActionRepository.actions(chatId),
            savedStateHandle.getStateFlow<Long?>(KEY_UNREAD_AFTER, null),
            thread.state,
            ::ChatSources,
        )

    /** What was tapped in the bubbles: the messages whose spoilers show, and those whose time shows. */
    private val tapped: Flow<Pair<Set<Long>, Set<Long>>> = combine(revealedSpoilers, shownTimes, ::Pair)

    private val messageSources: Flow<MessageSources> =
        combine(history, replies, tapped, window.state) { history, replies, (spoilers, times), load ->
            MessageSources(MessageState(load.historyFor(history), replies, spoilers, times), load)
        }

    private val people: Flow<ChatPeople> =
        combine(userRepository.users, userRepository.myUserId, referencedChats, ::ChatPeople)

    private val pinned =
        thread.pinnedMessages(
            actions = messageActionRepository,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            people = people,
            onShow = window::showMessage,
        )

    /** The sheet of every pinned message; null while it is closed. Kept out of [uiState], like the forward sheet. */
    val pinnedSheet: StateFlow<PinnedSheet?> get() = pinned.sheet

    private val scheduled =
        ChatScheduledMessages(
            chatId = chatId,
            repository = scheduledMessageRepository,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            enabled = topic == null,
            // "When online" without the name: the bar has room for little.
            next = { schedule ->
                val formatter = timeFormatter()
                formatter.nextText(schedule, clock.now().atZone(formatter.zone).toLocalDate(), user = null)
            },
        )

    private val search =
        ChatSearch(
            chatId = chatId,
            topic = topic,
            repositories = searchRepositories,
            users = userRepository,
            chats = chatRepository,
            chat = chat.mapNotNull { (it as? ChatLookup.Found)?.chat },
            savedState = savedStateHandle,
            scope = viewModelScope,
            zone = clock::zone,
            onShow = window::showMessage,
            onEffect = { effectChannel.trySend(it) },
        )

    /** The search in the chat while it is open, for its bars; null while it is closed. Kept out of [uiState]. */
    val searchState: StateFlow<ChatSearchUiState?> get() = search.state

    /** The text of the search field; kept across process death. */
    val searchQuery: TextFieldState get() = search.query

    /** The sheet of the members whose messages search can be limited to; null while it is closed. */
    val searchSenderSheet: StateFlow<SenderSheet?> get() = search.senderSheet

    /** The text of the members' sheet's search field. */
    val searchSenderQuery: TextFieldState get() = search.senderQuery

    /**
     * What shows over the conversation: the composer's mode, with the message its bar shows fetched when it is not
     * loaded, the actions sheet, the delete confirmation, the attach preview and the poll form.
     */
    private val overlays: Flow<Overlays> =
        combine(
            combine(composer.mode, history, composerTargets.messages) { mode, history, targets ->
                composerTargets.require(mode.messageId, history)
                mode to targets
            },
            messageActions.sheet,
            messageActions.deleteDialog,
            attachments.preview,
            attachments.isPollOpen,
        ) { (mode, targets), sheet, deleteDialog, preview, pollOpen ->
            Overlays(mode, targets, sheet, deleteDialog, preview, pollOpen)
        }

    val uiState: StateFlow<ChatUiState> =
        combine(chatSources, messageSources, people, clock.minutes, overlays, ::state)
            .combine(files.statesOf(history)) { state, fileStates -> state.copy(fileStates = fileStates) }
            .combine(files.uploadsOf(history)) { state, uploads -> state.copy(uploads = uploads) }
            .combine(playback.state) { state, playback -> state.copy(playback = playback) }
            .combine(sponsored.state(history)) { state, ads -> state.withSponsored(ads) }
            .combine(selection.state(history)) { state, selection -> state.copy(selection = selection) }
            .combine(reporting.sheet) { state, report -> state.copy(report = report) }
            .combine(sendAs.button) { state, sendAs -> state.withSendAs(sendAs) }
            .combine(bots.state) { state, bots -> state.withBots(bots) }
            .combine(autoDownloads.state) { state, downloads -> state.copy(mediaDownloads = downloads) }
            .combine(joining.isJoining) { state, isJoining -> state.withJoining(isJoining) }
            .combine(pinned.bar) { state, bar -> state.copy(pinnedBar = bar) }
            .combine(botButtons.pending) { state, pending -> state.withPendingButtons(pending) }
            .combine(translation.bubbles) { state, translations -> state.withTranslations(translations) }
            .combine(translation.bar) { state, bar -> state.copy(translateBar = bar) }
            .combine(scheduled.bar) { state, bar -> state.copy(scheduledBar = bar) }
            .combine(search.highlight) { state, highlight -> state.withSearchMatch(highlight) }
            .combine(polls.choices) { state, choices -> state.withPollChoices(choices) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatUiState())

    init {
        // Leaving the screen frees the player, and deletes picked files that were never sent.
        addCloseable { playback.release() }
        addCloseable { attachments.release() }
        addCloseable { recorder.release() }
        // The screen creates the view model in the frame that first composes it. What it starts runs once that frame is
        // done (dispatched, not at once as the main thread's immediate dispatcher would), so the frame does not wait.
        viewModelScope.launch(Dispatchers.Main) {
            composer.start()
            attachments.start()
            shares.start()
            bots.start()
            search.start()
            botKeyboard.start()
            recorder.start()
            views.start()
            autoDownloads.start()
            customEmojis.start(composer.text)
            translation.start()
            window.open()
            sponsored.start(chat.mapNotNull { (it as? ChatLookup.Found)?.chat }, userRepository.users)
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            ChatEvent.Started -> setOpen(true)
            ChatEvent.Stopped -> setOpen(false)
            is ChatEvent.NearOldestChanged -> window.onNearOldestChanged(event.isNearOldest)
            is ChatEvent.NearNewestChanged -> window.onNearNewestChanged(event.isNearNewest)
            ChatEvent.ShowNewest -> window.showNewest()
            is ChatEvent.MessagesVisible -> onMessagesVisible(event.messageIds)
            is ChatEvent.OpenReply -> window.showMessage(event.messageId)
            is ChatEvent.OpenThread -> thread.open(event.messageId)
            ChatEvent.Retry -> retry()
            ChatEvent.JoinChat -> joining.join()
            is ChatEvent.RevealSpoilers -> revealedSpoilers.update { it.adding(event.messageId) }
            is ChatEvent.ToggleTime -> shownTimes.update { it.toggling(event.messageId) }
            is ChatEvent.OpenMedia -> uiState.value.mediaToOpen(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.DownloadFile -> autoDownloads.download(event.fileId)
            is ChatEvent.CancelDownload -> autoDownloads.cancelDownload(event.fileId)
            is ChatEvent.OpenFile -> uiState.value.fileToOpen(event)?.let(effectChannel::trySend)
            is ChatEvent.OpenLocation -> uiState.value.locationOf(event.messageId)?.let(effectChannel::trySend)
            is ChatEvent.OpenProfile -> effectChannel.trySend(ChatEffect.OpenProfile(event.userId))
            is ChatEvent.OpenMention -> mentions.open(event.target)
            is ChatEvent.PlayPause -> playback.playPause(event.messageId)
            is ChatEvent.Seek -> playback.seek(event.messageId, event.fraction)
            is ChatEvent.Send -> composer.send(event.options)
            is ChatEvent.ComposerFocusChanged -> composer.setFocused(event.isFocused)
            ChatEvent.CancelReply -> composer.cancelReply()
            ChatEvent.CancelEdit -> composer.cancelEdit()
            is SponsoredEvent -> sponsored.onEvent(event)
            is SelectionEvent -> selectionEvents.onEvent(event)
            is VoiceEvent -> recorder.onEvent(event)
            is SendAsEvent -> sendAs.onEvent(event)
            is BotEvent -> bots.onEvent(event)
            is InlineBotEvent -> inlineBots.onEvent(event)
            is SecretChatEvent -> secret.onEvent(event)
            is PinnedEvent -> pinned.onEvent(event)
            is BotButtonEvent -> botButtons.onEvent(event)
            is ChatSearchEvent -> search.onEvent(event)
            is BotKeyboardEvent -> botKeyboard.onEvent(event)
            is TranslationEvent -> translation.onEvent(event)
            is PollEvent -> onPollEvent(event)
            else -> onMessageEvent(event)
        }
    }

    /** The events of polls: taking a vote back and stopping a poll go through the message actions' checks. */
    private fun onPollEvent(event: PollEvent) {
        when (event) {
            is PollEvent.RetractVote -> messageActions.retractVote(event.messageId)
            is PollEvent.StopPoll -> messageActions.stopPoll(event.messageId)
            else -> polls.onEvent(event)
        }
    }

    /** The events of the message actions, the reactions and the forward sheet, which [onEvent] hands on. */
    private fun onMessageEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.ShowMessageActions -> messageActions.show(event.messageId)
            is ChatEvent.ShowAllReactions -> messageActions.show(event.messageId, showsPicker = true)
            ChatEvent.ExpandReactions -> messageActions.showPicker(true)
            ChatEvent.CollapseReactions -> messageActions.showPicker(false)
            ChatEvent.DismissMessageActions -> messageActions.dismiss()
            is ChatEvent.Reply -> messageActions.reply(event.messageId)
            is ChatEvent.Copy -> messageActions.copy(event.messageId)
            is ChatEvent.Edit -> messageActions.edit(event.messageId)
            is ChatEvent.Pin -> messageActions.pin(event.messageId, event.onlyForSelf)
            is ChatEvent.Unpin -> messageActions.unpin(event.messageId)
            is ChatEvent.Delete -> messageActions.delete(event.messageId)
            is ChatEvent.ConfirmDelete -> messageActions.confirmDelete(event.forEveryone)
            ChatEvent.DismissDelete -> messageActions.dismissDelete()
            is ChatEvent.Resend -> messageActions.resend(event.messageId)
            is ChatEvent.ToggleReaction -> messageActions.toggleReaction(event.messageId, event.label)
            is ChatEvent.Forward -> messageActions.forward(event.messageId)
            is ChatEvent.OpenNextChannel -> nextChannels.open(event.chatId)
            is ForwardEvent -> forwarding.onEvent(event)
            else -> onAttachEvent(event)
        }
    }

    /** The events of the attach menu, its previews and the poll form, which [onMessageEvent] hands on. */
    private fun onAttachEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.AttachMedia -> attachments.pickMedia(event.uris)

            is ChatEvent.AttachFiles -> attachments.pickFiles(event.uris)

            ChatEvent.TakePhoto -> attachments.takePhoto()

            is ChatEvent.PhotoTaken -> attachments.photoTaken(event.taken)

            is ChatEvent.AttachContact -> attachments.sendContact(event.uri)

            is ChatEvent.SendLocation -> attachments.sendLocation(event.location, event.options)

            is ChatEvent.SendSticker -> attachments.sendSticker(event.sticker)

            is ChatEvent.SendAnimation -> attachments.sendAnimation(event.animation)

            is ChatEvent.RemoveAttachment -> attachments.remove(event.key)

            is ChatEvent.SetOriginalQuality -> attachments.setOriginal(event.original)

            ChatEvent.DismissAttachments -> attachments.dismissPreview()

            is ChatEvent.SendAttachments -> attachments.sendPreview(event.options)

            ChatEvent.OpenPoll -> attachments.openPoll()

            ChatEvent.DismissPoll -> attachments.dismissPoll()

            // A poll goes out anonymous in a channel.
            is ChatEvent.SendPoll -> attachments.sendPoll(event.options, uiState.value.poll?.isChannel == true)

            is ChatEvent.CancelUpload -> attachments.cancelUpload(event.messageIds)

            // The other events are handled by onEvent and onMessageEvent.
            else -> Unit
        }
    }

    /**
     * Opens or closes the chat in TDLib when that changes. Undispatched, so the request is sent before this returns:
     * a close as the screen leaves, right before the view model's scope is cancelled, still reaches TDLib. A failure
     * changes nothing: TDLib then just does not prioritize the chat. Stopping also pauses what plays: nothing plays in
     * the background; locks and pauses a voice recording, as the microphone is not the app's in the background; and it
     * saves the composer's draft at once, also before the scope is cancelled.
     */
    private fun setOpen(open: Boolean) {
        if (open == isOpen) return
        isOpen = open
        onScreen.value = open
        if (!open) {
            composer.flush()
            playback.pause()
            recorder.onStopped()
            views.setOpen(false)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (open) chatRepository.openChat(chatId) else chatRepository.closeChat(chatId)
        }
        // Once openChat is sent: TDLib reads the messages viewed in an open chat only.
        if (open) {
            views.setOpen(true)
            sponsored.onStarted()
        }
    }

    /**
     * The messages the list shows go to TDLib as viewed, move the pinned messages bar (`ChatPinnedMessages`), are
     * translated while the chat's translation is on (`ChatTranslation`) and have their custom emoji loaded
     * ([ChatCustomEmoji]); the
     * newest of them is kept in the [SavedStateHandle], which the conversation opens around after process death
     * ([ChatWindow]).
     */
    private fun onMessagesVisible(messageIds: Set<Long>) {
        views.setVisible(messageIds)
        pinned.setVisible(messageIds)
        translation.setVisible(messageIds)
        messageIds.maxOrNull()?.let { savedStateHandle[ChatWindow.KEY_SHOWN_MESSAGE] = it }
        val messages =
            history.replayCache
                .firstOrNull()
                ?.messages
                .orEmpty()
        val shown = messages.filter { it.id in messageIds }
        autoDownloads.setVisible(shown.map { it.content })
        customEmojis.setVisible(shown)
    }

    /** Finds the chat again if that failed, and opens again or loads the pages that failed ([ChatWindow.retry]). */
    private fun retry() {
        if (!window.retry() && uiState.value.content is ChatContent.Error) chatAttempts.update { it + 1 }
    }

    /**
     * Asks once for each replied message that is not in [history], for messages of this chat: the quote shows it
     * once it is there. A failure is kept, and shown; it is not asked for again.
     */
    private fun fetchMissingReplies(history: ChatHistory) {
        val missing =
            history.messages.filter { message ->
                val replyTo = message.replyTo as? ReplyTo.Message
                val loaded = replyTo?.chatId == chatId && history.messages.findById(replyTo.messageId) != null
                replyTo != null && replyTo.messageId != 0L && !loaded && message.id !in replies.value
            }
        for (message in missing) {
            var added = false
            replies.update { replies ->
                added = message.id !in replies
                if (added) replies.putting(message.id, ReplyState.Pending) else replies
            }
            if (added) {
                viewModelScope.launch {
                    val state =
                        when (val replied = messageRepository.repliedMessage(chatId, message.id)) {
                            is Outcome.Success -> ReplyState.Loaded(replied.value)
                            is Outcome.Failure -> ReplyState.Failed(replied.error)
                        }
                    replies.update { it.putting(message.id, state) }
                }
            }
        }
    }

    /** Runs on [dispatcher]: maps the header, the items, the composer and what shows over them. */
    private fun state(
        chatSources: ChatSources,
        messages: MessageSources,
        people: ChatPeople,
        now: Instant,
        overlays: Overlays,
    ): ChatUiState {
        val lookup = chatSources.chat
        val chat = (lookup as? ChatLookup.Found)?.chat
        val unreadAfterId = chatSources.unreadAfterId
        if (chat == null || unreadAfterId == null) {
            return ChatUiState(content = (lookup as? ChatLookup.Failed)?.error.toContent())
        }
        val formatter = timeFormatter()
        val group = chatSources.group
        val header =
            HeaderSources(
                chat = chat,
                memberCount = group.memberCount,
                secretChat = group.secretChat,
                actions = chatSources.actions,
                people = people,
                now = now,
                timeFormatter = formatter,
            ).toHeader()
                .let { chatSources.thread.header(it, chat) }
        val history = messages.state.history
        val user = chat.userId?.let(people.users::get)
        val composer =
            composerState(chat, user, group.basicGroup, group.supergroup, group.secretChat)
                .let { chatSources.thread.composer(it, group.supergroup) }
        val canWrite = composer is ComposerUiState.Available
        val options = attachOptions(chat, user, people.myUserId)
        val canSchedule = (composer as? ComposerUiState.Available)?.canSchedule == true
        return ChatUiState(
            header = header,
            content = mapper.content(chat, messages, people, unreadAfterId, now, formatter),
            unreadCount = chat.unreadCount,
            composer = if (canWrite) composer.withBar(overlays.bar(chat, history, people), options) else composer,
            // A message deleted meanwhile takes its sheet with it.
            messageActions = overlays.sheet?.takeIf { history.messages.findById(it.messageId) != null },
            deleteDialog = overlays.deleteDialog,
            shortcuts =
                MessageShortcuts(
                    canReply = canWrite,
                    canCopy = !chat.hasProtectedContent,
                    canForward = !chat.hasProtectedContent && chat.type !is ChatType.Secret,
                ),
            // What was picked or written stays while the user may not write; it shows again when they may.
            attachPreview =
                overlays.preview?.takeIf { canWrite }?.let {
                    AttachPreview(it.kind, it.items, canSchedule, it.offersOriginal, it.original)
                },
            poll =
                PollSheet(chat.isChannel, canSchedule).takeIf {
                    canWrite && overlays.pollOpen && AttachOption.Poll in options
                },
        )
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    private fun timeFormatter(): ChatTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }

    internal companion object {
        /** The last message read when the chat was first shown ([NO_UNREAD_DIVIDER] if nothing was unread). */
        const val KEY_UNREAD_AFTER = "chat.unreadAfterId"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The chat, or why it is not there (yet). */
private sealed interface ChatLookup {
    data object Pending : ChatLookup

    data class Found(
        val chat: Chat,
    ) : ChatLookup

    data class Failed(
        val error: DomainError,
    ) : ChatLookup
}

/** The messages of [messages] as items mapped by this mapper, or why there are none. */
@Suppress("LongParameterList") // What the items are made from, passed on to the mapper.
private fun ChatItemMapper.content(
    chat: Chat,
    messages: MessageSources,
    people: ChatPeople,
    unreadAfterId: Long,
    now: Instant,
    formatter: ChatTimeFormatter,
): ChatContent {
    val history = messages.state.history
    val load = messages.pageLoad
    val error = load.error
    val shown = load.shown
    return when {
        shown == null -> {
            load.openError?.let(ChatContent::Error) ?: ChatContent.Loading
        }

        history.messages.isNotEmpty() -> {
            val items =
                map(
                    ItemSources(
                        chat = chat,
                        messages = messages.state,
                        people = people,
                        unreadAfterId = unreadAfterId,
                        today = now.atZone(formatter.zone).toLocalDate(),
                        timeFormatter = formatter,
                    ),
                )
            ChatContent.Content(
                items = items,
                reachedStart = history.reachedStart,
                isLoadingOlder = load.isLoading || load.isFindingMessage,
                olderError = error,
                reachedEnd = history.reachedEnd,
                isLoadingNewer = load.isLoadingNewer || load.isFindingNewest,
                newerError = load.newerError,
                anchor = shown.anchor,
            )
        }

        error != null -> {
            ChatContent.Error(error)
        }

        history.reachedStart -> {
            ChatContent.Empty
        }

        else -> {
            ChatContent.Loading
        }
    }
}

private class MessageSources(
    val state: MessageState,
    val pageLoad: WindowState,
)

/**
 * The composer's mode with the bar messages fetched for it, the actions sheet, the delete confirmation, the attach
 * preview and whether the poll form is open.
 */
private class Overlays(
    val mode: ComposerMode,
    val targets: Map<Long, Message?>,
    val sheet: MessageActionsSheet?,
    val deleteDialog: DeleteMessagesDialog?,
    val preview: PreviewDraft?,
    val pollOpen: Boolean,
) {
    fun bar(
        chat: Chat,
        history: ChatHistory,
        people: ChatPeople,
    ): ComposerBar? = composerBar(mode, chat, history, targets, people)
}

private class ChatSources(
    val chat: ChatLookup,
    val group: ChatGroup,
    val actions: List<SenderAction>,
    /** Null until the chat was first shown. */
    val unreadAfterId: Long?,
    val thread: ThreadState,
)

/**
 * The chat as the conversation of [thread] shows it ([ThreadState.inThread]): pending while a forum topic is on its
 * way, not found when the topic cannot be found.
 */
private fun inThread(
    lookup: ChatLookup,
    thread: ThreadState,
): ChatLookup =
    when {
        lookup !is ChatLookup.Found -> lookup
        thread == ThreadState.Missing -> ChatLookup.Failed(DomainError.NotFound)
        thread is ThreadState.Failed -> ChatLookup.Failed(thread.error)
        else -> ChatLookup.Found(thread.inThread(lookup.chat))
    }

/**
 * The basic group or supergroup of a group or channel chat, the secret chat of a secret chat; none for other chats,
 * nor while unknown.
 */
private data class ChatGroup(
    val basicGroup: BasicGroup? = null,
    val supergroup: Supergroup? = null,
    val secretChat: SecretChat? = null,
) {
    /** Null while unknown, and for other chats. */
    val memberCount: Int? get() = basicGroup?.memberCount ?: supergroup?.memberCount
}

/** The composer with [bar], and the [attachOptions] of the chat unless it edits: an edit sends no attachments. */
private fun ComposerUiState.Available.withBar(
    bar: ComposerBar?,
    attachOptions: ImmutableList<AttachOption>,
): ComposerUiState.Available =
    copy(bar = bar, attachOptions = if (bar is ComposerBar.Edit) persistentListOf() else attachOptions)

/** The state with [sendAs] on the composer, where the user can write and is not editing: an edit keeps its sender. */
private fun ChatUiState.withSendAs(sendAs: SendAsChoice?): ChatUiState {
    val available = composer as? ComposerUiState.Available ?: return this
    return copy(composer = available.copy(sendAs = sendAs.takeIf { available.bar !is ComposerBar.Edit }))
}

/**
 * The location or venue of loaded message [messageId], as the effect that shows it in a maps app, named after the
 * venue; null for other messages.
 */
private fun ChatUiState.locationOf(messageId: Long): ChatEffect.OpenLocation? {
    val bubble =
        (content as? ChatContent.Content)
            ?.items
            ?.firstOrNull { it is ChatItem.Bubble && it.id == messageId } as? ChatItem.Bubble
    return when (val message = bubble?.content) {
        is MessageContent.Location -> {
            ChatEffect.OpenLocation(message.location.latitude, message.location.longitude, label = null)
        }

        is MessageContent.Venue -> {
            val venue = message.venue
            ChatEffect.OpenLocation(venue.location.latitude, venue.location.longitude, venue.title.ifBlank { null })
        }

        else -> {
            null
        }
    }
}

/**
 * The document of the loaded message that [event] names, its file downloaded, as the effect that opens it in another
 * app; null for other messages and for files not on the device. Documents of chats that protect their content open
 * too: Telegram lets them be viewed, only not saved (`has_protected_content`), and Pingogram offers no saving here.
 */
private fun ChatUiState.fileToOpen(event: ChatEvent.OpenFile): ChatEffect.OpenFile? {
    val bubble = items.firstOrNull { it is ChatItem.Bubble && it.id == event.messageId } as? ChatItem.Bubble
    val document = (bubble?.content as? MessageContent.Document)?.document?.takeIf { it.file.id == event.fileId }
    val path = (fileStates[event.fileId] as? FileState.Downloaded)?.path ?: return null
    return document?.let { ChatEffect.OpenFile(path, it.mimeType.ifBlank { null }) }
}

/**
 * The photo, video or GIF of loaded message [messageId], an album's item too, as the effect that opens it in the media
 * viewer; null for other messages. Video messages stay where they are, and so does self-destructing media, which needs
 * a viewer of its own (a timer once opened, screenshots blocked).
 */
private fun ChatUiState.mediaToOpen(messageId: Long): ChatEffect.OpenMedia? {
    val content =
        items.firstNotNullOfOrNull { item ->
            val bubble = item as? ChatItem.Bubble
            val album = bubble?.album
            when {
                album != null -> album.items.getOrNull(album.messageIds.indexOf(messageId))
                bubble != null && bubble.id == messageId -> bubble.content
                else -> null
            }
        }
    return if (content?.opensInViewer == true) ChatEffect.OpenMedia(messageId) else null
}

/** The items shown, none while there is no content. */
private val ChatUiState.items: List<ChatItem>
    get() = (content as? ChatContent.Content)?.items.orEmpty()

/** The title and photo of chat [id], then every change of them; null while TDLib has not announced the chat. */
internal fun ChatRepository.chatRef(id: Long): Flow<ChatRef?> =
    chat(id)
        .map { chat -> chat?.let { ChatRef(it.id, it.title, it.photo) } }
        .distinctUntilChanged()

/** Not found for a chat TDLib does not know, an error with a retry for other failures; loading without a failure. */
private fun DomainError?.toContent(): ChatContent =
    when (this) {
        null -> ChatContent.Loading
        DomainError.NotFound -> ChatContent.NotFound
        else -> ChatContent.Error(this)
    }

/** This set without [id] if it holds it, else with it. */
private fun PersistentSet<Long>.toggling(id: Long): PersistentSet<Long> = if (id in this) removing(id) else adding(id)

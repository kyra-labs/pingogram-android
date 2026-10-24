package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.LanguageDetection
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.testing.FakeAppLanguageRepository
import dev.kyralabs.pingogram.core.testing.FakeCustomEmojiRepository
import dev.kyralabs.pingogram.core.testing.FakeDeviceLanguageDetector
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotStartInbox
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotRepositories
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationConsent
import dev.kyralabs.pingogram.feature.chat.ui.location.FakeDeviceLocationRepository
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchRepositories
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeChatSearchRepository
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslationRepositories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * The fakes and helpers the tests of [ChatViewModel] share: the view model on fakes of the domain repositories, of
 * the player and of the voice recorder (whose clock is the test's), a fake wall clock at [NOW] in UTC, fixed English
 * time patterns and a log that keeps what it is told.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ChatViewModelHarness {
    protected val chats = FakeChatRepository()
    protected val messages = FakeMessageRepository()
    protected val messageActions = FakeMessageActionRepository()
    protected val attachments = FakeAttachmentRepository()
    protected val voiceNotes = FakeVoiceNoteRepository()
    protected val sendAs = FakeSendAsRepository()
    protected val secretChats = FakeSecretChatRepository()
    protected val sponsored = FakeSponsoredMessageRepository()
    protected val users = FakeUserRepository().apply { myUserId.value = ME }
    protected val groups = FakeGroupRepository()
    protected val actions = FakeChatActionRepository()
    protected val files = FakeFileRepository()
    protected val search = FakeSearchRepository()
    protected val sharedStorage = FakeSharedStorageRepository()
    protected val clock = FakeWallClock(NOW)
    protected val savedState = SavedStateHandle()
    internal val player = FakeAudioPlayer()
    internal val recorder = FakeVoiceRecorder()
    protected val autoDownloads = FakeAutoDownloadPolicy()
    protected val joins = FakeChatJoinRepository()
    protected val pinnedMessages = FakePinnedMessageRepository()
    protected val hiddenPins = FakeHiddenPinsRepository()
    protected val forumTopics = FakeForumTopicRepository()
    protected val threads = FakeMessageThreadRepository()
    protected val shareInbox = ShareInbox()
    protected val botButtons = FakeBotButtonRepository()
    protected val bots = FakeBotInfoRepository()
    protected val botStarts = BotStartInbox()
    protected val inlineBots = FakeInlineBotRepository()
    internal val deviceLocation = FakeDeviceLocationRepository()
    protected val locationConsent = InlineLocationConsent()
    protected val translations = FakeTranslationRepository()

    /** Texts are in English, the user's language, unless a test says otherwise. */
    protected val languageDetector = FakeDeviceLanguageDetector(default = LanguageDetection.Detected("en"))
    protected val appLanguage = FakeAppLanguageRepository()
    protected val hiddenTranslateBars = FakeHiddenTranslateBarsRepository()
    internal val chatSearch = FakeChatSearchRepository()
    internal val groupMembers = FakeGroupInfoRepository()
    protected val customEmoji = FakeCustomEmojiRepository()

    /** Every message logged, in order. */
    protected val logged = mutableListOf<String>()

    /** The view models the test made, for [runViewModelTest]. */
    private val created = mutableListOf<ChatViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A view model of [chatId] (of its forum topic or message thread [topic], if not null), opened at [messageId] if
     * not null, whose state is collected, as by the screen.
     */
    protected fun TestScope.viewModel(
        chatId: Long = ChatFixtures.PRIVATE_CHAT,
        handle: SavedStateHandle = savedState,
        messageId: Long? = null,
        topic: MessageTopic? = null,
        originalQuality: Boolean = false,
    ): ChatViewModel {
        val viewModel =
            ChatViewModel(
                chatId = chatId,
                savedStateHandle = handle,
                chatRepository = chats,
                messages = messages,
                messageActionRepository = messageActions,
                sponsoredMessageRepository = sponsored,
                attachmentRepository = attachments,
                userRepository = users,
                groupRepository = groups,
                chatActionRepository = actions,
                fileRepository = files,
                searchRepository = search,
                sharedStorageRepository = sharedStorage,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
                audioPlayer = player,
                voiceRecorder = recorder,
                voiceNoteRepository = voiceNotes,
                sendAsRepository = sendAs,
                secretChatRepository = secretChats,
                autoDownloadPolicy = autoDownloads,
                chatJoinRepository = joins,
                threadRepositories = ThreadRepositories(pinnedMessages, hiddenPins, forumTopics, threads),
                shareInbox = shareInbox,
                botButtonRepository = botButtons,
                botInfoRepository = bots,
                botStartInbox = botStarts,
                inlineBotRepositories = InlineBotRepositories(inlineBots, deviceLocation, locationConsent, botStarts),
                translationRepositories =
                    TranslationRepositories(translations, languageDetector, appLanguage, hiddenTranslateBars),
                searchRepositories = ChatSearchRepositories(chatSearch, groupMembers),
                customEmojiRepository = customEmoji,
                log = { logged += it },
                initialMessageId = messageId,
                recordingClock = { testScheduler.currentTime },
                topic = topic,
                sendsPhotosInOriginalQuality = flowOf(originalQuality),
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /**
     * [runTest] that clears every view model the test made once [testBody] ends, which stops what still runs in their
     * scopes: [runTest] runs every task left before it returns, and the progress ticker of something playing never
     * runs out.
     */
    protected fun runViewModelTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            try {
                testBody()
            } finally {
                created.forEach(::clear)
            }
        }

    /** A view model of [chat] showing [history]. */
    protected fun TestScope.showing(
        chat: Chat,
        history: ChatHistory,
    ): ChatViewModel {
        chats.setChats(chat)
        messages.setHistory(chat.id, history)
        return viewModel(chat.id)
    }

    protected fun TestScope.send(
        viewModel: ChatViewModel,
        vararg events: ChatEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** Clears [viewModel] as leaving the screen does: through a [ViewModelStore], which closes what it holds. */
    protected fun clear(viewModel: ChatViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[ChatViewModel::class]
        store.clear()
    }

    protected val ChatViewModel.state get() = uiState.value

    protected val ChatViewModel.content get() = state.content as ChatContent.Content

    protected val ChatViewModel.items get() = content.items

    protected val ChatViewModel.anchor get() = content.anchor

    protected val ChatViewModel.bubbles get() = items.filterIsInstance<ChatItem.Bubble>()

    protected fun ChatViewModel.bubble(id: Long) = bubbles.single { it.id == id }

    protected val ChatViewModel.subtitle get() = state.header?.subtitle

    /**
     * The items as short descriptions, newest first: `m<id>`, `s<id>` for service messages, dates, the divider and
     * `ad<id>` for sponsored messages.
     */
    protected fun ChatViewModel.outline(): List<String> =
        items.map { item ->
            when (item) {
                is ChatItem.Bubble -> "m${item.id}"
                is ChatItem.Service -> "s${item.id}"
                is ChatItem.DateSeparator -> "date:${LocalDate.ofEpochDay(item.epochDay)}"
                is ChatItem.UnreadDivider -> "unread"
                is ChatItem.Sponsored -> "ad${item.card.id}"
            }
        }

    protected fun res(
        id: Int,
        vararg args: Any,
    ) = UiText.resource(id, *args)

    protected fun plural(
        id: Int,
        count: Int,
    ) = UiText.plural(id, count, count)

    /** A loadOlder that never answers. */
    protected val loadsForever: suspend (Long) -> Outcome<Unit> = {
        CompletableDeferred<Outcome<Unit>>().await()
    }
}

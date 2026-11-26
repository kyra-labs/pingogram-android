package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.testing.FakeChatListSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeKeywordAlertsRepository
import dev.kyralabs.pingogram.core.testing.FakeKeywordHitRepository
import dev.kyralabs.pingogram.core.testing.FakeLocalChatNamesRepository
import dev.kyralabs.pingogram.core.testing.FakeMutedWordsRepository
import dev.kyralabs.pingogram.core.testing.FakeShownChatListRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredDismissalRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredSettingsRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpChats
import dev.kyralabs.pingogram.feature.chats.ui.keyword.KeywordAlertChats
import dev.kyralabs.pingogram.feature.chats.ui.unanswered.UnansweredChats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import java.util.Locale

/**
 * The fakes and helpers [ChatListViewModelTest] and [ChatListRowsTest] share: a [ChatListViewModel] on fakes of the
 * domain repositories, a fake wall clock at [NOW] and fixed English time patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ChatListViewModelHarness {
    protected val chats = FakeChatRepository()
    protected val shownLists = FakeShownChatListRepository()
    protected val users = FakeUserRepository().apply { myUserId.value = ME }
    protected val groups = FakeGroupRepository()
    protected val secretChats = FakeSecretChatRepository()
    protected val actions = FakeChatActionRepository()
    protected val connection = FakeConnectionRepository()
    protected val chatLock = FakeChatLockRepository()
    protected val archiveRow = FakeArchiveRowRepository()
    protected val archiveSettings = FakeArchiveSettingsRepository()
    protected val communities = FakeCommunityRepository()
    protected val forumTopics = FakeForumTopicRepository()
    protected val chatListSettings = FakeChatListSettingsRepository()
    protected val keywordHits = FakeKeywordHitRepository()
    protected val keywordAlerts = FakeKeywordAlertsRepository()
    protected val clock = FakeWallClock(NOW)
    protected val timePatterns = FakeTimePatterns()
    protected val localNames = FakeLocalChatNamesRepository()
    protected val mutedWords = FakeMutedWordsRepository()
    protected val unanswered = FakeUnansweredSettingsRepository()
    protected val unansweredDismissals = FakeUnansweredDismissalRepository()
    protected val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the screen. */
    protected fun TestScope.viewModel(
        handle: SavedStateHandle = savedState,
        kind: ChatListKind = ChatListKind.Chats,
    ): ChatListViewModel {
        val viewModel = createViewModel(handle, kind)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    protected fun TestScope.createViewModel(
        handle: SavedStateHandle = savedState,
        kind: ChatListKind = ChatListKind.Chats,
    ) = ChatListViewModel(
        kind = kind,
        savedStateHandle = handle,
        chatRepository = chats,
        userRepository = users,
        groupRepository = groups,
        secretChatRepository = secretChats,
        chatActionRepository = actions,
        connectionRepository = connection,
        chatLock = chatLock,
        archiveRowRepository = archiveRow,
        archiveSettingsRepository = archiveSettings,
        communityRepository = communities,
        forumTopicRepository = forumTopics,
        catchUpChats = CatchUpChats(chats, chatListSettings, chatLock),
        unansweredChats = UnansweredChats(chats, users, chatLock, unansweredDismissals, unanswered, clock),
        keywordChats = KeywordAlertChats(chats, keywordHits, keywordAlerts, chatLock),
        shownLists = shownLists,
        localChatNames = localNames,
        mutedWords = mutedWords,
        clock = clock,
        timePatterns = timePatterns,
        dispatcher = StandardTestDispatcher(testScheduler),
        locale = { Locale.US },
    )

    /** Runs what is due once the counts' pace ([COUNT_PACE]) has passed, so the last count changed shows. */
    protected fun TestScope.afterCountPace() {
        advanceTimeBy(COUNT_PACE)
        runCurrent()
    }

    protected fun TestScope.send(
        viewModel: ChatListViewModel,
        vararg events: ChatListEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** The effects [viewModel] sends from now on, collected as the screen does. */
    protected fun TestScope.effectsOf(viewModel: ChatListViewModel): List<ChatListEffect> {
        val effects = mutableListOf<ChatListEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    /** A view model showing [chat] alone in the main list, and its row. */
    protected fun TestScope.rowOf(chat: Chat): ChatListItemModel {
        chats.setChats(ChatListType.Main, chat)
        return viewModel().item(chat.id)
    }

    protected val ChatListViewModel.state get() = uiState.value

    protected val ChatListViewModel.items get() = (state.content as ChatListContent.Content).items

    protected fun ChatListViewModel.item(id: Long) = items.single { it.id == id }

    protected fun ChatListViewModel.ids() = items.map { it.id }

    protected fun res(
        id: Int,
        vararg args: Any,
    ) = UiText.resource(id, *args)

    protected val loadsChatsForever: suspend (ChatListType, Int) -> Outcome<Boolean> = { _, _ ->
        CompletableDeferred<Outcome<Boolean>>().await()
    }

    protected fun outgoing(
        chatId: Long,
        sendingState: SendingState? = null,
        sender: MessageSender = MessageSender.User(ME),
        date: Instant = NOW,
    ) = message(chatId, id = 10, sender = sender, isOutgoing = true, sendingState = sendingState, date = date)

    protected val nearEnd = ChatListEvent.NearEndChanged(isNearEnd = true)
    protected val awayFromEnd = ChatListEvent.NearEndChanged(isNearEnd = false)
    protected val ok = Outcome.Success(true)

    protected companion object {
        const val PAGE = ChatRepository.PAGE_SIZE
        const val GROUP = 50L
        const val OTHER_GROUP = 51L
        val BASIC_GROUP = ChatType.BasicGroup(basicGroupId = 8)
    }
}

package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    protected val users = FakeUserRepository().apply { myUserId.value = ME }
    protected val groups = FakeGroupRepository()
    protected val secretChats = FakeSecretChatRepository()
    protected val actions = FakeChatActionRepository()
    protected val connection = FakeConnectionRepository()
    protected val archiveRow = FakeArchiveRowRepository()
    protected val archiveSettings = FakeArchiveSettingsRepository()
    protected val clock = FakeWallClock(NOW)
    protected val timePatterns = FakeTimePatterns()
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
        archiveRowRepository = archiveRow,
        archiveSettingsRepository = archiveSettings,
        clock = clock,
        timePatterns = timePatterns,
        dispatcher = StandardTestDispatcher(testScheduler),
        locale = { Locale.US },
    )

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

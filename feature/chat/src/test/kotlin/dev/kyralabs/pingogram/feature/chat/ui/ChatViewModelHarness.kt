package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.util.Locale

/**
 * The fakes and helpers the tests of [ChatViewModel] share: the view model on fakes of the domain repositories, a fake
 * wall clock at [NOW] in UTC and fixed English time patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ChatViewModelHarness {
    protected val chats = FakeChatRepository()
    protected val messages = FakeMessageRepository()
    protected val users = FakeUserRepository().apply { myUserId.value = ME }
    protected val groups = FakeGroupRepository()
    protected val actions = FakeChatActionRepository()
    protected val clock = FakeWallClock(NOW)
    protected val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model of [chatId] whose state is collected, as by the screen. */
    protected fun TestScope.viewModel(
        chatId: Long = ChatFixtures.PRIVATE_CHAT,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        val viewModel =
            ChatViewModel(
                chatId = chatId,
                savedStateHandle = handle,
                chatRepository = chats,
                messageRepository = messages,
                userRepository = users,
                groupRepository = groups,
                chatActionRepository = actions,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
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

    protected val ChatViewModel.state get() = uiState.value

    protected val ChatViewModel.content get() = state.content as ChatContent.Content

    protected val ChatViewModel.items get() = content.items

    protected val ChatViewModel.bubbles get() = items.filterIsInstance<ChatItem.Bubble>()

    protected fun ChatViewModel.bubble(id: Long) = bubbles.single { it.id == id }

    protected val ChatViewModel.subtitle get() = state.header?.subtitle

    /** The items as short descriptions, newest first: `m<id>`, `s<id>` for service messages, dates and the divider. */
    protected fun ChatViewModel.outline(): List<String> =
        items.map { item ->
            when (item) {
                is ChatItem.Bubble -> "m${item.id}"
                is ChatItem.Service -> "s${item.id}"
                is ChatItem.DateSeparator -> "date:${LocalDate.ofEpochDay(item.epochDay)}"
                is ChatItem.UnreadDivider -> "unread"
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

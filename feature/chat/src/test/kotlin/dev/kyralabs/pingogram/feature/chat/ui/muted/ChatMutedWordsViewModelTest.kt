package dev.kyralabs.pingogram.feature.chat.ui.muted

import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.testing.FakeMutedWordsRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.MutedMessageEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Muted words in a conversation (FR-X21): groups only, while on with words, and the messages tapped open. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMutedWordsViewModelTest {
    private val words = FakeMutedWordsRepository(MutedWordSettings(enabled = true, words = persistentListOf("spoiler")))
    private val chats = FakeChatRepository().apply { setChats(groupChat, channelChat, chat()) }
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(chatId: Long = GROUP_CHAT): ChatMutedWordsViewModel {
        val viewModel = ChatMutedWordsViewModel(chatId, words, chats, users)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.filter.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a group collapses messages with the words, for the user`() =
        runTest {
            assertEquals(
                MutedWordsFilter(persistentListOf("spoiler"), ME, persistentSetOf()),
                viewModel().filter.value,
            )
        }

    @Test
    fun `channels and private chats never collapse a message`() =
        runTest {
            assertNull(viewModel(CHANNEL_CHAT).filter.value)
            assertNull(viewModel(PRIVATE_CHAT).filter.value)
        }

    @Test
    fun `nothing collapses while off or without words`() =
        runTest {
            val viewModel = viewModel()

            words.settings.value = MutedWordSettings(enabled = false, words = persistentListOf("spoiler"))
            runCurrent()
            assertNull(viewModel.filter.value)

            words.settings.value = MutedWordSettings(enabled = true)
            runCurrent()
            assertNull(viewModel.filter.value)
        }

    @Test
    fun `the route's events reach the conversation, but a muted message's tap comes here`() =
        runTest {
            val viewModel = viewModel()
            val passed = mutableListOf<ChatEvent>()
            val onEvent = viewModel.routing { passed += it }

            onEvent(ChatEvent.DismissMessageActions)
            onEvent(MutedMessageEvent.Show(42))
            runCurrent()

            assertEquals(listOf<ChatEvent>(ChatEvent.DismissMessageActions), passed)
            assertEquals(persistentSetOf(42L), viewModel.filter.value?.shown)
        }

    @Test
    fun `a message tapped open stays open`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(MutedMessageEvent.Show(42))
            runCurrent()

            assertEquals(persistentSetOf(42L), viewModel.filter.value?.shown)
        }
}

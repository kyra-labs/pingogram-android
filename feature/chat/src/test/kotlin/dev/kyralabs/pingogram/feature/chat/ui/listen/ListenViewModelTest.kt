package dev.kyralabs.pingogram.feature.chat.ui.listen

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.testing.FakeListenSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTextSpeaker
import dev.kyralabs.pingogram.core.testing.FakeUnreadPostsRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.ListenEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Listen to posts in a conversation (FR-X26): what "Listen" and "Listen to unread posts" read, where they are offered,
 * and the events the conversation hands on to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenViewModelTest {
    private val settings = FakeListenSettingsRepository()
    private val chats =
        FakeChatRepository().apply {
            setChats(groupChat, channelChat.copy(unreadCount = 3, lastReadInboxMessageId = 10))
        }
    private val messages =
        FakeMessageRepository().apply {
            onMessage = { chatId, id -> Outcome.Success(message(id, chatId, content = textContent("Post $id"))) }
        }
    private val unread =
        FakeUnreadPostsRepository(
            posts =
                mutableMapOf(
                    CHANNEL_CHAT to
                        listOf(
                            message(9, CHANNEL_CHAT, content = textContent("Read already")),
                            message(11, CHANNEL_CHAT, content = textContent("First")),
                            message(12, CHANNEL_CHAT, content = textContent("  ")),
                            message(13, CHANNEL_CHAT, content = textContent("Second")),
                        ),
                ),
        )
    private val speaker = FakeTextSpeaker()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun textContent(value: String) = MessageContent.Text(text(value), linkPreview = null)

    private fun TestScope.viewModel(chatId: Long = CHANNEL_CHAT): ListenViewModel {
        val texts = ListenTexts(chats, messages, unread)
        val viewModel =
            ListenViewModel(chatId, settings, chats, FakePlaybackSettingsRepository(), texts, speaker)
        val collecting = UnconfinedTestDispatcher(testScheduler)
        backgroundScope.launch(collecting) { viewModel.enabled.collect {} }
        backgroundScope.launch(collecting) { viewModel.canListenToUnread.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `listen reads the message's text`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(ListenEvent.Message(5))
            runCurrent()

            assertEquals(listOf("Post 5" to 1f), speaker.spoken)
            assertEquals(ListenPlayer(1, 1, isPaused = false, PlaybackSpeed.Normal), viewModel.player.value)
        }

    @Test
    fun `unread posts are read oldest first, those without text left out`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(ListenEvent.Unread)
            runCurrent()
            speaker.finish()
            runCurrent()

            assertEquals(listOf("First", "Second"), speaker.spoken.map { it.first })
        }

    @Test
    fun `a message without text is said to have nothing to read`() =
        runTest {
            messages.onMessage = { chatId, id -> Outcome.Success(message(id, chatId, content = textContent(" "))) }
            val viewModel = viewModel()

            viewModel.onEvent(ListenEvent.Message(5))
            runCurrent()

            assertEquals(ListenNotice.NothingToRead, viewModel.notices.first())
            assertEquals(0, speaker.opened)
        }

    @Test
    fun `only a channel with unread posts offers them, while the setting is on`() =
        runTest {
            assertTrue(viewModel().canListenToUnread.value)
            assertFalse(viewModel(GROUP_CHAT).canListenToUnread.value)

            settings.enabled.value = false
            val off = viewModel()

            assertFalse(off.canListenToUnread.value)
            assertFalse(off.enabled.value)
        }

    @Test
    fun `the pill's events reach the reader`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(ListenEvent.Unread)
            runCurrent()

            viewModel.onEvent(ListenEvent.Pause)
            runCurrent()
            assertEquals(true, viewModel.player.value?.isPaused)

            viewModel.onEvent(ListenEvent.Stop)
            runCurrent()
            assertNull(viewModel.player.value)
            assertEquals(1, speaker.closed)
        }
}

package dev.kyralabs.pingogram.feature.chat.ui.topics

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** What [ChatOpeningViewModel] opens a chat to (`REQUIREMENTS.md` FR-C9): a conversation, a forum's topics, a topic. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatOpeningViewModelTest {
    private val chats = FakeChatRepository()
    private val groups = FakeGroupRepository()
    private val messages = FakeMessageRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.opening(
        chatId: Long,
        messageId: Long? = null,
    ): ChatOpening {
        val viewModel = ChatOpeningViewModel(chatId, messageId, chats, groups, messages, users)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.opening.collect {} }
        runCurrent()
        return viewModel.opening.value
    }

    private fun forumIs(isForum: Boolean) {
        chats.setChats(forumChat)
        groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(10, isChannel = false, isForum = isForum))
    }

    @Test
    fun `a private chat and a channel open their conversation`() =
        runTest {
            chats.setChats(chat(), channelChat)

            assertEquals(ChatOpening.Conversation, opening(chat().id))
            assertEquals(ChatOpening.Conversation, opening(channelChat.id))
        }

    @Test
    fun `Saved Messages opens with its tabs`() =
        runTest {
            chats.setChats(chat(id = ME))

            assertEquals(ChatOpening.SavedMessages, opening(ME))
        }

    @Test
    fun `a forum opens its topics, a group without topics its conversation`() =
        runTest {
            forumIs(isForum = true)
            assertEquals(ChatOpening.Topics, opening(FORUM_CHAT))

            forumIs(isForum = false)
            assertEquals(ChatOpening.Conversation, opening(FORUM_CHAT))
        }

    @Test
    fun `a message found in a forum opens its topic`() =
        runTest {
            forumIs(isForum = true)
            messages.onMessage =
                { _, id -> Outcome.Success(message(id, chatId = FORUM_CHAT).copy(topic = MessageTopic.Forum(4))) }

            assertEquals(ChatOpening.Topic(MessageTopic.Forum(4)), opening(FORUM_CHAT, messageId = 12))
        }

    @Test
    fun `nothing is decided while the supergroup is unknown`() =
        runTest {
            chats.setChats(forumChat)

            assertEquals(ChatOpening.Unknown, opening(FORUM_CHAT))
        }
}

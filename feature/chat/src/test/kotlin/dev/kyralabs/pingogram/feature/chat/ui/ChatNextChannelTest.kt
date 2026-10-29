package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The next unread channel in [ChatViewModel] (`docs/DESIGN_SYSTEM.md` §6 "Next unread channel"): which channel a read
 * channel offers, when it offers none, and opening it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatNextChannelTest : ChatViewModelHarness() {
    private fun channel(
        id: Long,
        unread: Int,
    ) = chat(id = id, type = ChatType.Supergroup(id, isChannel = true), title = "Channel $id", unreadCount = unread)

    /** The conversation of [current], the main list holding [list], its next channel collected. */
    private fun TestScope.showing(
        current: Chat,
        vararg list: Chat,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(9))
        chats.setChats(current, *list)
        chats.mainList.value = listOf(current, *list)
        messages.setHistory(current.id, history(message(1, current.id)))
        val viewModel = viewModel(current.id, topic = topic)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.nextChannel.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a read channel offers the first other channel of the main list with unread posts`() =
        runViewModelTest {
            val viewModel = showing(channelChat, groupChat.copy(unreadCount = 3), channel(OTHER, 0), channel(NEXT, 4))

            assertEquals(NextChannel(NEXT, "Channel $NEXT", photo = null), viewModel.nextChannel.value)
        }

    @Test
    fun `the offer follows the list as channels are read`() =
        runViewModelTest {
            val viewModel = showing(channelChat, channel(NEXT, 4), channel(OTHER, 2))

            chats.mainList.value = listOf(channelChat, channel(NEXT, 0), channel(OTHER, 2))
            runCurrent()

            assertEquals(OTHER, viewModel.nextChannel.value?.chatId)
        }

    @Test
    fun `a channel with unread posts, a group and a thread offer none`() =
        runViewModelTest {
            assertNull(showing(channelChat.copy(unreadCount = 1), channel(NEXT, 4)).nextChannel.value)
            assertNull(showing(groupChat, channel(NEXT, 4)).nextChannel.value)
            assertNull(showing(channelChat, channel(NEXT, 4), topic = MessageTopic.Thread(1)).nextChannel.value)
        }

    @Test
    fun `a read channel with no unread one after it offers none`() =
        runViewModelTest {
            assertNull(showing(channelChat, channel(OTHER, 0)).nextChannel.value)
        }

    @Test
    fun `opening the offered channel opens it in this one's place, a stale one not`() =
        runViewModelTest {
            val viewModel = showing(channelChat, channel(NEXT, 4))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenNextChannel(OTHER), ChatEvent.OpenNextChannel(NEXT))
                assertEquals(ChatEffect.OpenNextChannel(NEXT), awaitItem())
                expectNoEvents()
            }
        }

    private companion object {
        const val NEXT = -300L
        const val OTHER = -400L
    }
}

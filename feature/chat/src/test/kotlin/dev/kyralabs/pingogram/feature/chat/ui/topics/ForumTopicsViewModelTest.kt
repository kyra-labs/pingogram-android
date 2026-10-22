package dev.kyralabs.pingogram.feature.chat.ui.topics

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeForumTopicRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import kotlinx.coroutines.CompletableDeferred
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
import java.util.Locale

/**
 * The topic list of [ForumTopicsViewModel] (`REQUIREMENTS.md` FR-C9): the header, the rows, the pages as the end comes
 * near, and the errors of the first and the next page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumTopicsViewModelTest {
    private val chats = FakeChatRepository()
    private val topics = FakeForumTopicRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ForumTopicsViewModel {
        val viewModel =
            ForumTopicsViewModel(
                chatId = FORUM_CHAT,
                chatRepository = chats,
                repository = topics,
                userRepository = users,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val ForumTopicsViewModel.content get() = uiState.value.content

    private val ForumTopicsViewModel.rows get() = (content as TopicsContent.Content).topics

    @Test
    fun `loads the first page after the first frame, and shows the forum's topics`() =
        runTest {
            chats.setChats(forumChat)
            users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
            val last = message(9, chatId = FORUM_CHAT, sender = MessageSender.User(ADA))
            topics.onLoadMore = {
                topics.setTopics(
                    FORUM_CHAT,
                    forumTopic(1, "General", isGeneral = true),
                    forumTopic(7, "Help", lastMessage = last, unreadCount = 2, color = TopicColor.Green),
                )
                Outcome.Success(Unit)
            }

            val viewModel = viewModel()

            assertEquals(listOf(FORUM_CHAT), topics.loadRequests)
            val help = viewModel.rows[1]
            assertEquals(
                listOf<Any?>("Help", 2, TopicIcon(TopicColor.Green, isGeneral = false)),
                listOf(help.name, help.unreadCount, help.icon),
            )
            assertEquals(UiText.Plain("Ada Lovelace").bidiIsolated(), help.sender)
            assertEquals(UiText.Plain("Message 9"), help.preview)
            assertEquals("9:41 AM", help.time)
            assertNull(viewModel.rows[0].preview)
            assertEquals(
                UiText.Plain("Compose Developers"),
                viewModel.uiState.value.header
                    ?.title,
            )
            assertEquals(
                ChatSubtitle.Status(UiText.plural(R.plurals.topics_subtitle, 2, 2)),
                viewModel.uiState.value.header
                    ?.subtitle,
            )
        }

    @Test
    fun `the user's own last message reads as theirs, the forum's own as nobody's`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(
                FORUM_CHAT,
                forumTopic(1, lastMessage = ChatFixtures.outgoing(3, chatId = FORUM_CHAT)),
                forumTopic(2, lastMessage = message(4, chatId = FORUM_CHAT, sender = MessageSender.Chat(FORUM_CHAT))),
            )

            val viewModel = viewModel()

            assertEquals(
                UiText.resource(dev.kyralabs.pingogram.core.ui.R.string.preview_sender_you),
                viewModel.rows[0].sender,
            )
            assertNull(viewModel.rows[1].sender)
        }

    @Test
    fun `shows the loading state until the first page is there`() =
        runTest {
            chats.setChats(forumChat)
            val page = CompletableDeferred<Outcome<Unit>>()
            topics.onLoadMore = { page.await() }

            val viewModel = viewModel()

            assertEquals(TopicsContent.Loading, viewModel.content)
            page.complete(Outcome.Success(Unit))
        }

    @Test
    fun `a forum without topics is empty`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT)

            assertEquals(TopicsContent.Empty, viewModel().content)
        }

    @Test
    fun `a failed first page shows the error, and a retry loads it`() =
        runTest {
            chats.setChats(forumChat)
            topics.onLoadMore = { Outcome.Failure(DomainError.FloodWait(30)) }
            val viewModel = viewModel()
            assertEquals(TopicsContent.Error(DomainError.FloodWait(30)), viewModel.content)

            topics.onLoadMore = {
                topics.setTopics(FORUM_CHAT, forumTopic(3))
                Outcome.Success(Unit)
            }
            viewModel.onEvent(ForumTopicsEvent.Retry)
            runCurrent()

            assertEquals(listOf(3), viewModel.rows.map { it.id })
        }

    @Test
    fun `the end coming near loads the next pages until every topic is loaded`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT, forumTopic(1), reachedEnd = false)
            var pages = 0
            topics.onLoadMore = {
                pages++
                topics.setTopics(FORUM_CHAT, *(1..pages).map { forumTopic(it) }.toTypedArray(), reachedEnd = pages >= 3)
                Outcome.Success(Unit)
            }
            val viewModel = viewModel()

            viewModel.onEvent(ForumTopicsEvent.NearEndChanged(true))
            runCurrent()

            assertEquals(3, pages)
            assertEquals(true, (viewModel.content as TopicsContent.Content).reachedEnd)
        }

    @Test
    fun `a failed next page shows below the topics`() =
        runTest {
            chats.setChats(forumChat)
            topics.setTopics(FORUM_CHAT, forumTopic(1), reachedEnd = false)
            val viewModel = viewModel()
            topics.onLoadMore = { Outcome.Failure(DomainError.Network) }

            viewModel.onEvent(ForumTopicsEvent.NearEndChanged(true))
            runCurrent()

            assertEquals(DomainError.Network, (viewModel.content as TopicsContent.Content).moreError)
        }
}

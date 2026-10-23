package dev.kyralabs.pingogram.feature.chats.ui.stories

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.testing.FakeStoryRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures
import dev.kyralabs.pingogram.feature.chats.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The story rings above the chat list: their order, names, read state, loading once and paging. */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryRingsViewModelTest {
    private val stories = FakeStoryRepository()
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StoryRingsViewModel(stories, chats, users)

    private fun active(
        chatId: Long,
        order: Long,
        maxRead: Int = 0,
        ids: List<Int> = listOf(1, 2),
        list: StoryList = StoryList.Main,
    ) = ActiveStories(chatId, list, order, maxRead, ids.map { StoryInfo(it, 0) }.toImmutableList())

    @Test
    fun `no rings without stories`() =
        runTest {
            viewModel().uiState.test {
                assertTrue(awaitItem().rings.isEmpty())
                expectNoEvents()
            }
        }

    @Test
    fun `rings follow the stories list's order with the chats' names and read state`() =
        runTest {
            chats.setChats(
                ChatListType.Main,
                ChatListFixtures.chat(id = 2, title = "Ada"),
                ChatListFixtures.chat(id = 3, title = "Grace"),
            )
            stories.setActive(active(chatId = 2, order = 5, maxRead = 2))
            stories.setActive(active(chatId = 3, order = 9, maxRead = 1))
            stories.setActive(active(chatId = 4, order = 7, list = StoryList.Archive))

            viewModel().uiState.test {
                skipItems(1)
                val rings = awaitItem().rings
                assertEquals(listOf("Grace", "Ada"), rings.map { it.name })
                assertEquals(listOf(true, false), rings.map { it.hasUnread })
                assertEquals(listOf(2, 2), rings.map { it.storyCount })
            }
        }

    @Test
    fun `a story seen in the viewer turns its ring seen`() =
        runTest {
            chats.setChats(ChatListType.Main, ChatListFixtures.chat(id = 2, title = "Ada"))
            stories.setActive(active(chatId = 2, order = 5, maxRead = 0))

            viewModel().uiState.test {
                skipItems(1)
                assertTrue(awaitItem().rings.single().hasUnread)
                stories.setActive(active(chatId = 2, order = 5, maxRead = 2))
                assertEquals(false, awaitItem().rings.single().hasUnread)
            }
        }

    @Test
    fun `the user's own ring is theirs, and a chat not announced yet waits`() =
        runTest {
            users.myUserId.value = ChatListFixtures.ME
            chats.setChats(ChatListType.Main, ChatListFixtures.chat(id = ChatListFixtures.ME, title = "Me"))
            stories.setActive(active(chatId = ChatListFixtures.ME, order = 10))
            stories.setActive(active(chatId = 8, order = 3))

            viewModel().uiState.test {
                skipItems(1)
                val ring = awaitItem().rings.single()
                assertEquals(ChatListFixtures.ME, ring.chatId)
                assertTrue(ring.isMine)
            }
        }

    @Test
    fun `loads the first chats with stories once the rings are collected`() =
        runTest {
            val viewModel = viewModel()
            runCurrent()
            assertTrue(stories.calls.isEmpty())

            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                assertEquals(listOf("load:Main"), stories.calls)
            }
        }

    @Test
    fun `the row's end loads more, one request at a time, until all are loaded`() =
        runTest {
            val answer = CompletableDeferred<Outcome<Boolean>>()
            stories.onLoadMore = { answer.await() }
            val viewModel = viewModel()

            viewModel.onEvent(StoryRingsEvent.NearEnd)
            viewModel.onEvent(StoryRingsEvent.NearEnd)
            runCurrent()
            assertEquals(listOf("loadMore:Main"), stories.calls)

            answer.complete(Outcome.Success(false))
            runCurrent()
            viewModel.onEvent(StoryRingsEvent.NearEnd)
            runCurrent()
            assertEquals(listOf("loadMore:Main"), stories.calls)
        }

    @Test
    fun `a failed page leaves the rings and loads again when the end comes near again`() =
        runTest {
            stories.onLoadMore = { Outcome.Failure(DomainError.FloodWait(5)) }
            val viewModel = viewModel()

            viewModel.onEvent(StoryRingsEvent.NearEnd)
            runCurrent()
            stories.onLoadMore = { Outcome.Failure(DomainError.Network) }
            viewModel.onEvent(StoryRingsEvent.NearEnd)
            runCurrent()
            stories.onLoadMore = { Outcome.Failure(DomainError.Unauthorized) }
            viewModel.onEvent(StoryRingsEvent.NearEnd)
            runCurrent()

            assertEquals(3, stories.calls.count { it == "loadMore:Main" })
            assertTrue(
                viewModel.uiState.value.rings
                    .isEmpty(),
            )
        }
}

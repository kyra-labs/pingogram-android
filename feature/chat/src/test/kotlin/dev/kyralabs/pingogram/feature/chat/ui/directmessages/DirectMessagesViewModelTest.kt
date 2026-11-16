package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.testing.FakeDirectMessagesTopicRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * A channel's direct messages as its administrators see them ([DirectMessagesViewModel], the owner's board 7b): the
 * header, the rows, the filters and the search, the pages, marking read and unread, failures and the saved state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DirectMessagesViewModelTest {
    private val chats = FakeChatRepository()
    private val topics = FakeDirectMessagesTopicRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        chats.setChats(
            chat(DM_CHAT, type = ChatType.Supergroup(DM_GROUP, isChannel = false), title = "Kyra Labs Updates"),
        )
        users.setUsers(
            user(ADA, firstName = "Ada", lastName = "Lovelace"),
            user(GRACE, firstName = "Grace", lastName = "Hopper"),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): DirectMessagesViewModel {
        val viewModel =
            DirectMessagesViewModel(
                chatId = DM_CHAT,
                chatRepository = chats,
                repository = topics,
                userRepository = users,
                savedState = handle,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun topic(
        id: Long,
        sender: Long,
        order: Long = id,
        unread: Long = 0,
        paid: Boolean = false,
        markedUnread: Boolean = false,
        last: Message? = message(id * 10, chatId = DM_CHAT, sender = MessageSender.User(sender)),
    ) = DirectMessagesTopic(
        chatId = DM_CHAT,
        id = id,
        sender = MessageSender.User(sender),
        order = order,
        isPaid = paid,
        isMarkedAsUnread = markedUnread,
        unreadCount = unread,
        lastReadInboxMessageId = 0,
        lastMessage = last,
    )

    private val DirectMessagesViewModel.rows get() = (uiState.value.content as DirectContent.Rows).rows

    private fun TestScope.send(
        viewModel: DirectMessagesViewModel,
        event: DirectMessagesEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    @Test
    fun `loads the first page and shows who wrote, by TDLib's order, under the channel`() =
        runTest {
            topics.topicList.value =
                listOf(topic(1, ADA, order = 5, unread = 2, paid = true), topic(2, GRACE, order = 9))

            val viewModel = viewModel()

            assertEquals(listOf("load:$DM_CHAT"), topics.calls)
            assertEquals(
                listOf(UiText.Plain("Grace Hopper"), UiText.Plain("Ada Lovelace")),
                viewModel.rows.map { it.name },
            )
            val ada = viewModel.rows[1]
            assertEquals(
                listOf<Any?>(2, true, true, "9:41 AM"),
                listOf(ada.unreadCount, ada.isUnread, ada.isPaid, ada.time),
            )
            assertEquals(UiText.Plain("Message 10"), ada.preview)
            val header = viewModel.uiState.value.header
            assertEquals(UiText.Plain("Kyra Labs Updates"), header?.title)
            val wrote = UiText.plural(R.plurals.direct_subtitle_people, 2, 2)
            assertEquals(
                ChatSubtitle.Status(UiText.plural(R.plurals.direct_subtitle_unread, 1, wrote, 1)),
                header?.subtitle,
            )
            assertEquals(2, viewModel.uiState.value.total)
            assertEquals(1, viewModel.uiState.value.unread)
        }

    @Test
    fun `the filters keep the unread and the paid conversations`() =
        runTest {
            topics.topicList.value =
                listOf(topic(1, ADA, unread = 1), topic(2, GRACE, paid = true), topic(3, ME, markedUnread = true))
            val viewModel = viewModel()

            send(viewModel, DirectMessagesEvent.FilterChanged(DirectFilter.Unread))
            assertEquals(listOf(3L, 1L), viewModel.rows.map { it.topicId })

            send(viewModel, DirectMessagesEvent.FilterChanged(DirectFilter.Paid))
            assertEquals(listOf(2L), viewModel.rows.map { it.topicId })
        }

    @Test
    fun `the search matches the senders' names, and nobody matching says so`() =
        runTest {
            topics.topicList.value = listOf(topic(1, ADA), topic(2, GRACE))
            val viewModel = viewModel()

            send(viewModel, DirectMessagesEvent.QueryChanged("hop"))
            assertEquals(listOf(2L), viewModel.rows.map { it.topicId })

            send(viewModel, DirectMessagesEvent.QueryChanged("turing"))
            assertEquals(DirectContent.Empty(isFiltered = true), viewModel.uiState.value.content)
        }

    @Test
    fun `nobody wrote yet is the empty state`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(DirectContent.Empty(isFiltered = false), viewModel.uiState.value.content)
        }

    @Test
    fun `more load near the end while there are more, and not after the last page`() =
        runTest {
            topics.topicList.value = listOf(topic(1, ADA))
            topics.loadAnswer = Outcome.Success(true)
            val viewModel = viewModel()

            send(viewModel, DirectMessagesEvent.LoadMore)
            topics.loadAnswer = Outcome.Success(false)
            send(viewModel, DirectMessagesEvent.LoadMore)
            send(viewModel, DirectMessagesEvent.LoadMore)

            assertEquals(3, topics.calls.count { it.startsWith("load:") })
        }

    @Test
    fun `a first page that fails shows its error, and retry asks again`() =
        runTest {
            topics.loadAnswer = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            assertEquals(DirectContent.Failed(DomainError.Network), viewModel.uiState.value.content)

            topics.loadAnswer = Outcome.Success(false)
            topics.topicList.value = listOf(topic(1, ADA))
            send(viewModel, DirectMessagesEvent.Retry)

            assertEquals(listOf(1L), viewModel.rows.map { it.topicId })
        }

    @Test
    fun `marking a conversation unread and read again changes it, a refusal is a snackbar`() =
        runTest {
            topics.topicList.value = listOf(topic(1, ADA))
            val viewModel = viewModel()

            send(viewModel, DirectMessagesEvent.MarkUnread(1, unread = true))
            assertTrue(viewModel.rows.single().isUnread)
            assertTrue("unread:$DM_CHAT:1:true" in topics.calls)

            topics.markAnswer = Outcome.Failure(DomainError.AdminRightsRequired)
            viewModel.effects.test {
                send(viewModel, DirectMessagesEvent.MarkUnread(1, unread = false))
                assertEquals(DomainError.AdminRightsRequired, awaitItem())
            }
        }

    @Test
    fun `the filter and the search survive process death`() =
        runTest {
            topics.topicList.value = listOf(topic(1, ADA, paid = true), topic(2, GRACE, paid = true))
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            send(first, DirectMessagesEvent.FilterChanged(DirectFilter.Paid))
            send(first, DirectMessagesEvent.QueryChanged("ada"))

            val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

            assertEquals(DirectFilter.Paid, restored.uiState.value.filter)
            assertEquals(listOf(1L), restored.rows.map { it.topicId })
        }

    private companion object {
        const val DM_CHAT = -1_000_000_000_777L
        const val DM_GROUP = 777L
    }
}

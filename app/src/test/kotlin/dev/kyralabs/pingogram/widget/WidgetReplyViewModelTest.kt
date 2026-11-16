package dev.kyralabs.pingogram.widget

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The small reply sheet a Conversation widget's Reply opens ([WidgetReplyViewModel], board 33). */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetReplyViewModelTest {
    private val sentTexts = mutableListOf<String>()
    private var answer: Outcome<Unit> = Outcome.Success(Unit)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): WidgetReplyViewModel =
        WidgetReplyViewModel(
            chatId = TRIP,
            chat = MutableStateFlow(chat()),
            myUserId = MutableStateFlow(ME),
            sendText = { text ->
                sentTexts += text.text
                answer
            },
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            runCurrent()
        }

    @Test
    fun `names the chat it replies to`() =
        runTest {
            val state = viewModel().state.value

            assertEquals("Trip", state.title)
            assertEquals(AvatarShape.Group, state.shape)
        }

    @Test
    fun `sends the text typed, trimmed, then says it was sent`() =
        runTest {
            val viewModel = viewModel()

            viewModel.send("  Booked it!  ")
            runCurrent()

            assertEquals(listOf("Booked it!"), sentTexts)
            assertEquals(Unit, viewModel.sent.first())
            assertFalse(viewModel.state.value.isSending)
        }

    @Test
    fun `blank text sends nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.send("   ")
            runCurrent()

            assertEquals(emptyList<String>(), sentTexts)
        }

    @Test
    fun `a refusal says why and sends again on the next tap`() =
        runTest {
            answer = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()

            viewModel.send("Hi")
            runCurrent()
            assertEquals(DomainError.Network, viewModel.state.value.error)

            answer = Outcome.Success(Unit)
            viewModel.send("Hi")
            runCurrent()

            assertEquals(listOf("Hi", "Hi"), sentTexts)
            assertNull(viewModel.state.value.error)
        }

    private fun chat() =
        Chat(
            id = TRIP,
            type = ChatType.BasicGroup(basicGroupId = 5),
            title = "Trip",
            photo = null,
            lastMessage = null,
            positions = persistentListOf(),
            unreadCount = 0,
            unreadMentionCount = 0,
            unreadReactionCount = 0,
            lastReadInboxMessageId = 0,
            lastReadOutboxMessageId = 0,
            isMarkedAsUnread = false,
            isMuted = false,
            draft = null,
            hasProtectedContent = false,
            messageAutoDeleteTime = 0,
            permissions = ChatPermissions.All,
        )

    private companion object {
        const val ME = 1L
        const val TRIP = -100L
    }
}

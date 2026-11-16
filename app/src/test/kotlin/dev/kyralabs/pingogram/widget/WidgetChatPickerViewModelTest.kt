package dev.kyralabs.pingogram.widget

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.User
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Conversation widget's chat picker ([WidgetChatPickerViewModel], board 33). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WidgetChatPickerViewModelTest {
    private val chosen = mutableListOf<Triple<Int, Int, Long>>()
    private val chats = MutableStateFlow(listOf(chat(TRIP, "Trip — Coorg", group), chat(CAFE, "Café crew", group)))

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(locked: Set<Long> = emptySet()): WidgetChatPickerViewModel =
        WidgetChatPickerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("appWidgetId" to WIDGET)),
            sources =
                WidgetPickerSources(
                    chats = chats,
                    users = MutableStateFlow(emptyMap<Long, User>()),
                    myUserId = MutableStateFlow(ME),
                    lockedChatIds = MutableStateFlow(locked),
                    forums = MutableStateFlow(emptySet()),
                ),
            activeSlot = MutableStateFlow(SLOT),
            chooser = { widget, slot, chatId ->
                chosen += Triple(widget, slot, chatId)
                true
            },
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            runCurrent()
        }

    @Test
    fun `lists the main list's chats without secret and locked ones, Saved Messages by its name`() =
        runTest {
            chats.value +=
                listOf(
                    chat(SECRET, "Meera", ChatType.Secret(secretChatId = 1, userId = 9)),
                    chat(LOCKED, "Diary", ChatType.Private(userId = LOCKED)),
                    chat(ME, "Me", ChatType.Private(userId = ME)),
                )

            val viewModel = viewModel(locked = setOf(LOCKED))

            val shown =
                viewModel.state.value.chats
                    .orEmpty()
            assertEquals(listOf(TRIP, CAFE, ME), shown.map { it.id })
            assertEquals(AvatarShape.Group, shown.first().shape)
            assertEquals(AvatarShape.SavedMessages, shown.last().shape)
            assertEquals(UiR.string.chat_saved_messages, shown.last().titleRes)
        }

    @Test
    fun `filters by the start of a word, in any case and without accents`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChange("cafe")
            runCurrent()

            assertEquals(
                listOf(CAFE),
                viewModel.state.value.chats
                    ?.map { it.id },
            )
        }

    @Test
    fun `picking a chat keeps it in the widget with the shown account, then closes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onPick(TRIP)
            runCurrent()

            assertEquals(listOf(Triple(WIDGET, SLOT, TRIP)), chosen)
            assertEquals(Unit, viewModel.closed.first())
        }

    private fun chat(
        id: Long,
        title: String,
        type: ChatType,
    ) = Chat(
        id = id,
        type = type,
        title = title,
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
        const val WIDGET = 42
        const val SLOT = 1
        const val ME = 1L
        const val TRIP = -100L
        const val CAFE = -200L
        const val SECRET = -300L
        const val LOCKED = 7L
        val group = ChatType.BasicGroup(basicGroupId = 5)
    }
}

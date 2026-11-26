package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.core.testing.FakeLocalChatNamesRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredDismissalRepository
import dev.kyralabs.pingogram.core.testing.FakeUnansweredSettingsRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeGroupRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chats.ui.SearchRowInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

/**
 * [UnansweredViewModel] (`REQUIREMENTS.md` FR-X22): the unanswered chats as rows (who counts is
 * `UnansweredChatsTest`'s); Dismiss keeps a chat off until a newer message; Reply opens the chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UnansweredViewModelTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val dismissals = FakeUnansweredDismissalRepository()
    private val settings = FakeUnansweredSettingsRepository(UnansweredSettings(afterDays = 3))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): UnansweredViewModel {
        val viewModel =
            UnansweredViewModel(
                unansweredChats =
                    UnansweredChats(
                        chats,
                        users,
                        FakeChatLockRepository(locked = setOf(LOCKED)),
                        dismissals,
                        settings,
                        FakeWallClock(NOW),
                    ),
                dismissals = dismissals,
                rowInputs = SearchRowInputs(users, FakeGroupRepository(), FakeLocalChatNamesRepository()),
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val UnansweredViewModel.chatIds
        get() = (uiState.value as UnansweredUiState.Content).chats.map { it.chatId }

    /** Private chat [id] with a person whose last message, theirs unless [outgoing], came [ago]. */
    private fun privateChat(
        id: Long,
        ago: Duration,
        outgoing: Boolean = false,
    ) = chat(
        id = id,
        type = ChatType.Private(userId = id),
        lastMessage =
            message(
                chatId = id,
                id = id * 10,
                sender = MessageSender.User(if (outgoing) ME else id),
                date = NOW.minus(ago.toJavaDuration()),
                isOutgoing = outgoing,
            ),
    )

    private fun lists() {
        users.setUsers(
            user(ADA),
            user(BOB),
            user(CAROL),
            user(BOT, type = UserType.Bot),
            user(OLD),
            user(LOCKED),
            user(EVE),
            user(ME),
        )
        chats.setChats(
            ChatListType.Main,
            privateChat(ADA, ago = 4.days),
            privateChat(BOB, ago = 1.days),
            privateChat(CAROL, ago = 5.days, outgoing = true),
            privateChat(BOT, ago = 5.days),
            privateChat(OLD, ago = 40.days),
            privateChat(LOCKED, ago = 5.days),
            privateChat(ME, ago = 6.days),
            chat(
                id = GROUP,
                type = ChatType.BasicGroup(1),
                lastMessage = message(chatId = GROUP, date = NOW.minus(5.days.toJavaDuration())),
            ),
            privateChat(EVE, ago = 10.days),
        )
    }

    @Test
    fun `lists the people who wrote last and waited the chosen days, the longest waiting first`() =
        runTest {
            lists()

            assertEquals(listOf(EVE, ADA), viewModel().chatIds)
        }

    @Test
    fun `a dismissed chat leaves until a newer message comes`() =
        runTest {
            lists()
            val viewModel = viewModel()

            viewModel.onEvent(UnansweredEvent.Dismiss(ADA, ADA * 10))
            runCurrent()
            assertEquals(listOf(EVE), viewModel.chatIds)

            val newer = privateChat(ADA, ago = 3.days)
            chats.setChats(ChatListType.Main, newer.copy(lastMessage = newer.lastMessage?.copy(id = NEWER)))
            runCurrent()

            assertEquals(listOf(ADA), viewModel.chatIds)
        }

    @Test
    fun `reply opens the chat`() =
        runTest {
            val viewModel = viewModel()
            val effects = mutableListOf<UnansweredEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(UnansweredEvent.Reply(ADA))
            runCurrent()

            assertEquals(listOf(UnansweredEffect.Reply(ADA)), effects)
        }

    private companion object {
        const val ADA = 2L
        const val BOB = 3L
        const val CAROL = 4L
        const val BOT = 5L
        const val OLD = 6L
        const val LOCKED = 7L
        const val EVE = 8L
        const val GROUP = -100L
        const val NEWER = 999L
    }
}

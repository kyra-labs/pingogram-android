package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import dev.kyralabs.pingogram.core.testing.FakeChatOpeningsRepository
import dev.kyralabs.pingogram.core.testing.FakeCleanupSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeLocalChatNamesRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The Cleanup suggestions page (`REQUIREMENTS.md` FR-X24): one chat at a time, the longest unopened first; Mute,
 * Archive, Leave after a confirmation, and Not now act on that chat only, which then leaves the page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CleanupViewModelTest {
    private val list = MutableStateFlow(listOf(group(GROUP), group(CHANNEL, channel = true)))
    private val openings = FakeChatOpeningsRepository(ChatOpenings(since = BEGUN), now = NOW)
    private val settings = FakeCleanupSettingsRepository()
    private val done = mutableListOf<String>()
    private var failing = false
    private val leavable = setOf(CHANNEL)

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private fun record(what: String): Outcome<Unit> {
        if (failing) return Outcome.Failure(DomainError.Network)
        done += what
        return Outcome.Success(Unit)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): CleanupViewModel {
        val chats =
            CleanupChats(
                CleanupSources(
                    chats = list,
                    users = MutableStateFlow(emptyMap()),
                    me = MutableStateFlow(ME),
                    locked = MutableStateFlow(emptySet()),
                    openings = openings.openings,
                ),
                settings.settings,
                clock,
            )
        val actions =
            CleanupActions(
                mute = { record("mute $it") },
                archive = { record("archive $it") },
                canLeave = { it in leavable },
                leave = { record("leave $it") },
                keep = { chatId ->
                    openings.markOpened(chatId).also { if (it is Outcome.Success) done += "keep $chatId" }
                },
            )
        val names = FakeLocalChatNamesRepository(persistentMapOf(GROUP to "Book club"))
        return CleanupViewModel(chats, actions, settings, openings, names)
            .also { viewModel ->
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
                runCurrent()
            }
    }

    private fun TestScope.effectsOf(viewModel: CleanupViewModel): List<CleanupEffect> {
        val effects = mutableListOf<CleanupEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `the first chat, by the user's own name, never opened, with one more after it and no Leave for it`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(CleanupSettings(), state.settings)
            assertEquals(GROUP, state.suggestion?.chatId)
            assertEquals("Book club", state.suggestion?.title)
            assertTrue(state.suggestion?.neverOpened == true)
            assertFalse(state.suggestion?.canLeave == true)
            assertEquals(1, state.remaining)
            assertFalse(state.loading)
        }

    @Test
    fun `muting keeps the chat as opened now, archiving archives the next, and each says so`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(CleanupEvent.Mute)
            runCurrent()
            assertEquals(
                CHANNEL,
                viewModel.uiState.value.suggestion
                    ?.chatId,
            )
            viewModel.onEvent(CleanupEvent.Archive)
            runCurrent()

            assertEquals(listOf("mute $GROUP", "keep $GROUP", "archive $CHANNEL"), done)
            assertEquals(
                listOf(CleanupEffect.Done(CleanupAction.Muted), CleanupEffect.Done(CleanupAction.Archived)),
                effects,
            )
            assertEquals(null, viewModel.uiState.value.suggestion)
        }

    @Test
    fun `leave asks first, only where it is offered, and leaves once confirmed`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(CleanupEvent.NotNow)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.suggestion
                    ?.canLeave == true,
            )

            viewModel.onEvent(CleanupEvent.Leave)
            assertTrue(viewModel.uiState.value.isConfirmingLeave)
            viewModel.onEvent(CleanupEvent.DismissLeave)
            assertFalse(viewModel.uiState.value.isConfirmingLeave)
            viewModel.onEvent(CleanupEvent.Leave)
            viewModel.onEvent(CleanupEvent.ConfirmLeave)
            runCurrent()

            assertEquals(listOf("keep $GROUP", "leave $CHANNEL"), done)
        }

    @Test
    fun `a choice that fails is a snackbar and the chat stays`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            failing = true

            viewModel.onEvent(CleanupEvent.Archive)
            runCurrent()

            assertEquals(listOf<CleanupEffect>(CleanupEffect.ShowError(DomainError.Network)), effects)
            assertEquals(
                GROUP,
                viewModel.uiState.value.suggestion
                    ?.chatId,
            )
            assertFalse(viewModel.uiState.value.isActing)
        }

    @Test
    fun `its settings save and show, and turned off it suggests nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(CleanupEvent.SetMonths(6))
            viewModel.onEvent(CleanupEvent.SetEnabled(false))
            runCurrent()

            assertEquals(CleanupSettings(enabled = false, months = 6), viewModel.uiState.value.settings)
            assertEquals(null, viewModel.uiState.value.suggestion)
        }

    private fun group(
        id: Long,
        channel: Boolean = false,
    ) = Chat(
        id = id,
        type = ChatType.Supergroup(supergroupId = -id, isChannel = channel),
        title = "Chat $id",
        photo = null,
        lastMessage = null,
        positions = persistentListOf(ChatPosition(ChatListType.Main, order = -id, isPinned = false)),
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
        const val GROUP = -100L
        const val CHANNEL = -200L
        val BEGUN: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val NOW: Instant = Instant.parse("2026-11-26T09:00:00Z")
    }
}

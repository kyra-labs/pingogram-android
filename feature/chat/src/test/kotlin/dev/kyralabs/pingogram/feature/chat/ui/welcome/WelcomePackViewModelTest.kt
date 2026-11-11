package dev.kyralabs.pingogram.feature.chat.ui.welcome

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.repository.WelcomeMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.WelcomePackRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The welcome pack a new member sees (the owner's board 6, [WelcomePackViewModel]). */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomePackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val chats = FakeChatRepository()
    private val messages = FakeWelcomeMessages()
    private val packs = FakeWelcomePacks()
    private val savedState = SavedStateHandle()

    private val hello = WelcomeMessage(1, FormattedText("Welcome! Read the rules.", persistentListOf()))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): WelcomePackViewModel {
        val viewModel = WelcomePackViewModel(GROUP, handle, chats, messages, packs)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pack.collect {} }
        runCurrent()
        return viewModel
    }

    private fun group(hasWelcomeMessages: Boolean = true) =
        chat(id = GROUP, type = ChatType.Supergroup(SUPERGROUP, isChannel = false), title = "Beta")
            .copy(hasWelcomeMessages = hasWelcomeMessages)

    @Test
    fun `a group with welcome messages shows its pack in full`() =
        runTest(dispatcher) {
            chats.setChats(group())
            messages.loaded.value = Outcome.Success(persistentListOf(hello))

            val pack = viewModel().pack.value

            assertEquals(listOf("Welcome! Read the rules."), pack?.messages?.map { it.text.text })
            assertEquals(false, pack?.collapsed)
        }

    @Test
    fun `nothing is asked where the group has none, or in a channel`() =
        runTest(dispatcher) {
            chats.setChats(group(hasWelcomeMessages = false))
            assertNull(viewModel().pack.value)
            assertEquals(0, messages.asked)

            chats.setChats(group().copy(type = ChatType.Supergroup(SUPERGROUP, isChannel = true)))
            assertNull(viewModel().pack.value)
            assertEquals(0, messages.asked)
        }

    @Test
    fun `Got it collapses the pack for good, and a tap opens it again for a while`() =
        runTest(dispatcher) {
            chats.setChats(group())
            messages.loaded.value = Outcome.Success(persistentListOf(hello))
            val viewModel = viewModel()

            viewModel.onEvent(WelcomePackEvent.Collapse)
            runCurrent()
            assertEquals(true, viewModel.pack.value?.collapsed)
            assertTrue(GROUP in packs.collapsed.value)

            viewModel.onEvent(WelcomePackEvent.Expand)
            runCurrent()
            assertEquals(false, viewModel.pack.value?.collapsed)
        }

    @Test
    fun `a pack collapsed before shows collapsed, and messages that cannot load show nothing`() =
        runTest(dispatcher) {
            chats.setChats(group())
            packs.collapsed.value = setOf(GROUP)
            messages.loaded.value = Outcome.Success(persistentListOf(hello))
            assertEquals(true, viewModel().pack.value?.collapsed)

            messages.loaded.value = Outcome.Failure(DomainError.Network)
            assertNull(viewModel(SavedStateHandle()).pack.value)
        }

    private companion object {
        const val GROUP = -1_000_000_000_500L
        const val SUPERGROUP = 500L
    }
}

private class FakeWelcomeMessages : WelcomeMessageRepository {
    val loaded = MutableStateFlow<Outcome<ImmutableList<WelcomeMessage>>>(Outcome.Success(persistentListOf()))
    var asked = 0

    override fun messages(chatId: Long): Flow<Outcome<ImmutableList<WelcomeMessage>>> {
        asked++
        return loaded
    }

    override suspend fun countMax(): Int = 5

    override suspend fun add(
        chatId: Long,
        draft: WelcomeDraft,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun edit(
        chatId: Long,
        id: Int,
        draft: WelcomeDraft,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun delete(
        chatId: Long,
        id: Int,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun deleteAll(chatId: Long): Outcome<Unit> = Outcome.Success(Unit)
}

private class FakeWelcomePacks : WelcomePackRepository {
    val collapsed = MutableStateFlow<Set<Long>>(emptySet())

    override fun isCollapsed(chatId: Long): Flow<Boolean> = collapsed.map { chatId in it }

    override suspend fun collapse(chatId: Long): Outcome<Unit> {
        collapsed.value += chatId
        return Outcome.Success(Unit)
    }
}

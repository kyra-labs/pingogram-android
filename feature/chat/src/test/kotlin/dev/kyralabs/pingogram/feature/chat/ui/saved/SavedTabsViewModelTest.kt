package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTopic
import dev.kyralabs.pingogram.core.domain.model.SavedTopicKind
import dev.kyralabs.pingogram.core.testing.FakeSavedMessagesTopicRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Saved Messages' tabs: when they show, their names, loading the topics, and the selection in the saved state. */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedTabsViewModelTest {
    private val topics = FakeSavedMessagesTopicRepository()
    private val chats = FakeChatRepository()
    private val handle = SavedStateHandle()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun topic(
        id: Long,
        kind: SavedTopicKind = SavedTopicKind.SavedFrom(id * 10),
    ) = SavedMessagesTopic(id, kind, isPinned = false, order = id, lastMessage = null)

    private fun TestScope.viewModel(): SavedTabsViewModel {
        val viewModel = SavedTabsViewModel(handle, topics, chats)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `no tabs with fewer than two topics`() =
        runTest {
            topics.set(topic(1, SavedTopicKind.MyNotes))

            assertTrue(
                viewModel()
                    .uiState.value.tabs
                    .isEmpty(),
            )
        }

    @Test
    fun `All first, then each topic named by its kind or its chat's title`() =
        runTest {
            chats.setChats(chat(id = 20, title = "Android Developers"))
            topics.set(topic(1, SavedTopicKind.MyNotes), topic(2), topic(3, SavedTopicKind.AuthorHidden), topic(4))

            val tabs = viewModel().uiState.value.tabs

            assertEquals(listOf(null, 1L, 2L, 3L, 4L), tabs.map { it.topicId })
            assertEquals(
                listOf(
                    UiText.resource(R.string.saved_tab_all),
                    UiText.resource(R.string.saved_tab_my_notes),
                    UiText.Plain("Android Developers"),
                    UiText.resource(R.string.saved_tab_author_hidden),
                    UiText.resource(R.string.saved_tab_unknown_chat),
                ),
                tabs.map { it.title },
            )
        }

    @Test
    fun `loads the first topics once collected, then more until all are loaded`() =
        runTest {
            topics.loadAnswer = Outcome.Success(true)
            val viewModel = viewModel()
            assertEquals(1, topics.loads)

            topics.loadAnswer = Outcome.Success(false)
            viewModel.loadMore()
            runCurrent()
            viewModel.loadMore()
            runCurrent()

            assertEquals(2, topics.loads)
        }

    @Test
    fun `a failed load is asked again`() =
        runTest {
            topics.loadAnswer = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()

            viewModel.loadMore()
            runCurrent()

            assertEquals(2, topics.loads)
        }

    @Test
    fun `the selection survives in the saved state, and goes back to All when its topic leaves`() =
        runTest {
            topics.set(topic(1, SavedTopicKind.MyNotes), topic(2))
            viewModel().select(2)
            runCurrent()

            val restored = viewModel()
            assertEquals(2L, restored.uiState.value.selectedTopicId)

            topics.set(topic(1, SavedTopicKind.MyNotes), topic(3))
            runCurrent()
            assertNull(restored.uiState.value.selectedTopicId)
        }
}

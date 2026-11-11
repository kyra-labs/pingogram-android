package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.FORUM_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.forumTopic
import dev.kyralabs.pingogram.feature.chat.ui.FakeForumTopicRepository
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

/** A forum's topics as tabs ([ForumTabsViewModel]): their order, loading, the selection and process death. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumTabsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val topics = FakeForumTopicRepository()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        initialTopicId: Int? = null,
        handle: SavedStateHandle = savedState,
    ): ForumTabsViewModel {
        val viewModel = ForumTabsViewModel(FORUM_CHAT, initialTopicId, handle, topics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val ForumTabsViewModel.titles get() = uiState.value.tabs.map { (it.title as? UiText.Plain)?.text ?: "All" }

    @Test
    fun `All, then General, the pinned topics and the others, loading the first page`() =
        runTest(dispatcher) {
            topics.setTopics(
                FORUM_CHAT,
                forumTopic(3, "Releases"),
                forumTopic(2, "Bugs", isPinned = true),
                forumTopic(1, "General", isGeneral = true),
            )

            val viewModel = viewModel()

            assertEquals(listOf("All", "General", "Bugs", "Releases"), viewModel.titles)
            assertNull(viewModel.uiState.value.selectedTopicId)
            assertEquals(listOf(FORUM_CHAT), topics.loadRequests)
        }

    @Test
    fun `a General topic hidden from members is left out`() =
        runTest(dispatcher) {
            topics.setTopics(FORUM_CHAT, forumTopic(1, "General", isGeneral = true, isHidden = true), forumTopic(5))

            assertEquals(listOf("All", "Topic 5"), viewModel().titles)
        }

    @Test
    fun `opens at a found message's topic, and a selection survives process death`() =
        runTest(dispatcher) {
            topics.setTopics(FORUM_CHAT, forumTopic(4), forumTopic(5))
            val first = viewModel(initialTopicId = 4)
            assertEquals(4L, first.uiState.value.selectedTopicId)

            first.select(5)
            runCurrent()
            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
            assertEquals(5L, viewModel(initialTopicId = 4, handle = restored).uiState.value.selectedTopicId)
        }

    @Test
    fun `a topic that leaves gives the selection back to All, and more load near the end`() =
        runTest(dispatcher) {
            topics.setTopics(FORUM_CHAT, forumTopic(4), reachedEnd = false)
            val viewModel = viewModel()
            viewModel.select(4)
            runCurrent()

            topics.setTopics(FORUM_CHAT, forumTopic(5), reachedEnd = false)
            runCurrent()
            assertNull(viewModel.uiState.value.selectedTopicId)

            viewModel.loadMore()
            runCurrent()
            assertEquals(2, topics.loadRequests.size)
        }
}

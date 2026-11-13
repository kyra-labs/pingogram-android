package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTag
import dev.kyralabs.pingogram.core.domain.model.TaggedMessages
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTagRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Saved Messages' tags ([SavedTagsViewModel], the owner's board 8): the chips, filtering by a tag with its pages, a tag
 * that goes away, renaming and Telegram's refusal, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedTagsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSavedMessagesTagRepository()
    private val ideas = SavedMessagesTag(ReactionType.Emoji("❤"), "Ideas", 3)
    private val later = SavedMessagesTag(ReactionType.Emoji("👍"), "", 7)
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): SavedTagsViewModel {
        val viewModel = SavedTagsViewModel(handle, repository, FakeUserRepository(), dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private val SavedTagsViewModel.state get() = uiState.value

    @Test
    fun `the tags show as chips, nothing filtered, nothing searched`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas, later)

            val viewModel = viewModel()

            assertEquals(listOf("Ideas", ""), viewModel.state.tags.map { it.label })
            assertNull(viewModel.state.selected)
            assertTrue(repository.searches.isEmpty())
        }

    @Test
    fun `a tag filters its messages, a page at a time, newest first`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas)
            repository.pages[0L] = page(total = 3, next = 2, 3, 2)
            repository.pages[2L] = page(total = 3, next = 0, 1)
            val viewModel = viewModel()

            viewModel.select(viewModel.state.tags.single())
            runCurrent()
            assertEquals(
                listOf(3L, 2L),
                viewModel.state.results.items
                    .map { it.messageId },
            )
            assertEquals(3, viewModel.state.results.total)
            assertTrue(viewModel.state.results.hasMore)

            viewModel.loadMore()
            runCurrent()
            assertEquals(
                listOf(3L, 2L, 1L),
                viewModel.state.results.items
                    .map { it.messageId },
            )
            assertEquals(listOf(0L, 2L), repository.searches.map { it.second })

            // Nothing more to load.
            viewModel.loadMore()
            runCurrent()
            assertEquals(2, repository.searches.size)
        }

    @Test
    fun `All shows everything again, and a tag that goes away does too`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas, later)
            val viewModel = viewModel()
            viewModel.select(viewModel.state.tags.first())
            runCurrent()

            viewModel.select(null)
            runCurrent()
            assertNull(viewModel.state.selected)

            viewModel.select(viewModel.state.tags.first())
            repository.tags.value = listOf(later)
            runCurrent()
            assertNull(viewModel.state.selected)
        }

    @Test
    fun `a failed page shows its error, and Try again loads it again`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas)
            repository.failure = DomainError.Network
            val viewModel = viewModel()

            viewModel.select(viewModel.state.tags.single())
            runCurrent()
            assertEquals(DomainError.Network, viewModel.state.results.error)

            repository.failure = null
            repository.pages[0L] = page(total = 1, next = 0, 5)
            viewModel.retry()
            runCurrent()
            assertNull(viewModel.state.results.error)
            assertEquals(
                listOf(5L),
                viewModel.state.results.items
                    .map { it.messageId },
            )
        }

    @Test
    fun `renaming waits for Telegram, then closes with a snackbar`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas)
            val answer = CompletableDeferred<Outcome<Unit>>()
            repository.onLabel = { answer.await() }
            val viewModel = viewModel()
            val tag = viewModel.state.tags.single()

            viewModel.effects.test {
                viewModel.startRename(tag)
                viewModel.rename("Projects")
                runCurrent()
                assertEquals(true, viewModel.state.rename?.isSaving)

                answer.complete(Outcome.Success(Unit))
                runCurrent()
                assertEquals(SavedTagsEffect.Renamed, awaitItem())
            }
            assertNull(viewModel.state.rename)
            assertEquals(listOf(ReactionType.Emoji("❤") to "Projects"), repository.labels)
        }

    @Test
    fun `a refused label keeps the dialog open and says why`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas)
            repository.onLabel = { Outcome.Failure(DomainError.PremiumRequired) }
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.startRename(viewModel.state.tags.single())
                viewModel.rename("Projects")
                runCurrent()
                assertEquals(SavedTagsEffect.ShowError(DomainError.PremiumRequired), awaitItem())
            }
            assertEquals(false, viewModel.state.rename?.isSaving)
        }

    @Test
    fun `after process death the tag filters again, and the rename dialog comes back`() =
        runTest(dispatcher) {
            repository.tags.value = listOf(ideas)
            repository.pages[0L] = page(total = 1, next = 0, 4)
            val first = viewModel()
            first.select(first.state.tags.single())
            first.startRename(first.state.tags.single())
            runCurrent()
            repository.searches.clear()

            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) })
            val viewModel = viewModel(restored)
            runCurrent()

            assertEquals("Ideas", viewModel.state.selected?.label)
            assertEquals(
                listOf(4L),
                viewModel.state.results.items
                    .map { it.messageId },
            )
            assertEquals(
                "Ideas",
                viewModel.state.rename
                    ?.tag
                    ?.label,
            )
            assertEquals(1, repository.searches.size)
        }

    private fun page(
        total: Int,
        next: Long,
        vararg ids: Long,
    ) = TaggedMessages(ids.map { message(it) }.toImmutableList(), total, next)
}

/** [SavedMessagesTagRepository] in memory: the [tags], a page per first message id, and what was asked. */
private class FakeSavedMessagesTagRepository : SavedMessagesTagRepository {
    val tags = MutableStateFlow<List<SavedMessagesTag>>(emptyList())
    val pages = mutableMapOf<Long, TaggedMessages>()
    var failure: DomainError? = null
    val searches = mutableListOf<Pair<ReactionType, Long>>()
    val labels = mutableListOf<Pair<ReactionType, String>>()
    var onLabel: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }

    override fun tags(): Flow<List<SavedMessagesTag>> = tags

    override suspend fun taggedMessages(
        tag: ReactionType,
        fromMessageId: Long,
        limit: Int,
    ): Outcome<TaggedMessages> {
        searches += tag to fromMessageId
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(pages[fromMessageId] ?: TaggedMessages(emptyList<Nothing>().toImmutableList(), 0, 0))
    }

    override suspend fun setLabel(
        tag: ReactionType,
        label: String,
    ): Outcome<Unit> {
        labels += tag to label
        return onLabel()
    }
}

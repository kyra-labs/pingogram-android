package dev.kyralabs.pingogram.feature.chat.ui.panel

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [EmojiPanelViewModel]: nothing loads before the panel opens; the emoji with the recent ones first; the stickers'
 * sections with sets loading as they come into view; failures and "Try again".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmojiPanelViewModelTest {
    private val smileys = EmojiGroup(EmojiCategory.SmileysAndEmotion, persistentListOf("😀", "😃"))
    private val flags = EmojiGroup(EmojiCategory.Flags, persistentListOf())
    private val catalog = FakeEmojiCatalogRepository(listOf(smileys, flags))
    private val recentEmoji = FakeRecentEmojiRepository()
    private val stickers = FakeStickerRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = EmojiPanelViewModel(catalog, recentEmoji, stickers)

    private fun set(
        id: Long,
        size: Int = 2,
    ) = StickerSetSummary(id, "Set $id", size, cover = sticker(fileId = (id * 100).toInt(), setId = id))

    /** The state once the panel opened and everything it reads was read. */
    private fun TestScope.opened(viewModel: EmojiPanelViewModel): EmojiPanelUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onEvent(EmojiPanelEvent.Opened)
        runCurrent()
        return viewModel.uiState.value
    }

    @Test
    fun `loads nothing until the panel opens`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(EmojiPanelUiState(), awaitItem())
                runCurrent()
                expectNoEvents()
            }
            assertEquals(0, catalog.reads)
            assertEquals(0, stickers.installedSetsCollections)
        }

    @Test
    fun `the emoji tab shows the recent emoji first, then the groups that have emoji`() =
        runTest {
            recentEmoji.recentEmoji.value = listOf("🎉")

            val state = opened(viewModel())

            assertEquals(
                EmojiTab.Content(
                    persistentListOf(
                        EmojiSection(category = null, emoji = persistentListOf("🎉")),
                        EmojiSection(EmojiCategory.SmileysAndEmotion, smileys.emoji),
                    ),
                ),
                state.emoji,
            )
        }

    @Test
    fun `a picked emoji becomes the first recent one`() =
        runTest {
            val viewModel = viewModel()
            opened(viewModel)

            viewModel.onEvent(EmojiPanelEvent.EmojiPicked("😃"))
            runCurrent()

            assertEquals(listOf("😃"), recentEmoji.added)
            val sections = (viewModel.uiState.value.emoji as EmojiTab.Content).sections
            assertEquals(EmojiSection(category = null, emoji = persistentListOf("😃")), sections.first())
        }

    @Test
    fun `stickers show favorites, recent ones and the sets, the first sets loaded at once`() =
        runTest {
            val favorite = sticker(1)
            val recent = sticker(2)
            stickers.favorites.value = Outcome.Success(listOf(favorite))
            stickers.recent.value = Outcome.Success(listOf(recent))
            stickers.sets.value = Outcome.Success((1L..4L).map { set(it) })
            (1L..4L).forEach { id -> stickers.setContents[id] = Outcome.Success(listOf(sticker(id.toInt() * 10, id))) }

            val tab = opened(viewModel()).stickers as StickerTab.Content

            assertEquals(listOf(1L, 2L, 3L), stickers.setRequests)
            assertEquals(listOf("favorites", "recent", "set:1", "set:2", "set:3", "set:4"), tab.sections.map { it.key })
            assertEquals(listOf(favorite), tab.sections[0].stickers)
            assertEquals(listOf(sticker(10, 1)), tab.sections[2].stickers)
            // Not loaded yet: as many placeholders as the set has stickers.
            assertEquals(0, tab.sections[5].stickers.size)
            assertEquals(2, tab.sections[5].placeholders)
        }

    @Test
    fun `a set coming into view loads once, and a failed one is asked for again`() =
        runTest {
            stickers.sets.value = Outcome.Success((1L..5L).map { set(it) })
            stickers.setContents[5] = Outcome.Success(listOf(sticker(50, 5)))
            val viewModel = viewModel()
            opened(viewModel)

            viewModel.onEvent(EmojiPanelEvent.SetsVisible(listOf(4, 5)))
            viewModel.onEvent(EmojiPanelEvent.SetsVisible(listOf(5)))
            runCurrent()
            viewModel.onEvent(EmojiPanelEvent.SetsVisible(listOf(4, 5)))
            runCurrent()

            // 1–3 preloaded, all failing (no contents); 4 failed and asked for again; 5 loaded once.
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 4L), stickers.setRequests)
            val sets = (viewModel.uiState.value.stickers as StickerTab.Content).sections
            assertEquals(listOf(sticker(50, 5)), sets.single { it.key == "set:5" }.stickers)
        }

    @Test
    fun `sets that change are shown in their new order, loaded ones kept`() =
        runTest {
            stickers.sets.value = Outcome.Success(listOf(set(1), set(2)))
            stickers.setContents[1] = Outcome.Success(listOf(sticker(10, 1)))
            stickers.setContents[2] = Outcome.Success(listOf(sticker(20, 2)))
            val viewModel = viewModel()
            opened(viewModel)

            stickers.sets.value = Outcome.Success(listOf(set(2), set(1)))
            runCurrent()

            val sets = (viewModel.uiState.value.stickers as StickerTab.Content).sections
            assertEquals(listOf("set:2", "set:1"), sets.map { it.key })
            assertEquals(listOf(1L, 2L), stickers.setRequests)
        }

    @Test
    fun `no stickers at all is the empty state`() =
        runTest {
            assertEquals(StickerTab.Empty, opened(viewModel()).stickers)
        }

    @Test
    fun `sets that cannot be read show the error, and try again reads them again`() =
        runTest {
            stickers.sets.value = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()

            assertEquals(StickerTab.Error(DomainError.Network), opened(viewModel).stickers)

            stickers.sets.value = Outcome.Success(listOf(set(1)))
            viewModel.onEvent(EmojiPanelEvent.Retry)
            runCurrent()

            assertInstanceOf(StickerTab.Content::class.java, viewModel.uiState.value.stickers)
            assertEquals(2, stickers.installedSetsCollections)
        }

    @Test
    fun `favorites that cannot be read are left out`() =
        runTest {
            stickers.favorites.value = Outcome.Failure(DomainError.FloodWait(5))
            stickers.recent.value = Outcome.Success(listOf(sticker(2)))

            val tab = opened(viewModel()).stickers as StickerTab.Content

            assertEquals(listOf("recent"), tab.sections.map { it.key })
        }
}

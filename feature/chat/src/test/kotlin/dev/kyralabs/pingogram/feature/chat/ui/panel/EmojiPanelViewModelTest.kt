package dev.kyralabs.pingogram.feature.chat.ui.panel

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.feature.chat.ui.FakeInlineBotRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

    private val savedGifs = FakeSavedGifRepository()
    private val inlineBots = FakeInlineBotRepository()

    private fun viewModel() = EmojiPanelViewModel(CHAT_ID, catalog, recentEmoji, stickers, savedGifs, inlineBots)

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

    @Test
    fun `the GIF tab shows the saved GIFs, and offers search where Telegram names a bot`() =
        runTest {
            savedGifs.saved.value = listOf(gif(1), gif(2))

            val tab = opened(viewModel()).gifs as GifTab.Content

            assertEquals(listOf(1, 2), tab.saved.map { it.file.id })
            assertEquals(true, tab.canSearch)
            assertEquals(null, tab.search)
        }

    @Test
    fun `without a search bot there is no search`() =
        runTest {
            savedGifs.searchBot = null

            assertEquals(false, (opened(viewModel()).gifs as GifTab.Content).canSearch)
        }

    @Test
    fun `saved GIFs that cannot be read show the error, and try again reads them`() =
        runTest {
            savedGifs.onSaved = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel()
            assertInstanceOf(GifTab.Error::class.java, opened(viewModel).gifs)

            savedGifs.onSaved = null
            viewModel.onEvent(EmojiPanelEvent.RetryGifs)
            runCurrent()

            assertInstanceOf(GifTab.Content::class.java, viewModel.uiState.value.gifs)
        }

    @Test
    fun `a search asks the GIF bot after a pause, as typed in this chat, keeping only GIFs`() =
        runTest {
            inlineBots.bots["gif"] = InlineBot(GIF_BOT, GIF_BOT, "gif", "Search GIFs", needsLocation = false)
            inlineBots.onResults = {
                Outcome.Success(
                    InlineQueryResults(
                        5,
                        null,
                        persistentListOf(
                            InlineResult.Animation("a", gif(7), "Cat"),
                            InlineResult.Sticker("s", sticker(fileId = 8)),
                        ),
                        "next",
                    ),
                )
            }
            val viewModel = viewModel()
            opened(viewModel)

            viewModel.onEvent(EmojiPanelEvent.GifQueryChanged("ca"))
            viewModel.onEvent(EmojiPanelEvent.GifQueryChanged("cats"))
            runCurrent()
            assertEquals(true, (viewModel.uiState.value.gifs as GifTab.Content).search?.isLoading)
            advanceTimeBy(SEARCH_PAUSE)
            runCurrent()

            val search = (viewModel.uiState.value.gifs as GifTab.Content).search!!
            assertEquals(listOf(7), search.results.map { it.file.id })
            assertEquals(true, search.hasMore)
            assertEquals(listOf(FakeInlineBotRepository.Query(GIF_BOT, CHAT_ID, "cats", "", null)), inlineBots.queries)
        }

    @Test
    fun `the next page appends to the results, and clearing the field shows the saved GIFs`() =
        runTest {
            inlineBots.bots["gif"] = InlineBot(GIF_BOT, GIF_BOT, "gif", "", needsLocation = false)
            inlineBots.onResults = { query ->
                val id = if (query.offset.isEmpty()) 7 else 9
                Outcome.Success(
                    InlineQueryResults(
                        5,
                        null,
                        persistentListOf(InlineResult.Animation("r$id", gif(id), "")),
                        if (query.offset.isEmpty()) "next" else "",
                    ),
                )
            }
            val viewModel = viewModel()
            opened(viewModel)
            viewModel.onEvent(EmojiPanelEvent.GifQueryChanged("cats"))
            advanceTimeBy(SEARCH_PAUSE)
            runCurrent()

            viewModel.onEvent(EmojiPanelEvent.MoreGifs)
            runCurrent()
            val search = (viewModel.uiState.value.gifs as GifTab.Content).search!!
            assertEquals(listOf(7, 9), search.results.map { it.file.id })
            assertEquals(false, search.hasMore)
            assertEquals("next", inlineBots.queries.last().offset)

            viewModel.onEvent(EmojiPanelEvent.GifQueryChanged(""))
            runCurrent()
            assertEquals(null, (viewModel.uiState.value.gifs as GifTab.Content).search)
        }

    @Test
    fun `a failed search shows its error, and try again asks again`() =
        runTest {
            inlineBots.bots["gif"] = InlineBot(GIF_BOT, GIF_BOT, "gif", "", needsLocation = false)
            inlineBots.onResults = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            opened(viewModel)
            viewModel.onEvent(EmojiPanelEvent.GifQueryChanged("cats"))
            advanceTimeBy(SEARCH_PAUSE)
            runCurrent()
            assertEquals(DomainError.Network, (viewModel.uiState.value.gifs as GifTab.Content).search?.error)

            inlineBots.onResults = { Outcome.Success(InlineQueryResults(5, null, persistentListOf(), "")) }
            viewModel.onEvent(EmojiPanelEvent.RetryGifs)
            runCurrent()

            val search = (viewModel.uiState.value.gifs as GifTab.Content).search!!
            assertEquals(null, search.error)
            assertEquals(2, inlineBots.queries.size)
        }

    @Test
    fun `removing a saved GIF takes it out of the saved GIFs`() =
        runTest {
            savedGifs.saved.value = listOf(gif(1), gif(2))
            val viewModel = viewModel()
            opened(viewModel)

            viewModel.onEvent(EmojiPanelEvent.RemoveSavedGif(gif(1)))
            runCurrent()

            assertEquals(listOf(gif(1)), savedGifs.removed)
            assertEquals(listOf(2), (viewModel.uiState.value.gifs as GifTab.Content).saved.map { it.file.id })
        }
}

private const val CHAT_ID = 42L
private const val GIF_BOT = 140L

/** The pause after the last change of the query before it is searched, and a little more. */
private const val SEARCH_PAUSE = 401L

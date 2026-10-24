package dev.kyralabs.pingogram.feature.settings.ui.stickers

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerSetRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StickersViewModelTest {
    private val client = FakeTdlibClient()

    /** The installed sets TDLib answers with, by id, in this order. */
    private var installed = listOf(1L, 2L, 3L)
    private var archived = listOf(9L)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetInstalledStickerSets::class) {
            val sets = installed.map { setInfo(it, installed = true) }.toTypedArray()
            TdResult.Success(TdApi.StickerSets(sets.size, sets))
        }
        client.on(TdApi.GetArchivedStickerSets::class) {
            val sets = archived.map { setInfo(it, archived = true) }.toTypedArray()
            TdResult.Success(TdApi.StickerSets(sets.size, sets))
        }
        client.on(TdApi.GetTrendingStickerSets::class) {
            val sets =
                arrayOf(
                    setInfo(20, viewed = false),
                    setInfo(21, viewed = true),
                    setInfo(22, installed = true, viewed = false),
                )
            TdResult.Success(TdApi.TrendingStickerSets(sets.size, sets, false))
        }
        client.on(TdApi.ChangeStickerSet::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.ReorderInstalledStickerSets::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setInfo(
        id: Long,
        installed: Boolean = false,
        archived: Boolean = false,
        viewed: Boolean = true,
    ) = TdApi.StickerSetInfo().apply {
        this.id = id
        title = "Set $id"
        name = "set$id"
        size = 10
        covers = arrayOf(TdApiFixtures.sticker(setId = id))
        isInstalled = installed
        isArchived = archived
        isViewed = viewed
        stickerType = TdApi.StickerTypeRegular()
    }

    private fun TestScope.viewModel(): StickersViewModel {
        val viewModel = StickersViewModel(TdlibStickerRepository(client), TdlibStickerSetRepository(client))
        runCurrent()
        return viewModel
    }

    private fun StickersViewModel.content() = uiState.value as StickersUiState.Content

    private fun StickersViewModel.ids() = content().installed.map { it.id }

    @Test
    fun `shows the installed sets in order, the archived count and the trending sets not seen or added`() =
        runTest {
            val viewModel = viewModel()

            val content = viewModel.content()
            assertEquals(listOf(1L, 2L, 3L), content.installed.map { it.id })
            assertEquals("set1", content.installed.first().name)
            assertEquals(1, content.archivedCount)
            assertEquals(1, content.newTrendingCount)
        }

    @Test
    fun `a failed read of the installed sets shows the error, and retry reads again`() =
        runTest {
            client.on(TdApi.GetInstalledStickerSets::class, TdResult.Failure(TdError(500, "INTERNAL")))
            val viewModel = viewModel()
            assertInstanceOf(StickersUiState.Error::class.java, viewModel.uiState.value)

            client.on(TdApi.GetInstalledStickerSets::class, TdResult.Success(TdApi.StickerSets(0, emptyArray())))
            viewModel.onEvent(StickersEvent.Retry)
            runCurrent()

            assertEquals(emptyList<Long>(), viewModel.ids())
        }

    @Test
    fun `the archived and trending counts are left out when they fail`() =
        runTest {
            client.on(TdApi.GetArchivedStickerSets::class, TdResult.Failure(TdError(500, "INTERNAL")))
            client.on(TdApi.GetTrendingStickerSets::class, TdResult.Failure(TdError(500, "INTERNAL")))

            val content = viewModel().content()

            assertEquals(3, content.installed.size)
            assertNull(content.archivedCount)
            assertNull(content.newTrendingCount)
        }

    @Test
    fun `a dragged order shows at once and goes to Telegram`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(StickersEvent.Reorder(listOf(3, 1, 2)))
            assertEquals(listOf(3L, 1L, 2L), viewModel.ids())
            runCurrent()

            assertEquals(
                listOf(3L, 1L, 2L),
                client
                    .sentOf<TdApi.ReorderInstalledStickerSets>()
                    .single()
                    .stickerSetIds
                    .toList(),
            )
        }

    @Test
    fun `moving a set for screen readers swaps it with its neighbour`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(StickersEvent.Move(2, up = false))

            assertEquals(listOf(1L, 3L, 2L), viewModel.ids())
            viewModel.onEvent(StickersEvent.Move(1, up = true))
            assertEquals(listOf(1L, 3L, 2L), viewModel.ids())
        }

    @Test
    fun `a refused order goes back to Telegram's with a snackbar`() =
        runTest {
            client.on(TdApi.ReorderInstalledStickerSets::class, TdResult.Failure(TdError(400, "BAD_REQUEST")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StickersEvent.Reorder(listOf(2, 1, 3)))
                runCurrent()

                assertInstanceOf(StickersEffect.ShowError::class.java, awaitItem())
                assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
            }
        }

    @Test
    fun `archiving a set takes it out at once and offers to undo it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StickersEvent.Archive(2))
                assertEquals(listOf(1L, 3L), viewModel.ids())
                runCurrent()

                val changed = awaitItem() as StickersEffect.Changed
                assertEquals(2L, changed.id)
                assertEquals(true, changed.archived)
                assertEquals(listOf(1L, 2L, 3L), changed.previousOrder)
                val request = client.sentOf<TdApi.ChangeStickerSet>().single()
                assertEquals(Triple(2L, false, true), Triple(request.setId, request.isInstalled, request.isArchived))
            }
        }

    @Test
    fun `removing a set sends it out of both lists`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(StickersEvent.Remove(3))
            runCurrent()

            val request = client.sentOf<TdApi.ChangeStickerSet>().single()
            assertEquals(Triple(3L, false, false), Triple(request.setId, request.isInstalled, request.isArchived))
            assertEquals(listOf(1L, 2L), viewModel.ids())
        }

    @Test
    fun `a refused archive brings the set back with a snackbar`() =
        runTest {
            client.on(TdApi.ChangeStickerSet::class, TdResult.Failure(TdError(400, "STICKERSET_INVALID")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StickersEvent.Archive(1))
                runCurrent()

                assertEquals(StickersEffect.ShowError(DomainError.NotFound), awaitItem())
                assertEquals(listOf(1L, 2L, 3L), viewModel.ids())
            }
        }

    @Test
    fun `undo installs the set again and puts it back where it was`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(StickersEvent.Archive(2))
            runCurrent()
            installed = listOf(1L, 3L)
            client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(1, 3)))
            runCurrent()

            viewModel.onEvent(StickersEvent.Undo(2, listOf(1, 2, 3)))
            installed = listOf(2L, 1L, 3L)
            client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(2, 1, 3)))
            runCurrent()

            val install = client.sentOf<TdApi.ChangeStickerSet>().last()
            assertEquals(Triple(2L, true, false), Triple(install.setId, install.isInstalled, install.isArchived))
            assertEquals(
                listOf(1L, 2L, 3L),
                client
                    .sentOf<TdApi.ReorderInstalledStickerSets>()
                    .single()
                    .stickerSetIds
                    .toList(),
            )
        }

    @Test
    fun `the restored order puts a set after the one it followed, or first`() {
        assertEquals(listOf(1L, 2L, 3L), restoredOrder(2, listOf(1, 2, 3), listOf(2, 1, 3)))
        assertEquals(listOf(4L, 1L, 3L), restoredOrder(4, listOf(4, 1, 3), listOf(1, 3)))
        assertEquals(listOf(5L, 1L, 2L, 3L), restoredOrder(2, listOf(1, 2, 3), listOf(5, 1, 3)))
        assertNull(restoredOrder(1, listOf(1, 2), listOf(1, 2)))
    }
}

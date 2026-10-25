package dev.kyralabs.pingogram.feature.settings.ui.stickers

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerSetRepository
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StickerSetListViewModelTest {
    private val client = FakeTdlibClient()

    /** The trending sets, by id, with whether they are installed and seen. */
    private var trending = listOf(setInfo(1, viewed = false), setInfo(2), setInfo(3, installed = true))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetTrendingStickerSets::class) {
            TdResult.Success(TdApi.TrendingStickerSets(trending.size, trending.toTypedArray(), false))
        }
        client.on(TdApi.GetArchivedStickerSets::class) {
            TdResult.Success(TdApi.StickerSets(1, arrayOf(setInfo(9, archived = true))))
        }
        client.on(TdApi.ChangeStickerSet::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.ViewTrendingStickerSets::class, TdResult.Success(TdApi.Ok()))
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

    private fun TestScope.viewModel(
        archived: Boolean = false,
        kind: StickerSetKind = StickerSetKind.Stickers,
    ): StickerSetListViewModel {
        val viewModel = StickerSetListViewModel(archived, TdlibStickerSetRepository(client), kind)
        runCurrent()
        return viewModel
    }

    private fun StickerSetListViewModel.sets() = (uiState.value as StickerSetListUiState.Content).sets

    @Test
    fun `trending sets show in Telegram's order, and those not seen are marked seen once`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(listOf(1L, 2L, 3L), viewModel.sets().map { it.id })
            assertEquals(listOf(false, false, true), viewModel.sets().map { it.isInstalled })
            val viewed = client.sentOf<TdApi.ViewTrendingStickerSets>()
            assertEquals(listOf(listOf(1L)), viewed.map { it.stickerSetIds.toList() })

            client.emit(TdApi.UpdateTrendingStickerSets(TdApi.StickerTypeRegular(), TdApi.TrendingStickerSets()))
            runCurrent()
            assertEquals(1, client.sentOf<TdApi.ViewTrendingStickerSets>().size)
        }

    @Test
    fun `the emoji pages ask for the custom emoji sets`() =
        runTest {
            viewModel(kind = StickerSetKind.Emoji)
            viewModel(archived = true, kind = StickerSetKind.Emoji)

            val trending = client.sentOf<TdApi.GetTrendingStickerSets>().single()
            assertInstanceOf(TdApi.StickerTypeCustomEmoji::class.java, trending.stickerType)
            val archived = client.sentOf<TdApi.GetArchivedStickerSets>().single()
            assertInstanceOf(TdApi.StickerTypeCustomEmoji::class.java, archived.stickerType)
        }

    @Test
    fun `the archived page shows the archived sets and marks nothing seen`() =
        runTest {
            val viewModel = viewModel(archived = true)

            assertEquals(listOf(9L), viewModel.sets().map { it.id })
            assertTrue(client.sentOf<TdApi.ViewTrendingStickerSets>().isEmpty())
        }

    @Test
    fun `adding a set shows progress until Telegram reports it installed`() =
        runTest {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StickerSetListEvent.Add(2))
                runCurrent()
                assertTrue(viewModel.sets().first { it.id == 2L }.isBusy)
                assertEquals(StickerSetListEffect.Added("Set 2"), awaitItem())

                trending = listOf(setInfo(1), setInfo(2, installed = true), setInfo(3, installed = true))
                client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(2, 3)))
                runCurrent()

                val added = viewModel.sets().first { it.id == 2L }
                assertTrue(added.isInstalled)
                assertFalse(added.isBusy)
            }
            val request = client.sentOf<TdApi.ChangeStickerSet>().single()
            assertEquals(Triple(2L, true, false), Triple(request.setId, request.isInstalled, request.isArchived))
        }

    @Test
    fun `a set already installed is not added again`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(StickerSetListEvent.Add(3))
            runCurrent()

            assertTrue(client.sentOf<TdApi.ChangeStickerSet>().isEmpty())
        }

    @Test
    fun `a refused add stops the progress and shows the error`() =
        runTest {
            client.on(TdApi.ChangeStickerSet::class, TdResult.Failure(TdError(400, "STICKERSETS_TOO_MUCH")))
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StickerSetListEvent.Add(1))
                runCurrent()

                assertInstanceOf(StickerSetListEffect.ShowError::class.java, awaitItem())
                assertFalse(viewModel.sets().first { it.id == 1L }.isBusy)
            }
        }

    @Test
    fun `a failed read shows the error, and retry reads again`() =
        runTest {
            client.on(TdApi.GetTrendingStickerSets::class, TdResult.Failure(TdError(500, "INTERNAL")))
            val viewModel = viewModel()
            assertInstanceOf(StickerSetListUiState.Error::class.java, viewModel.uiState.value)

            client.on(TdApi.GetTrendingStickerSets::class) {
                TdResult.Success(TdApi.TrendingStickerSets(trending.size, trending.toTypedArray(), false))
            }
            viewModel.onEvent(StickerSetListEvent.Retry)
            runCurrent()

            assertEquals(3, viewModel.sets().size)
        }
}

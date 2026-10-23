package dev.kyralabs.pingogram.feature.settings.ui.stickers

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerSetRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetEvent
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetUiState
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetViewModel
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StickerSetSheetViewModelTest {
    private val client = FakeTdlibClient()
    private var installed = false

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetStickerSet::class) { request -> TdResult.Success(set(request.setId)) }
        client.on(TdApi.SearchStickerSet::class) { request ->
            if (request.name == "garden") {
                TdResult.Success(set(3))
            } else {
                TdResult.Failure(TdError(400, "STICKERSET_INVALID"))
            }
        }
        client.on(TdApi.ChangeStickerSet::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun set(id: Long) =
        TdApi.StickerSet().apply {
            this.id = id
            title = "Garden"
            name = "garden"
            isInstalled = installed
            stickerType = TdApi.StickerTypeRegular()
            stickers = arrayOf(TdApiFixtures.sticker(emoji = "🐝"), TdApiFixtures.sticker(emoji = "🌻"))
            emojis = emptyArray()
        }

    private fun TestScope.viewModel(
        setId: Long = 3,
        name: String? = null,
    ): StickerSetSheetViewModel {
        val viewModel = StickerSetSheetViewModel(setId, name, TdlibStickerSetRepository(client))
        runCurrent()
        return viewModel
    }

    private fun StickerSetSheetViewModel.content() = uiState.value as StickerSetSheetUiState.Content

    @Test
    fun `shows the set by its id with its stickers`() =
        runTest {
            val content = viewModel().content()

            assertEquals("Garden", content.title)
            assertEquals(listOf("🐝", "🌻"), content.stickers.map { it.emoji })
            assertFalse(content.isInstalled)
            assertEquals(3L, client.sentOf<TdApi.GetStickerSet>().single().setId)
        }

    @Test
    fun `a link's name is looked up first`() =
        runTest {
            val viewModel = viewModel(setId = 0, name = "garden")

            assertEquals("garden", viewModel.content().name)
            assertEquals(listOf("garden"), client.sentOf<TdApi.SearchStickerSet>().map { it.name })
        }

    @Test
    fun `a name no set has is not found, without trying again`() =
        runTest {
            val viewModel = viewModel(setId = 0, name = "nothing")

            assertEquals(StickerSetSheetUiState.Failed(DomainError.NotFound, canRetry = false), viewModel.uiState.value)
        }

    @Test
    fun `adding installs the set and shows progress until Telegram reports it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(StickerSetSheetEvent.Add)
            assertTrue(viewModel.content().isChanging)
            runCurrent()
            val request = client.sentOf<TdApi.ChangeStickerSet>().single()
            assertEquals(Triple(3L, true, false), Triple(request.setId, request.isInstalled, request.isArchived))

            installed = true
            client.emit(TdApi.UpdateInstalledStickerSets(TdApi.StickerTypeRegular(), longArrayOf(3)))
            runCurrent()

            assertTrue(viewModel.content().isInstalled)
            assertFalse(viewModel.content().isChanging)
        }

    @Test
    fun `removing takes an installed set out of the user's sets`() =
        runTest {
            installed = true
            val viewModel = viewModel()

            viewModel.onEvent(StickerSetSheetEvent.Remove)
            runCurrent()

            val request = client.sentOf<TdApi.ChangeStickerSet>().single()
            assertEquals(Triple(3L, false, false), Triple(request.setId, request.isInstalled, request.isArchived))
        }

    @Test
    fun `a refused add shows why and lets the user try again`() =
        runTest {
            client.on(TdApi.ChangeStickerSet::class, TdResult.Failure(TdError(500, "INTERNAL")))
            val viewModel = viewModel()

            viewModel.onEvent(StickerSetSheetEvent.Add)
            runCurrent()

            assertFalse(viewModel.content().isChanging)
            assertTrue(viewModel.content().changeError != null)
        }

    @Test
    fun `a set Telegram asks to wait for can be tried again`() =
        runTest {
            client.on(TdApi.GetStickerSet::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")))
            val viewModel = viewModel()
            assertTrue((viewModel.uiState.value as StickerSetSheetUiState.Failed).canRetry)

            client.on(TdApi.GetStickerSet::class) { request -> TdResult.Success(set(request.setId)) }
            viewModel.onEvent(StickerSetSheetEvent.Retry)
            runCurrent()

            assertEquals("Garden", viewModel.content().title)
        }
}

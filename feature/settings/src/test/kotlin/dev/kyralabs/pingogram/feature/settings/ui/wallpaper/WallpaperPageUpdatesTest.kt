package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibWallpaperRepository
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.FakeWallpaperSeedRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The wallpaper page on TDLib's own repository: after Apply it shows what was set, never the wallpaper before it, and
 * follows a later change, for the account and for a chat, Saved Messages included; the conversation's flow too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperPageUpdatesTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.page(
        repository: TdlibWallpaperRepository,
        chatId: Long? = null,
    ): WallpaperPageViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, UnconfinedTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.updateNewChat(TdApiFixtures.chat(id = ME, type = TdApi.ChatTypePrivate(ME))))
        runCurrent()
        val repositories = WallpaperRepositories(repository, FakeWallpaperSeedRepository())
        val appearance = FakeAppearanceRepository(AppearanceSettings())
        val photos = FakePickedPhotoRepository()
        return WallpaperPageViewModel(chatId, repositories, appearance, chats, PremiumUsers(ME), photos).also {
            it.onEvent(WallpaperPageEvent.Picker(WallpaperEvent.ShowTheme(dark = true)))
        }
    }

    private fun WallpaperPageViewModel.applyPick(event: WallpaperEvent) {
        onEvent(WallpaperPageEvent.Picker(event))
        onEvent(WallpaperPageEvent.Apply(emptyList()))
    }

    private val WallpaperPageViewModel.shown get() = uiState.value.saved.wallpaper

    @Test
    fun `the account's page shows what Apply set, then a change made elsewhere`() =
        runTest {
            val repository = TdlibWallpaperRepository(client, backgroundScope)
            val page = page(repository)
            client.on(TdApi.SetDefaultBackground::class, TdResult.Success(photoBackground(id = 1)))

            page.applyPick(WallpaperEvent.PickPhoto("content://photo", SCREEN_SIDE))
            assertEquals(1L, page.shown?.id)
            assertNull(page.uiState.value.picker.pending)
            assertFalse(page.uiState.value.canApply)

            client.emit(TdApi.UpdateDefaultBackground(true, photoBackground(id = 2)))
            assertEquals(2L, page.shown?.id)
        }

    @Test
    fun `Saved Messages' page and conversation show a new photo once Apply sets it`() =
        runTest {
            val repository = TdlibWallpaperRepository(client, backgroundScope)
            val page = page(repository, chatId = ME)
            client.on(TdApi.SetChatBackground::class, TdResult.Success(TdApi.Ok()))

            page.applyPick(WallpaperEvent.PickPhoto("content://photo", SCREEN_SIDE))
            val set = TdApi.MessageChatSetBackground(0, TdApi.ChatBackground(photoBackground(id = 9), 30), true)
            client.emit(TdApiFixtures.updateNewMessage(TdApiFixtures.message(chatId = ME, content = set)))

            assertEquals(9L, page.shown?.id)
            assertNull(page.uiState.value.picker.pending)
            assertEquals(
                9L,
                repository
                    .chatWallpaper(ME)
                    .first()
                    ?.wallpaper
                    ?.id,
            )
        }

    @Test
    fun `a chat's page and conversation show one of Telegram's wallpapers as soon as Apply sets it`() =
        runTest {
            val repository = TdlibWallpaperRepository(client, backgroundScope)
            val page = page(repository, chatId = ME)
            client.on(TdApi.SetChatBackground::class, TdResult.Success(TdApi.Ok()))

            page.applyPick(WallpaperEvent.Pick(PATTERN))

            assertEquals(PATTERN, page.shown)
            assertEquals(PATTERN, repository.chatWallpaper(ME).first()?.wallpaper)
        }

    private companion object {
        const val ME = 1L
        const val SCREEN_SIDE = 2400
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(11, 0)), intensity = 50, isInverted = false)

        fun photoBackground(id: Long) =
            TdApi.Background(
                id,
                false,
                false,
                "photo$id",
                TdApiFixtures.document(mimeType = "image/jpeg", file = TdApiFixtures.file(id = id.toInt() + 60)),
                TdApi.BackgroundTypeWallpaper(false, false),
            )
    }
}

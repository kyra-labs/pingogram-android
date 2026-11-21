package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The wallpaper page for the account and for a chat: nothing is set before Apply; then a pick, a theme color, the
 * dimming or the reset, for the user alone or with Telegram Premium both people; a failure keeps what was set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperPageViewModelTest {
    private val client = FakeTdlibClient()
    private val wallpapers = FakeWallpaperRepository(installedWallpapers = listOf(PHOTO, PATTERN))
    private val appearance = FakeAppearanceRepository(AppearanceSettings())
    private val users = PremiumUsers(ME)
    private val photos = FakePickedPhotoRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(chatId: Long? = null): WallpaperPageViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, UnconfinedTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.updateNewChat(TdApiFixtures.chat(id = CHAT, type = TdApi.ChatTypePrivate(5))))
        runCurrent()
        return WallpaperPageViewModel(chatId, wallpapers, appearance, chats, users, photos).also {
            it.picker(WallpaperEvent.ShowTheme(dark = true))
        }
    }

    private fun WallpaperPageViewModel.picker(event: WallpaperEvent) = onEvent(WallpaperPageEvent.Picker(event))

    private fun WallpaperPageViewModel.apply(forBoth: Boolean = false) =
        onEvent(WallpaperPageEvent.Apply(emptyList(), forBoth))

    private fun TestScope.effectsOf(viewModel: WallpaperPageViewModel): List<WallpaperPageEffect> {
        val effects = mutableListOf<WallpaperPageEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `Apply waits for a change, and a pick is set as the account's for the theme only then`() =
        runTest {
            val viewModel = viewModel()
            assertFalse(viewModel.uiState.value.canApply)

            viewModel.picker(WallpaperEvent.Pick(PHOTO))
            assertTrue(viewModel.uiState.value.canApply)
            assertEquals(emptyList<Any>(), wallpapers.defaultsSet)

            viewModel.apply()

            assertEquals(listOf(WallpaperChoice.Existing(PHOTO) to true), wallpapers.defaultsSet)
            assertEquals(PHOTO, viewModel.uiState.value.saved.wallpaper)
            assertFalse(viewModel.uiState.value.canApply)
        }

    @Test
    fun `the account's dimming is previewed and saved with Apply`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(WallpaperPageEvent.SetDimming(50))
            assertEquals(50, viewModel.uiState.value.dimming)
            assertEquals(ChatAppearance.DEFAULT_WALLPAPER_DIMMING, viewModel.uiState.value.saved.dimming)

            viewModel.apply()

            assertEquals(50, viewModel.uiState.value.saved.dimming)
            assertNull(viewModel.uiState.value.draft.dimming)
        }

    @Test
    fun `a theme color replaces the account's wallpaper for the theme`() =
        runTest {
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            val viewModel = viewModel()

            viewModel.onEvent(WallpaperPageEvent.PickThemeColor(ChatWallpaper.Primary))
            assertNull(viewModel.uiState.value.shownWallpaper)
            assertEquals(PHOTO, wallpapers.defaults.value.dark)
            viewModel.apply()

            assertEquals(AccountWallpapers(), wallpapers.defaults.value)
            assertEquals(ChatWallpaper.Primary, viewModel.uiState.value.saved.themeColor)
        }

    @Test
    fun `reset previews the default, and Apply sets it`() =
        runTest {
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            appearance.setChatWallpaper(ChatWallpaper.Secondary)
            appearance.setWallpaperDimming(70)
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.canReset)

            viewModel.onEvent(WallpaperPageEvent.Reset)
            assertEquals(ChatAppearance.DEFAULT_WALLPAPER_DIMMING, viewModel.uiState.value.dimming)
            assertEquals(ChatWallpaper.None, viewModel.uiState.value.shownThemeColor)
            assertFalse(viewModel.uiState.value.canReset)
            viewModel.apply()

            assertTrue(viewModel.uiState.value.saved.isDefault)
            assertFalse(viewModel.uiState.value.canReset)
        }

    @Test
    fun `blur picks the photo that is set, blurred`() =
        runTest {
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            val viewModel = viewModel()

            viewModel.picker(WallpaperEvent.SetBlurred(true))
            viewModel.apply()

            assertEquals(WallpaperChoice.Existing(PHOTO, isBlurred = true), wallpapers.defaultsSet.last().first)
        }

    @Test
    fun `a photo is prepared at the screen's size, up to 2560 px`() =
        runTest {
            val viewModel = viewModel()
            viewModel.picker(WallpaperEvent.PickPhoto("content://phone", SCREEN_SIDE))
            viewModel.picker(WallpaperEvent.PickPhoto("content://tablet", 3200))

            assertEquals(listOf(SCREEN_SIDE, 2560), photos.preparedSides)
            assertTrue(viewModel.uiState.value.picker.pending is WallpaperChoice.NewPhoto)
        }

    @Test
    fun `a chat's page shows its title and sets its own wallpaper for the user alone, with its dimming`() =
        runTest {
            val viewModel = viewModel(CHAT)
            assertEquals(
                "Ada",
                viewModel.uiState.value.chat
                    ?.title,
            )

            viewModel.onEvent(WallpaperPageEvent.SetDimming(40))
            viewModel.picker(WallpaperEvent.Pick(PATTERN))
            viewModel.apply()

            val set = wallpapers.chatWallpapersSet.single()
            assertEquals(
                FakeWallpaperRepository.ChatWallpaperSet(CHAT, WallpaperChoice.Existing(PATTERN), 40, true),
                set,
            )
            assertEquals(emptyList<Any>(), wallpapers.defaultsSet)
        }

    @Test
    fun `only Telegram Premium sets a chat's wallpaper for both people`() =
        runTest {
            val viewModel = viewModel(CHAT)
            viewModel.picker(WallpaperEvent.Pick(PHOTO))
            viewModel.apply(forBoth = true)
            assertEquals(true, wallpapers.chatWallpapersSet.last().onlyForSelf)

            users.premium.value = true
            assertTrue(viewModel.uiState.value.canSetForBoth)
            viewModel.picker(WallpaperEvent.Pick(PATTERN))
            viewModel.apply(forBoth = true)

            assertEquals(false, wallpapers.chatWallpapersSet.last().onlyForSelf)
        }

    @Test
    fun `a chat's dimming alone sets its own wallpaper again, and needs one`() =
        runTest {
            val viewModel = viewModel(CHAT)
            viewModel.onEvent(WallpaperPageEvent.SetDimming(60))
            assertFalse(viewModel.uiState.value.canApply)

            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 30))
            assertTrue(viewModel.uiState.value.canApply)
            viewModel.apply()

            assertEquals(SavedWallpaper(PHOTO, dimming = 60), viewModel.uiState.value.saved)
            assertEquals(60, wallpapers.chatWallpapersSet.single().darkThemeDimming)
        }

    @Test
    fun `reset removes a chat's own wallpaper with Apply`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PATTERN, darkThemeDimming = 20))
            val viewModel = viewModel(CHAT)

            viewModel.onEvent(WallpaperPageEvent.Reset)
            assertEquals(PATTERN, viewModel.uiState.value.saved.wallpaper)
            viewModel.apply()

            assertNull(viewModel.uiState.value.saved.wallpaper)
        }

    @Test
    fun `a failed upload keeps the wallpaper there was and the pick, and says why`() =
        runTest {
            wallpapers.setChat(CHAT, ChatWallpaperOverride(PHOTO, darkThemeDimming = 0))
            val viewModel = viewModel(CHAT)
            val effects = effectsOf(viewModel)
            viewModel.picker(WallpaperEvent.PickPhoto("content://photo", SCREEN_SIDE))
            wallpapers.failure = DomainError.Network

            viewModel.apply()

            assertEquals(listOf(WallpaperPageEffect.ShowError(DomainError.Network)), effects)
            assertEquals(PHOTO, viewModel.uiState.value.saved.wallpaper)
            assertTrue(viewModel.uiState.value.picker.pending is WallpaperChoice.NewPhoto)
            assertTrue(viewModel.uiState.value.canApply)
        }

    @Test
    fun `a reset that fails keeps the account's wallpaper and the preview, and says why`() =
        runTest {
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            viewModel.onEvent(WallpaperPageEvent.Reset)
            wallpapers.failure = DomainError.Network

            viewModel.apply()

            assertEquals(listOf(WallpaperPageEffect.ShowError(DomainError.Network)), effects)
            assertEquals(PHOTO, wallpapers.defaults.value.dark)
            assertTrue(viewModel.uiState.value.draft.isReset)
        }

    private companion object {
        const val CHAT = 5L
        const val ME = 1L
        const val SCREEN_SIDE = 2400
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(11, 0)), intensity = 50, isInverted = false)
    }
}

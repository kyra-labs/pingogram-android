package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A conversation's wallpaper: the account's for each theme, and the chat's own over it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWallpaperViewModelTest {
    private val repository = FakeWallpaperRepository(AccountWallpapers(light = LIGHT, dark = DARK))
    private val appearance = FakeAppearanceRepository(AppearanceSettings(chat = ChatAppearance(wallpaperDimming = 20)))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose wallpaper is collected, as the screen collects it. */
    private fun TestScope.collected(): ChatWallpaperViewModel =
        ChatWallpaperViewModel(CHAT, repository, appearance).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.wallpaper.collect {} }
        }

    @Test
    fun `shows the account's wallpaper for the theme, with the dimming kept on the device`() =
        runTest {
            val viewModel = collected()

            assertEquals(ShownWallpaper(LIGHT, 20), viewModel.wallpaper.value.forTheme(dark = false))
            assertEquals(ShownWallpaper(DARK, 20), viewModel.wallpaper.value.forTheme(dark = true))

            appearance.setWallpaperDimming(45)
            assertEquals(ShownWallpaper(DARK, 45), viewModel.wallpaper.value.forTheme(dark = true))
        }

    @Test
    fun `a chat's own wallpaper wins in both themes, with its dimming, until it is removed`() =
        runTest {
            val viewModel = collected()

            repository.setChat(CHAT, ChatWallpaperOverride(OWN, darkThemeDimming = 50))
            assertEquals(ShownWallpaper(OWN, 50), viewModel.wallpaper.value.forTheme(dark = false))
            assertEquals(ShownWallpaper(OWN, 50), viewModel.wallpaper.value.forTheme(dark = true))

            repository.setChat(CHAT, null)
            assertEquals(ShownWallpaper(LIGHT, 20), viewModel.wallpaper.value.forTheme(dark = false))
        }

    @Test
    fun `another chat's wallpaper changes nothing here`() =
        runTest {
            val viewModel = collected()

            repository.setChat(CHAT + 1, ChatWallpaperOverride(OWN, darkThemeDimming = 50))

            assertEquals(ShownWallpaper(LIGHT, 20), viewModel.wallpaper.value.forTheme(dark = false))
        }

    private companion object {
        const val CHAT = 7L
        val LIGHT = Wallpaper.Fill(1, listOf(0xFFFFFF))
        val DARK = Wallpaper.Fill(2, listOf(0x000000))
        val OWN = Wallpaper.Photo(3, WallpaperImage(FileRef(30, 0)), isBlurred = false)
    }
}

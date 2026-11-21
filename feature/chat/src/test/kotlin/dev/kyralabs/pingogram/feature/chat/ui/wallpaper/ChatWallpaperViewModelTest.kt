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
import dev.kyralabs.pingogram.core.testing.FakeWallpaperSeedRepository
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A conversation's wallpaper: the account's for each theme, the chat's own over it, and its colors' seed. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWallpaperViewModelTest {
    private val repository = FakeWallpaperRepository(AccountWallpapers(light = LIGHT, dark = DARK))
    private val appearance = FakeAppearanceRepository(AppearanceSettings(chat = ChatAppearance(wallpaperDimming = 20)))
    private val seeds = FakeWallpaperSeedRepository()

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
        ChatWallpaperViewModel(CHAT, repository, appearance, seeds).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.wallpaper.collect {} }
        }

    @Test
    fun `shows the account's wallpaper for the theme, with the dimming kept on the device`() =
        runTest {
            val viewModel = collected()

            assertEquals(LIGHT to 20, viewModel.shown(dark = false))
            assertEquals(DARK to 20, viewModel.shown(dark = true))

            appearance.setWallpaperDimming(45)
            assertEquals(DARK to 45, viewModel.shown(dark = true))
        }

    @Test
    fun `a chat's own wallpaper wins in both themes, with its dimming, until it is removed`() =
        runTest {
            val viewModel = collected()

            repository.setChat(CHAT, ChatWallpaperOverride(OWN, darkThemeDimming = 50))
            assertEquals(OWN to 50, viewModel.shown(dark = false))
            assertEquals(OWN to 50, viewModel.shown(dark = true))

            repository.setChat(CHAT, null)
            assertEquals(LIGHT to 20, viewModel.shown(dark = false))
        }

    @Test
    fun `a photo's colors come from its kept seed, found and kept once`() =
        runTest {
            val viewModel = collected()
            repository.setChat(CHAT, ChatWallpaperOverride(OWN, darkThemeDimming = 50))
            assertNull(
                viewModel.wallpaper.value
                    .forTheme(dark = false)
                    .seed,
            )

            viewModel.keepSeed(OWN.id, SEED)

            assertEquals(
                SEED,
                viewModel.wallpaper.value
                    .forTheme(dark = false)
                    .seed,
            )
            assertEquals(mapOf(OWN.id to SEED), seeds.seeds.value)
        }

    @Test
    fun `a fill's colors come from its own colors, a pattern keeps the app's`() =
        runTest {
            val viewModel = collected()
            assertEquals(
                0xFFFFFFFF.toInt(),
                viewModel.wallpaper.value
                    .forTheme(dark = false)
                    .seed,
            )

            repository.setChat(CHAT, ChatWallpaperOverride(PATTERN, darkThemeDimming = 0))
            assertNull(
                viewModel.wallpaper.value
                    .forTheme(dark = false)
                    .seed,
            )
        }

    @Test
    fun `another chat's wallpaper changes nothing here`() =
        runTest {
            val viewModel = collected()

            repository.setChat(CHAT + 1, ChatWallpaperOverride(OWN, darkThemeDimming = 50))

            assertEquals(LIGHT to 20, viewModel.shown(dark = false))
        }

    private fun ChatWallpaperViewModel.shown(dark: Boolean): Pair<Wallpaper?, Int> =
        wallpaper.value.forTheme(dark).let { it.wallpaper to it.darkThemeDimming }

    private companion object {
        const val CHAT = 7L
        const val SEED = 0xFFB4744B.toInt()
        val LIGHT = Wallpaper.Fill(1, listOf(0xFFFFFF))
        val DARK = Wallpaper.Fill(2, listOf(0x000000))
        val OWN = Wallpaper.Photo(3, WallpaperImage(FileRef(30, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(4, WallpaperImage(FileRef(40, 0), isVector = true), 50, false)
    }
}

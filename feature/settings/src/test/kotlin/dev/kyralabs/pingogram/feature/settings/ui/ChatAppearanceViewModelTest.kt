package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAppearanceViewModelTest {
    private val repository = FakeAppearanceRepository()
    private val wallpapers = FakeWallpaperRepository()
    private val photos = FakePickedPhotoRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = ChatAppearanceViewModel(repository, wallpapers, photos).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: ChatAppearanceViewModel): List<ChatAppearanceEffect> {
        val effects = mutableListOf<ChatAppearanceEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val ChatAppearanceViewModel.content
        get() = uiState.value as ChatAppearanceUiState.Content

    private val ChatAppearanceViewModel.appearance
        get() = content.appearance

    @Test
    fun `is loading until the settings are read`() =
        runTest {
            assertEquals(ChatAppearanceUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun `shows the saved chat appearance`() =
        runTest {
            val saved = ChatAppearance(textScale = 1.2f, wallpaper = ChatWallpaper.Tertiary)
            repository.save(AppearanceSettings(chat = saved))

            assertEquals(saved, viewModel().appearance)
        }

    @Test
    fun `a text size is saved on its nearest step, within the range`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            viewModel.setTextScale(1.12f)
            runCurrent()
            assertEquals(1.1f, viewModel.appearance.textScale, STEP_TOLERANCE)

            viewModel.setTextScale(2f)
            runCurrent()
            assertEquals(ChatAppearance.MAX_TEXT_SCALE, viewModel.appearance.textScale, STEP_TOLERANCE)
        }

    @Test
    fun `corners and wallpaper are saved, keeping the rest of the appearance`() =
        runTest {
            repository.save(AppearanceSettings(themeMode = ThemeMode.Dark, chat = ChatAppearance(textScale = 0.9f)))
            val viewModel = viewModel()

            viewModel.setBubbleCorners(BubbleCorners.Large)
            runCurrent()
            viewModel.setWallpaper(ChatWallpaper.PrimaryToTertiary)
            runCurrent()

            val settings = repository.appearance.first()
            assertEquals(ThemeMode.Dark, settings.themeMode)
            assertEquals(
                ChatAppearance(0.9f, BubbleCorners.Large, ChatWallpaper.PrimaryToTertiary),
                settings.chat,
            )
        }

    @Test
    fun `a setting that cannot be saved shows an error and changes nothing`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val error = DomainError.Unknown(0, "disk full")
            repository.failure = error

            viewModel.setWallpaper(ChatWallpaper.Primary)
            runCurrent()

            assertEquals(listOf(ChatAppearanceEffect.ShowError(error)), effects)
            assertEquals(ChatWallpaper.None, viewModel.appearance.wallpaper)
        }

    @Test
    fun `shows the account's wallpapers, and applies a pick for the theme picked for`() =
        runTest {
            repository.save(AppearanceSettings())
            wallpapers.installedWallpapers = listOf(PHOTO)
            val viewModel = viewModel()

            viewModel.onWallpaperEvent(WallpaperEvent.ShowTheme(dark = true))
            viewModel.onWallpaperEvent(WallpaperEvent.Pick(PHOTO))
            viewModel.onWallpaperEvent(WallpaperEvent.Apply(emptyList()))
            runCurrent()

            assertEquals(AccountWallpapers(dark = PHOTO), viewModel.content.account)
            assertEquals(null, viewModel.content.picker.pending)
        }

    @Test
    fun `a theme-color wallpaper removes the account's for the theme picked for, keeping the other`() =
        runTest {
            repository.save(AppearanceSettings())
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = false)
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            val viewModel = viewModel()

            viewModel.onWallpaperEvent(WallpaperEvent.ShowTheme(dark = false))
            viewModel.setWallpaper(ChatWallpaper.Tertiary)
            runCurrent()

            assertEquals(ChatWallpaper.Tertiary, viewModel.appearance.wallpaper)
            assertEquals(AccountWallpapers(dark = PHOTO), viewModel.content.account)
        }

    @Test
    fun `the dark theme's dimming is saved`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()

            viewModel.setWallpaperDimming(40)
            runCurrent()

            assertEquals(40, viewModel.appearance.wallpaperDimming)
        }

    @Test
    fun `a wallpaper that cannot be removed says why`() =
        runTest {
            repository.save(AppearanceSettings())
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = false)
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            viewModel.onWallpaperEvent(WallpaperEvent.ShowTheme(dark = false))
            wallpapers.failure = DomainError.Network

            viewModel.onWallpaperEvent(WallpaperEvent.Remove)
            runCurrent()

            assertEquals(listOf(ChatAppearanceEffect.ShowError(DomainError.Network)), effects)
            assertEquals(PHOTO, viewModel.content.account.light)
        }

    private companion object {
        const val STEP_TOLERANCE = 0.001f
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
    }
}

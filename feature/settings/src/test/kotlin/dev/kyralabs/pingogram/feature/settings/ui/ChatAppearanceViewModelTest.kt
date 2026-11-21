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
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel() = ChatAppearanceViewModel(repository, wallpapers).also { runCurrent() }

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
    fun `corners are saved, keeping the rest of the appearance`() =
        runTest {
            val chat = ChatAppearance(textScale = 0.9f, wallpaper = ChatWallpaper.PrimaryToTertiary)
            repository.save(AppearanceSettings(themeMode = ThemeMode.Dark, chat = chat))
            val viewModel = viewModel()

            viewModel.setBubbleCorners(BubbleCorners.Large)
            runCurrent()

            val settings = repository.appearance.first()
            assertEquals(ThemeMode.Dark, settings.themeMode)
            assertEquals(chat.copy(bubbleCorners = BubbleCorners.Large), settings.chat)
        }

    @Test
    fun `a setting that cannot be saved shows an error and changes nothing`() =
        runTest {
            repository.save(AppearanceSettings())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val error = DomainError.Unknown(0, "disk full")
            repository.failure = error

            viewModel.setBubbleCorners(BubbleCorners.Medium)
            runCurrent()

            assertEquals(listOf(ChatAppearanceEffect.ShowError(error)), effects)
            assertEquals(BubbleCorners.ExtraLarge, viewModel.appearance.bubbleCorners)
        }

    @Test
    fun `shows the account's wallpapers for the preview and the Wallpaper row`() =
        runTest {
            repository.save(AppearanceSettings())
            wallpapers.setDefault(WallpaperChoice.Existing(PHOTO), forDarkTheme = true)
            val viewModel = viewModel()

            assertEquals(AccountWallpapers(dark = PHOTO), viewModel.content.account)
        }

    private companion object {
        const val STEP_TOLERANCE = 0.001f
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
    }
}

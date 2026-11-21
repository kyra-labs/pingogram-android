package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The wallpaper page's grid: theme colors on the account's page only, the one shown checked, picks and "Try again". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WallpaperGridTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private fun setGrid(state: WallpaperPageUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                LazyVerticalGrid(columns = GridCells.Adaptive(MaterialTheme.sizes.sharedMediaCell)) {
                    wallpaperGrid(
                        state = state,
                        onPick = { events += "pick ${it.id}" },
                        onThemeColor = { events += "color ${it.name}" },
                        onRetry = { events += "retry" },
                    )
                }
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `the account's page offers the theme colors, the one shown checked`() {
        setGrid(WallpaperPageUiState(isChat = false, saved = SavedWallpaper(themeColor = ChatWallpaper.Secondary)))

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_secondary))
            .assertIsSelected()
        composeRule.onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_primary)).performClick()

        assertEquals(listOf("color Primary"), events)
        composeRule.assertAccessible()
    }

    @Test
    fun `a chat's page has Telegram's wallpapers only, the picked one checked`() {
        val picker =
            WallpaperPickerState(
                installed = InstalledWallpapers.Loaded(persistentListOf(PHOTO, PATTERN)),
                pending = WallpaperChoice.Existing(PATTERN),
            )
        setGrid(WallpaperPageUiState(isChat = true, picker = picker, saved = SavedWallpaper(wallpaper = PHOTO)))

        composeRule.onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_none)).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_photo_number, 1))
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithContentDescription(string(R.string.settings_chat_wallpaper_pattern, 2)).assertIsSelected()

        assertEquals(listOf("pick 1"), events)
        composeRule.assertAccessible()
    }

    @Test
    fun `wallpapers that cannot load can be tried again`() {
        setGrid(
            WallpaperPageUiState(isChat = true, picker = WallpaperPickerState(installed = InstalledWallpapers.Failed)),
        )

        composeRule
            .onNodeWithText(string(R.string.settings_chat_wallpaper_installed_failed))
            .performScrollTo()
            .performClick()

        assertEquals(listOf("retry"), events)
        composeRule.assertAccessible()
    }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(11, 0)), intensity = 50, isInverted = false)
    }
}

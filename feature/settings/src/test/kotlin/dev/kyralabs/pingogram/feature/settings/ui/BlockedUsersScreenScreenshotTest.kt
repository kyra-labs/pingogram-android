package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Blocked users page in each of its states, and with the next page loading. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BlockedUsersScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.blockedUsers)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.blockedUsers)

    @Test
    fun loadingMoreLight() =
        capture(
            "loadingMoreLight",
            ThemeMode.Light,
            SettingsPreviewData.blockedUsers.copy(more = MoreBlockedSenders.Loading),
        )

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, BlockedUsersUiState.Empty)

    @Test
    fun emptyDark() = capture("emptyDark", ThemeMode.Dark, BlockedUsersUiState.Empty)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, BlockedUsersUiState.Loading)

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, BlockedUsersUiState.Loading)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, BlockedUsersUiState.Error(DomainError.Network))

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, BlockedUsersUiState.Error(DomainError.Network))

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: BlockedUsersUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        BlockedUsersScreen(state = state, onEvent = {}, onBack = {})
    }
}

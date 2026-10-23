package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * The Settings tab's list-detail layout (`SettingsListDetail`, `feature:settings`) on an expanded window: with no page
 * open, with About beside Settings home (its row selected), light and dark, and with the automatic downloads on Wi-Fi
 * opened from Data and storage in the detail pane (its back button showing). Nothing in them depends on the flavor.
 * The screens run their real view models on a [FakeTdlibClient] bound with Hilt.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w840dp-h891dp-xxhdpi")
class SettingsListDetailScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 1)
    val notificationsAllowed = NotificationsAllowed()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)

    @Test
    fun noPageOpenLight() = capture(ThemeMode.Light, Page.None)

    @Test
    fun pageOpenLight() = capture(ThemeMode.Light, Page.About)

    @Test
    fun pageOpenDark() = capture(ThemeMode.Dark, Page.About)

    @Test
    fun pageAbovePageLight() = capture(ThemeMode.Light, Page.AutoDownload)

    private enum class Page { None, About, AutoDownload }

    private fun capture(
        themeMode: ThemeMode,
        page: Page,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        composeRule.onNode(hasText(string(R.string.nav_settings)) and isSelectable()).performClick()
        await(hasText(string(SettingsR.string.settings_appearance_summary)))
        when (page) {
            Page.None -> {
                Unit
            }

            Page.About -> {
                row(SettingsR.string.settings_about).performClick()
                await(hasText(string(SettingsR.string.settings_about_licenses)))
            }

            Page.AutoDownload -> {
                row(SettingsR.string.settings_data_storage).performClick()
                await(hasText(string(SettingsR.string.settings_auto_download_wifi)) and hasClickAction())
                row(SettingsR.string.settings_auto_download_wifi).performScrollTo().performClick()
                await(hasContentDescription(string(SettingsR.string.settings_back)))
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private fun row(
        @StringRes title: Int,
    ) = composeRule.onNode(hasText(string(title)) and hasClickAction())

    /** The screens get their state from a background dispatcher. */
    private fun await(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

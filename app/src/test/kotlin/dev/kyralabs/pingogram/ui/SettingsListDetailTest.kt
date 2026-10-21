package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * The Settings tab's list-detail layout (`SettingsListDetail`, `feature:settings`) in the app shell: on an expanded
 * window a row shows its page beside Settings home instead of pushing it, and the pages it opens stay in the detail
 * pane; back returns through them, then closes the page; on medium and compact windows a row still pushes the page.
 * The screens run their real view models on a [FakeTdlibClient] bound with Hilt. Window sizes other than the
 * configured one come from a [DeviceConfigurationOverride]; saved state restoration stands in for the activity that a
 * resize recreates.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = EXPANDED)
class SettingsListDetailTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)

    private lateinit var navController: NavHostController

    /** The window size the app sees; null for the configured one. */
    private var windowSize by mutableStateOf<DpSize?>(null)

    @Test
    fun `with no page open, the detail pane asks to pick one`() {
        setApp()
        openSettings()

        text(SettingsR.string.settings_no_page_selected_title).assertIsDisplayed()
        text(SettingsR.string.settings_no_page_selected_body).assertIsDisplayed()
    }

    @Test
    fun `a row shows its page beside settings home, without pushing it or a back button`() {
        setApp()
        openSettings()

        row(SettingsR.string.settings_about).performClick()

        awaitText(SettingsR.string.settings_about_source_code)
        assertOnlySettingsTab()
        text(SettingsR.string.settings_appearance_summary).assertIsDisplayed()
        assertNoBackButton()
        composeRule.onAllNodes(hasText(string(SettingsR.string.settings_no_page_selected_title))).assertCountEquals(0)
    }

    @Test
    fun `the open page's row reads as selected, and only it`() {
        setApp()
        openSettings()

        row(SettingsR.string.settings_about).performClick()
        awaitText(SettingsR.string.settings_about_source_code)

        row(SettingsR.string.settings_about).assertIsSelected()
        row(SettingsR.string.settings_appearance).assert(isMarkedSelected().not())
    }

    @Test
    fun `another row swaps the page`() {
        setApp()
        openSettings()
        row(SettingsR.string.settings_about).performClick()
        awaitText(SettingsR.string.settings_about_source_code)

        row(SettingsR.string.settings_data_storage).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText(string(SettingsR.string.settings_about_source_code))).assertCountEquals(0)
        row(SettingsR.string.settings_data_storage).assertIsSelected()
        assertOnlySettingsTab()
    }

    @Test
    fun `a page opened from the page stays in the detail pane, and back returns through both`() {
        setApp()
        openSettings()
        row(SettingsR.string.settings_about).performClick()
        awaitText(SettingsR.string.settings_about_licenses)

        text(SettingsR.string.settings_about_licenses).performScrollTo().performClick()

        awaitNode(hasScrollToKeyAction())
        assertOnlySettingsTab()
        text(SettingsR.string.settings_appearance_summary).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(string(SettingsR.string.settings_back))).assertIsDisplayed()
        pressBack()
        awaitText(SettingsR.string.settings_about_source_code)
        assertNoBackButton()
        pressBack()
        awaitText(SettingsR.string.settings_no_page_selected_title)
        row(SettingsR.string.settings_about).assert(isMarkedSelected().not())
        assertOnlySettingsTab()
    }

    @Test
    fun `the open page and the page above it survive saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        openSettings()
        row(SettingsR.string.settings_about).performClick()
        awaitText(SettingsR.string.settings_about_licenses)
        text(SettingsR.string.settings_about_licenses).performScrollTo().performClick()
        awaitNode(hasScrollToKeyAction())

        restorationTester.emulateSavedInstanceStateRestore()

        awaitNode(hasScrollToKeyAction())
        row(SettingsR.string.settings_about).assertIsSelected()
        pressBack()
        awaitText(SettingsR.string.settings_about_source_code)
    }

    @Test
    fun `narrower, the open page shows alone with its back button, which returns to settings home`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        openSettings()
        row(SettingsR.string.settings_about).performClick()
        awaitText(SettingsR.string.settings_about_source_code)

        // A resize recreates the activity.
        windowSize = COMPACT_SIZE
        restorationTester.emulateSavedInstanceStateRestore()

        awaitText(SettingsR.string.settings_about_source_code)
        composeRule.onAllNodes(hasText(string(SettingsR.string.settings_appearance_summary))).assertCountEquals(0)
        assertOnlySettingsTab()
        composeRule.onNode(hasContentDescription(string(SettingsR.string.settings_back))).performClick()
        composeRule.waitForIdle()
        awaitText(SettingsR.string.settings_appearance_summary)
        tab(R.string.nav_settings).assertIsSelected()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `on a medium window a row still pushes the page full screen`() {
        setApp()
        openSettings()

        text(SettingsR.string.settings_about).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<AboutRoute>() == true)
        composeRule.onNode(hasContentDescription(string(SettingsR.string.settings_back))).assertIsDisplayed()
    }

    private fun setApp() = composeRule.setContent { App() }

    /** Always inside the override, so a new window size keeps the composition's structure, and its saved state. */
    @Composable
    private fun App() {
        val size = windowSize ?: LocalWindowInfo.current.containerDpSize
        DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(size)) {
            PingogramTheme(dynamicColor = false) {
                PingogramApp(
                    uiState = MainActivityUiState.SignedIn,
                    navController = rememberNavController().also { navController = it },
                )
            }
        }
    }

    private fun openSettings() {
        tab(R.string.nav_settings).performClick()
        composeRule.waitForIdle()
        awaitText(SettingsR.string.settings_appearance_summary)
    }

    /** The Settings tab shows: nothing was pushed above it. */
    private fun assertOnlySettingsTab() {
        assertTrue(navController.currentDestination?.hasRoute<SettingsRoute>() == true)
    }

    private fun assertNoBackButton() {
        composeRule.onAllNodes(hasContentDescription(string(SettingsR.string.settings_back))).assertCountEquals(0)
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** A row of Settings home, which opens a page. */
    private fun row(
        @StringRes title: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(string(title)) and hasClickAction())

    private fun awaitText(
        @StringRes id: Int,
    ) = awaitNode(hasText(string(id)))

    /** Waits for [matcher]: the screens get their state from a background dispatcher. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private fun tab(
        @StringRes label: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun text(
        @StringRes id: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(string(id)))

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        val COMPACT_SIZE = DpSize(411.dp, 891.dp)
    }
}

/** Says it is selected; unlike `isSelected()`, matches nodes without the property too, as not selected. */
private fun isMarkedSelected() =
    SemanticsMatcher("is marked selected") { it.config.getOrNull(SemanticsProperties.Selected) == true }

private const val EXPANDED = "w840dp-h891dp-xxhdpi"
private const val MEDIUM = "w700dp-h891dp-xxhdpi"

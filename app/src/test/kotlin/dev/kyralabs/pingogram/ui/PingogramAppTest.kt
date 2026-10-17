package dev.kyralabs.pingogram.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chats.R as ChatsR
import dev.kyralabs.pingogram.feature.contacts.R as ContactsR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/** Top-level navigation of [PingogramApp]. A plain [Application] keeps app start-up work out of UI tests. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PingogramAppTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    @Test
    fun `starts on the chats tab`() {
        setApp()

        tab(R.string.nav_chats).assertIsSelected().assertHasClickAction()
        tab(R.string.nav_contacts).assertIsNotSelected().assertHasClickAction()
        tab(R.string.nav_settings).assertIsNotSelected().assertHasClickAction()
        text(ChatsR.string.chats_placeholder_title).assertIsDisplayed()
    }

    @Test
    fun `selecting a tab navigates to its destination and selects it`() {
        setApp()

        tab(R.string.nav_contacts).performClick()

        assertTrue(navController.currentDestination?.hasRoute<ContactsRoute>() == true)
        tab(R.string.nav_contacts).assertIsSelected()
        tab(R.string.nav_chats).assertIsNotSelected()
        text(ContactsR.string.contacts_placeholder_title).assertIsDisplayed()
        text(ChatsR.string.chats_placeholder_title).assertDoesNotExist()
    }

    @Test
    fun `switching tabs keeps only chats below the current tab`() {
        setApp()

        tab(R.string.nav_contacts).performClick()
        tab(R.string.nav_settings).performClick()
        tab(R.string.nav_contacts).performClick()

        val destinations =
            navController.currentBackStack.value
                .map { it.destination }
                .filterNot { it is NavGraph }
        assertEquals(2, destinations.size)
        assertTrue(destinations[0].hasRoute<ChatsRoute>())
        assertTrue(destinations[1].hasRoute<ContactsRoute>())
    }

    @Test
    fun `back from another tab returns to chats`() {
        setApp()
        tab(R.string.nav_settings).performClick()
        // Let the NavHost recompose, which enables its back handler now that there is something to pop.
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        tab(R.string.nav_chats).assertIsSelected()
        text(ChatsR.string.chats_placeholder_title).assertIsDisplayed()
    }

    @Test
    fun `selected tab survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        tab(R.string.nav_settings).performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        tab(R.string.nav_settings).assertIsSelected()
        tab(R.string.nav_chats).assertIsNotSelected()
        text(SettingsR.string.settings_placeholder_title).assertIsDisplayed()
    }

    private fun setApp() = composeRule.setContent { App() }

    @Composable
    private fun App() {
        PingogramTheme(dynamicColor = false) {
            PingogramApp(navController = rememberNavController().also { navController = it })
        }
    }

    private fun tab(
        @StringRes label: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun text(
        @StringRes id: Int,
    ): SemanticsNodeInteraction = composeRule.onNodeWithText(string(id))

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}

/**
 * Declares [ComponentActivity] to Robolectric so the compose rule can launch it. The usual
 * `ui-test-manifest` would have to be a debug dependency of the app, which ships the activity in debug APKs.
 */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.packageManager)
            .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
    }
}

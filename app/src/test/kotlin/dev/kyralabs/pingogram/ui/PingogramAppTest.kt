package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.auth.R as AuthR
import dev.kyralabs.pingogram.feature.chats.R as ChatsR
import dev.kyralabs.pingogram.feature.contacts.R as ContactsR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * Navigation of [PingogramApp]: the top-level tabs, and the switch between the login flow and the signed-in app that
 * follows [MainActivityUiState] (FR-A8), set directly here. The destinations run their real view models and
 * repositories on a [FakeTdlibClient] bound with Hilt.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PingogramAppTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.WaitPhoneNumber, connectionState = ConnectionState.Ready).apply {
            on(TdApi.LogOut::class, TdResult.Success(TdApi.Ok()))
            // TDLib's answer once a chat list is loaded completely: the chat list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
        }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.SignedIn)

    private lateinit var navController: NavHostController

    // Top-level tabs

    @Test
    fun `starts on the chats tab`() {
        setApp()

        tab(R.string.nav_chats).assertIsSelected().assertHasClickAction()
        tab(R.string.nav_contacts).assertIsNotSelected().assertHasClickAction()
        tab(R.string.nav_settings).assertIsNotSelected().assertHasClickAction()
        awaitText(ChatsR.string.chat_list_empty_title)
    }

    @Test
    fun `selecting a tab navigates to its destination and selects it`() {
        setApp()

        tab(R.string.nav_contacts).performClick()

        assertTrue(navController.currentDestination?.hasRoute<ContactsRoute>() == true)
        tab(R.string.nav_contacts).assertIsSelected()
        tab(R.string.nav_chats).assertIsNotSelected()
        text(ContactsR.string.contacts_placeholder_title).assertIsDisplayed()
        text(ChatsR.string.chat_list_empty_title).assertDoesNotExist()
    }

    @Test
    fun `switching tabs keeps only chats below the current tab`() {
        setApp()

        tab(R.string.nav_contacts).performClick()
        tab(R.string.nav_settings).performClick()
        tab(R.string.nav_contacts).performClick()

        val destinations = backStackDestinations()
        assertEquals(2, destinations.size)
        assertTrue(destinations[0].hasRoute<ChatsRoute>())
        assertTrue(destinations[1].hasRoute<ContactsRoute>())
    }

    @Test
    fun `returning to a tab restores its saved entry`() {
        setApp()
        tab(R.string.nav_contacts).performClick()
        val contactsEntry = navController.currentBackStackEntry?.id

        tab(R.string.nav_chats).performClick()
        tab(R.string.nav_contacts).performClick()

        assertEquals(contactsEntry, navController.currentBackStackEntry?.id)
    }

    @Test
    fun `back from another tab returns to chats`() {
        setApp()
        tab(R.string.nav_settings).performClick()
        // Let the NavHost recompose, which enables its back handler now that there is something to pop.
        composeRule.waitForIdle()

        pressBack()

        tab(R.string.nav_chats).assertIsSelected()
        awaitText(ChatsR.string.chat_list_empty_title)
    }

    @Test
    fun `selected tab survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        tab(R.string.nav_settings).performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        tab(R.string.nav_settings).assertIsSelected()
        tab(R.string.nav_chats).assertIsNotSelected()
        text(SettingsR.string.settings_log_out).assertIsDisplayed()
    }

    // Search (FR-L6)

    @Test
    fun `the search button opens search full screen`() {
        setApp()

        composeRule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<SearchRoute>() == true)
        text(ChatsR.string.search_placeholder_title).assertIsDisplayed()
        assertNoNavigation()
    }

    @Test
    fun `back from search returns to the tab and its navigation bar`() {
        setApp()
        tab(R.string.nav_contacts).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(string(ChatsR.string.search_back)).performClick()
        composeRule.waitForIdle()

        tab(R.string.nav_contacts).assertIsSelected()
        text(ContactsR.string.contacts_placeholder_title).assertIsDisplayed()
    }

    // Signed in or out (FR-A8)

    @Test
    fun `shows progress and no graph until the login state is known`() {
        uiState = MainActivityUiState.Loading
        setApp()

        composeRule.onNodeWithContentDescription(string(AuthR.string.auth_loading)).assertIsDisplayed()
        assertNull(navController.currentDestination)
        assertNoNavigation()

        uiState = MainActivityUiState.SignedOut

        awaitText(AuthR.string.auth_start_messaging)
    }

    @Test
    fun `signed out shows the login flow full screen`() {
        uiState = MainActivityUiState.SignedOut
        setApp()

        awaitText(AuthR.string.auth_start_messaging)
        assertTrue(navController.currentDestination.isAuthRoute())
        assertNoNavigation()
    }

    @Test
    fun `logging in lands on chats with no way back into the login flow`() {
        uiState = MainActivityUiState.SignedOut
        setApp()
        awaitText(AuthR.string.auth_start_messaging)

        uiState = MainActivityUiState.SignedIn
        composeRule.waitForIdle()

        tab(R.string.nav_chats).assertIsSelected()
        awaitText(ChatsR.string.chat_list_empty_title)
        assertEquals(1, backStackDestinations().size)
        pressBack()
        assertTrue("Back leaves the app", composeRule.activity.isFinishing)
    }

    @Test
    fun `logging out in settings returns to a new login flow`() {
        setApp()
        tab(R.string.nav_settings).performClick()

        text(SettingsR.string.settings_log_out).performClick()
        composeRule
            .onNode(hasText(string(SettingsR.string.settings_log_out_dialog_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, tdlib.sentOf<TdApi.LogOut>().size)

        // TDLib reports that it is logging out.
        tdlib.authState.value = AuthState.LoggingOut
        uiState = MainActivityUiState.SignedOut

        awaitText(AuthR.string.auth_logging_out)
        assertNoNavigation()
        assertEquals(listOf(true), backStackDestinations().map { it.isAuthRoute() })
    }

    @Test
    fun `logging out drops the saved tabs of the account`() {
        setApp()
        tab(R.string.nav_contacts).performClick()
        val contactsEntry = navController.currentBackStackEntry?.id
        tab(R.string.nav_chats).performClick()

        uiState = MainActivityUiState.SignedOut
        composeRule.waitForIdle()
        uiState = MainActivityUiState.SignedIn
        composeRule.waitForIdle()
        tab(R.string.nav_contacts).performClick()

        assertNotEquals(contactsEntry, navController.currentBackStackEntry?.id)
        assertEquals(2, backStackDestinations().size)
    }

    @Test
    fun `the login flow survives saved state restoration`() {
        uiState = MainActivityUiState.SignedOut
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        awaitText(AuthR.string.auth_start_messaging)

        restorationTester.emulateSavedInstanceStateRestore()

        awaitText(AuthR.string.auth_start_messaging)
        assertTrue(navController.currentDestination.isAuthRoute())
        assertNoNavigation()
    }

    private fun setApp() = composeRule.setContent { App() }

    @Composable
    private fun App() {
        PingogramTheme(dynamicColor = false) {
            PingogramApp(uiState = uiState, navController = rememberNavController().also { navController = it })
        }
    }

    /** The destinations on the back stack, without the graphs around them. */
    private fun backStackDestinations(): List<NavDestination> =
        navController.currentBackStack.value
            .map { it.destination }
            .filterNot { it is NavGraph }

    private fun NavDestination?.isAuthRoute() = this?.hasRoute<AuthRoute>() == true

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun assertNoNavigation() {
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)
    }

    /** Waits for [id]'s text: the login flow and the chat list get their state from a background dispatcher. */
    private fun awaitText(
        @StringRes id: Int,
    ) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().isNotEmpty()
        }
        text(id).assertIsDisplayed()
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

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

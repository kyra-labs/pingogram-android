package dev.kyralabs.pingogram.ui

import androidx.activity.BackEventCompat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.AboutRoute
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.AuthRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLibraryRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLicensesRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.core.navigation.SettingsRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.component.HideNavigationBar
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.component.NavigationBarVisibility
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.coroutines.runBlocking
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
import dev.kyralabs.pingogram.feature.chat.R as ChatR
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
    fun `the search button opens search full screen, its field focused for typing`() {
        setApp()

        composeRule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<SearchRoute>() == true)
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        // Nothing searched or opened yet: the hint.
        awaitText(ChatsR.string.search_hint_title)
        assertNoNavigation()
    }

    @Test
    fun `a chat found by search opens above it, joins the recent chats, and back returns to the results`() {
        searchFinds(chatId = FOUND_CHAT)
        tdlib.on(TdApi.AddRecentlyFoundChat::class, TdResult.Success(TdApi.Ok()))
        setApp()
        search("ada")

        awaitNode(hasContentDescription(FOUND_TITLE))
        composeRule.onNode(hasContentDescription(FOUND_TITLE)).performClick()
        composeRule.waitForIdle()

        assertEquals(ChatRoute(FOUND_CHAT), navController.currentBackStackEntry?.toRoute<ChatRoute>())
        composeRule.waitUntil(TIMEOUT_MILLIS) { tdlib.sentOf<TdApi.AddRecentlyFoundChat>().isNotEmpty() }
        assertEquals(FOUND_CHAT, tdlib.sentOf<TdApi.AddRecentlyFoundChat>().single().chatId)
        pressBack()
        composeRule.waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute<SearchRoute>() == true)
        awaitNode(hasContentDescription(FOUND_TITLE))
    }

    @Test
    fun `a message found by search opens its conversation at the message`() {
        searchFinds(messageId = FOUND_MESSAGE, chatId = FOUND_CHAT)
        setApp()
        search("ada")

        val row = hasContentDescription("Ada in a message", substring = true)
        awaitNode(row)
        composeRule.onNode(row).performClick()
        composeRule.waitForIdle()

        assertEquals(
            ChatRoute(FOUND_CHAT, messageId = FOUND_MESSAGE),
            navController.currentBackStackEntry?.toRoute<ChatRoute>(),
        )
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

    // The archive (FR-L9)

    @Test
    fun `the archive opens above the chats tab, without the navigation bar`() {
        setApp()

        composeRule.runOnUiThread { navController.navigate(ArchiveRoute) }

        awaitText(ChatsR.string.chat_list_archive_empty_title)
        assertNoNavigation()
        composeRule.onNodeWithContentDescription(string(ChatsR.string.chat_list_archive_back)).performClick()
        composeRule.waitForIdle()
        tab(R.string.nav_chats).assertIsSelected()
    }

    // Settings pages (FR-S1)

    @Test
    fun `settings pages open above the settings tab, without the navigation bar`() {
        setApp()
        tab(R.string.nav_settings).performClick()

        text(SettingsR.string.settings_data_storage).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<DataStorageRoute>() == true)
        assertNoNavigation()
        composeRule.onNodeWithContentDescription(string(SettingsR.string.settings_back)).performClick()
        composeRule.waitForIdle()
        tab(R.string.nav_settings).assertIsSelected()

        text(SettingsR.string.settings_about).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<AboutRoute>() == true)
        assertNoNavigation()
        pressBack()
        tab(R.string.nav_settings).assertIsSelected()

        text(SettingsR.string.settings_appearance).performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<AppearanceRoute>() == true)
        // The page's segmented buttons are selectable: only tabs count here.
        composeRule
            .onAllNodes(isSelectable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertCountEquals(0)
        pressBack()
        tab(R.string.nav_settings).assertIsSelected()
    }

    @Test
    fun `about opens the open-source licenses, and they open a library's page with its license`() {
        setApp()
        tab(R.string.nav_settings).performClick()
        text(SettingsR.string.settings_about).performClick()
        composeRule.waitForIdle()

        text(SettingsR.string.settings_about_licenses).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<OpenSourceLicensesRoute>() == true)
        assertNoNavigation()
        // The list comes from the build's raw resource, read on the IO dispatcher.
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasScrollToKeyAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("org.drinkless:tdlib")
        composeRule.onNodeWithText("TDLib").performClick()
        composeRule.waitForIdle()

        val route = navController.currentBackStackEntry?.toRoute<OpenSourceLibraryRoute>()
        assertEquals("org.drinkless:tdlib", route?.libraryId)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Boost Software License 1.0").fetchSemanticsNodes().isNotEmpty()
        }
        pressBack()
        composeRule.waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute<OpenSourceLicensesRoute>() == true)
    }

    @Test
    fun `privacy and security opens above the settings tab, and its pages above it`() {
        setApp()
        tab(R.string.nav_settings).performClick()

        text(SettingsR.string.settings_privacy_security).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<PrivacySecurityRoute>() == true)
        assertNoNavigation()

        for (route in listOf(BlockedUsersRoute, ActiveSessionsRoute, PrivacySettingRoute("Calls"))) {
            composeRule.runOnUiThread { navController.navigate(route) }
            composeRule.waitForIdle()

            // A privacy setting's choices are selectable: only tabs count here.
            composeRule
                .onAllNodes(isSelectable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .assertCountEquals(0)
            pressBack()
            composeRule.waitForIdle()
            assertTrue(navController.currentDestination?.hasRoute<PrivacySecurityRoute>() == true)
        }
        text(SettingsR.string.settings_privacy_security).assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()
        tab(R.string.nav_settings).assertIsSelected()
    }

    @Test
    fun `edit profile opens above the settings tab, and saving returns to it with a snackbar`() {
        val me = TdApiFixtures.user(id = 42, firstName = "Ada", lastName = "Lovelace", usernames = listOf("ada"))
        tdlib.on(TdApi.GetMe::class, TdResult.Success(me))
        tdlib.on(TdApi.GetUserFullInfo::class, TdResult.Success(ProfileFixtures.userFullInfo(bio = "Analyst")))
        tdlib.on(TdApi.SetBio::class, TdResult.Success(TdApi.Ok()))
        setApp()
        tab(R.string.nav_settings).performClick()
        composeRule.waitForIdle()
        // The Settings tab's view model has created the user repository, which takes TDLib's updates from now on.
        runBlocking {
            tdlib.emit(TdApiFixtures.updateUser(me))
            tdlib.emit(TdApiFixtures.myIdOption(42))
        }
        // The account's card, which opens Edit profile.
        val account = hasText("Ada Lovelace") and hasClickAction()
        awaitNode(account)

        composeRule.onNode(account).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<EditProfileRoute>() == true)
        assertNoNavigation()
        val bio =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and
                hasText(string(SettingsR.string.settings_profile_bio))
        awaitNode(bio)
        composeRule.onNode(bio).performScrollTo().performTextReplacement("Countess")
        text(SettingsR.string.settings_edit_profile_save).performClick()

        awaitText(SettingsR.string.settings_profile_saved)
        assertTrue(navController.currentDestination?.hasRoute<SettingsRoute>() == true)
        assertEquals(listOf("Countess"), tdlib.sentOf<TdApi.SetBio>().map { it.bio })
    }

    @Test
    fun `a privacy setting this version does not know goes back`() {
        setApp()
        tab(R.string.nav_settings).performClick()
        composeRule.runOnUiThread { navController.navigate(PrivacySecurityRoute) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { navController.navigate(PrivacySettingRoute("SomethingNew")) }
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<PrivacySecurityRoute>() == true)
    }

    // A conversation (FR-C1)

    @Test
    fun `a conversation opens above the chats tab, without the navigation bar`() {
        // TDLib does not know the chat.
        tdlib.on(TdApi.GetChat::class, TdResult.Failure(TdError(400, "Chat not found")))
        setApp()

        composeRule.runOnUiThread { navController.navigate(ChatRoute(chatId = 5)) }

        awaitText(ChatR.string.chat_not_found_title)
        assertTrue(navController.currentDestination?.hasRoute<ChatRoute>() == true)
        assertNoNavigation()
        composeRule.onNodeWithContentDescription(string(ChatR.string.chat_back)).performClick()
        composeRule.waitForIdle()
        tab(R.string.nav_chats).assertIsSelected()
    }

    @Test
    fun `a predictive back gesture shrinks the conversation over the chat list, then leaves it`() {
        tdlib.on(TdApi.GetChat::class, TdResult.Failure(TdError(400, "Chat not found")))
        setApp()
        composeRule.runOnUiThread { navController.navigate(ChatRoute(chatId = 5)) }
        awaitText(ChatR.string.chat_not_found_title)
        val resting = text(ChatR.string.chat_not_found_title).fetchSemanticsNode().boundsInRoot.width
        val dispatcher = composeRule.activity.onBackPressedDispatcher

        composeRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, HALF_GESTURE, BackEventCompat.EDGE_LEFT))
        }
        awaitText(ChatsR.string.chat_list_empty_title)

        // Navigation's default shrinks it to 78% by half the gesture, on its way to 70%.
        val scale = text(ChatR.string.chat_not_found_title).fetchSemanticsNode().boundsInRoot.width / resting
        assertTrue("$scale", scale in PREDICTIVE_BACK_SCALE_RANGE)
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
        text(ChatR.string.chat_not_found_title).assertDoesNotExist()
        tab(R.string.nav_chats).assertIsSelected()
    }

    // A screen hiding the floating navigation bar, as the chat list does while chats are selected

    @Test
    fun `a screen can hide the floating navigation bar until it stops asking`() {
        val visibility = NavigationBarVisibility()
        var hide by mutableStateOf(true)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PingogramApp(
                    uiState = uiState,
                    navController = rememberNavController().also { navController = it },
                    navigationBarVisibility = visibility,
                )
                CompositionLocalProvider(LocalNavigationBarVisibility provides visibility) {
                    if (hide) HideNavigationBar(hidden = true)
                }
            }
        }
        awaitText(ChatsR.string.chat_list_empty_title)
        assertNoNavigation()

        hide = false
        composeRule.waitForIdle()

        tab(R.string.nav_chats).assertIsSelected()
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

    /**
     * TDLib's searches find chat [chatId], [FOUND_TITLE], on the device and its message [messageId] (if any), and
     * nothing else.
     */
    private fun searchFinds(
        chatId: Long,
        messageId: Long? = null,
    ) {
        val noChats = TdResult.Success(TdApi.Chats(0, LongArray(0)))
        tdlib.on(TdApi.SearchChats::class, TdResult.Success(TdApi.Chats(1, longArrayOf(chatId))))
        tdlib.on(TdApi.SearchChatsOnServer::class, noChats)
        tdlib.on(TdApi.SearchPublicChats::class, noChats)
        tdlib.on(TdApi.SearchContacts::class, TdResult.Success(TdApi.Users(0, LongArray(0))))
        tdlib.on(TdApi.GetChat::class) { request ->
            TdResult.Success(TdApiFixtures.chat(request.chatId, title = FOUND_TITLE))
        }
        val found =
            listOfNotNull(messageId).map { id ->
                TdApiFixtures.message(id = id, chatId = chatId, content = TdApiFixtures.messageText("Ada in a message"))
            }
        val messages = TdApi.FoundMessages(found.size, found.toTypedArray(), "")
        tdlib.on(TdApi.SearchMessages::class, TdResult.Success(messages))
    }

    /** Opens search from the navigation bar and types [query]. */
    private fun search(query: String) {
        composeRule.onNodeWithContentDescription(string(R.string.nav_search)).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    /** Waits for a node matching [matcher]: search answers from a background dispatcher, after its debounce. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

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
        const val HALF_GESTURE = 0.5f

        /** How far a predictive back gesture has shrunk the screen it leaves by [HALF_GESTURE]: to about 92%. */
        val PREDICTIVE_BACK_SCALE_RANGE = 0.9f..0.95f

        /** The chat and message search finds. */
        const val FOUND_CHAT = 5L
        const val FOUND_MESSAGE = 7L
        const val FOUND_TITLE = "Ada Lovelace"
    }
}

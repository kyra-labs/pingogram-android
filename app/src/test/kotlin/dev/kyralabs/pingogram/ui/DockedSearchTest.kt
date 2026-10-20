package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.SearchRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE_TITLE
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * Global search docked in the Chats tab's list pane on expanded windows (`docs/DESIGN_SYSTEM.md` §6 "Global search"):
 * the search button opens it in the list pane without pushing anything, from any tab; results open beside it in the
 * detail pane; back closes the conversation first, then search. On a medium window search stays a full-screen
 * destination. The screens run their real view models and repositories on a [FakeTdlibClient] with the chats of
 * [ChatsScript], search finding Ada's chat and an older message of hers.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w840dp-h891dp-xxhdpi")
class DockedSearchTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val foundMessage = ChatsScript.olderMessageOf(ADA)

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)
            .apply {
                val noChats = TdResult.Success(TdApi.Chats(0, LongArray(0)))
                on(TdApi.SearchChats::class, TdResult.Success(TdApi.Chats(1, longArrayOf(ADA))))
                on(TdApi.SearchChatsOnServer::class, noChats)
                on(TdApi.SearchPublicChats::class, noChats)
                on(TdApi.SearchContacts::class, TdResult.Success(TdApi.Users(0, LongArray(0))))
                on(TdApi.SearchRecentlyFoundChats::class, noChats)
                on(TdApi.GetTopChats::class, noChats)
                on(TdApi.AddRecentlyFoundChat::class, TdResult.Success(TdApi.Ok()))
                on(TdApi.SearchMessages::class, TdResult.Success(TdApi.FoundMessages(1, arrayOf(foundMessage), "")))
            }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var navController: NavHostController

    @Test
    fun `the search button opens search in the list pane, without pushing it, and steps aside`() {
        setApp()
        awaitRow(ADA_TITLE)

        searchButton().performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        awaitText(ChatsR.string.search_hint_title)
        assertOnlyChatsTab()
        // The detail pane stays beside it, and the button is away while search shows.
        text(R.string.chats_no_chat_selected_title).assertIsDisplayed()
        composeRule.onAllNodes(searchButtonMatcher()).assertCountEquals(0)
    }

    @Test
    fun `a chat found opens beside search, which stays`() {
        setApp()
        search("ada")

        awaitNode(hasContentDescription(ADA_TITLE) and hasClickAction())
        composeRule.onNode(hasContentDescription(ADA_TITLE) and hasClickAction()).performClick()

        awaitMessage()
        composeRule.waitForIdle()
        assertOnlyChatsTab()
        searchResults().assertIsDisplayed()
        assertEquals(listOf("+$ADA"), ChatsScript.openAndClose(tdlib))
        composeRule.onAllNodes(hasText(string(R.string.chats_no_chat_selected_title))).assertCountEquals(0)
    }

    @Test
    fun `a message found opens its conversation beside search`() {
        setApp()
        search("engine")
        val row = hasContentDescription(ChatsScript.olderMessage.getValue(ADA), substring = true) and hasClickAction()
        awaitNode(row)

        composeRule.onNode(row).performClick()

        awaitMessage()
        composeRule.waitForIdle()
        assertOnlyChatsTab()
        searchResults().assertIsDisplayed()
    }

    @Test
    fun `back closes the conversation beside search first, then search`() {
        setApp()
        search("ada")
        awaitNode(hasContentDescription(ADA_TITLE) and hasClickAction())
        composeRule.onNode(hasContentDescription(ADA_TITLE) and hasClickAction()).performClick()
        awaitMessage()

        pressBack()

        awaitText(R.string.chats_no_chat_selected_title)
        assertEquals(listOf("+$ADA", "-$ADA"), ChatsScript.openAndClose(tdlib))
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        pressBack()
        awaitRow(GRACE_TITLE)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        searchButton().assertIsDisplayed()
    }

    @Test
    fun `the back arrow closes search, back to the chat list`() {
        setApp()
        searchButton().performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription(string(ChatsR.string.search_back))).performClick()

        awaitRow(GRACE_TITLE)
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun `from another tab, search opens docked in the Chats tab`() {
        setApp()
        awaitRow(ADA_TITLE)
        composeRule.onNode(hasText(string(R.string.nav_settings)) and isSelectable()).performClick()
        composeRule.waitForIdle()

        searchButton().performClick()
        composeRule.waitForIdle()

        assertOnlyChatsTab()
        awaitText(ChatsR.string.search_hint_title)
    }

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun `on a medium window search is pushed full screen`() {
        setApp()
        awaitRow(ADA_TITLE)

        searchButton().performClick()
        composeRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<SearchRoute>() == true)
        awaitText(ChatsR.string.search_hint_title)
    }

    private fun setApp() =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PingogramApp(
                    uiState = MainActivityUiState.SignedIn,
                    navController = rememberNavController().also { navController = it },
                )
            }
        }

    /** Opens search and types [query]. */
    private fun search(query: String) {
        awaitRow(ADA_TITLE)
        searchButton().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    /** Search's results, by their Chats heading: the conversation beside them has a text field too. */
    private fun searchResults() =
        composeRule.onNode(
            hasText(string(ChatsR.string.search_section_chats)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        )

    private fun searchButton() = composeRule.onNode(searchButtonMatcher())

    /** The search button, not the search field, which bears the same name. */
    private fun searchButtonMatcher() = hasContentDescription(string(R.string.nav_search)) and !hasSetTextAction()

    /** The Chats tab is the only destination: nothing was pushed. */
    private fun assertOnlyChatsTab() {
        val destinations =
            navController.currentBackStack.value
                .map { it.destination }
                .filterNot { it is NavGraph }
        assertEquals(1, destinations.size)
        assertTrue(destinations.single().hasRoute<ChatsRoute>())
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun awaitRow(title: String) = awaitNode(hasContentDescription(title, substring = true) and hasClickAction())

    /** Ada's conversation shows in the detail pane: it opened her chat (her messages also show among the results). */
    private fun awaitMessage() = composeRule.waitUntil(TIMEOUT_MILLIS) { "+$ADA" in ChatsScript.openAndClose(tdlib) }

    private fun awaitText(
        @StringRes id: Int,
    ) = awaitNode(hasText(string(id)))

    /** Waits for [matcher]: the screens get their state from a background dispatcher. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun text(
        @StringRes id: Int,
    ) = composeRule.onNode(hasText(string(id)))

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

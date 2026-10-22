package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.ProfileRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.navigation.navigateToProfile
import dev.kyralabs.pingogram.navigation.openLinkTarget
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE_TITLE
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.profile.R as ProfileR

/**
 * The Chats tab's third pane ([ChatsExtraPane], `docs/DESIGN_SYSTEM.md` §7) and chats that other screens open in its
 * detail pane: on an extra-large window the conversation's title opens the profile beside the conversation instead of
 * pushing [ProfileRoute], back closes it first, it survives saved state restoration, and its drag handle resizes it;
 * on narrower windows the profile is still pushed, and on expanded ones a chat from a deep link or from a profile's
 * "Message" shows in the detail pane. The screens run their real view models and repositories on a [FakeTdlibClient]
 * bound with Hilt, with the chats of [ChatsScript].
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = EXTRA_LARGE)
class ChatsExtraPaneTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)
            .also {
                it.on(TdApi.GetUserFullInfo::class) { request ->
                    TdResult.Success(ProfileFixtures.userFullInfo(bio = bios.getValue(request.userId)))
                }
                it.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)))
                it.on(TdApi.GetUserProfilePhotos::class, TdResult.Success(TdApi.ChatPhotos(0, emptyArray())))
            }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var navController: NavHostController

    // Extra-large

    @Test
    fun `the conversation's title opens the profile beside it, without pushing it`() {
        setApp()
        openChat(ADA_TITLE, ADA)

        title().performClick()

        awaitText(bios.getValue(ADA))
        message(ADA).assertIsDisplayed()
        row(GRACE_TITLE).assertIsDisplayed()
        assertOnlyChatsTab()
    }

    @Test
    fun `back closes the profile first, then the conversation`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))

        pressBack()

        assertNoText(bios.getValue(ADA))
        message(ADA).assertIsDisplayed()
        pressBack()
        awaitText(R.string.chats_no_chat_selected_title)
        assertNoMessage(ADA)
    }

    @Test
    fun `the profile's back button closes it`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))

        composeRule.onNode(hasContentDescription(string(ProfileR.string.profile_back))).performClick()
        composeRule.waitForIdle()

        assertNoText(bios.getValue(ADA))
        message(ADA).assertIsDisplayed()
    }

    @Test
    fun `another chat from the list closes the profile`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))

        row(GRACE_TITLE).performClick()

        awaitMessage(GRACE)
        assertNoText(bios.getValue(ADA))
        assertNoMessage(ADA)
    }

    @Test
    fun `the profile's Message shows that chat in the detail pane and closes the profile`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))

        awaitText(ProfileR.string.profile_action_message)
        messageButton().performClick()
        composeRule.waitForIdle()

        assertNoText(bios.getValue(ADA))
        message(ADA).assertIsDisplayed()
        assertOnlyChatsTab()
    }

    @Test
    fun `the profile beside the conversation survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))

        restorationTester.emulateSavedInstanceStateRestore()

        awaitText(bios.getValue(ADA))
        awaitMessage(ADA)
        assertOnlyChatsTab()
    }

    @Test
    fun `the drag handle between the conversation and the profile moves through its widths`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        title().performClick()
        awaitText(bios.getValue(ADA))
        val handle = composeRule.onNode(hasContentDescription(string(UiR.string.pane_extra_drag_handle)))
        awaitNode(hasContentDescription(string(UiR.string.pane_extra_drag_handle)))
        assertEquals(string(UiR.string.pane_extra_width_medium), handle.stateDescription())

        handle.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertNotEquals(string(UiR.string.pane_extra_width_medium), handle.stateDescription())
    }

    @Test
    fun `with no page beside the conversation, there is no second drag handle`() {
        setApp()
        openChat(ADA_TITLE, ADA)

        composeRule
            .onAllNodes(hasContentDescription(string(UiR.string.pane_extra_drag_handle)))
            .assertCountEquals(0)
    }

    // Narrower

    @Test
    @Config(qualifiers = LARGE)
    fun `on a large window the title still pushes the profile above the layout`() {
        setApp()
        openChat(ADA_TITLE, ADA)

        title().performClick()

        awaitText(bios.getValue(ADA))
        val entry = navController.currentBackStackEntry
        assertTrue(entry?.destination?.hasRoute<ProfileRoute>() == true)
        assertEquals(ADA, entry?.toRoute<ProfileRoute>()?.userId)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `on an expanded window a pushed profile's Message shows its chat beside the list`() {
        setApp()
        openChat(ADA_TITLE, ADA)
        composeRule.runOnUiThread { navController.navigateToProfile(GRACE) }
        awaitText(bios.getValue(GRACE))

        awaitText(ProfileR.string.profile_action_message)
        messageButton().performClick()

        awaitMessage(GRACE)
        assertNoMessage(ADA)
        row(ADA_TITLE).assertIsDisplayed()
        assertOnlyChatsTab()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `on an expanded window a link to a chat shows it beside the list, above whatever was pushed`() {
        setApp()
        awaitRow(ADA_TITLE)
        composeRule.runOnUiThread { navController.navigateToProfile(ADA) }
        awaitText(bios.getValue(ADA))

        composeRule.runOnUiThread {
            navController.openLinkTarget(
                target = LinkTarget.Chat(GRACE),
                fromOutside = true,
                onOpenSearch = {},
                besideList = true,
            )
        }

        awaitMessage(GRACE)
        row(ADA_TITLE).assertIsDisplayed()
        assertOnlyChatsTab()
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `a link to a message shows its chat beside the list at that message`() {
        setApp()
        awaitRow(ADA_TITLE)
        val message = ChatsScript.olderMessageOf(GRACE)

        composeRule.runOnUiThread {
            navController.openLinkTarget(
                target = LinkTarget.Chat(GRACE, message.id),
                fromOutside = false,
                onOpenSearch = {},
                besideList = true,
            )
        }

        awaitMessage(GRACE)
        assertOnlyChatsTab()
    }

    private fun setApp() = composeRule.setContent { App() }

    @Composable
    private fun App() {
        PingogramTheme(dynamicColor = false) {
            PingogramApp(
                uiState = MainActivityUiState.SignedIn,
                navController = rememberNavController().also { navController = it },
            )
        }
    }

    private fun openChat(
        title: String,
        chatId: Long,
    ) {
        awaitRow(title)
        row(title).performClick()
        awaitMessage(chatId)
    }

    /** The profile's "Message", a button, unlike the composer's field of the same name. */
    private fun messageButton(): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(string(ProfileR.string.profile_action_message)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )

    /** The conversation's title, which opens the profile. */
    private fun title(): SemanticsNodeInteraction =
        composeRule.onNode(
            SemanticsMatcher("opens the profile") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == string(ChatR.string.chat_open_profile)
            },
        )

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

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun assertNoText(text: String) {
        composeRule.onAllNodes(hasText(text, substring = true)).assertCountEquals(0)
    }

    private fun assertNoMessage(chatId: Long) {
        composeRule
            .onAllNodes(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))
            .assertCountEquals(0)
    }

    private fun row(title: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(title, substring = true) and hasClickAction())

    private fun message(chatId: Long): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))

    private fun awaitRow(title: String) = awaitNode(hasContentDescription(title, substring = true) and hasClickAction())

    private fun awaitMessage(chatId: Long) =
        awaitNode(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))

    private fun awaitText(text: String) = awaitNode(hasText(text, substring = true))

    private fun awaitText(
        @StringRes id: Int,
    ) = awaitNode(hasText(string(id)))

    /** Waits for [matcher]: the screens get their state from a background dispatcher. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(matcher)[0].assertIsDisplayed()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L

        /** Each user's bio, which only their profile shows. */
        val bios = mapOf(ADA to "Poet of the Analytical Engine", GRACE to "Rear admiral, compiler pioneer")
    }
}

/** Extra-large: 1600 dp and wider, three panes. */
private const val EXTRA_LARGE = "w1600dp-h1000dp-mdpi"

/** Large: two panes, as on expanded windows. */
private const val LARGE = "w1280dp-h800dp-xhdpi"

private const val EXPANDED = "w840dp-h891dp-xxhdpi"

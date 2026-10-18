package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE_TITLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * The Chats tab's list-detail layout ([ChatsListDetail]) in the app shell: on an expanded window a tap shows the chat
 * beside the list instead of pushing [ChatRoute]; on compact ones it still pushes it; resizing keeps the open chat.
 * The chat list and the conversation run their real view models and repositories on a [FakeTdlibClient] bound with
 * Hilt, with the chats of [ChatsScript]. Window sizes other than the configured one come from a
 * [DeviceConfigurationOverride]; saved state restoration stands in for the activity that a resize recreates.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = EXPANDED)
class ChatsListDetailTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var navController: NavHostController

    /** The window size the app sees; null for the configured one. */
    private var windowSize by mutableStateOf<DpSize?>(null)

    // Expanded

    @Test
    fun `with no chat open, the detail pane asks to select one`() {
        setApp()

        awaitRow(ADA_TITLE)
        text(R.string.chats_no_chat_selected_title).assertIsDisplayed()
        text(R.string.chats_no_chat_selected_body).assertIsDisplayed()
    }

    @Test
    fun `tapping a chat shows its conversation beside the list, without pushing it`() {
        setApp()
        awaitRow(ADA_TITLE)

        row(ADA_TITLE).performClick()

        awaitMessage(ADA)
        row(GRACE_TITLE).assertIsDisplayed()
        assertOnlyChatsTab()
        composeRule.onAllNodes(hasContentDescription(string(ChatR.string.chat_back))).assertCountEquals(0)
        composeRule.onAllNodes(hasText(string(R.string.chats_no_chat_selected_title))).assertCountEquals(0)
        assertEquals(listOf("+$ADA"), ChatsScript.openAndClose(tdlib))
    }

    @Test
    fun `tapping another chat swaps the conversation and closes the first chat`() {
        setApp()
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        row(GRACE_TITLE).performClick()

        awaitMessage(GRACE)
        assertNoMessage(ADA)
        assertOnlyChatsTab()
        assertEquals(listOf("+$ADA", "-$ADA", "+$GRACE"), ChatsScript.openAndClose(tdlib))
    }

    @Test
    fun `tapping the open chat again keeps its conversation`() {
        setApp()
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        row(ADA_TITLE).performClick()
        composeRule.waitForIdle()

        message(ADA).assertIsDisplayed()
        assertEquals(listOf("+$ADA"), ChatsScript.openAndClose(tdlib))
    }

    @Test
    fun `the open chat's row reads as selected, and only it`() {
        setApp()
        awaitRow(ADA_TITLE)

        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        row(ADA_TITLE).assertIsSelected()
        row(GRACE_TITLE).assert(isMarkedSelected().not())
    }

    @Test
    fun `back closes the conversation, then leaves the app`() {
        setApp()
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        pressBack()

        awaitText(R.string.chats_no_chat_selected_title)
        assertNoMessage(ADA)
        row(ADA_TITLE).assert(isMarkedSelected().not())
        assertEquals(listOf("+$ADA", "-$ADA"), ChatsScript.openAndClose(tdlib))
        pressBack()
        assertTrue("Back leaves the app", composeRule.activity.isFinishing)
    }

    @Test
    fun `selection mode in the list leaves the open conversation alone`() {
        setApp()
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        row(GRACE_TITLE).performTouchInput { longClick() }

        awaitNode(hasContentDescription(GRACE_TITLE, substring = true) and isChecked())
        message(ADA).assertIsDisplayed()
        // Back leaves selection mode first, and only that.
        pressBack()
        awaitNode(hasContentDescription(GRACE_TITLE, substring = true) and hasClickAction() and isChecked().not())
        message(ADA).assertIsDisplayed()
        row(ADA_TITLE).assertIsSelected()
        assertEquals(listOf("+$ADA"), ChatsScript.openAndClose(tdlib))
    }

    @Test
    fun `the open chat survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        restorationTester.emulateSavedInstanceStateRestore()

        awaitMessage(ADA)
        row(ADA_TITLE).assertIsSelected()
    }

    // Resizing

    @Test
    fun `narrower, the open conversation shows alone, and back returns to the list`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        // A resize recreates the activity.
        windowSize = COMPACT_SIZE
        restorationTester.emulateSavedInstanceStateRestore()

        awaitMessage(ADA)
        assertNoRows()
        assertNoNavigation()
        assertOnlyChatsTab()
        composeRule.onNode(hasContentDescription(string(ChatR.string.chat_back))).performClick()
        composeRule.waitForIdle()
        awaitRow(ADA_TITLE)
        assertNoMessage(ADA)
        tab(R.string.nav_chats).assertIsSelected()
        assertEquals(listOf("+$ADA", "-$ADA", "+$ADA", "-$ADA"), ChatsScript.openAndClose(tdlib))
    }

    @Test
    fun `wider again, both panes show the open chat`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { App() }
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)
        windowSize = COMPACT_SIZE
        restorationTester.emulateSavedInstanceStateRestore()
        awaitMessage(ADA)

        windowSize = null
        restorationTester.emulateSavedInstanceStateRestore()

        awaitMessage(ADA)
        row(ADA_TITLE).assertIsSelected()
        composeRule.onAllNodes(hasContentDescription(string(ChatR.string.chat_back))).assertCountEquals(0)
    }

    @Test
    fun `resized in place, the panes follow the window`() {
        setApp()
        awaitRow(ADA_TITLE)
        row(ADA_TITLE).performClick()
        awaitMessage(ADA)

        // Without crossing a size threshold of the app's resources, a resize keeps the activity.
        windowSize = COMPACT_SIZE
        composeRule.waitForIdle()

        message(ADA).assertIsDisplayed()
        assertNoRows()
        assertNoNavigation()
        composeRule.onNode(hasContentDescription(string(ChatR.string.chat_back))).performClick()
        composeRule.waitForIdle()
        awaitRow(ADA_TITLE)
        assertNoMessage(ADA)
        windowSize = null
        composeRule.waitForIdle()
        text(R.string.chats_no_chat_selected_title).assertIsDisplayed()
        row(ADA_TITLE).assertIsDisplayed()
    }

    // Compact

    @Test
    @Config(qualifiers = COMPACT)
    fun `on a compact window a tap still pushes the conversation full screen`() {
        setApp()
        awaitRow(ADA_TITLE)

        row(ADA_TITLE).performClick()

        awaitMessage(ADA)
        val entry = navController.currentBackStackEntry
        assertTrue(entry?.destination?.hasRoute<ChatRoute>() == true)
        assertEquals(ADA, entry?.toRoute<ChatRoute>()?.chatId)
        assertNoNavigation()
        assertEquals(listOf("+$ADA"), ChatsScript.openAndClose(tdlib))
        composeRule.onNode(hasContentDescription(string(ChatR.string.chat_back))).performClick()
        composeRule.waitForIdle()
        awaitRow(ADA_TITLE)
        assertOnlyChatsTab()
        tab(R.string.nav_chats).assertIsSelected()
    }

    @Test
    @Config(qualifiers = MEDIUM)
    fun `on a medium window a tap still pushes the conversation full screen`() {
        setApp()
        awaitRow(ADA_TITLE)

        row(ADA_TITLE).performClick()

        awaitMessage(ADA)
        assertTrue(navController.currentDestination?.hasRoute<ChatRoute>() == true)
    }

    @Test
    @Config(qualifiers = COMPACT)
    fun `a pushed conversation shows the chat of its route`() {
        setApp()
        awaitRow(ADA_TITLE)

        composeRule.runOnUiThread { navController.navigate(ChatRoute(chatId = GRACE)) }

        awaitMessage(GRACE)
        assertEquals(listOf("+$GRACE"), ChatsScript.openAndClose(tdlib))
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

    /** The Chats tab is the only destination: nothing was pushed. */
    private fun assertOnlyChatsTab() {
        val destinations =
            navController.currentBackStack.value
                .map { it.destination }
                .filterNot { it is NavGraph }
        assertEquals(1, destinations.size)
        assertTrue(destinations.single().hasRoute<ChatsRoute>())
        assertNotEquals(true, navController.currentDestination?.hasRoute<ChatRoute>())
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun assertNoNavigation() {
        composeRule.onAllNodes(isSelectable() and hasText(string(R.string.nav_chats))).assertCountEquals(0)
    }

    private fun assertNoRows() {
        composeRule.onAllNodes(hasContentDescription(GRACE_TITLE, substring = true)).assertCountEquals(0)
    }

    private fun assertNoMessage(chatId: Long) {
        composeRule
            .onAllNodes(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))
            .assertCountEquals(0)
    }

    /** A chat's row, named by its description, which starts with the title; the conversation's title is text. */
    private fun row(title: String): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(title, substring = true) and hasClickAction())

    /** An older message of [chatId]'s conversation, which its row never shows. */
    private fun message(chatId: Long): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))

    private fun awaitRow(title: String) = awaitNode(hasContentDescription(title, substring = true) and hasClickAction())

    private fun awaitMessage(chatId: Long) =
        awaitNode(hasContentDescription(ChatsScript.olderMessage.getValue(chatId), substring = true))

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

/** A checked check box, as rows in selection mode are. */
private fun isChecked() =
    SemanticsMatcher("is checked") { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }

private const val EXPANDED = "w840dp-h891dp-xxhdpi"
private const val MEDIUM = "w700dp-h891dp-xxhdpi"
private const val COMPACT = "w411dp-h891dp-xxhdpi"

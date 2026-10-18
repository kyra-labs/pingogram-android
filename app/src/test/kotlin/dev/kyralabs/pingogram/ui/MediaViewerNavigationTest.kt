package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
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
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.media.R as MediaR

/**
 * Opening the media viewer from the conversation: a photo tapped in the conversation ([ChatEvent.OpenMedia] in
 * `feature:chat`) pushes [MediaViewerRoute] full screen, without the navigation bar or rail, above the pushed
 * conversation on compact windows and above the whole list-detail layout on expanded ones; back returns. The chats of
 * [ChatsScript], Ada's history with a photo as its newest message, run their real view models and repositories on a
 * [FakeTdlibClient] bound with Hilt, and so does the viewer.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = COMPACT)
class MediaViewerNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val photo =
        TdApiFixtures.message(
            id = PHOTO_MESSAGE,
            chatId = ADA,
            sender = TdApi.MessageSenderUser(ADA),
            content = TdApiFixtures.messagePhoto(),
        )

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).also { tdlib ->
            ChatsScript.install(tdlib)
            // Only Ada's conversation opens here: its history is the photo.
            tdlib.on(TdApi.GetChatHistory::class) { request ->
                val messages =
                    if (request.chatId == ADA &&
                        request.fromMessageId == 0L
                    ) {
                        arrayOf(photo)
                    } else {
                        emptyArray()
                    }
                TdResult.Success(TdApi.Messages(messages.size, messages))
            }
            tdlib.on(TdApi.GetMessage::class) { request ->
                if (request.messageId == PHOTO_MESSAGE) {
                    TdResult.Success(photo)
                } else {
                    TdResult.Failure(TdError(404, "Not Found"))
                }
            }
            tdlib.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(1, arrayOf(photo), 0)))
        }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var navController: NavHostController

    @Test
    fun `a photo tapped in a pushed conversation opens the viewer above it`() {
        setApp()
        openAdaAndTapPhoto()

        assertTrue(navController.currentDestination?.hasRoute<MediaViewerRoute>() == true)
        assertEquals(
            MediaViewerRoute(ADA, PHOTO_MESSAGE),
            navController.currentBackStackEntry?.toRoute<MediaViewerRoute>(),
        )
        awaitNode(hasContentDescription("Photo, 1 of 1, Ada Lovelace", substring = true))
        assertNoNavigation()
        assertEquals(1, tdlib.sentOf<TdApi.SearchChatMessages>().size)

        composeRule.onNode(hasContentDescription(string(MediaR.string.media_back))).performClick()
        composeRule.waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute<ChatRoute>() == true)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `on an expanded window the viewer covers the list-detail layout and the rail`() {
        setApp()
        openAdaAndTapPhoto()

        assertTrue(navController.currentDestination?.hasRoute<MediaViewerRoute>() == true)
        awaitNode(hasContentDescription("Photo, 1 of 1, Ada Lovelace", substring = true))
        assertNoNavigation()
        composeRule.onAllNodes(adaRow).assertCountEquals(0)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        awaitNode(adaRow)
        assertTrue(navController.currentDestination?.hasRoute<MediaViewerRoute>() == false)
    }

    /** Ada's row in the chat list, named by its description, which starts with the title (the viewer's does not). */
    private val adaRow =
        SemanticsMatcher("is Ada's row") { node ->
            node.config
                .getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.firstOrNull()
                ?.startsWith(ADA_TITLE) == true
        } and hasClickAction()

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

    /** Opens Ada's conversation from the chat list and opens its photo, as the bubble's "Open" action does. */
    private fun openAdaAndTapPhoto() {
        awaitNode(adaRow)
        composeRule.onNode(adaRow).performClick()
        val bubble = hasCustomAction(string(ChatR.string.content_open))
        awaitNode(bubble)
        val open =
            composeRule
                .onNode(bubble)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == string(ChatR.string.content_open) }
        composeRule.runOnUiThread { open.action() }
        composeRule.waitForIdle()
    }

    private fun assertNoNavigation() {
        composeRule.onAllNodes(isSelectable() and hasText(string(R.string.nav_chats))).assertCountEquals(0)
    }

    /** Waits for [matcher]: the screens get their state from a background dispatcher. */
    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val PHOTO_MESSAGE = ADA * 1_000 + 10
    }
}

/** Offers a custom accessibility action labelled [label], as a bubble does for its content. */
private fun hasCustomAction(label: String) =
    SemanticsMatcher("has custom action $label") { node ->
        node.config
            .getOrElseNullable(SemanticsActions.CustomActions) { null }
            .orEmpty()
            .any { it.label == label }
    }

private const val COMPACT = "w411dp-h891dp-xxhdpi"
private const val EXPANDED = "w840dp-h891dp-xxhdpi"

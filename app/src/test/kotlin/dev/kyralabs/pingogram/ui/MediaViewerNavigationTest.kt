package dev.kyralabs.pingogram.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
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
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
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
import dev.kyralabs.pingogram.ui.ChatsScript.GRACE
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
import org.robolectric.shadows.ShadowDialog
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.media.R as MediaR

/**
 * Opening the media viewer from the conversation: a photo tapped in the conversation ([ChatEvent.OpenMedia] in
 * `feature:chat`) pushes [MediaViewerRoute] full screen, without the navigation bar or rail, above the pushed
 * conversation on compact windows and above the whole list-detail layout on expanded ones; back returns. The chats of
 * [ChatsScript], Ada's history with a photo as its newest message, run their real view models and repositories on a
 * [FakeTdlibClient] bound with Hilt, and so does the viewer.
 *
 * The `NavHost` is a `SharedTransitionLayout`, so the photo grows out of its bubble and shrinks back into it
 * (`sharedMediaBounds`): opening and every way of closing (the back button, predictive back, a swipe down, a zoomed
 * photo, a photo paged to) end on the right screen with the bubble back in the conversation. "Show in chat" leaves the
 * viewer for the conversation at the photo's message. Forward opens the forward sheet (`ForwardRoute`, a dialog
 * destination) above the viewer, which stays below it, and back closes the sheet.
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

    /** A photo sent after [photo], for the tests that page in the viewer. */
    private val newerPhoto =
        TdApiFixtures.message(
            id = PHOTO_MESSAGE + 1,
            chatId = ADA,
            sender = TdApi.MessageSenderUser(ADA),
            content = TdApiFixtures.messagePhoto(),
        )

    /** Ada's history, newest first: what the conversation and the viewer's search find. */
    private var photos = listOf(photo)

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).also { tdlib ->
            ChatsScript.install(tdlib)
            // Only Ada's conversation opens here: its history is the photos.
            tdlib.on(TdApi.GetChatHistory::class) { request ->
                val messages =
                    if (request.chatId == ADA &&
                        request.fromMessageId == 0L
                    ) {
                        photos.toTypedArray()
                    } else {
                        emptyArray()
                    }
                TdResult.Success(TdApi.Messages(messages.size, messages))
            }
            tdlib.on(TdApi.GetMessage::class) { request ->
                photos.firstOrNull { it.id == request.messageId }?.let { TdResult.Success(it) }
                    ?: TdResult.Failure(TdError(404, "Not Found"))
            }
            tdlib.on(TdApi.SearchChatMessages::class) {
                TdResult.Success(TdApi.FoundChatMessages(photos.size, photos.toTypedArray(), 0))
            }
            tdlib.on(TdApi.ForwardMessages::class) { TdResult.Success(TdApi.Messages(1, arrayOf(photo))) }
            // Ada's photos may be forwarded and saved.
            tdlib.on(TdApi.GetMessageProperties::class) {
                TdResult.Success(
                    TdApi.MessageProperties().apply {
                        canBeForwarded = true
                        canBeSaved = true
                    },
                )
            }
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
    fun `Show in chat replaces the viewer and the conversation below with the conversation at the message`() {
        setApp()
        openAdaAndTapPhoto()
        awaitNode(photoPage)

        composeRule.onNode(hasContentDescription(string(MediaR.string.media_more))).performClick()
        awaitNode(hasText(string(MediaR.string.media_show_in_chat)))
        composeRule.onNode(hasText(string(MediaR.string.media_show_in_chat))).performClick()

        awaitConversation()
        assertEquals(ChatRoute(ADA, PHOTO_MESSAGE), navController.currentBackStackEntry?.toRoute<ChatRoute>())
        // The conversation the viewer was opened from gave way: back goes to the chat list.
        assertTrue(navController.previousBackStackEntry?.destination?.hasRoute<ChatRoute>() == false)
    }

    @Test
    fun `Forward opens the forward sheet above the viewer, and back closes it`() {
        setApp()
        openAdaAndTapPhoto()
        val forward = hasContentDescription(string(MediaR.string.media_forward))
        awaitNode(forward)

        composeRule.onNode(forward).performClick()

        val sheetTitle = hasText(string(ChatR.string.forward_title))
        awaitNode(sheetTitle)
        assertEquals(
            ForwardRoute(ADA, listOf(PHOTO_MESSAGE)),
            navController.currentBackStackEntry?.toRoute<ForwardRoute>(),
        )
        // The viewer stays below the sheet, without the navigation.
        assertTrue(navController.previousBackStackEntry?.destination?.hasRoute<MediaViewerRoute>() == true)
        composeRule.onNode(photoPage).assertExists()
        assertNoNavigation()

        // System back goes to the focused window, the sheet's: it hides, then the destination leaves.
        composeRule.runOnUiThread {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            navController.currentDestination?.hasRoute<MediaViewerRoute>() == true
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(sheetTitle).assertCountEquals(0)
        composeRule.onNode(photoPage).assertIsDisplayed()
    }

    @Test
    fun `after forwarding to one chat, the viewer's snackbar opens it in the viewer's place`() {
        setApp()
        openAdaAndTapPhoto()
        val forward = hasContentDescription(string(MediaR.string.media_forward))
        awaitNode(forward)
        composeRule.onNode(forward).performClick()
        val grace = hasText(GRACE_TITLE, substring = true) and hasClickAction()
        awaitNode(grace)

        composeRule.onNode(grace).performClick()
        val send = hasText(string(ChatR.string.forward_send)) and hasClickAction()
        awaitNode(send)
        composeRule.onNode(send).performClick()

        val open = hasText(string(ChatR.string.forward_open_chat)) and hasClickAction()
        awaitNode(hasText(composeRule.activity.getString(ChatR.string.forward_done_to, GRACE_TITLE)))
        assertTrue(navController.currentDestination?.hasRoute<MediaViewerRoute>() == true)
        assertEquals(GRACE, tdlib.sentOf<TdApi.ForwardMessages>().single().chatId)
        composeRule.onNode(open).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { navController.currentDestination?.hasRoute<ChatRoute>() == true }
        assertEquals(ChatRoute(GRACE), navController.currentBackStackEntry?.toRoute<ChatRoute>())
        // The viewer gave way; back returns to the conversation it was opened from.
        assertEquals(ChatRoute(ADA), navController.previousBackStackEntry?.toRoute<ChatRoute>())
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

    @Test
    fun `predictive back shrinks the photo back into the conversation`() {
        setApp()
        openAdaAndTapPhoto()
        awaitNode(photoPage)

        val dispatcher = composeRule.activity.onBackPressedDispatcher
        composeRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(BACK_SWIPE, 0f, 1f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { dispatcher.onBackPressed() }

        awaitConversation()
    }

    @Test
    fun `a swipe down hands the photo back to the conversation`() {
        setApp()
        openAdaAndTapPhoto()
        awaitNode(photoPage)

        composeRule.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }

        awaitConversation()
    }

    @Test
    fun `a zoomed photo closes too, fading`() {
        setApp()
        openAdaAndTapPhoto()
        awaitNode(photoPage)
        composeRule.onNode(photoPage).performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription(string(MediaR.string.media_back))).performClick()

        awaitConversation()
    }

    @Test
    fun `closing on a photo paged to returns to the conversation`() {
        photos = listOf(newerPhoto, photo)
        setApp()
        openAdaAndTapPhoto(index = 1)
        awaitNode(hasContentDescription("Photo, 1 of 2, Ada Lovelace", substring = true))

        composeRule.onRoot().performTouchInput { swipeLeft() }
        awaitNode(hasContentDescription("Photo, 2 of 2, Ada Lovelace", substring = true))
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        awaitConversation()
    }

    /** The viewer's page of the photo, described with its position among Ada's photos. */
    private val photoPage = hasContentDescription("Photo, 1 of 1, Ada Lovelace", substring = true)

    /**
     * Back in the conversation once the viewer is gone: its route, its photo bubbles (which offer to open the photo)
     * on screen, and none of the viewer's pages left.
     */
    private fun awaitConversation() {
        composeRule.waitForIdle()
        assertTrue(navController.currentDestination?.hasRoute<ChatRoute>() == true)
        awaitPhotoBubbles()
        composeRule.onAllNodes(viewerPage).assertCountEquals(0)
    }

    /** A photo's bubble in the conversation, which offers to open the photo. */
    private val photoBubble get() = hasCustomAction(string(ChatR.string.content_open))

    /** Waits for the bubbles of all of Ada's photos. */
    private fun awaitPhotoBubbles() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(photoBubble).fetchSemanticsNodes().size == photos.size
        }
    }

    /** A page of the viewer, whose description starts with what it shows (a bubble's starts with the sender). */
    private val viewerPage =
        SemanticsMatcher("is a viewer page") { node ->
            node.config
                .getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.firstOrNull()
                ?.startsWith("Photo, ") == true
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

    /**
     * Opens Ada's conversation from the chat list and opens a photo, the bubble's at [index] (0 for the newest), as
     * its "Open" action does.
     */
    private fun openAdaAndTapPhoto(index: Int = 0) {
        awaitNode(adaRow)
        composeRule.onNode(adaRow).performClick()
        val bubble = photoBubble
        awaitPhotoBubbles()
        val open =
            composeRule
                .onAllNodes(bubble)
                .fetchSemanticsNodes()
                .sortedByDescending { it.boundsInRoot.top }[index]
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

        /** How far a predictive back gesture travels, in pixels; its progress is what counts. */
        const val BACK_SWIPE = 300f
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

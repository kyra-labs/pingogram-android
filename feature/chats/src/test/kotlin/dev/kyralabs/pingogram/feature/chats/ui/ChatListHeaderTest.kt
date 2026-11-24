package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Chats tab's header (`docs/DESIGN_SYSTEM.md` §6 "Chat list top bar"; owner decision 2026-11-06): its status line,
 * its search and account buttons, and its collapsed look once the list scrolled, with goldens of the collapsed bar.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListHeaderTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var searches = 0
    private var accounts = 0
    private val actions =
        ChatListHeaderActions(
            search = GlobalSearchState(),
            searchOpen = mutableStateOf(false),
            openSearch = { searches++ },
            openAccount = { accounts++ },
        )

    @Test
    fun `the status line says the unread chats and mentions in one text`() {
        show()

        composeRule.onNodeWithText("12 unread · 3 mentions").assertExists()
    }

    @Test
    fun `with nothing unread there is no status line`() {
        show(summary = ChatListSummary())

        composeRule.onNodeWithText("unread", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the connection's state takes the status line's place while not connected`() {
        show(state = ChatListPreviewData.connecting)

        composeRule.onNodeWithText("Connecting…").assertExists()
        composeRule.onNodeWithText("12 unread", substring = true).assertDoesNotExist()
    }

    @Test
    fun `search and the account are named buttons with 48 dp targets that do what the shell says`() {
        show()

        listOf(R.string.chat_list_search, R.string.chat_list_your_profile).forEach { label ->
            val button = composeRule.onNodeWithContentDescription(string(label)).assertHasClickAction()
            val target =
                with(composeRule.density) {
                    button
                        .fetchSemanticsNode()
                        .touchBoundsInRoot.size
                        .toDpSize()
                }
            assertTrue("$label: $target", target.width >= 48.dp && target.height >= 48.dp)
            button.performClick()
        }

        assertEquals(1, searches)
        assertEquals(1, accounts)
    }

    @Test
    fun `while a chat has a draft the header offers Drafts, unless it is turned off`() {
        var drafts = 0
        val withDrafts = ChatListPreviewData.summary.copy(hasDrafts = true)
        val label = string(R.string.drafts_title)

        show(summary = ChatListPreviewData.summary)
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()

        val withOpenDrafts =
            ChatListHeaderActions(
                search = GlobalSearchState(),
                searchOpen = mutableStateOf(false),
                openSearch = {},
                openAccount = {},
                openDrafts = { drafts++ },
            )
        show(summary = withDrafts, actions = withOpenDrafts)
        composeRule.onNodeWithContentDescription(label).assertHasClickAction().performClick()
        assertEquals(1, drafts)

        show(summary = withDrafts, swipes = ChatSwipes(draftsButton = false))
        composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
    }

    @Test
    fun draftsButtonLight() = captureExpanded("draftsButton.light", ChatListPreviewData.summary.copy(hasDrafts = true))

    @Test
    fun draftsButtonOffLight() =
        captureExpanded(
            "draftsButtonOff.light",
            ChatListPreviewData.summary.copy(hasDrafts = true),
            ChatSwipes(draftsButton = false),
        )

    @Test
    fun `scrolling the list collapses the header to its title and buttons`() {
        show()

        collapse()

        composeRule.onNodeWithText("12 unread · 3 mentions").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.chat_list_search)).assertExists()
    }

    @Test
    fun collapsedLight() = captureCollapsed(ScreenshotVariant.Light, "collapsed.light")

    @Test
    fun collapsedDark() = captureCollapsed(ScreenshotVariant.Dark, "collapsed.dark")

    @Test
    fun collapsedConnecting() =
        captureCollapsed(ScreenshotVariant.Light, "collapsedConnecting.light", ChatListPreviewData.connecting)

    private fun captureCollapsed(
        variant: ScreenshotVariant,
        name: String,
        state: ChatListUiState = ChatListPreviewData.contentWithFolders,
    ) {
        show(variant = variant, state = state)
        collapse()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    /** The expanded header with [summary] and [swipes], as a golden. */
    private fun captureExpanded(
        name: String,
        summary: ChatListSummary,
        swipes: ChatSwipes = ChatSwipes(),
    ) {
        show(summary = summary, swipes = swipes)
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun show(
        variant: ScreenshotVariant = ScreenshotVariant.Light,
        state: ChatListUiState = ChatListPreviewData.content,
        summary: ChatListSummary = ChatListPreviewData.summary,
        actions: ChatListHeaderActions = this.actions,
        swipes: ChatSwipes = ChatSwipes(),
    ) {
        val shown: @Composable () -> Unit = {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalChatSwipes provides swipes) {
                    ChatListScreen(
                        state = state,
                        onEvent = {},
                        summary = { summary },
                        headerActions = actions,
                        onEditFolder = {},
                    )
                }
            }
        }
        // setContent runs once per test: later calls swap what it shows.
        if (content == null) composeRule.setContent { content?.invoke() }
        content = shown
        composeRule.waitForIdle()
    }

    private fun collapse() {
        composeRule.onNode(hasTestTag("chat_list")).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)
}

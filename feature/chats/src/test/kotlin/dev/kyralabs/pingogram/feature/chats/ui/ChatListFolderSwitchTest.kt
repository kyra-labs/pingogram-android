package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToIndex
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching folders (FR-L3, `docs/DESIGN_SYSTEM.md` §5): Material's fade-through between the two lists, the old one
 * fading out before the new one shows, at its top; with animations off (reduced motion) the lists swap at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListFolderSwitchTest {
    /** The system's animator duration scale, as Compose applies it to every animation; 0 turns them off. */
    private val durationScale =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }

    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private var state by mutableStateOf(ChatListPreviewData.contentWithFolders)

    @Test
    fun `the shown list fades out before the selected folder's list replaces it`() {
        setScreen()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = workFolder
        advanceFrames(FEW_FRAMES)

        assertTrue("All chats' list is still fading out", exists(SAVED_MESSAGES))
        assertFalse(exists(ROCKET_CLUB))
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse(exists(SAVED_MESSAGES))
        row(ROCKET_CLUB).assertIsDisplayed()
    }

    @Test
    fun `with animations off the selected folder's list shows at once`() {
        durationScale.scaleFactor = 0f
        setScreen()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        state = workFolder
        advanceFrames(FEW_FRAMES)

        assertFalse(exists(SAVED_MESSAGES))
        row(ROCKET_CLUB).assertIsDisplayed()
    }

    @Test
    fun `the selected folder's list starts at its top`() {
        state = ChatListPreviewData.contentWithFolders.copy(content = ChatListContent.Content(manyRows))
        setScreen()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(manyRows.lastIndex)

        state = workFolder.copy(selectedFolderId = 2, content = ChatListContent.Content(manyRows))
        composeRule.waitForIdle()

        row(SAVED_MESSAGES).assertIsDisplayed()
    }

    /** Runs [count] frames, and what they dispatch to the main thread, as a device does between frames. */
    private fun advanceFrames(count: Int) =
        repeat(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

    private fun setScreen() =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatListScreen(state = state, onEvent = {}) }
        }

    private fun row(name: String): SemanticsNodeInteraction = composeRule.onNode(rowMatcher(name))

    private fun exists(name: String) = composeRule.onAllNodes(rowMatcher(name)).fetchSemanticsNodes().isNotEmpty()

    private fun rowMatcher(name: String) = hasContentDescription("$name,", substring = true) and hasClickAction()

    private companion object {
        const val SAVED_MESSAGES = "Saved Messages"
        const val ROCKET_CLUB = "Rocket club"

        /**
         * Three frames: enough to swap the lists without animations (the frame that sees the folder change, the one
         * that runs the swap, the one that shows the new list), not for the fade-out on the fast effects spring.
         */
        const val FEW_FRAMES = 3

        /** The Work folder, with only a chat that is not among All chats' rows. */
        val workFolder =
            ChatListPreviewData.contentWithFolders.copy(
                selectedFolderId = 1,
                content =
                    ChatListContent.Content(
                        persistentListOf(
                            ChatListPreviewData.items.last().copy(id = 99, title = UiText.Plain(ROCKET_CLUB)),
                        ),
                    ),
            )

        /** The preview's rows three times over, with other ids, so the list scrolls. */
        val manyRows =
            (0..2)
                .flatMap { copy -> ChatListPreviewData.items.map { it.copy(id = it.id + copy * 100) } }
                .toImmutableList()
    }
}

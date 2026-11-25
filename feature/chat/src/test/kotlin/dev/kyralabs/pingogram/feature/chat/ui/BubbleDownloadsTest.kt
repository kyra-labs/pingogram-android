package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A download the user asks for ([MediaDownloads.requested]) keeps the resources every bubble shares
 * ([rememberBubbleResources]), so a bubble that does not show that photo is not composed again; the photo asked for
 * is, through [requestedState], as `PhotoImage` reads it, and no other photo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BubbleDownloadsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var downloads by mutableStateOf(MediaDownloads(photos = false))
    private val resourcesSeen = mutableListOf<BubbleResources>()
    private val compositions = mutableMapOf<String, Int>()
    private val shown = mutableMapOf<Int, Boolean>()

    private fun setBubbles() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val jump = remember { MessageJumpState() }
                val resources = rememberBubbleResources(jump, MessageShortcuts(), downloads)
                SideEffect { resourcesSeen += resources }
                // As the conversation's bubbles are: items of a lazy list, composed as it measures.
                LazyColumn {
                    item(key = "text") { TextBubble(resources) }
                    item(key = FILE) { PhotoBubble(resources, FILE) }
                    item(key = OTHER_FILE) { PhotoBubble(resources, OTHER_FILE) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** A bubble that shows no photo: it takes the resources, as every bubble does. */
    @Composable
    private fun TextBubble(resources: BubbleResources) {
        SideEffect { compositions.merge("text", 1, Int::plus) }
        resources.theme
    }

    /** A photo with a download button: it reads whether its file is asked for, as `PhotoImage` does. */
    @Composable
    private fun PhotoBubble(
        resources: BubbleResources,
        fileId: Int,
    ) {
        val downloading = resources.mediaDownloads.requestedState(fileId).value
        SideEffect {
            compositions.merge("photo$fileId", 1, Int::plus)
            shown[fileId] = downloading
        }
    }

    @Test
    fun `a download asked for keeps the bubbles' resources, and no bubble without that photo recomposes`() {
        setBubbles()

        composeRule.runOnIdle { downloads = downloads.copy(requested = persistentSetOf(FILE)) }
        composeRule.waitForIdle()

        assertTrue("$resourcesSeen", resourcesSeen.size >= 2)
        resourcesSeen.forEach { assertSame(resourcesSeen.first(), it) }
        assertEquals(1, compositions["text"])
        assertEquals(1, compositions["photo$OTHER_FILE"])
    }

    @Test
    fun `the photo asked for shows its download under way`() {
        setBubbles()
        assertEquals(false, shown[FILE])

        composeRule.runOnIdle { downloads = downloads.copy(requested = persistentSetOf(FILE)) }
        composeRule.waitForIdle()

        assertEquals(true, shown[FILE])
        assertEquals(2, compositions["photo$FILE"])
        assertEquals(false, shown[OTHER_FILE])
    }

    private companion object {
        const val FILE = 7
        const val OTHER_FILE = 8
    }
}

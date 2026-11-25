package dev.kyralabs.pingogram.feature.profile.notes

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The name and note editor: what typing and its buttons send, and the accessibility checks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatNoteDialogTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatNoteEvent>()

    @Test
    fun `the editor reports typing, saves only a change, and passes the accessibility checks`() {
        val unchanged = ChatNoteEditor("Book club", "", savedName = "Book club", savedNote = "")
        composeRule.setContent { PingogramTheme(dynamicColor = false) { ChatNoteDialog(unchanged, events::add) } }

        composeRule.onNodeWithText(string(R.string.profile_note_save)).assertIsNotEnabled()
        composeRule.onNode(hasText(string(R.string.chat_note_note)) and hasSetTextAction()).performTextInput("Tue")
        composeRule.onNodeWithText(string(R.string.profile_note_cancel)).performClick()

        assertTrue(ChatNoteEvent.NoteChanged("Tue") in events)
        assertEquals(ChatNoteEvent.Dismiss, events.last())
        composeRule.assertAccessible()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}

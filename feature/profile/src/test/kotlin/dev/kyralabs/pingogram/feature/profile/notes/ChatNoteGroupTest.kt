package dev.kyralabs.pingogram.feature.profile.notes

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The name and note rows: a tap opens the editor, nothing shows with the setting off, and the accessibility checks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatNoteGroupTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatNoteEvent>()

    @Test
    fun `either row opens the editor, and the rows pass the accessibility checks`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatNoteGroup(ChatNoteGroupStates().values.last(), events::add) }
        }

        composeRule.onNode(hasText(string(R.string.chat_note_name_add)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(string(R.string.chat_note_note_add)) and hasClickAction()).performClick()

        assertEquals(listOf(ChatNoteEvent.Edit, ChatNoteEvent.Edit), events)
        composeRule.assertAccessible()
    }

    @Test
    fun `nothing shows while the setting is off`() {
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { ChatNoteGroup(ChatNoteUiState(), events::add) }
        }

        composeRule.onNodeWithText(string(R.string.chat_note_section)).assertDoesNotExist()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}

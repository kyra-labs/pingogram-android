package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.BookmarkEvent
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The bookmark sheet's controls: tags as checkboxes, a new tag, the note and Save, and its accessibility. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarkSheetScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BookmarkEvent>()

    private fun setSheet(state: BookmarkSheetUiState = BookmarkSheetPreviewData.new) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { Surface { BookmarkSheetContent(state, onEvent = { events += it }) } }
        }
    }

    @Test
    fun `tags are checkboxes named by the tag`() {
        setSheet()

        composeRule.onNodeWithText("Trips").assertIsSelected()
        composeRule.onNodeWithText("Work").assertIsNotSelected().performClick()

        assertEquals(listOf<BookmarkEvent>(BookmarkEvent.ToggleTag(3)), events)
    }

    @Test
    fun `a new tag is named and added`() {
        setSheet()

        composeRule.onNodeWithText("New tag").performClick()
        composeRule.onNodeWithText("Tag name").performTextInput("Recipes")
        composeRule.onNodeWithText("Add").performClick()

        assertEquals(listOf<BookmarkEvent>(BookmarkEvent.NewTag("Recipes")), events)
    }

    @Test
    fun `save carries the note and what the message says`() {
        setSheet()

        composeRule.onNodeWithText("Note (optional)").performTextInput("Ask Meera")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(
            listOf<BookmarkEvent>(
                BookmarkEvent.Save(
                    note = "Ask Meera",
                    sender = "Meera Iyer",
                    text = "@you can you check the price for 4 people? The good homestay fills up fast",
                ),
            ),
            events,
        )
    }

    @Test
    fun `a bookmarked message offers removing it`() {
        setSheet(BookmarkSheetPreviewData.bookmarked)

        composeRule.onNodeWithText("Remove bookmark").performClick()

        assertEquals(listOf<BookmarkEvent>(BookmarkEvent.Remove), events)
    }

    @Test
    fun `every control has a name and a 48 dp target`() {
        setSheet()

        composeRule.assertAccessible()
    }
}

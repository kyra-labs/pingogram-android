package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tag editor's controls and what screen readers get of them: rename, delete, moves, colors and the question. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BookmarkTagsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BookmarkTagsEvent>()

    private fun show(state: BookmarkTagsUiState = BookmarkTagsPreviewData.content) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { BookmarkTagsScreen(state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `a row's buttons rename and delete its tag`() {
        show()

        composeRule.onNodeWithContentDescription("Rename Work").performClick()
        composeRule.onNodeWithContentDescription("Delete Work").performClick()

        assertEquals(listOf(BookmarkTagsEvent.StartEdit(2), BookmarkTagsEvent.AskDelete(2)), events)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `screen readers move a tag up and down`() {
        show()

        composeRule
            .onNodeWithContentDescription(
                "Work, 9 bookmarks",
            ).performCustomAccessibilityActionWithLabel("Move up")
        composeRule
            .onNodeWithContentDescription("Work, 9 bookmarks")
            .performCustomAccessibilityActionWithLabel("Move down")

        assertEquals(
            listOf(BookmarkTagsEvent.Move(2, up = true), BookmarkTagsEvent.Move(2, up = false)),
            events,
        )
    }

    @Test
    fun `the colors are one radio group named by their hues`() {
        show(BookmarkTagsPreviewData.renaming)

        composeRule.onNodeWithContentDescription("Color: Green").assertIsSelected()
        composeRule.onNodeWithContentDescription("Color: Blue").performClick()

        assertEquals(listOf<BookmarkTagsEvent>(BookmarkTagsEvent.EditColor(5)), events)
    }

    @Test
    fun `the question says what becomes of the bookmarks, and deletes only on its button`() {
        show(BookmarkTagsPreviewData.deleting)

        composeRule
            .onNodeWithText(
                "1 bookmark keeps its other tags. 2 bookmarks with only this tag will have no tag. No bookmark is removed.",
            ).assertExists()
        composeRule.onNodeWithText("Delete tag").performClick()

        assertEquals(listOf<BookmarkTagsEvent>(BookmarkTagsEvent.ConfirmDelete), events)
    }

    @Test
    fun `the tags and the editor are accessible`() {
        show(BookmarkTagsPreviewData.renaming)
        composeRule.assertAccessible()
    }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.PaidMediaPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Paid posts and the unsupported placeholder on screen (`REQUIREMENTS.md` FR-C4, §1.2): a locked post offers no tap
 * and tells screen readers why; "Update Pingogram" opens the releases page and is left out of store builds; every
 * control has a name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PaidMediaScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `a locked post takes no tap and says why for screen readers`() {
        setScreen(PaidMediaPreviewData.posts)

        val stars =
            RuntimeEnvironment.getApplication().resources.getQuantityString(
                R.plurals.paid_media_stars,
                25,
                "25",
            )
        val photos = RuntimeEnvironment.getApplication().resources.getQuantityString(R.plurals.paid_media_photos, 3, 3)
        val locked = string(R.string.paid_media_locked_description, photos, stars)
        val bubble =
            composeRule
                .onNode(
                    hasContentDescription(string(R.string.paid_media_note), substring = true),
                ).fetchSemanticsNode()
        val description =
            bubble.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString()
                .orEmpty()
        composeRule
            .onNode(
                hasContentDescription(locked),
                useUnmergedTree = true,
            ).assertHasNoClickAction()
            .performClick()

        assertTrue(description, description.contains(locked))
        assertTrue(events.filterIsInstance<ChatEvent.OpenMedia>().isEmpty())
    }

    @Test
    fun `Update Pingogram opens the releases page`() {
        setScreen(PaidMediaPreviewData.unsupported(showsUpdateLink = true))

        composeRule.onNodeWithText(string(R.string.content_update_app), useUnmergedTree = true).performClick()

        assertEquals(listOf<ChatEvent>(ChatEvent.OpenUpdatePage), events.filterIsInstance<ChatEvent.OpenUpdatePage>())
    }

    @Test
    fun `a store build shows no update link`() {
        setScreen(PaidMediaPreviewData.unsupported(showsUpdateLink = false))

        assertTrue(
            composeRule
                .onAllNodes(hasText(string(R.string.content_update_app)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        composeRule.onNodeWithText(string(R.string.content_unsupported_body), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `every control has a name and a touch target`() {
        setScreen(PaidMediaPreviewData.posts)

        composeRule.assertAccessible()
    }

    private fun hasText(text: String) =
        androidx.compose.ui.test
            .hasText(text)

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}

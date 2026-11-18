package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The feed's filter sheet (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 4): choosing channels and "Only unread"
 * changes "Show N posts", which applies them; "Mark all as read" asks first from 20 unread posts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedFilterSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<FeedEvent>()
    private var dismissed = 0

    private fun setSheet(unreadOfFirst: Int = 3) =
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                FeedFilterSheet(
                    channels =
                        persistentListOf(
                            FeedChannel(1, "Kotlin Weekly", null, postCount = 5, unreadCount = unreadOfFirst),
                            FeedChannel(2, "Astronomy Daily", null, postCount = 4, unreadCount = 1),
                        ),
                    filter = FeedFilter(),
                    onEvent = { events += it },
                    onDismiss = { dismissed++ },
                )
            }
        }

    @Test
    fun `choosing channels and only unread posts changes what Show applies`() {
        setSheet()
        composeRule.onNodeWithText("Show 9 posts").assertIsDisplayed()
        composeRule.onNodeWithText("Astronomy Daily").assertIsNotEnabled()

        composeRule.onNodeWithText("Chosen · 2").performClick()
        composeRule.onNodeWithText("Astronomy Daily").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Only unread").performClick()
        composeRule.onNodeWithText("Show 3 posts").performClick()

        assertEquals(listOf<FeedEvent>(FeedEvent.SetFilter(FeedFilter(persistentSetOf(1), onlyUnread = true))), events)
        assertEquals(1, dismissed)
        composeRule.assertAccessible()
    }

    @Test
    fun `Mark all as read asks first from twenty unread posts`() {
        setSheet(unreadOfFirst = 19)

        composeRule.onNodeWithText("Mark all as read").performClick()
        composeRule.onNodeWithText("Mark 20 posts as read?").assertIsDisplayed()
        composeRule.onNodeWithText("Mark as read").performClick()

        assertEquals(listOf<FeedEvent>(FeedEvent.MarkAllAsRead(null)), events)
    }
}

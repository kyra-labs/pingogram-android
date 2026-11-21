package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The chat list's content color over its transparent `Scaffold` (`docs/PERFORMANCE.md`, "Overdraw"): `onSurface`, as
 * the opaque container gave it, not the default black a transparent one gives text without a color of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListContentColorTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the list's content is onSurface`() {
        var onSurface = Color.Unspecified
        var contentColor = Color.Unspecified
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                onSurface = MaterialTheme.colorScheme.onSurface
                ChatListScreen(
                    state = ChatListPreviewData.content,
                    onEvent = {},
                    header = { contentColor = LocalContentColor.current },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(onSurface, contentColor)
    }
}

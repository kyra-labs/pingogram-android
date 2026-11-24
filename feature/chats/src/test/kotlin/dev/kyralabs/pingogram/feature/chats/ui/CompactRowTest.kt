package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Compact chat list" (`REQUIREMENTS.md` FR-X19): a compact row is lower than a full one, and 56 dp at least. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CompactRowTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a compact row is lower than a full one, and at least 56 dp`() {
        val item = ChatListPreviewData.items.first { it.topics == null }
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Column {
                    ChatListItem(item = item, onClick = {}, modifier = Modifier.testTag(FULL))
                    CompositionLocalProvider(LocalChatSwipes provides ChatSwipes(compact = true)) {
                        ChatListItem(item = item, onClick = {}, modifier = Modifier.testTag(COMPACT_ROW))
                    }
                }
            }
        }

        val full =
            composeRule
                .onNodeWithTag(FULL)
                .fetchSemanticsNode()
                .size.height
        val compact =
            composeRule
                .onNodeWithTag(COMPACT_ROW)
                .fetchSemanticsNode()
                .size.height
        val min = with(composeRule.density) { 56.dp.roundToPx() }
        assertTrue("compact $compact, full $full", compact < full)
        assertTrue("compact $compact, at least $min", compact >= min)
    }

    private companion object {
        const val FULL = "full"
        const val COMPACT_ROW = "compact"
    }
}

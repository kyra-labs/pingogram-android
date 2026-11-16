package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The keyword match bar's count is a polite live region that screen readers hear as "Match 2 of 4" after each step. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class KeywordMatchBarLiveRegionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the count is a polite live region that says the match after each step`() {
        var bar by mutableStateOf(KeywordMatchBar("launch", messageId = 1, position = 1, count = 4))
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                KeywordMatchBarRow(bar, onEvent = { event ->
                    when (event) {
                        KeywordMatchEvent.Older -> bar = bar.copy(position = bar.position + 1)
                        KeywordMatchEvent.Newer -> bar = bar.copy(position = bar.position - 1)
                        KeywordMatchEvent.Close -> Unit
                    }
                })
            }
        }
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        composeRule.onNodeWithContentDescription("Match 1 of 4").assert(polite)

        composeRule.onNodeWithContentDescription("Older match").requestFocus().performClick()

        composeRule.onNodeWithContentDescription("Match 2 of 4").assert(polite)
        composeRule.onNodeWithContentDescription("Older match").assertIsFocused()

        composeRule.onNodeWithContentDescription("Newer match").performClick()

        composeRule.onNodeWithContentDescription("Match 1 of 4").assert(polite)
    }
}

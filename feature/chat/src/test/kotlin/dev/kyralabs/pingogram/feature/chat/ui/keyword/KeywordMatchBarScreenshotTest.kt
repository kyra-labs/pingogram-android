package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the keyword match bar (board 30) at the top of a group's conversation, its match's word marked in its
 * bubble, on a compact phone, light, dark, right to left and at 200% font, and its buttons' events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class KeywordMatchBarScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<KeywordMatchEvent>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @Test
    fun `the arrows and close send their events, and the newest match has no newer one`() {
        setScreen(ScreenshotVariant.Light)

        composeRule.onNodeWithContentDescription("Older match").performClick()
        composeRule.onNodeWithContentDescription("Newer match").performClick()
        composeRule.onNodeWithContentDescription("Close keyword matches").performClick()

        assertEquals(listOf(KeywordMatchEvent.Older, KeywordMatchEvent.Close), events)
    }

    private fun setScreen(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val small = MaterialTheme.spacing.small
                CompositionLocalProvider(
                    LocalKeywordMatchBar provides { padding: PaddingValues ->
                        KeywordMatchBarRow(BAR, { events += it }, Modifier.padding(padding).padding(small))
                    },
                ) {
                    ChatScreen(state = STATE, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        setScreen(variant)
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
        val STATE = ChatPreviewData.groupChat.withKeywordMatch(KeywordShown(messageId = 24, word = "Motion"))
        val BAR = KeywordMatchBar("motion", messageId = 24, position = 1, count = 4)
    }
}

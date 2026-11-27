package dev.kyralabs.pingogram.feature.chat.ui.listen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ListenEvent
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
 * Goldens of Listen to posts' player at the top of a channel's conversation (FR-X26), on a compact phone, light, dark,
 * right to left and at 200% font, and its controls' events and accessibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ListenBarScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ListenEvent>()

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
    fun `its controls send their events, and the count is a polite live region`() {
        setScreen(ScreenshotVariant.Light)

        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next post").performClick()
        composeRule.onNodeWithContentDescription("Stop listening").performClick()
        composeRule.onNodeWithContentDescription("Playback speed, 1.5 times").performClick()

        assertEquals(listOf(ListenEvent.Pause, ListenEvent.Next, ListenEvent.Stop, ListenEvent.ChangeSpeed), events)
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        composeRule.onNodeWithContentDescription("Post 2 of 5").assert(polite)
        composeRule.assertAccessible()
    }

    @Test
    fun `a paused last post offers play and no next post`() {
        setScreen(ScreenshotVariant.Light, ListenPlayer(5, 5, isPaused = true, PlaybackSpeed.Normal))

        composeRule.onNodeWithContentDescription("Next post").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Play").performClick()

        assertEquals(listOf(ListenEvent.Resume), events)
    }

    private fun setScreen(
        variant: ScreenshotVariant,
        player: ListenPlayer = PLAYER,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val small = MaterialTheme.spacing.small
                CompositionLocalProvider(
                    LocalListenBar provides { padding: PaddingValues ->
                        ListenBar(player, { events += it }, Modifier.padding(padding).padding(small))
                    },
                ) {
                    ChatScreen(state = ChatPreviewData.channel, onEvent = {}, onBack = {})
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
        val PLAYER = ListenPlayer(2, 5, isPaused = false, PlaybackSpeed.Faster)
    }
}

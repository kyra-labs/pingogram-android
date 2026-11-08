package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The messages fading into the composer ([ComposerFade], `docs/DESIGN_SYSTEM.md` §6 "Composer"): once the list is
 * scrolled away from its newest message, in every look the audit asks for (§12). At rest on the newest message the
 * fade does not show, which every other golden of the conversation keeps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerFadeScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        val listState = LazyListState()
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = longChat, onEvent = {}, onBack = {}, listState = listState)
            }
        }
        composeRule.waitForIdle()
        // A little older than the newest message: the messages run under the fade.
        composeRule.runOnIdle { runBlocking { listState.scrollToItem(0, SCROLLED) } }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "scrolled.${variant.goldenName}"))
    }

    private companion object {
        const val SCROLLED = 120
        const val COPIES = 4
        const val ID_STEP = 1_000L

        /** The private chat's messages a few times over, with other ids, so the list scrolls. */
        val longChat: ChatUiState =
            ChatPreviewData.privateChat.let { chat ->
                val content = chat.content as ChatContent.Content
                val bubbles = content.items.filterIsInstance<ChatItem.Bubble>()
                val more =
                    (1..COPIES).flatMap { copy -> bubbles.map { it.copy(id = it.id + copy * ID_STEP) } }
                chat.copy(content = content.copy(items = (content.items + more).toImmutableList()))
            }
    }
}

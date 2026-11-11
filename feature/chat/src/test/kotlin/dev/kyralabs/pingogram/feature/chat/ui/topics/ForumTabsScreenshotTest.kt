package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.saved.LocalConversationTabs
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTab
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabs
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabsUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a forum that shows its topics as tabs (`docs/DESIGN_SYSTEM.md` §6 "Forum topic tabs") on a compact phone,
 * a topic selected: light, dark, AMOLED, high contrast and right to left. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ForumTabsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tabs =
        SavedTabsUiState(
            tabs =
                persistentListOf(
                    SavedTab(null, UiText.resource(R.string.forum_tab_all)),
                    SavedTab(1, UiText.Plain("General")),
                    SavedTab(2, UiText.Plain("Bugs")),
                    SavedTab(3, UiText.Plain("Releases")),
                    SavedTab(4, UiText.Plain("Design")),
                ),
            selectedTopicId = 2,
        )

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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalConversationTabs provides { SavedTabs(tabs, {}, {}) }) {
                    ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, variant.goldenName))
    }
}

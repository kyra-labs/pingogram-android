package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import dev.kyralabs.pingogram.feature.chats.ui.preview.UnansweredPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Unanswered page (`docs/DESIGN_SYSTEM.md` §6 "Unanswered"): its chats light and dark, in AMOLED black, right to
 * left and at twice the font size, and the loading and empty states (§12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class UnansweredScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chats = UnansweredPreviewStates().values.first()

    @Test
    fun chatsLight() = capture("chatsLight", ScreenshotVariant.Light)

    @Test
    fun chatsDark() = capture("chatsDark", ScreenshotVariant.Dark)

    @Test
    fun chatsAmoled() = capture("chatsAmoled", ScreenshotVariant.Amoled)

    @Test
    fun chatsRtl() = capture("chatsRtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w411dp-h1200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun chatsFontScale200() = capture("chatsFontScale200", ScreenshotVariant.Light)

    @Test
    fun loadingLight() = capture("loadingLight", ScreenshotVariant.Light, UnansweredUiState.Loading)

    @Test
    fun emptyLight() = capture("emptyLight", ScreenshotVariant.Light, UnansweredUiState.Empty)

    @Test
    fun emptyDark() = capture("emptyDark", ScreenshotVariant.Dark, UnansweredUiState.Empty)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: UnansweredUiState = chats,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { UnansweredScreen(state = state, onEvent = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        // Loading shows its indicator once it takes a moment; the golden shows it then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}

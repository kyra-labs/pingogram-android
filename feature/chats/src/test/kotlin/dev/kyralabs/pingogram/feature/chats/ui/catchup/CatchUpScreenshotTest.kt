package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Catch-up (`docs/DESIGN_SYSTEM.md` §6 "Catch-up"): a card, "All caught up", the empty state, loading and the
 * settings sheet, light and dark, and the card in AMOLED black, high contrast, right to left, a 700 dp window and at
 * twice the font size (§12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CatchUpScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cardLight() = capture("cardLight", ScreenshotVariant.Light)

    @Test
    fun cardDark() = capture("cardDark", ScreenshotVariant.Dark)

    @Test
    fun doneLight() = capture("doneLight", ScreenshotVariant.Light, CatchUpPreviewData.done)

    @Test
    fun doneDark() = capture("doneDark", ScreenshotVariant.Dark, CatchUpPreviewData.done)

    @Test
    fun emptyLight() = capture("emptyLight", ScreenshotVariant.Light, CatchUpUiState(CatchUpStage.Empty))

    @Test
    fun loadingLight() = capture("loadingLight", ScreenshotVariant.Light, CatchUpUiState())

    @Test
    fun settingsLight() =
        capture("settingsLight", ScreenshotVariant.Light, CatchUpPreviewData.cards.copy(showsSettings = true))

    /** Mid-swipe toward the end: the card tilted, the "READ" stamp in, the next chat's card behind it. */
    @Test
    fun swipingToRead() = capture("swipingToRead", ScreenshotVariant.Light, drag = SWIPE_FRACTION)

    /** Mid-swipe toward the start, in dark theme: the "SKIP" stamp. */
    @Test
    fun swipingToSkip() = capture("swipingToSkip", ScreenshotVariant.Dark, drag = -SWIPE_FRACTION)

    @Test
    fun replyingLight() = capture("replyingLight", ScreenshotVariant.Light, CatchUpPreviewData.replying)

    @Test
    fun replyingDark() = capture("replyingDark", ScreenshotVariant.Dark, CatchUpPreviewData.replying)

    @Test
    fun replyingRtl() = capture("replyingRtl", ScreenshotVariant.Rtl, CatchUpPreviewData.replying)

    @Test
    @Config(qualifiers = "w411dp-h1200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun replyingFontScale200() = capture("replyingFontScale200", ScreenshotVariant.Light, CatchUpPreviewData.replying)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture("medium", ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun doneFontScale200() = capture("doneFontScale200", ScreenshotVariant.Light, CatchUpPreviewData.done)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: CatchUpUiState = CatchUpPreviewData.cards,
        drag: Float = 0f,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { CatchUpScreen(state = state, onEvent = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        // Loading shows its indicator once it takes a moment; the golden shows it then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        if (drag != 0f) {
            // Held mid-swipe: the finger stays down while the golden is taken.
            composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).performTouchInput {
                down(center)
                moveBy(Offset(width * drag / 2, 0f))
                moveBy(Offset(width * drag / 2, 0f))
            }
            composeRule.waitForIdle()
        }
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
        const val SWIPE_FRACTION = 0.4f
    }
}

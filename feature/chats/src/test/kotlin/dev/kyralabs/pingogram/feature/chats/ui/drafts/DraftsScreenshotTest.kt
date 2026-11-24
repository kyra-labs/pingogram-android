package dev.kyralabs.pingogram.feature.chats.ui.drafts

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
import dev.kyralabs.pingogram.feature.chats.ui.preview.DraftsPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Drafts page (`docs/DESIGN_SYSTEM.md` §6 "Drafts"): its drafts light and dark, in AMOLED black, right to left
 * and at twice the font size, and the loading and empty states (§12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DraftsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = DraftsPreviewStates().values.first()

    @Test
    fun draftsLight() = capture("draftsLight", ScreenshotVariant.Light)

    @Test
    fun draftsDark() = capture("draftsDark", ScreenshotVariant.Dark)

    @Test
    fun draftsAmoled() = capture("draftsAmoled", ScreenshotVariant.Amoled)

    @Test
    fun draftsRtl() = capture("draftsRtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w411dp-h1200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun draftsFontScale200() = capture("draftsFontScale200", ScreenshotVariant.Light)

    @Test
    fun loadingLight() = capture("loadingLight", ScreenshotVariant.Light, DraftsUiState.Loading)

    @Test
    fun emptyLight() = capture("emptyLight", ScreenshotVariant.Light, DraftsUiState.Empty)

    @Test
    fun emptyDark() = capture("emptyDark", ScreenshotVariant.Dark, DraftsUiState.Empty)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: DraftsUiState = drafts,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { DraftsScreen(state = state, onEvent = {}, onBack = {}) }
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

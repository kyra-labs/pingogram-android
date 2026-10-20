package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of every login screen state ([AuthPreviewData.all]) on a compact phone (`docs/TESTING.md`): light, dark, and
 * dynamic color from Robolectric's system palette. The whole screen is captured, so sheets (country picker, terms of
 * service) show over their screen.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AuthScreenScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun dynamic() = capture(ScreenshotVariant.Dynamic)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    /** The audit's other looks (`docs/DESIGN_SYSTEM.md` §12) for one representative state only. */
    private fun captureAudit(variant: ScreenshotVariant) {
        assumeTrue(stateName == "welcome" || stateName == "phoneNumber")
        capture(variant)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: ScreenshotVariant) {
        val state = AuthPreviewData.all.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                AuthScreen(state = state, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        // A state that is still loading shows its indicator once loading takes a moment; the golden shows it then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.${variant.goldenName}"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = AuthPreviewData.all.keys.map { arrayOf(it) }
    }
}

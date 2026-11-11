package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of "Who can vote" in a channel's poll form (`docs/DESIGN_SYSTEM.md` §6 "New poll") on a compact phone:
 * subscribers only, and some countries with three picked or none yet; light and dark, the countries also right to left
 * and at 200% font.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PollVotersScreenshotTest(
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
    fun rtl() {
        assumeTrue(stateName == "countries")
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "countries")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val (voters, countries) = STATES.getValue(stateName)
        composeRule.setContent { ScreenshotTheme(variant) { PollVotersSample(voters, countries) } }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, Pair<PollVoters, List<String>>> =
            mapOf(
                "subscribers" to (PollVoters.Subscribers to emptyList()),
                "countries" to (PollVoters.Countries to listOf("DE", "FR", "IN")),
                "noCountry" to (PollVoters.Countries to emptyList()),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

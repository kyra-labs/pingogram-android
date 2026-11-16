package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Goldens of the action bar at the top of a conversation someone new started (`docs/DESIGN_SYSTEM.md` §6 "Chat action
 * bar", `docs/TESTING.md`) on a compact phone: the bar of a new person with what Telegram tells of their account, and
 * the bars to share the phone number and of a join request; light and dark, and the first also AMOLED, high contrast,
 * right to left and at 200% font. Brand colors, in UTC.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatActionBarScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var zone: TimeZone? = null

    /** The days shown are in the device's time zone: UTC, so the goldens show the same days everywhere. */
    @Before
    fun useUtc() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(zone)
    }

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() {
        assumeTrue(stateName == "newPerson")
        capture(ScreenshotVariant.Amoled)
    }

    @Test
    fun highContrast() {
        assumeTrue(stateName == "newPerson")
        capture(ScreenshotVariant.HighContrast)
    }

    @Test
    fun rtl() {
        assumeTrue(stateName == "newPerson")
        capture(ScreenshotVariant.Rtl)
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3): the actions wrap rather than clip. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "newPerson")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val bar = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(
                    LocalChatActionBar provides { padding: PaddingValues -> ActionBarSlot(bar, {}, padding) },
                ) {
                    ChatScreen(state = ChatPreviewData.privateChat, onEvent = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, ActionBarUi> =
            mapOf(
                "newPerson" to ChatActionBarPreviewData.reportAddBlock,
                "sharePhone" to ChatActionBarPreviewData.sharePhoneNumber,
                "joinRequest" to ChatActionBarPreviewData.joinRequest,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.LocationPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the "Location" sheet over a private chat on a compact phone, light and dark (`docs/TESTING.md`): the
 * explanation before Android asks and once it no longer does, the search, a location still improving, an accurate
 * one, an approximate one, and none found; the accurate one also at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ShareLocationScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    /** At 200% font scale the sheet scrolls, and the actions wrap where they do not fit (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "found")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    ChatScreen(
                        state = ChatPreviewData.privateChat,
                        onEvent = {},
                        onBack = {},
                        attach = AttachInputs(location = LocationInputs(state = { state })),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // Past the delay of the search's loading indicator.
        composeRule.mainClock.advanceTimeBy(LOADING_DELAY_MILLIS)
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f
        private const val LOADING_DELAY_MILLIS = 500L

        val STATES: Map<String, ShareLocationUiState> =
            mapOf(
                "permission" to ShareLocationUiState.Permission(),
                "deniedForGood" to ShareLocationUiState.Permission(deniedForGood = true),
                "searching" to ShareLocationUiState.Locating(),
                "refining" to LocationPreviewData.refining,
                "found" to LocationPreviewData.found,
                "approximate" to LocationPreviewData.approximate,
                "notFound" to ShareLocationUiState.NotFound,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.preview.PaidMediaPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of paid posts (`docs/DESIGN_SYSTEM.md` §6 "Paid media") and of the unsupported placeholder, every state in
 * the looks the audit asks for (§12): a channel with a post paid for over a locked one, and a message this version
 * cannot show with "Update Pingogram" and, as a store build shows it, without. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PaidMediaScreenshotTest(
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
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                when (stateName) {
                    "posts" -> {
                        ChatScreen(state = PaidMediaPreviewData.posts, onEvent = {}, onBack = {})
                    }

                    "unsupported" -> {
                        ChatScreen(
                            state = PaidMediaPreviewData.unsupported(showsUpdateLink = true),
                            onEvent = {},
                            onBack = {},
                        )
                    }

                    "unsupportedStore" -> {
                        ChatScreen(
                            state = PaidMediaPreviewData.unsupported(showsUpdateLink = false),
                            onEvent = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf("posts", "unsupported", "unsupportedStore").map { arrayOf(it) }
    }
}

package dev.kyralabs.pingogram.feature.chat.ui.saved

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
 * Goldens of Saved Messages filtered by a tag (the owner's board 8, `docs/TESTING.md`) on a compact phone: three
 * tagged messages under the tag chips, and the rename dialog; light, dark, AMOLED, high contrast and right to left, and
 * the list at 200% font. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SavedTagsScreenshotTest(
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
    fun amoled() {
        assumeTrue(stateName == "filtered")
        capture(ScreenshotVariant.Amoled)
    }

    @Test
    fun highContrast() {
        assumeTrue(stateName == "filtered")
        capture(ScreenshotVariant.HighContrast)
    }

    @Test
    fun rtl() {
        assumeTrue(stateName == "filtered")
        capture(ScreenshotVariant.Rtl)
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3): the chips and rows grow and never clip. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "filtered")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                // A dialog is a window of its own, captured alone, as the profile's note dialog is.
                val rename = state.rename
                if (rename != null) {
                    RenameTagDialog(rename, onEvent = {})
                } else {
                    SavedTagScreen(state, savedTagPreview, chips = { SavedTagChips(state, {}, {}) }, onEvent = {})
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, SavedTagsUiState> =
            mapOf(
                "filtered" to savedTagsPreview,
                "rename" to savedTagsPreview.copy(rename = SavedTagRename(savedTagPreview)),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

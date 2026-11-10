package dev.kyralabs.pingogram.feature.chats.ui.stories

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the sheet of the user's stories being posted (board 10c), its content on the sheet's container as
 * `ModalBottomSheet` draws it: two stories 62% sent with one to view, sizes not known yet, one TDLib gave up on, and
 * stopping that failed; the first also dark, true black, high contrast, right to left and at 200% font.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryUploadSheetScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = only("posting") { capture(ScreenshotVariant.Dark) }

    @Test
    fun amoled() = only("posting") { capture(ScreenshotVariant.Amoled) }

    @Test
    fun highContrast() = only("posting") { capture(ScreenshotVariant.HighContrast) }

    @Test
    fun rtl() = only("posting") { capture(ScreenshotVariant.Rtl) }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = only("posting") { capture(ScreenshotVariant.Light, "fontScale200") }

    private fun only(
        name: String,
        test: () -> Unit,
    ) {
        assumeTrue(stateName == name)
        test()
    }

    @OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
    private fun capture(
        variant: ScreenshotVariant,
        goldenName: String = variant.name.lowercase(),
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) {
                        StoryUploadContent(state.upload, state.cancel, state.canView, {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(goldenPath(javaClass, "$stateName.$goldenName"))
    }

    /** A state of the sheet. */
    class State(
        val upload: StoryRingUpload,
        val cancel: UploadCancel = UploadCancel(),
        val canView: Boolean = false,
    )

    companion object {
        internal val STATES =
            mapOf(
                "posting" to State(StoryRingUpload(persistentListOf(-1, -2), progress = 0.62f), canView = true),
                "waiting" to State(StoryRingUpload(persistentListOf(-1), progress = null)),
                "failed" to
                    State(
                        StoryRingUpload(persistentListOf(-1), progress = null, failed = 1, error = DomainError.Network),
                    ),
                "stopFailed" to
                    State(
                        StoryRingUpload(persistentListOf(-1), progress = 0.4f),
                        cancel = UploadCancel(error = DomainError.Network),
                    ),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

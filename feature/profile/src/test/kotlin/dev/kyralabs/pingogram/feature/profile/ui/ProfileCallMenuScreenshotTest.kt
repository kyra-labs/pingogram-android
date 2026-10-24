package dev.kyralabs.pingogram.feature.profile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.CallStarter
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Goldens of the profile's "Call" pill with its menu open (`docs/DESIGN_SYSTEM.md` §6, "Call buttons and
 * notifications"): "Voice call" and "Video call" in one expressive group, anchored to the pill, in every look the audit
 * asks for, a 700 dp window and at 200% font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileCallMenuScreenshotTest {
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
    @Config(fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val content = ProfilePreviewData.content
        val state = content.copy(actions = content.actions.copy(callUserId = content.header.userId))
        composeRule.setContent {
            ScreenshotTheme(variant) {
                CompositionLocalProvider(LocalCallStarter provides VideoCalls) {
                    ProfileScreen(
                        gridState = rememberLazyGridState(),
                        state = state,
                        media = ProfilePreviewData.media,
                        onEvent = {},
                        onMediaEvent = {},
                        onBack = {},
                        mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
                        onEditContact = {},
                    )
                }
            }
        }
        val call = RuntimeEnvironment.getApplication().getString(R.string.profile_action_call)
        composeRule.onNodeWithText(call).performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage(
            "src/test/snapshots/dev.kyralabs.pingogram.feature.profile.ui.ProfileCallMenuScreenshotTest.$name.png",
        )
    }

    /** Every user can get a video call. */
    private object VideoCalls : CallStarter {
        override fun call(userId: Long) = Unit

        override fun videoCall(userId: Long) = Unit

        override fun canVideoCall(userId: Long): Flow<Boolean> = flowOf(true)
    }
}

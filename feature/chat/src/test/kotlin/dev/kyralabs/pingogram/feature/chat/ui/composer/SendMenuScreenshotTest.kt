package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The send button's menu open (`docs/DESIGN_SYSTEM.md` §6 Composer): "Send without sound" first by default, "Send with
 * sound" in its place while messages go silently by default (`REQUIREMENTS.md` FR-X11), light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SendMenuScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun withSoundLight() = capture("withSoundLight", ThemeMode.Light, sendsSilently = false)

    @Test
    fun silentByDefaultLight() = capture("silentByDefaultLight", ThemeMode.Light, sendsSilently = true)

    @Test
    fun silentByDefaultDark() = capture("silentByDefaultDark", ThemeMode.Dark, sendsSilently = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        sendsSilently: Boolean,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(LocalSendsSilently provides sendsSilently) {
                    // The button alone: a screen capture draws a text field's text twice under Robolectric.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(MaterialTheme.spacing.small),
                        Alignment.BottomEnd,
                    ) {
                        SendButton(canSchedule = true, onSend = {}, onSendSound = {}, onSchedule = {})
                    }
                }
            }
        }
        val send = RuntimeEnvironment.getApplication().getString(R.string.composer_send)
        composeRule.onNodeWithContentDescription(send).performTouchInput { longClick() }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}

package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The attach preview for screen readers and touch (audit A15): every photo's and video's remove button, drawn small
 * at the thumbnail's corner, is named and takes a 48 dp touch target, as every other control of the sheet does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AttachPreviewAccessibilityTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `every control of the media preview has a name and a touch target`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(dynamicColor = false) {
                    PreviewImages {
                        AttachPreviewSheet(
                            preview = AttachPreviewData.media,
                            caption = ComposerText(),
                            onEvent = {},
                            onSend = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Remove Photo 1 of 4", substring = true).assertExists()
        composeRule.assertAccessible()
    }
}

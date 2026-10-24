package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of transferring the ownership (`docs/TESTING.md`): the owner's rights page with "Transfer ownership", and its
 * question in light, dark, AMOLED, high contrast, right to left, a 700 dp window and twice the font size; a wrong
 * password, and the transfer under way. Through a compose rule's activity, so the dialog's window has the focus, and
 * its field with it, in every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TransferOwnershipScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Tall, so the row below the rights shows.
    @Test
    @Config(qualifiers = TALL)
    fun ownerRights() = capture("ownerRights", ScreenshotVariant.Light, transfer = null)

    @Test
    @Config(qualifiers = TALL)
    fun ownerRightsDark() = capture("ownerRightsDark", ScreenshotVariant.Dark, transfer = null)

    @Test
    fun dialogLight() = capture("dialogLight", ScreenshotVariant.Light)

    @Test
    fun dialogDark() = capture("dialogDark", ScreenshotVariant.Dark)

    @Test
    fun dialogAmoled() = capture("dialogAmoled", ScreenshotVariant.Amoled)

    @Test
    fun dialogHighContrast() = capture("dialogHighContrast", ScreenshotVariant.HighContrast)

    @Test
    fun dialogRtl() = capture("dialogRtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun dialogMedium() = capture("dialogMedium", ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = 2f)
    fun dialogFontScale200() = capture("dialogFontScale200", ScreenshotVariant.Light)

    @Test
    fun wrongPassword() =
        capture("wrongPassword", ScreenshotVariant.Light, ChatInfoPreviewData.transferWrongPassword, "hunter2")

    @Test
    fun transferring() = capture("transferring", ScreenshotVariant.Light, ChatInfoPreviewData.transferring, "hunter2")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        transfer: TransferDialog? = ChatInfoPreviewData.transferDialog,
        password: String = "",
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                MemberRightsScreen(
                    ChatInfoPreviewData.ownerRights,
                    onEvent = {},
                    onBack = {},
                    transfer = transfer,
                    transferPassword = remember { TextFieldState(password) },
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("$SNAPSHOTS/dev.kyralabs.pingogram.feature.profile.chatinfo.$TEST.$name.png")
    }

    private companion object {
        const val TALL = "w411dp-h1400dp-xxhdpi"
        const val SNAPSHOTS = "src/test/snapshots"
        const val TEST = "TransferOwnershipScreenshotTest"
    }
}

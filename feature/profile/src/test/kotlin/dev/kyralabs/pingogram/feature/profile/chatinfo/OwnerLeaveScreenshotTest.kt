package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the owner's sheet before leaving (`docs/DESIGN_SYSTEM.md` §6 "Leaving as the owner"), on the sheet's
 * container as `ModalBottomSheet` draws it: choosing in every look the audit asks for, at a 700 dp width and at 200%
 * font; the password, a wrong one, and a basic group without other administrators. Transferring is a preview only: its
 * wavy indicator moves between runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class OwnerLeaveScreenshotTest {
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
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    fun chosen() = capture(ScreenshotVariant.Light, ChatInfoPreviewData.ownerLeave.copy(chosen = 3))

    @Test
    fun password() = capture(ScreenshotVariant.Light, ChatInfoPreviewData.ownerLeavePassword, "hunter2")

    @Test
    fun wrongPasswordDark() = capture(ScreenshotVariant.Dark, ChatInfoPreviewData.ownerLeaveWrongPassword, "hunter2")

    @Test
    fun noAdministrators() = capture(ScreenshotVariant.Light, ChatInfoPreviewData.ownerLeaveNoAdmins)

    private fun capture(
        variant: ScreenshotVariant,
        sheet: OwnerLeaveSheet = ChatInfoPreviewData.ownerLeave,
        password: String = "",
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            SheetSurface {
                OwnerLeaveContent(
                    sheet,
                    "Rust Berlin",
                    remember { TextFieldState(password) },
                    onEvent = {},
                    focusPassword = false,
                )
            }
        }
    }
}

/** [content] on a bottom sheet's container, its top corners and padding, as in the sheet's window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
    ) {
        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
    }
}

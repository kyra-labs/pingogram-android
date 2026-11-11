package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the "Set tag" sheet's content (`docs/DESIGN_SYSTEM.md` §6 "Member tags", the owner's board 18b), on the
 * sheet's container as `ModalBottomSheet` draws it: a member's tag being typed in every look the audit asks for, at a
 * 700 dp width and at 200% font, an administrator's tag, saving, a refusal and the user's own tag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MemberTagScreenshotTest {
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
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    fun administratorDark() = capture(ScreenshotVariant.Dark, MemberTagPreviewData.administrator)

    @Test
    fun saving() = capture(ScreenshotVariant.Light, MemberTagPreviewData.typing.copy(isSaving = true))

    @Test
    fun failed() =
        capture(ScreenshotVariant.Light, MemberTagPreviewData.typing.copy(error = DomainError.AdminRightsRequired))

    @Test
    fun own() = capture(ScreenshotVariant.Light, MemberTagPreviewData.own)

    private fun capture(
        variant: ScreenshotVariant,
        editor: MemberTagEditor = MemberTagPreviewData.typing,
    ) = captureRoboImage { ScreenshotTheme(variant) { SheetSurface { MemberTagContent(editor, onEvent = {}) } } }
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

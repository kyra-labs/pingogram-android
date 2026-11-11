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
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeEditor
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeEditorContent
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesUiState
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a group's welcome messages (`docs/DESIGN_SYSTEM.md` §6 "Welcome message setup", the owner's board 20):
 * the page with two messages in every look the audit asks for, at a 700 dp width and at 200% font, the page while off,
 * the error, and the editor's content (a photo message, a new one, a formatted one).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class WelcomeMessagesScreenshotTest {
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
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = COMPACT)
    fun offDark() = capture(ScreenshotVariant.Dark, WelcomeMessagesPreviewData.off)

    @Test
    @Config(qualifiers = COMPACT)
    fun error() = capture(ScreenshotVariant.Light, WelcomeMessagesUiState.Error(DomainError.Network))

    @Test
    @Config(qualifiers = COMPACT)
    fun editorPhoto() = captureEditor(ScreenshotVariant.Light, WelcomeMessagesPreviewData.photoEditor)

    @Test
    @Config(qualifiers = COMPACT)
    fun editorNewDark() = captureEditor(ScreenshotVariant.Dark, WelcomeMessagesPreviewData.newEditor)

    @Test
    @Config(qualifiers = COMPACT)
    fun editorFormatted() = captureEditor(ScreenshotVariant.Light, WelcomeMessagesPreviewData.formattedEditor)

    private fun capture(
        variant: ScreenshotVariant,
        state: WelcomeMessagesUiState = WelcomeMessagesPreviewData.on,
    ) = captureRoboImage { ScreenshotTheme(variant) { WelcomeMessagesScreen(state, onEvent = {}, onBack = {}) } }

    private fun captureEditor(
        variant: ScreenshotVariant,
        editor: WelcomeEditor,
    ) = captureRoboImage {
        ScreenshotTheme(variant) { SheetSurface { WelcomeEditorContent(editor, onEvent = {}) } }
    }
}

/** A sheet's content on the sheet's surface, without the sheet's window, which takes the focus on some runs only. */
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

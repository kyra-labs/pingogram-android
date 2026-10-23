package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteContent
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteUiState
import dev.kyralabs.pingogram.feature.settings.ui.preview.FolderInvitePreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared folder sheet's content on the sheet's container, in each of its states, light and dark; a new folder in
 * AMOLED black, high contrast, right to left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FolderInviteScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun newFolderLight() = capture("newFolderLight", ThemeMode.Light, FolderInvitePreviewStates.newFolder)

    @Test
    fun newFolderDark() = capture("newFolderDark", ThemeMode.Dark, FolderInvitePreviewStates.newFolder)

    @Test
    fun newChatsLight() = capture("newChatsLight", ThemeMode.Light, FolderInvitePreviewStates.newChats)

    @Test
    fun newChatsDark() = capture("newChatsDark", ThemeMode.Dark, FolderInvitePreviewStates.newChats)

    @Test
    fun addedLight() = capture("addedLight", ThemeMode.Light, FolderInvitePreviewStates.added)

    @Test
    fun addedDark() = capture("addedDark", ThemeMode.Dark, FolderInvitePreviewStates.added)

    @Test
    fun addingLight() = capture("addingLight", ThemeMode.Light, FolderInvitePreviewStates.adding)

    @Test
    fun addingDark() = capture("addingDark", ThemeMode.Dark, FolderInvitePreviewStates.adding)

    @Test
    fun addFailedLight() = capture("addFailedLight", ThemeMode.Light, FolderInvitePreviewStates.addFailed)

    @Test
    fun addFailedDark() = capture("addFailedDark", ThemeMode.Dark, FolderInvitePreviewStates.addFailed)

    @Test
    fun invalidLight() = capture("invalidLight", ThemeMode.Light, FolderInvitePreviewStates.invalid)

    @Test
    fun invalidDark() = capture("invalidDark", ThemeMode.Dark, FolderInvitePreviewStates.invalid)

    @Test
    fun offlineLight() = capture("offlineLight", ThemeMode.Light, FolderInvitePreviewStates.offline)

    @Test
    fun offlineDark() = capture("offlineDark", ThemeMode.Dark, FolderInvitePreviewStates.offline)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, FolderInviteUiState.Loading)

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, FolderInviteUiState.Loading)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, FolderInvitePreviewStates.newFolder)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: FolderInviteUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { SheetSurface { FolderInviteContent(state, {}, {}) } }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            SheetSurface { FolderInviteContent(FolderInvitePreviewStates.newFolder, {}, {}) }
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

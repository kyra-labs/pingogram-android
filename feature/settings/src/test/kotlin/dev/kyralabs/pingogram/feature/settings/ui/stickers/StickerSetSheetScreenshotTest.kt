package dev.kyralabs.pingogram.feature.settings.ui.stickers

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
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickerSetSheetPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetContent
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetSheetUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sticker set's sheet content on the sheet's container, in each of its states, light and dark; a set not added
 * in AMOLED black, high contrast, right to left, a 700 dp window and at twice the font size
 * (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StickerSetSheetScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun notAddedLight() = capture("notAddedLight", ThemeMode.Light, StickerSetSheetPreviewStates.notAdded)

    @Test
    fun notAddedDark() = capture("notAddedDark", ThemeMode.Dark, StickerSetSheetPreviewStates.notAdded)

    @Test
    fun addedLight() = capture("addedLight", ThemeMode.Light, StickerSetSheetPreviewStates.added)

    @Test
    fun addedDark() = capture("addedDark", ThemeMode.Dark, StickerSetSheetPreviewStates.added)

    @Test
    fun addingLight() = capture("addingLight", ThemeMode.Light, StickerSetSheetPreviewStates.adding)

    @Test
    fun addFailedLight() = capture("addFailedLight", ThemeMode.Light, StickerSetSheetPreviewStates.addFailed)

    @Test
    fun addFailedDark() = capture("addFailedDark", ThemeMode.Dark, StickerSetSheetPreviewStates.addFailed)

    @Test
    fun notFoundLight() = capture("notFoundLight", ThemeMode.Light, StickerSetSheetPreviewStates.notFound)

    @Test
    fun notFoundDark() = capture("notFoundDark", ThemeMode.Dark, StickerSetSheetPreviewStates.notFound)

    @Test
    fun offlineLight() = capture("offlineLight", ThemeMode.Light, StickerSetSheetPreviewStates.offline)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, StickerSetSheetUiState.Loading)

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
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, StickerSetSheetPreviewStates.notAdded)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: StickerSetSheetUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        SheetSurface { StickerSetContent(state, onEvent = {}, onShare = {}, onClose = {}) }
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            SheetSurface {
                StickerSetContent(StickerSetSheetPreviewStates.notAdded, onEvent = {}, onShare = {}, onClose = {})
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

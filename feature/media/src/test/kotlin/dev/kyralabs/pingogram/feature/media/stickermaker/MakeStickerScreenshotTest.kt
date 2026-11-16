package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of making a sticker (`docs/TESTING.md`, boards 14a, 14b): the editor in light, dark, AMOLED, high contrast,
 * right to left, 200% font scale and a 700 dp window, the photo downloading and failed; the sheet choosing a set in
 * light, dark and at 200% font, its sets loading and failed, a refusal, a new set, a taken link, and done. Brand
 * colors; the photo is a gradient of the theme's colors, and the sticker's preview its placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MakeStickerScreenshotTest {
    @Test
    fun editorLight() = editor(MakeStickerPreviewData.editing)

    @Test
    fun editorDark() = editor(MakeStickerPreviewData.editing, ThemeMode.Dark)

    @Test
    fun editorAmoled() = editor(MakeStickerPreviewData.editing, ThemeMode.Dark, amoled = true)

    @Test
    fun editorHighContrast() = editor(MakeStickerPreviewData.editing, contrast = Contrast.High)

    @Test
    fun editorRtl() = editor(MakeStickerPreviewData.editing, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun editorFontScale200() = editor(MakeStickerPreviewData.editing)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun editorMedium() = editor(MakeStickerPreviewData.editing)

    @Test
    fun loadingLight() = editor(MakeStickerPreviewData.loading)

    @Test
    fun failedLight() = editor(MakeStickerPreviewData.failed)

    @Test
    fun chooseLight() = sheet(MakeStickerPreviewData.choose)

    @Test
    fun chooseDark() = sheet(MakeStickerPreviewData.choose, ThemeMode.Dark)

    @Test
    @Config(fontScale = 2f)
    fun chooseFontScale200() = sheet(MakeStickerPreviewData.choose)

    @Test
    fun chooseRtl() = sheet(MakeStickerPreviewData.choose, layoutDirection = LayoutDirection.Rtl)

    @Test
    fun setsLoadingLight() = sheet(MakeStickerPreviewData.chooseLoading)

    @Test
    fun setsFailedLight() = sheet(MakeStickerPreviewData.chooseFailed)

    @Test
    fun refusedLight() = sheet(MakeStickerPreviewData.refused)

    @Test
    fun newSetLight() = sheet(MakeStickerPreviewData.newSet)

    @Test
    fun newSetDark() = sheet(MakeStickerPreviewData.newSet, ThemeMode.Dark)

    @Test
    fun takenLight() = sheet(MakeStickerPreviewData.taken)

    @Test
    fun doneLight() = sheet(MakeStickerPreviewData.done)

    @Test
    fun doneDark() = sheet(MakeStickerPreviewData.done, ThemeMode.Dark)

    private fun editor(
        state: MakeStickerUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = capture(themeMode, amoled, contrast, layoutDirection) {
        MakeStickerScreen(state = state, photo = previewStickerPhoto(), capture = null, onEvent = {}, onClose = {})
    }

    private fun sheet(
        sheet: AddToSetState,
        themeMode: ThemeMode = ThemeMode.Light,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = capture(themeMode, layoutDirection = layoutDirection) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { AddToSetContent(sheet, onEvent = {}) }
    }

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(
                themeMode = themeMode,
                dynamicColor = false,
                amoled = amoled,
                contrast = contrast,
                content = content,
            )
        }
    }
}

package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

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
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Making a sticker set (`docs/DESIGN_SYSTEM.md` §6 "New sticker set"): the name step light and dark, a taken link, the
 * stickers step in every look the audit asks for, at a 700 dp width and at 200% font, the set published, and the
 * emoji sheet's content. The stickers' images are not on the test's device, so their tiles show their color only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NewStickerSetScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nameLight() = capture("nameLight", ThemeMode.Light, NewStickerSetPreviewData.name)

    @Test
    fun nameDark() = capture("nameDark", ThemeMode.Dark, NewStickerSetPreviewData.name)

    @Test
    fun takenLight() = capture("takenLight", ThemeMode.Light, NewStickerSetPreviewData.taken)

    @Test
    fun stickersLight() = capture("stickersLight", ThemeMode.Light, NewStickerSetPreviewData.stickers)

    @Test
    fun stickersDark() = capture("stickersDark", ThemeMode.Dark, NewStickerSetPreviewData.stickers)

    @Test
    fun publishedLight() = capture("publishedLight", ThemeMode.Light, NewStickerSetPreviewData.published)

    @Test
    fun publishedDark() = capture("publishedDark", ThemeMode.Dark, NewStickerSetPreviewData.published)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, NewStickerSetPreviewData.stickers)

    @Test
    fun emojiSheet() =
        composeRule.captureScreen(javaClass, "emojiSheet", ThemeMode.Light) {
            SheetSurface { EmojiChoices(index = 2, groups = NewStickerSetPreviewData.emoji, onEvent = {}) }
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NewStickerSetUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { Screen(state) }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) { Screen(NewStickerSetPreviewData.stickers) }
}

@Composable
private fun Screen(state: NewStickerSetUiState) {
    NewStickerSetScreen(state = state, emoji = persistentListOf(), onEvent = {}, onShare = {}, onOpenSet = {})
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

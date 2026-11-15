package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.activity.ComponentActivity
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Focus page and its editor, light and dark, in AMOLED black, high contrast, right to left, a 700 dp window and at
 * twice the font size (`docs/DESIGN_SYSTEM.md` §12): profiles with one on, none (the templates), the editor of a
 * scheduled profile with chats it also allows, a new one by hand refused without a name, the question before deleting,
 * and the picker of chats to also allow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FocusScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = page("light", ThemeMode.Light)

    @Test
    fun dark() = page("dark", ThemeMode.Dark)

    @Test
    fun emptyLight() = page("emptyLight", ThemeMode.Light, FocusPreviewStates.empty)

    @Test
    fun editLight() = edit("editLight", ThemeMode.Light)

    @Test
    fun editDark() = edit("editDark", ThemeMode.Dark)

    @Test
    fun newRefusedLight() = edit("newRefusedLight", ThemeMode.Light, FocusPreviewStates.newByHand)

    @Test
    fun deleteAskedLight() = edit("deleteAskedLight", ThemeMode.Light, FocusPreviewStates.askDelete)

    @Test
    fun chatPickerLight() = picker("chatPickerLight", ThemeMode.Light)

    @Test
    fun chatPickerDark() = picker("chatPickerDark", ThemeMode.Dark)

    @Test
    fun amoled() = audit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = audit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = audit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = audit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = page("fontScale200", ThemeMode.Light)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun editFontScale200() = edit("editFontScale200", ThemeMode.Light)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun chatPickerFontScale200() = picker("chatPickerFontScale200", ThemeMode.Light)

    @Test
    fun chatPickerRtl() =
        composeRule.captureScreen(javaClass, "chatPickerRtl", ScreenshotVariant.Rtl) { PickerOnSheet() }

    private fun page(
        name: String,
        themeMode: ThemeMode,
        state: FocusUiState = FocusPreviewStates.content,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        FocusScreen(state, onEvent = {}, onOpenProfile = { _, _ -> }, onBack = {})
    }

    private fun edit(
        name: String,
        themeMode: ThemeMode,
        state: FocusEditUiState = FocusPreviewStates.editing,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        FocusEditScreen(state, onEvent = {}, onBack = {})
    }

    private fun picker(
        name: String,
        themeMode: ThemeMode,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { PickerOnSheet() }

    /** The picker's content on the bottom sheet's container, as it shows. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerOnSheet() {
        Surface(color = BottomSheetDefaults.ContainerColor) {
            FocusChatPickerContent(FocusPreviewStates.chatPicker, onEvent = {})
        }
    }

    private fun audit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            FocusScreen(FocusPreviewStates.content, onEvent = {}, onOpenProfile = { _, _ -> }, onBack = {})
        }
}

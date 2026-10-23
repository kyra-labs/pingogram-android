package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFolderEditPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The folder editor in each of its states and its chat picker, light and dark; an edited folder in AMOLED black, high
 * contrast, right to left, a 700 dp window, an expanded one and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL)
class ChatFolderEditScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editingLight() = capture("editingLight", ThemeMode.Light, ChatFolderEditPreviewStates.editing)

    @Test
    fun editingDark() = capture("editingDark", ThemeMode.Dark, ChatFolderEditPreviewStates.editing)

    @Test
    fun newLight() = capture("newLight", ThemeMode.Light, ChatFolderEditPreviewStates.new)

    @Test
    fun newDark() = capture("newDark", ThemeMode.Dark, ChatFolderEditPreviewStates.new)

    @Test
    fun refusedLight() = capture("refusedLight", ThemeMode.Light, ChatFolderEditPreviewStates.refused)

    @Test
    fun refusedDark() = capture("refusedDark", ThemeMode.Dark, ChatFolderEditPreviewStates.refused)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, ChatFolderEditPreviewStates.saving)

    @Test
    fun savingDark() = capture("savingDark", ThemeMode.Dark, ChatFolderEditPreviewStates.saving)

    @Test
    fun confirmingDeleteLight() =
        capture("confirmingDeleteLight", ThemeMode.Light, ChatFolderEditPreviewStates.confirmingDelete)

    @Test
    fun confirmingDeleteDark() =
        capture("confirmingDeleteDark", ThemeMode.Dark, ChatFolderEditPreviewStates.confirmingDelete)

    @Test
    @Config(qualifiers = COMPACT)
    fun pickerLight() = capturePicker("pickerLight", ThemeMode.Light)

    @Test
    @Config(qualifiers = COMPACT)
    fun pickerDark() = capturePicker("pickerDark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, ChatFolderEditUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, ChatFolderEditUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun errorLight() = capture("errorLight", ThemeMode.Light, ChatFolderEditUiState.Error(DomainError.Network))

    @Test
    @Config(qualifiers = COMPACT)
    fun errorDark() = capture("errorDark", ThemeMode.Dark, ChatFolderEditUiState.Error(DomainError.Network))

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM_TALL)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, ChatFolderEditPreviewStates.editing)

    @Test
    @Config(qualifiers = "w411dp-h3600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ChatFolderEditPreviewStates.editing)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChatFolderEditUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChatFolderEditScreen(state = state, picker = null, onEvent = {}, onBack = {})
    }

    private fun capturePicker(
        name: String,
        themeMode: ThemeMode,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChatFolderEditScreen(
            state = ChatFolderEditPreviewStates.editing,
            picker = ChatFolderEditPreviewStates.picker,
            onEvent = {},
            onBack = {},
        )
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ChatFolderEditScreen(state = ChatFolderEditPreviewStates.editing, picker = null, onEvent = {}, onBack = {})
        }
}

/** A phone as [COMPACT], tall enough for the whole page. */
private const val TALL = "w411dp-h1800dp-xxhdpi"

/** The medium (700 dp) and expanded windows of the other settings goldens, tall enough for the whole page. */
private const val MEDIUM_TALL = "w700dp-h1800dp-xxhdpi"
private const val EXPANDED_TALL = "w840dp-h1800dp-xxhdpi"

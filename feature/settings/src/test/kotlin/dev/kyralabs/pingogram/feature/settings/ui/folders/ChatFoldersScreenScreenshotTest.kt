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
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFoldersPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Chat folders page in each of its states, light and dark; with folders in AMOLED black, high contrast, right to
 * left, a 700 dp window, an expanded one and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL)
class ChatFoldersScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentLight() = capture("contentLight", ThemeMode.Light, ChatFoldersPreviewStates.content)

    @Test
    fun contentDark() = capture("contentDark", ThemeMode.Dark, ChatFoldersPreviewStates.content)

    @Test
    fun noFoldersLight() = capture("noFoldersLight", ThemeMode.Light, ChatFoldersPreviewStates.noFolders)

    @Test
    fun noFoldersDark() = capture("noFoldersDark", ThemeMode.Dark, ChatFoldersPreviewStates.noFolders)

    @Test
    fun addingLight() = capture("addingLight", ThemeMode.Light, ChatFoldersPreviewStates.adding)

    @Test
    fun addingDark() = capture("addingDark", ThemeMode.Dark, ChatFoldersPreviewStates.adding)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, ChatFoldersUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, ChatFoldersUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun errorLight() = capture("errorLight", ThemeMode.Light, ChatFoldersUiState.Error(DomainError.Network))

    @Test
    @Config(qualifiers = COMPACT)
    fun errorDark() = capture("errorDark", ThemeMode.Dark, ChatFoldersUiState.Error(DomainError.Network))

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
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, ChatFoldersPreviewStates.content)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ChatFoldersPreviewStates.content)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChatFoldersUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChatFoldersScreen(state = state, onEvent = {}, onOpenFolder = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ChatFoldersScreen(state = ChatFoldersPreviewStates.content, onEvent = {}, onOpenFolder = {}, onBack = {})
        }
}

/** A phone as [COMPACT], tall enough for the whole page. */
private const val TALL = "w411dp-h1200dp-xxhdpi"

/** The medium (700 dp) and expanded windows of the other settings goldens, tall enough for the whole page. */
private const val MEDIUM_TALL = "w700dp-h1200dp-xxhdpi"
private const val EXPANDED_TALL = "w840dp-h1200dp-xxhdpi"

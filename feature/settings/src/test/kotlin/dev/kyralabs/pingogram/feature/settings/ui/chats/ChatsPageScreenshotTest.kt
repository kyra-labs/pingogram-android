package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
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
 * The Chats page (owner decision 2026-11-19): its defaults, the settings changed (raise to listen off, so "Pause when
 * lowered" is gone; send silently by default and confirm bot commands on and undo and redo off, FR-X11 to FR-X13,
 * light and dark), loading, 200% font scale, and the audit's variants (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatsPageScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, defaults)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, defaults)

    @Test
    fun changedLight() = capture("changedLight", ThemeMode.Light, changed)

    @Test
    fun changedDark() = capture("changedDark", ThemeMode.Dark, changed)

    /** The whole page, down to the Chat list group, with "Drafts button" off (`REQUIREMENTS.md` FR-X16). */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun draftsButtonOffLight() =
        capture(
            "draftsButtonOffLight",
            ThemeMode.Light,
            defaults.copy(chatList = ChatListSettings(showDraftsButton = false)),
        )

    /** The whole page, down to the Chat list group, with "Unanswered button" off and its days dimmed (FR-X22). */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun unansweredOffLight() =
        capture(
            "unansweredOffLight",
            ThemeMode.Light,
            defaults.copy(unanswered = UnansweredSettings(showButton = false, afterDays = 7)),
        )

    /** The whole page, down to the Chat list group, with "Compact chat list" on (`REQUIREMENTS.md` FR-X19). */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun compactOnLight() = capture("compactOnLight", ThemeMode.Light, defaults.copy(compactChatList = true))

    /** The whole page, down to the Chat list group, with "Notes and names on this phone" off (FR-X20). */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun notesAndNamesOffLight() =
        capture("notesAndNamesOffLight", ThemeMode.Light, defaults.copy(notesAndNames = false))

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, ChatsPageUiState.Loading)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, defaults)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    private val defaults = ChatsPageUiState.Content(transcribeAutomatically = false)

    private val changed =
        ChatsPageUiState.Content(
            sendKeys = SendKeys(hardwareKeyboard = false, onScreenKeyboard = true),
            raiseToListen = RaiseToListenSettings(enabled = false),
            skipSilence = true,
            sendsSilently = true,
            confirmsBotCommands = true,
            showsUndoRedo = false,
        )

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ChatsPageScreen(defaults, onEvent = {}, onBack = {})
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ChatsPageUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ChatsPageScreen(state, onEvent = {}, onBack = {})
    }
}

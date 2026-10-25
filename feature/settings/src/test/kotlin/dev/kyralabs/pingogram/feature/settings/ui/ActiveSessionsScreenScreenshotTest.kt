package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Active sessions page in each of its states, and with a termination to confirm (a screen capture). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ActiveSessionsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.sessions)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.sessions)

    @Test
    fun onlyThisDeviceLight() = capture("onlyThisDeviceLight", ThemeMode.Light, SettingsPreviewData.onlyThisDevice)

    @Test
    fun testServerLight() = capture("testServerLight", ThemeMode.Light, onTestServers)

    @Test
    fun testServerDark() = capture("testServerDark", ThemeMode.Dark, onTestServers)

    @Test
    fun testServerAmoled() = capture("testServer", ScreenshotVariant.Amoled)

    @Test
    fun testServerHighContrast() = capture("testServer", ScreenshotVariant.HighContrast)

    @Test
    fun testServerRtl() = capture("testServer", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun testServerMedium() = capture("testServer", ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun testServerFontScale200() = capture("testServerFontScale200", ThemeMode.Light, onTestServers)

    @Test
    fun terminateDialogLight() = capture("terminateDialogLight", ThemeMode.Light, withSessionDialog)

    @Test
    fun terminateAllDialogDark() =
        capture(
            "terminateAllDialogDark",
            ThemeMode.Dark,
            SettingsPreviewData.sessions.copy(dialog = TerminateDialog.AllOthers),
        )

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, ActiveSessionsUiState())

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, ActiveSessionsUiState())

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, failed)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, failed)

    private val withSessionDialog =
        SettingsPreviewData.sessions.copy(dialog = TerminateDialog.Session(3, UiText.Plain("MacBook Pro")))

    private val failed = ActiveSessionsUiState(sessions = SessionsState.Failed(DomainError.Network))

    private val onTestServers = SettingsPreviewData.sessions.copy(isOnTestServers = true)

    private fun capture(
        name: String,
        variant: ScreenshotVariant,
    ) = composeRule.captureScreen(javaClass, "$name${variant.name}", variant) {
        ActiveSessionsScreen(state = onTestServers, onEvent = {}, onBack = {})
    }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ActiveSessionsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ActiveSessionsScreen(state = state, onEvent = {}, onBack = {})
    }
}

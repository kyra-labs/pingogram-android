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

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ActiveSessionsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ActiveSessionsScreen(state = state, onEvent = {}, onBack = {})
    }
}

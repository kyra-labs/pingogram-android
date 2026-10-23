package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.EXPANDED
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of linking a device and of the user's own QR code (`docs/TESTING.md`): the question, an expired code and
 * the new session, light and dark; the code of a username in light, dark and on an expanded window, and a temporary
 * one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class QrScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginLight() = login("loginLight", ThemeMode.Light, QrLoginUiState.Confirm())

    @Test
    fun loginDark() = login("loginDark", ThemeMode.Dark, QrLoginUiState.Confirm())

    @Test
    fun loginExpiredLight() =
        login("loginExpiredLight", ThemeMode.Light, QrLoginUiState.Confirm(error = DomainError.QrLoginExpired))

    @Test
    fun loginDoneLight() = login("loginDoneLight", ThemeMode.Light, QrLoginUiState.Done(PreviewLinkedSession))

    @Test
    fun loginDoneDark() = login("loginDoneDark", ThemeMode.Dark, QrLoginUiState.Done(PreviewLinkedSession))

    @Test
    fun myCodeLight() = myCode("myCodeLight", ThemeMode.Light, PERMANENT)

    @Test
    fun myCodeDark() = myCode("myCodeDark", ThemeMode.Dark, PERMANENT)

    @Test
    @Config(qualifiers = EXPANDED)
    fun myCodeExpanded() = myCode("myCodeExpanded", ThemeMode.Light, PERMANENT)

    @Test
    fun myCodeTemporaryLight() = myCode("myCodeTemporaryLight", ThemeMode.Light, TEMPORARY)

    private fun login(
        name: String,
        themeMode: ThemeMode,
        state: QrLoginUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { QrLoginScreen(state, {}, {}) }

    private fun myCode(
        name: String,
        themeMode: ThemeMode,
        link: ProfileLink,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        MyQrCodeScreen(MyQrCodeUiState.Content(1, UiText.Plain("Ada Lovelace"), null, link), {}, {}, {}, {}, {})
    }

    private companion object {
        val PERMANENT = ProfileLink("https://t.me/ada", 0)
        val TEMPORARY = ProfileLink("https://t.me/contact/AAAAAAAAAAAAAAAA", 3_600)
    }
}

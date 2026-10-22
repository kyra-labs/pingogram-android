package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every login step's controls have a name and a 48 dp touch target ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AuthAccessibilityChecksTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun welcome() = check(AuthPreviewData.welcome)

    @Test
    fun phoneNumber() = check(AuthPreviewData.phoneNumberError)

    @Test
    fun countryPicker() = check(AuthPreviewData.countryPicker)

    @Test
    fun code() = check(AuthPreviewData.codeResend)

    @Test
    fun password() = check(AuthPreviewData.passwordError)

    @Test
    fun registration() = check(AuthPreviewData.registration)

    @Test
    fun qrCode() = check(AuthPreviewData.qrCode)

    @Test
    fun emailCode() = check(AuthPreviewData.emailCode)

    private fun check(state: AuthUiState) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { AuthScreen(state = state, onEvent = {}) } }
        composeRule.assertAccessible()
    }
}

package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone number step with "Sign in with a passkey" (`docs/adr/0037-passkeys.md`, board 17b), as on Android 14 and
 * later: light, dark, AMOLED black, high contrast, right to left, a 700 dp window, twice the font size, and while
 * Telegram and the password manager answer. The phone step's own goldens stay as before Android 14 shows it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PasskeySignInScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, name = "fontScale200")

    @Test
    fun signingInLight() =
        capture(
            ScreenshotVariant.Light,
            name = "signingInLight",
            state = AuthPreviewData.phoneNumber.copy(pendingAction = AuthAction.SignInWithPasskey),
        )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
        state: AuthUiState = AuthPreviewData.phoneNumber,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                AuthScreen(state = state, onEvent = {}, canUsePasskeys = true)
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}

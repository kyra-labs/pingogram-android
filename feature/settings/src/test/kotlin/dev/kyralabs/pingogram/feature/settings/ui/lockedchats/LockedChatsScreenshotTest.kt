package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeEntryUiState
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeInput
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locked chats (`docs/adr/0029-locked-chats.md`): the page, light and dark, in AMOLED black, high contrast, right to
 * left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12), without chats, without the passcode
 * lock, the picker, and the passcode entry before them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LockedChatsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, LockedChatsPreviewStates.empty)

    @Test
    fun noPasscodeLight() = capture("noPasscodeLight", ThemeMode.Light, LockedChatsUiState.NoPasscode)

    @Test
    fun pickerLight() =
        composeRule.captureScreen(javaClass, "pickerLight", ThemeMode.Light) {
            LockedChatsPickerContent(LockedChatsPreviewStates.picker, onEvent = {})
        }

    @Test
    fun unlockLight() = composeRule.captureScreen(javaClass, "unlockLight", ThemeMode.Light) { Unlock() }

    @Test
    fun unlockWrongDark() =
        composeRule.captureScreen(javaClass, "unlockWrongDark", ThemeMode.Dark) {
            Unlock(ChatUnlockUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Wrong, rejections = 1)))
        }

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
    @Config(qualifiers = "w411dp-h1200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun unlockFontScale200() = composeRule.captureScreen(javaClass, "unlockFontScale200", ThemeMode.Light) { Unlock() }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: LockedChatsUiState = LockedChatsPreviewStates.content,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { Page(state) }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) { Page(LockedChatsPreviewStates.content) }

    @Composable
    private fun Page(state: LockedChatsUiState) {
        LockedChatsScreen(state, onEvent = {}, onOpenChat = {}, onOpenPasscodeLock = {}, onBack = {})
    }

    @Composable
    private fun Unlock(state: ChatUnlockUiState = ChatUnlockUiState(biometricUnlock = true)) {
        val input = remember { PasscodeInput().apply { append('1', PasscodeType.Pin) } }
        ChatUnlockScreen(
            title = stringResource(R.string.locked_chat_title),
            state = state,
            input = input,
            onEvent = {},
            onBiometricUnlock = {},
            onCancel = {},
        )
    }
}

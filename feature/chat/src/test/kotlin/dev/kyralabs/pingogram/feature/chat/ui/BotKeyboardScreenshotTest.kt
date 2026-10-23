package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardDialogHost
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotReplyUiState
import dev.kyralabs.pingogram.feature.chat.ui.bots.KeyboardShareBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.KeyboardShareSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.ReplyKeyboardUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotKeyboardPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a bot's keyboard (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Bot keyboard"): the conversation with a
 * shop bot's keyboard in the system keyboard's place (rows of one to three connected buttons, a primary one, its
 * toggle in the pill), in the looks the audit asks for (§12) and on an expanded window; a resized one-time keyboard
 * with a button waiting; and what its requests open: the phone number question and the people and group pickers.
 * Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotKeyboardScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("keyboard", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("keyboard", ScreenshotVariant.Dark)

    @Test
    fun highContrast() = capture("keyboard", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("keyboard", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture("keyboard.expanded", ScreenshotVariant.Light)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("keyboard.fontScale200", ScreenshotVariant.Light)

    @Test
    fun resizedLight() = capture("resized", ScreenshotVariant.Light, BotKeyboardPreviewData.resizedKeyboard)

    @Test
    fun resizedDark() = capture("resized", ScreenshotVariant.Dark, BotKeyboardPreviewData.resizedKeyboard)

    @Test
    fun phoneLight() = capture("phone", ScreenshotVariant.Light, phone = true)

    @Test
    fun phoneDark() = capture("phone", ScreenshotVariant.Dark, phone = true)

    @Test
    fun usersLight() = capture("users", ScreenshotVariant.Light, sheet = BotKeyboardPreviewData.usersSheet)

    @Test
    fun usersDark() = capture("users", ScreenshotVariant.Dark, sheet = BotKeyboardPreviewData.usersSheet)

    @Test
    fun groupLight() = capture("group", ScreenshotVariant.Light, sheet = BotKeyboardPreviewData.groupSheet)

    @Test
    fun groupEmptyDark() = capture("groupEmpty", ScreenshotVariant.Dark, sheet = BotKeyboardPreviewData.emptyGroupSheet)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        keyboard: ReplyKeyboardUiState = BotKeyboardPreviewData.keyboard,
        phone: Boolean = false,
        sheet: KeyboardShareSheet? = null,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(
                    state = BotButtonsPreviewData.conversation,
                    onEvent = {},
                    onBack = {},
                    botReply = BotReplyUiState(keyboard, placeholder = "Choose a size"),
                )
                if (phone) BotKeyboardDialogHost(BotKeyboardPreviewData.phoneDialog, onEvent = {})
                sheet?.let { KeyboardShareBottomSheet(it, onEvent = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$name.${variant.goldenName}"))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}

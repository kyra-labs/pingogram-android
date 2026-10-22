package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialogHost
import dev.kyralabs.pingogram.feature.chat.ui.bots.SwitchInlineChatsSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of bots' buttons under messages (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Bot buttons"): a chat with a
 * bot whose messages carry connected rows of buttons (a link, an inline query, a destructive, a primary and a disabled
 * one, one waiting for its bot), in every look the audit asks for (§12) and on an expanded window; and what follows a
 * tap: the password question, the login question and the chat picker of an inline query. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BotButtonsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("conversation", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("conversation", ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture("conversation", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("conversation", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("conversation", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture("conversation.expanded", ScreenshotVariant.Light)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture("conversation.fontScale200", ScreenshotVariant.Light)

    @Test
    fun passwordLight() = capture("password", ScreenshotVariant.Light) { BotButtonsPreviewData.passwordWrong }

    @Test
    fun passwordDark() = capture("password", ScreenshotVariant.Dark) { BotButtonsPreviewData.passwordWrong }

    @Test
    fun loginLight() = capture("login", ScreenshotVariant.Light) { BotButtonsPreviewData.login }

    @Test
    fun loginDark() = capture("login", ScreenshotVariant.Dark) { BotButtonsPreviewData.login }

    @Test
    fun chatPickerLight() = capture("chatPicker", ScreenshotVariant.Light, chatPicker = true)

    @Test
    fun chatPickerDark() = capture("chatPicker", ScreenshotVariant.Dark, chatPicker = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        chatPicker: Boolean = false,
        dialog: (() -> BotButtonDialog)? = null,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = BotButtonsPreviewData.conversation, onEvent = {}, onBack = {})
                dialog?.let { BotButtonDialogHost(it(), remember { TextFieldState("hunter2") }, onEvent = {}) }
                if (chatPicker) SwitchInlineChatsSheet(BotButtonsPreviewData.chatPicker, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$name.${variant.goldenName}"))
    }
}

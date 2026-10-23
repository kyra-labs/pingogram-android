package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the floating message menu (`docs/DESIGN_SYSTEM.md` §6 "Message actions"), opened by a real long press:
 * someone else's message, light and dark, right to left and at 200% font scale; the user's own message,
 * which lifts at the end; and a short window, where the same long press opens the sheet. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageActionsMenuScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun incomingLight() = capture("incoming.light", ChatPreviewData.groupChat, GRACE)

    @Test
    fun incomingDark() = capture("incoming.dark", ChatPreviewData.groupChat, GRACE, ThemeMode.Dark)

    @Test
    fun incomingRtl() = capture("incoming.rtl", ChatPreviewData.groupChat, GRACE, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun incomingFontScale200() = capture("incoming.fontScale200", ChatPreviewData.privateChat, ADA_LAST)

    @Test
    fun ownDark() = capture("own.dark", ChatPreviewData.privateChat, OWN, ThemeMode.Dark)

    @Test
    @Config(qualifiers = SHORT_WINDOW)
    fun shortWindowSheet() = capture("shortWindow.light", ChatPreviewData.privateChat, ADA_LAST)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        initial: ChatUiState,
        bubble: String,
        themeMode: ThemeMode = ThemeMode.Light,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        var state by mutableStateOf(initial)
        val onEvent = { event: ChatEvent ->
            if (event is ChatEvent.ShowMessageActions) {
                val sheet =
                    if (event.messageId == ChatPreviewData.incomingMessageActions.messageId) {
                        ChatPreviewData.incomingMessageActions
                    } else {
                        ChatPreviewData.ownMessageActions.copy(messageId = event.messageId)
                    }
                state = state.copy(messageActions = sheet)
            }
        }
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    ChatScreen(state = state, onEvent = onEvent, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(bubble, substring = true).performTouchInput { longClick() }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f

        /** A phone in landscape: the actions open in their sheet. */
        const val SHORT_WINDOW = "w891dp-h411dp-xxhdpi"

        /** Grace's message 24 in [ChatPreviewData.groupChat], and the user's message 6 in the private chat. */
        const val GRACE = "Grace Hopper, 10:12 AM"
        const val OWN = "You, 9:39 AM"

        /** Ada's newest message in the private chat, on screen in every window. */
        const val ADA_LAST = "Ada, 9:46 AM"
    }
}

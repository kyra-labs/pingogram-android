package dev.kyralabs.pingogram.widget

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.ui.ActivityRegistration
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Goldens of the Conversation widget's two sheets (board 33), their content on the sheet's container as
 * `ModalBottomSheet` draws it: the chat picker with chats, loading, and with none matching; the reply sheet with a
 * reply typed, while it sends, and after a refusal. Light and dark for each, and right to left and 200% font for the
 * first of each.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetSheetsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pickerLight() = capture(ThemeMode.Light) { Picker(PICKER) }

    @Test
    fun pickerDark() = capture(ThemeMode.Dark) { Picker(PICKER) }

    @Test
    fun pickerRtl() = capture(ThemeMode.Light, LayoutDirection.Rtl) { Picker(PICKER) }

    @Test
    @Config(fontScale = 2f)
    fun pickerFontScale200() = capture(ThemeMode.Light) { Picker(PICKER) }

    @Test
    fun pickerLoadingLight() = capture(ThemeMode.Light) { Picker(WidgetChatPickerState()) }

    @Test
    fun pickerLoadingDark() = capture(ThemeMode.Dark) { Picker(WidgetChatPickerState()) }

    @Test
    fun pickerNoneLight() = capture(ThemeMode.Light) { Picker(NONE) }

    @Test
    fun pickerNoneDark() = capture(ThemeMode.Dark) { Picker(NONE) }

    @Test
    fun replyLight() = capture(ThemeMode.Light) { Reply(REPLY, "Booked it! See you at 7") }

    @Test
    fun replyDark() = capture(ThemeMode.Dark) { Reply(REPLY, "Booked it! See you at 7") }

    @Test
    fun replyRtl() = capture(ThemeMode.Light, LayoutDirection.Rtl) { Reply(REPLY, "Booked it! See you at 7") }

    @Test
    @Config(fontScale = 2f)
    fun replyFontScale200() = capture(ThemeMode.Light) { Reply(REPLY, "Booked it! See you at 7") }

    @Test
    fun replySendingLight() = capture(ThemeMode.Light) { Reply(REPLY.copy(isSending = true), "On my way") }

    @Test
    fun replySendingDark() = capture(ThemeMode.Dark) { Reply(REPLY.copy(isSending = true), "On my way") }

    @Test
    fun replyFailedLight() = capture(ThemeMode.Light) { Reply(FAILED, "On my way") }

    @Test
    fun replyFailedDark() = capture(ThemeMode.Dark) { Reply(FAILED, "On my way") }

    @Composable
    private fun Picker(state: WidgetChatPickerState) {
        WidgetChatPickerContent(state, {}, {})
    }

    @Composable
    private fun Reply(
        state: WidgetReplyState,
        text: String,
    ) {
        WidgetReplyContent(state, rememberTextFieldState(text), {}, requestFocus = false)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun capture(
        themeMode: ThemeMode,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            // Robolectric's `ldrtl` qualifier does not reach the test activity's composition: the direction is set
            // here.
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BottomSheetDefaults.ExpandedShape,
                        color = BottomSheetDefaults.ContainerColor,
                    ) {
                        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private companion object {
        val PICKER =
            WidgetChatPickerState(
                chats =
                    persistentListOf(
                        WidgetPickerChat(1, "Trip — Coorg 2026", null, AvatarShape.Group),
                        WidgetPickerChat(2, "Meera Iyer", null, AvatarShape.Circle),
                        WidgetPickerChat(3, "Pingogram News", null, AvatarShape.Channel),
                        WidgetPickerChat(4, "Rust Berlin", null, AvatarShape.RoundedSquare),
                        WidgetPickerChat(
                            id = 5,
                            title = "",
                            photo = null,
                            shape = AvatarShape.SavedMessages,
                            placeholderIcon = UiR.drawable.ic_bookmark_filled,
                            titleRes = UiR.string.chat_saved_messages,
                        ),
                    ),
            )

        val NONE = WidgetChatPickerState(chats = persistentListOf(), query = "zebra")

        val REPLY = WidgetReplyState(chatId = 1, title = "Trip — Coorg 2026", shape = AvatarShape.Group)

        val FAILED = WidgetReplyState(chatId = 2, title = "Meera Iyer", error = DomainError.Network)
    }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the conversation with a hardware keyboard and a mouse (`docs/DESIGN_SYSTEM.md` §9, `docs/TESTING.md`),
 * light and dark, compact and expanded: a message with the keyboard's focus, its bubble in the theme's inset focus
 * ring; and a sender's name under the mouse, in Material's hover state. Brand colors; inspection mode, so Coil shows
 * solid colors for the images ([PreviewImages]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class MessageFocusScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModes: InputModeManager

    @Test
    fun focusedLight() = captureFocused("focused.light", ThemeMode.Light)

    @Test
    fun focusedDark() = captureFocused("focused.dark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun focusedExpanded() = captureFocused("focused.expanded", ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun focusedExpandedDark() = captureFocused("focused.expandedDark", ThemeMode.Dark)

    @Test
    fun hoveredLight() = captureHovered("hovered.light", ThemeMode.Light)

    @Test
    fun hoveredDark() = captureHovered("hovered.dark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun hoveredExpanded() = captureHovered("hovered.expanded", ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun hoveredExpandedDark() = captureHovered("hovered.expandedDark", ThemeMode.Dark)

    /** Ada's newest message with the keyboard's focus. */
    private fun captureFocused(
        name: String,
        themeMode: ThemeMode,
    ) {
        setScreen(ChatPreviewData.privateChat, themeMode)
        composeRule.runOnIdle { inputModes.requestInputMode(InputMode.Keyboard) }
        composeRule.onNodeWithContentDescription(ADA_LAST).requestFocus()
        capture(name)
    }

    /** The mouse over Linus's name in the group chat, which opens his profile. */
    private fun captureHovered(
        name: String,
        themeMode: ThemeMode,
    ) {
        setScreen(groupChatWithProfiles(), themeMode)
        // His name starts the bubble, after the avatar column (32 dp and its 8 dp gap) and the bubble's 12 dp padding.
        composeRule.onNodeWithContentDescription(LINUS_MESSAGE, substring = true).performMouseInput {
            moveTo(Offset(NAME_X.toPx(), NAME_Y.toPx()))
        }
        capture(name)
    }

    private fun setScreen(
        state: ChatUiState,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            inputModes = LocalInputModeManager.current
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    PreviewImages { ChatScreen(state = state, onEvent = {}, onBack = {}) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    /** [ChatPreviewData.groupChat] with its senders' profiles, so their names and avatars open them. */
    private fun groupChatWithProfiles(): ChatUiState {
        val state = ChatPreviewData.groupChat
        val content = state.content as ChatContent.Content
        val items =
            content.items.map { item ->
                if (item is ChatItem.Bubble && !item.isOutgoing) item.copy(senderUserId = item.senderId) else item
            }
        return state.copy(content = content.copy(items = items.toImmutableList()))
    }

    private companion object {
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"
        const val LINUS_MESSAGE = "Linus Torvalds, 10:20 AM"
        val NAME_X = 64.dp
        val NAME_Y = 18.dp
    }
}

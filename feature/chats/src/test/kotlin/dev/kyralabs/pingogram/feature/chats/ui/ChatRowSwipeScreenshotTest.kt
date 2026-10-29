package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row held mid-swipe, past the threshold (`docs/DESIGN_SYSTEM.md` §5): the action's container, icon at full size and
 * label behind it. The finger stays down, so the row stays where it was dragged; the icon's spring has settled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatRowSwipeScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Grace Hopper's row dragged towards the end: mark as unread, on `secondaryContainer`. */
    @Test
    fun swipeToReadLight() = capture("swipeToReadLight", ThemeMode.Light, SWIPE_FRACTION)

    /** Grace Hopper's row dragged towards the start: archive, on `tertiaryContainer`. */
    @Test
    fun swipeToArchiveDark() = capture("swipeToArchiveDark", ThemeMode.Dark, -SWIPE_FRACTION)

    /**
     * With "Swipe to change folders" the whole list follows the finger instead, Work's list dragged towards the start,
     * past the threshold, on its way to Family; the tabs stay.
     */
    @Test
    fun listSwipeToFolderLight() =
        capture(
            "listSwipeToFolderLight",
            ThemeMode.Light,
            -LIST_SWIPE_FRACTION,
            ChatListPreviewData.contentWithFolders.copy(selectedFolderId = 1),
            ChatSwipes(changesFolder = true),
        )

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        fraction: Float,
        state: ChatListUiState = ChatListPreviewData.content,
        swipes: ChatSwipes = ChatSwipes(),
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalChatSwipes provides swipes) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    ChatListScreen(state = state, onEvent = {})
                }
            }
        }
        composeRule
            .onNode(
                hasContentDescription("Grace Hopper,", substring = true) and hasClickAction(),
            ).performTouchInput {
                down(center)
                moveBy(Offset(width * fraction, 0f))
            }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        /** Well past the swipe's threshold, with the label still clear of the moved row. */
        const val SWIPE_FRACTION = 0.6f

        /** Past the third of the width that changes the folder. */
        const val LIST_SWIPE_FRACTION = 0.4f
    }
}

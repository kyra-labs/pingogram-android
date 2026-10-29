package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The chat list's menus and the archive row's styles (`docs/DESIGN_SYSTEM.md` §6 "Folder tabs", "Archive row"): a
 * folder tab's menu open below it, the archive row's menu, the compact row, and the hidden row with its snackbar and
 * the top bar's archive button, light and dark, and the menus in the audit's other looks (§12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListMenusScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tabMenuLight() = captureTabMenu("tabMenu.light", ScreenshotVariant.Light)

    @Test
    fun tabMenuDark() = captureTabMenu("tabMenu.dark", ScreenshotVariant.Dark)

    @Test
    fun tabMenuAmoled() = captureTabMenu("tabMenu.amoled", ScreenshotVariant.Amoled)

    @Test
    fun tabMenuHighContrast() = captureTabMenu("tabMenu.highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun tabMenuRtl() = captureTabMenu("tabMenu.rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun tabMenuMedium() = captureTabMenu("tabMenu.medium", ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun tabMenuFontScale200() = captureTabMenu("tabMenu.fontScale200", ScreenshotVariant.Light)

    @Test
    fun archiveMenuLight() = captureArchiveMenu("archiveMenu.light", ScreenshotVariant.Light)

    @Test
    fun archiveMenuDark() = captureArchiveMenu("archiveMenu.dark", ScreenshotVariant.Dark)

    @Test
    fun archiveMenuRtl() = captureArchiveMenu("archiveMenu.rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun archiveMenuFontScale200() = captureArchiveMenu("archiveMenu.fontScale200", ScreenshotVariant.Light)

    @Test
    fun compactRowLight() =
        capture("compactRow.light", ScreenshotVariant.Light, ChatListPreviewData.contentWithCompactArchive)

    @Test
    fun compactRowDark() =
        capture("compactRow.dark", ScreenshotVariant.Dark, ChatListPreviewData.contentWithCompactArchive)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun compactRowFontScale200() =
        capture("compactRow.fontScale200", ScreenshotVariant.Light, ChatListPreviewData.contentWithCompactArchive)

    @Test
    fun hiddenLight() = captureHidden("hidden.light", ScreenshotVariant.Light)

    @Test
    fun hiddenDark() = captureHidden("hidden.dark", ScreenshotVariant.Dark)

    @Test
    fun hiddenRtl() = captureHidden("hidden.rtl", ScreenshotVariant.Rtl)

    @Test
    fun `the open menus and the hidden archive's button pass the accessibility checks`() {
        setScreen(ScreenshotVariant.Light, ChatListPreviewData.contentWithFolders)
        tab("Work").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.assertAccessible()
    }

    private fun captureTabMenu(
        name: String,
        variant: ScreenshotVariant,
    ) {
        setScreen(variant, ChatListPreviewData.contentWithFolders)
        tab("Work").performTouchInput { longClick() }
        captureNow(name)
    }

    private fun captureArchiveMenu(
        name: String,
        variant: ScreenshotVariant,
    ) {
        setScreen(variant, ChatListPreviewData.contentWithArchive)
        composeRule
            .onNode(
                hasContentDescription("${string(R.string.chat_list_archived_chats)},", substring = true) and
                    hasClickAction(),
            ).performTouchInput { longClick() }
        captureNow(name)
    }

    /** The row hidden, with the snackbar that offers to undo it, as the route shows it. */
    private fun captureHidden(
        name: String,
        variant: ScreenshotVariant,
    ) {
        val message = string(R.string.chat_list_archive_row_hidden)
        val undo = string(R.string.chat_list_undo)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val snackbars = remember { SnackbarHostState() }
                LaunchedEffect(snackbars) {
                    snackbars.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Indefinite)
                }
                ChatListScreen(
                    state = ChatListPreviewData.contentWithHiddenArchive,
                    onEvent = {},
                    snackbarHostState = snackbars,
                    onEditFolder = {},
                )
            }
        }
        captureNow(name)
    }

    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: ChatListUiState,
    ) {
        setScreen(variant, state)
        captureNow(name)
    }

    private fun setScreen(
        variant: ScreenshotVariant,
        state: ChatListUiState,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { ChatListScreen(state = state, onEvent = {}, onEditFolder = {}) }
        }
        composeRule.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureNow(name: String) {
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private fun tab(label: String) =
        composeRule.onNode(
            hasContentDescription(label, substring = true) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        )

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}

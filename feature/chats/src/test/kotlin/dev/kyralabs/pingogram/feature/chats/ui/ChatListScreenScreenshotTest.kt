package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of every chat list state on a compact phone (`docs/TESTING.md`), light and dark: chats with and without
 * folder tabs, rows in all their looks, the archive row, selection mode with its toolbar, the connection pill, loading,
 * empty and error; the archive with chats and empty; the delete dialogs. Brand colors, so goldens do not depend on a
 * wallpaper.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListScreenScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    /** At 200% font scale (`REQUIREMENTS.md` §6.3): the selection's bars and the connection pill grow, never clip. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "selecting" || stateName == "connecting")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    /** The audit's other looks (`docs/DESIGN_SYSTEM.md` §12) for one representative state only. */
    private fun captureAudit(variant: ScreenshotVariant) {
        assumeTrue(stateName == "content")
        capture(variant)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatListScreen(state = state, onEvent = {})
            }
        }
        composeRule.waitForIdle()
        // A state that is still loading shows its indicator once loading takes a moment; the golden shows it then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        val STATES =
            mapOf(
                "contentWithFolders" to ChatListPreviewData.contentWithFolders,
                "content" to ChatListPreviewData.content,
                "archiveRow" to ChatListPreviewData.contentWithArchive,
                "selecting" to ChatListPreviewData.selecting,
                "connecting" to ChatListPreviewData.connecting,
                "loading" to ChatListPreviewData.loading,
                "empty" to ChatListPreviewData.empty,
                "emptyFolder" to ChatListPreviewData.emptyFolder,
                "error" to ChatListPreviewData.error,
                "archive" to ChatListPreviewData.archived,
                "archiveEmpty" to ChatListPreviewData.archivedEmpty,
                "deletePrivateChat" to ChatListPreviewData.deletePrivateChatDialog,
                "leaveGroup" to ChatListPreviewData.leaveGroupDialog,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}

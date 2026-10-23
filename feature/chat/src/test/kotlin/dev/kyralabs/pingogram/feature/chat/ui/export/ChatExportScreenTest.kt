package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.export.preview.ChatExportPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The export page's controls: what each sends, and that every one has a name and a touch target. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatExportScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatExportEvent>()
    private var chosen = 0
    private val opened = mutableListOf<String>()

    private fun show(state: ChatExportUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatExportScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    onChooseFolder = { chosen++ },
                    onOpenFolder = { opened += it },
                )
            }
        }
    }

    @Test
    fun `the form sends each choice, and Export waits for a folder`() {
        show(ChatExportPreviewData.emptyForm)

        composeRule.onNodeWithText("JSON").performClick()
        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithText("Choose").performScrollTo().performClick()
        composeRule.onNodeWithText("Export").performScrollTo().assertIsNotEnabled()

        assertEquals(
            listOf(
                ChatExportEvent.SetFormat(ExportFormat.Json),
                ChatExportEvent.SetMedia(ExportMediaKind.Photos, true),
            ),
            events,
        )
        assertEquals(1, chosen)
    }

    @Test
    fun `Export sends the chat's name for the notification`() {
        show(ChatExportPreviewData.form)

        composeRule
            .onNodeWithText("Export")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf(ChatExportEvent.Export("Ada Lovelace")), events)
    }

    @Test
    fun `the day's row opens the date picker, whose OK sends the day`() {
        show(ChatExportPreviewData.form)

        composeRule.onNodeWithText("From a date").performScrollTo().performClick()
        composeRule.onNodeWithText("OK").performClick()

        assertEquals(ChatExportEvent.Since(ChatExportPreviewData.form.form.sinceDay!!), events.single())
    }

    @Test
    fun `running, it cancels`() {
        show(ChatExportPreviewData.running)
        composeRule.onNodeWithText("Cancel export").performClick()
        assertEquals(listOf<ChatExportEvent>(ChatExportEvent.Cancel), events)
    }

    @Test
    fun `done, it opens the folder or exports again`() {
        show(ChatExportPreviewData.done.copy(run = ExportRun.Done(doneResult)))

        composeRule.onNodeWithText("Open folder").performClick()
        composeRule.onNodeWithText("Export again").performClick()

        assertEquals(listOf("content://doc/export"), opened)
        assertEquals(listOf<ChatExportEvent>(ChatExportEvent.Dismiss), events)
    }

    @Test
    fun `every control has a name and a touch target`() {
        show(ChatExportPreviewData.form)

        composeRule.onNodeWithContentDescription("Change folder").performScrollTo()
        composeRule.assertAccessible()
    }

    private val doneResult = ChatExportResult(1_234, 5, 0, "content://doc/export")
}

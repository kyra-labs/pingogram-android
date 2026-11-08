package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A document's download: pausing it, and a paused one saying so and going on where it stopped when tapped (TDLib keeps
 * what came), rather than looking like one under way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageDocumentTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var downloads = 0
    private var pauses = 0

    @Test
    fun `a download under way pauses`() {
        setDocument(isActive = true)

        composeRule.onNodeWithContentDescription("Pause download").performClick()

        assertEquals(1, pauses)
        assertEquals(0, downloads)
    }

    @Test
    fun `a paused download says so and resumes`() {
        setDocument(isActive = false)

        composeRule.onNodeWithText("Paused", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("Resume download").performClick()

        assertEquals(1, downloads)
        assertEquals(0, pauses)
    }

    private fun setDocument(isActive: Boolean) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MessageDocument(
                    content = ContentPreviewData.document("report.pdf", "application/pdf"),
                    fileState = FileState.Downloading(downloadedSize = HALF, size = SIZE, isActive = isActive),
                    onDownload = { downloads++ },
                    onCancel = { pauses++ },
                    onOpen = {},
                )
            }
        }
    }

    private companion object {
        const val SIZE = 4_000_000L
        const val HALF = SIZE / 2
    }
}

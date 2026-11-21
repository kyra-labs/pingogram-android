package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The archive settings page's top bar ([ArchivePageScaffold]) lays out its title only on the row that shows
 * (`docs/PERFORMANCE.md`, "Overdraw"): `headlineMediumEmphasized` on the expanded row at rest,
 * `titleLargeEmphasized` on the collapsed one once scrolled. Compared by font family and weight, as a long title may
 * get smaller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ArchivePageTopBarTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var typography: Typography

    @Test
    fun `at rest the title is laid out once, on the expanded row`() {
        setPage()

        val styles = titleStyles()

        assertEquals(1, styles.size)
        assertTrue("$styles", styles.single().matches(typography.headlineMediumEmphasized))
    }

    @Test
    fun `scrolled up, the bar collapses to its collapsed row's title alone`() {
        setPage()

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val styles = titleStyles()
        assertEquals(1, styles.size)
        assertTrue("$styles", styles.single().matches(typography.titleLargeEmphasized))
        assertFalse("$styles", styles.single().matches(typography.headlineMediumEmphasized))
    }

    private fun setPage() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                typography = MaterialTheme.typography
                ArchivePageScaffold(title = TITLE, onBack = {}, snackbarHostState = SnackbarHostState()) {
                    repeat(ROWS) { Text("Row $it") }
                }
            }
        }
    }

    /** The styles the title is laid out in, one per row of the bar that composes it. */
    private fun titleStyles(): List<TextStyle> =
        composeRule
            .onAllNodesWithText(TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.single().layoutInput.style
            }.also { assertTrue("one title per row", it.isNotEmpty()) }

    private fun TextStyle.matches(expected: TextStyle) =
        fontFamily == expected.fontFamily && fontWeight == expected.fontWeight

    private companion object {
        const val TITLE = "Archive settings"
        const val ROWS = 60
    }
}

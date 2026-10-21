package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The title of the settings pages' medium flexible top bar ([SettingsTopBar], `docs/DESIGN_SYSTEM.md` §3.3, audit
 * G47): `headlineMediumEmphasized` on the bar's expanded row and `titleLargeEmphasized` on its collapsed one, picked
 * from the row the bar's title slot fills, never from the bar's text styles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsTopBarTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var typography: Typography

    @Test
    fun `the expanded row's title is headlineMediumEmphasized and the collapsed row's titleLargeEmphasized`() {
        setPage()

        val styles = titleStyles()

        assertTrue(styles.any { it.matches(typography.headlineMediumEmphasized) })
        assertTrue(styles.any { it.matches(typography.titleLargeEmphasized) })
        assertTrue(
            styles.all {
                it.matches(typography.headlineMediumEmphasized) ||
                    it.matches(typography.titleLargeEmphasized)
            },
        )
    }

    @Test
    fun `scrolled up, the bar collapses to its titleLargeEmphasized row`() {
        setPage()

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val shown = titleStyles(shownOnly = true)
        assertTrue("$shown", shown.isNotEmpty() && shown.all { it.matches(typography.titleLargeEmphasized) })
    }

    private fun setPage() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                typography = MaterialTheme.typography
                SettingsPageScaffold(title = TITLE, onBack = {}) {
                    repeat(ROWS) { Text("Row $it") }
                }
            }
        }
    }

    /** The styles the title is laid out in, one per row of the bar; with [shownOnly], of rows not collapsed away. */
    private fun titleStyles(shownOnly: Boolean = false): List<TextStyle> =
        composeRule
            .onAllNodesWithText(TITLE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { !shownOnly || it.boundsInRoot.height > 0f }
            .map { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.single().layoutInput.style
            }.also { assertEquals("one title per row", true, it.isNotEmpty()) }

    private fun TextStyle.matches(expected: TextStyle) =
        fontSize == expected.fontSize && fontWeight == expected.fontWeight

    private companion object {
        const val TITLE = "Notifications"
        const val ROWS = 60
    }
}

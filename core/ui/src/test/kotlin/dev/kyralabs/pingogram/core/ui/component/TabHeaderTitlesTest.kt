package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [TabHeader]'s titles ([TwoRowTitles], `docs/PERFORMANCE.md`, "Overdraw"): only the rows that can show compose
 * their title, the expanded one at rest, both while the bar collapses, the collapsed one once it is collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class TabHeaderTitlesTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val composed = mutableSetOf<String>()

    /** The rows whose subtitle is composed: true for the expanded one. */
    private val subtitles = mutableSetOf<Boolean>()
    private lateinit var scrollBehavior: TopAppBarScrollBehavior

    @Test
    fun `at rest only the expanded title is composed`() {
        setHeader()

        assertEquals(setOf(EXPANDED), composed)
    }

    @Test
    fun `while the bar collapses both titles are composed`() {
        setHeader()

        collapse(fraction = 0.5f)

        assertEquals(setOf(EXPANDED, COLLAPSED), composed)
    }

    @Test
    fun `collapsed only the collapsed title is composed`() {
        setHeader()

        collapse(fraction = 1f)

        assertEquals(setOf(COLLAPSED), composed)
    }

    @Test
    fun `collapsed the expanded row's subtitle is not composed, and the collapsed row's is`() {
        setHeader()
        assertEquals(setOf(true, false), subtitles)

        collapse(fraction = 1f)

        assertEquals(setOf(false), subtitles)
    }

    private fun setHeader() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val expandedSize = MaterialTheme.experimentalTypography.displaySmallWide.fontSize
                TabHeader(
                    scrollBehavior = scrollBehavior,
                    title = { style ->
                        val row = if (style.fontSize == expandedSize) EXPANDED else COLLAPSED
                        DisposableEffect(row) {
                            composed += row
                            onDispose { composed -= row }
                        }
                        Text("Chats", style = style)
                    },
                    subtitle = { expanded ->
                        DisposableEffect(expanded) {
                            subtitles += expanded
                            onDispose { subtitles -= expanded }
                        }
                        Text("3 unread")
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun collapse(fraction: Float) {
        composeRule.runOnIdle {
            val state = scrollBehavior.state
            state.heightOffset = state.heightOffsetLimit * fraction
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val EXPANDED = "expanded"
        const val COLLAPSED = "collapsed"
    }
}

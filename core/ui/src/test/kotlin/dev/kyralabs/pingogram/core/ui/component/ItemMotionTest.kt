package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [animateItem] with the theme's [ItemMotion] (`docs/plans/motion-expressive-plan.md` §3.3): when a row goes, the
 * rows after it move up over several frames, in a list and in a grid.
 */
@RunWith(RobolectricTestRunner::class)
class ItemMotionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var items by mutableStateOf(listOf(1, 2, 3))

    @Test
    fun `in a list, the rows after a removed one move over several frames`() {
        setContent {
            val motion = rememberItemMotion()
            LazyColumn {
                items(items, key = { it }) { item ->
                    Box(Modifier.animateItem(this, motion).testTag("$item").size(ROW))
                }
            }
        }

        assertMovesOverSeveralFrames()
    }

    @Test
    fun `in a grid, the cells after a removed one move over several frames`() {
        setContent {
            val motion = rememberItemMotion()
            LazyVerticalGrid(GridCells.Fixed(1)) {
                items(items, key = { it }) { item ->
                    Box(Modifier.animateItem(this, motion).testTag("$item").size(ROW))
                }
            }
        }

        assertMovesOverSeveralFrames()
    }

    private fun assertMovesOverSeveralFrames() {
        val start = top("3")

        items = listOf(2, 3)
        val tops = mutableListOf<Dp>()
        val frames = composeRule.framesUntil { top("3").also(tops::add) == start - ROW }

        assertTrue("it took $frames frames: $tops", frames > 2)
        assertTrue("it moved in steps: $tops", tops.any { it < start && it > start - ROW })
    }

    private fun top(tag: String): Dp = composeRule.onNodeWithTag(tag).getBoundsInRoot().top

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        val ROW = 48.dp
    }
}

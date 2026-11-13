package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [animateScrollToTop] (`docs/DESIGN_SYSTEM.md` §5.1, Q-09): a list far down jumps to within a screen of its top,
 * then springs the rest of the way over several frames, and ends exactly at its first item.
 */
@RunWith(RobolectricTestRunner::class)
class ScrollToTopTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    @Test
    fun `a list far down springs back to its first item over several frames`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberLazyListState(initialFirstVisibleItemIndex = FAR_DOWN)
                scope = rememberCoroutineScope()
                val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(ITEMS) { Box(Modifier.height(ROW)) }
                }
                start = { scope.launch { listState.animateScrollToTop(spec) } }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { start() }
        val indexes =
            List(SETTLE_FRAMES) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                listState.firstVisibleItemIndex
            }

        assertTrue("it springs over several frames: $indexes", indexes.distinct().size > 2)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    private var start: () -> Unit = {}

    private companion object {
        const val ITEMS = 500
        const val FAR_DOWN = 300
        const val SETTLE_FRAMES = 60
        val ROW = 48.dp
    }
}

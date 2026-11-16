package dev.kyralabs.pingogram.core.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A dropped row settles into its place (`docs/plans/motion-expressive-plan.md` S-2, M-002): the drop is reported as
 * the finger lifts, the offset springs to 0 over several frames (in one with animations off), and a new drag takes
 * the row from where it is.
 */
@RunWith(RobolectricTestRunner::class)
class ListReorderStateTest {
    private val durationScale = TestDurationScale()

    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private lateinit var reorder: ListReorderState<Int>
    private val drops = mutableListOf<List<Int>>()

    @Test
    fun `a drop is reported at once and the row settles into its place over several frames`() {
        setUp()
        reorder.start(ROW)
        reorder.drag(DRAG)

        reorder.end()

        assertEquals(listOf(IDS), drops)
        assertEquals(ROW, reorder.draggedId)
        val frames = composeRule.framesUntil { reorder.draggedId == null }
        assertTrue("settled in $frames frames", frames > 2)
        assertEquals(0f, reorder.offset)
    }

    @Test
    fun `the settle is on its way between the drop and the slot`() {
        setUp()
        reorder.start(ROW)
        reorder.drag(DRAG)
        reorder.end()

        composeRule.framesUntil(limit = 3) { false }

        assertTrue("midway at ${reorder.offset}", reorder.offset > 0f && reorder.offset < DRAG)
    }

    @Test
    fun `with animations off the row is in its place in the next frame`() {
        durationScale.scaleFactor = 0f
        setUp()
        reorder.start(ROW)
        reorder.drag(DRAG)
        reorder.end()

        val frames = composeRule.framesUntil { reorder.draggedId == null }

        assertTrue("settled in $frames frames", frames <= 2)
        assertEquals(0f, reorder.offset)
    }

    @Test
    fun `a drag during the settle takes the row from where it is`() {
        setUp()
        reorder.start(ROW)
        reorder.drag(DRAG)
        reorder.end()
        composeRule.framesUntil(limit = 2) { false }
        val caught = reorder.offset

        reorder.start(ROW)
        reorder.drag(MORE)
        composeRule.framesUntil(limit = 60) { false }

        assertEquals(caught + MORE, reorder.offset)
        assertEquals(ROW, reorder.draggedId)
        assertEquals(1, drops.size)
    }

    @Test
    fun `a drag of another row puts the settling one in its place`() {
        setUp()
        reorder.start(ROW)
        reorder.drag(DRAG)
        reorder.end()
        composeRule.framesUntil(limit = 2) { false }

        reorder.start(OTHER)

        assertEquals(OTHER, reorder.draggedId)
        assertEquals(0f, reorder.offset)
    }

    @Test
    fun `the drop carries on in the finger's direction`() {
        setUp()
        reorder.start(ROW)
        // Moving away from the slot, fast: the first frames still move on before it springs back.
        repeat(STEPS) { reorder.drag(STEP, uptimeMillis = it * STEP_MILLIS) }
        val dropped = reorder.offset
        reorder.end()

        val first = List(FIRST_FRAMES) { composeRule.framesUntil(limit = 1) { false }.let { reorder.offset } }

        assertTrue("first frames at $first", first.max() > dropped)
    }

    @Test
    fun `new ids while a finger holds a row end the drag without a drop`() {
        var ids by mutableStateOf(IDS)
        setUp { ids }
        reorder.start(ROW)
        reorder.drag(DRAG)

        ids = listOf(OTHER, ROW)
        composeRule.framesUntil(limit = 1) { false }
        reorder.end()

        assertNull(reorder.draggedId)
        assertEquals(0f, reorder.offset)
        assertEquals(listOf(OTHER, ROW), reorder.order)
        assertTrue(drops.isEmpty())
    }

    private fun setUp(ids: () -> List<Int> = { IDS }) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                reorder = rememberListReorderState(rememberLazyListState(), ids(), rowKey = { it }) { drops += it }
            }
        }
        composeRule.framesUntil(limit = 1) { false }
    }

    private companion object {
        const val ROW = 1
        const val OTHER = 2
        val IDS = listOf(ROW, OTHER)
        const val DRAG = 120f
        const val MORE = 15f
        const val STEPS = 6
        const val STEP = 40f
        const val STEP_MILLIS = 8L
        const val FIRST_FRAMES = 3
    }
}

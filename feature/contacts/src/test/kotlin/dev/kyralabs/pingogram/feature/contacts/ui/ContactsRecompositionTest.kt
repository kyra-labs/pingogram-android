package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the contacts list composes again while the user scrolls and drags it (`docs/PERFORMANCE.md` §4: no
 * recomposition of what stays on screen), its sticky headers and fast-scroll index included, counted with
 * [RecompositionCounter] frame by frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactsRecompositionTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    private lateinit var listState: LazyListState

    private var state by mutableStateOf(ContactsPreviewData.long)

    @Test
    fun `scrolling the list recomposes none of its composables, and nothing in every frame`() {
        setScreen()

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ROWS_ON_SCREEN)
        assertEquals(emptyList<String>(), counter.recomposedOwn)
        // Now and then the lazy list's machinery recomposes a text of a row it brings in (a reused or prefetched one).
        assertTrue(counter.recomposed.toString(), counter.recomposed.size < SCROLL_FRAMES / RARE)
    }

    @Test
    fun `dragging the list recomposes none of its composables`() {
        setScreen()
        val list = composeRule.onNodeWithTag(CONTACTS_LIST_TEST_TAG)

        list.performTouchInput { down(center) }
        repeat(DRAG_FRAMES) {
            list.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        list.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 0)
        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `the counter sees a recomposition`() {
        setScreen()

        // The counter sees a composable compose again only from its second time on: change the list twice.
        val long = state
        state = ContactsPreviewData.results
        repeat(SETTLE_FRAMES) { frame() }
        state = long
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwn)
    }

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                ContactsScreen(
                    state = state,
                    onEvent = {},
                    onAddContact = {},
                    onEditContact = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(SETTLE_FRAMES) { frame() }
        counter.start()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val ROWS_ON_SCREEN = 10

        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f

        /** At most one recomposition in this many frames counts as rare, nothing like one in every frame. */
        const val RARE = 10

        const val DRAG_FRAMES = 30
        const val DRAG_STEP = 20f
        const val SETTLE_FRAMES = 20
    }
}

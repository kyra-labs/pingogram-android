package dev.kyralabs.pingogram.feature.profile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the profile composes again while the user scrolls it, its large top bar collapsing and the photo grid moving
 * (`docs/PERFORMANCE.md` §4: nothing on screen composes again per frame), counted with [RecompositionCounter] frame by
 * frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileRecompositionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()
    private lateinit var gridState: LazyGridState
    private var media by mutableStateOf(manyPhotos())

    @Test
    fun `dragging the profile up collapses its bar without recomposing its rows or cells`() {
        setScreen()
        val grid = composeRule.onNodeWithTag(PROFILE_GRID_TEST_TAG)

        grid.performTouchInput { down(center) }
        repeat(DRAG_FRAMES) {
            grid.performTouchInput { moveBy(Offset(0f, -DRAG_STEP)) }
            frame()
        }
        grid.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        assertTrue("${gridState.firstVisibleItemIndex}", gridState.firstVisibleItemIndex > 0)
        // The bar's title composes again where the avatar morphs and the title row changes; nothing else does.
        val others = counter.recomposedOwnFunctions.filterNot { it.substringAfterLast('.') in BAR_PARTS }
        assertEquals(emptyList<String>(), others)
        assertTrue(counter.recomposedOwnFunctions.toString(), counter.recomposedOwnFunctions.size < DRAG_FRAMES / RARE)
    }

    @Test
    fun `scrolling the grid recomposes none of its composables`() {
        setScreen()
        // Past the header, so the bar stays collapsed.
        composeRule.runOnIdle { gridState.requestScrollToItem(GRID_START) }
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { gridState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${gridState.firstVisibleItemIndex}", gridState.firstVisibleItemIndex > GRID_START)
        assertEquals(emptyList<String>(), counter.recomposedOwnFunctions)
    }

    @Test
    fun `the counter sees a recomposition`() {
        setScreen()

        media = media.copy(selected = SharedMediaKind.Files)
        repeat(SETTLE_FRAMES) { frame() }
        media = media.copy(selected = SharedMediaKind.Links)
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwn)
    }

    private fun setScreen() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                gridState = rememberLazyGridState()
                ProfileScreen(
                    state = ProfilePreviewData.content,
                    media = media,
                    onEvent = {},
                    onMediaEvent = {},
                    onBack = {},
                    mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
                    gridState = gridState,
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

    /** The preview's media, repeated into a long grid. */
    private fun manyPhotos(): SharedMediaUiState {
        val items = (ProfilePreviewData.media.content as SharedMediaContent.Items).items
        val many =
            (0 until PHOTOS).map { index ->
                (items[index % items.size] as SharedMediaItem.Media).copy(messageId = FIRST_ID + index)
            }
        return ProfilePreviewData.media.copy(content = SharedMediaContent.Items(many.toImmutableList()))
    }

    private companion object {
        const val PHOTOS = 300
        const val FIRST_ID = 1_000L
        const val GRID_START = 10

        const val DRAG_FRAMES = 40
        const val DRAG_STEP = 30f
        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f
        const val SETTLE_FRAMES = 60

        /** At most one recomposition in this many frames counts as rare, nothing like one in every frame. */
        const val RARE = 4

        /** The top bar's title, whose two rows compose as it collapses. */
        val BAR_PARTS = setOf("ProfileTitle", "ProfileAvatar", "ProfileSubtitle", "ProfileLabel")
    }
}

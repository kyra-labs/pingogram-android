package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.RecompositionCounter
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * New sticker set's tiles slide to their new places when a sticker moves (`docs/plans/motion-expressive-plan.md` C-5,
 * M-113): "Move later" takes the first sticker through the frames between its old place and its new one rather than
 * jumping there, and the slide, read in the placement phase, recomposes nothing per frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StickerTilesMotionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(
        NewStickerSetPreviewData.stickers.copy(stickers = persistentListOf(CAT, SLEEP, LAUGH)),
    )

    @Test
    fun `a moved tile slides to its new place`() {
        showTiles()
        val from = left(CAT.emoji)
        val to = left(SLEEP.emoji)

        state = state.copy(stickers = persistentListOf(SLEEP, CAT, LAUGH))
        repeat(SLIDING_FRAMES) { frame() }
        val between = left(CAT.emoji)
        assertTrue("the tile jumped: $from, $between, $to", between > from && between < to)

        settle()
        assertEquals(to.value, left(CAT.emoji).value, TOLERANCE)
        assertEquals(from.value, left(SLEEP.emoji).value, TOLERANCE)
    }

    @Test
    fun `the slide recomposes nothing per frame`() {
        showTiles()
        val counter = RecompositionCounter()
        counter.start()

        state = state.copy(stickers = persistentListOf(SLEEP, CAT, LAUGH))
        frame()
        counter.reset()
        repeat(FRAMES) { frame() }

        assertTrue("recomposed: ${counter.recomposedOwnFunctions}", counter.recomposedOwnFunctions.isEmpty())
    }

    private fun showTiles() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PingogramTheme(dynamicColor = false) { Column { StickersStep(state, onEvent = {}) } } }
        settle()
    }

    private fun left(emoji: String): Dp =
        composeRule
            .onNodeWithContentDescription(emoji, substring = true)
            .getUnclippedBoundsInRoot()
            .left

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun settle() = repeat(SETTLE_FRAMES) { frame() }

    private companion object {
        val CAT = StickerTile("/none/1.webp", "😺")
        val SLEEP = StickerTile("/none/2.webp", "😴")
        val LAUGH = StickerTile("/none/3.webp", "😂")
        const val FRAMES = 20

        /** The frame that moves the tile, the one that starts its spring, and a few of the slide. */
        const val SLIDING_FRAMES = 5
        const val SETTLE_FRAMES = 120
        const val TOLERANCE = 0.5f
    }
}

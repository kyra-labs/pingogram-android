package dev.kyralabs.pingogram.feature.chat.ui.composer

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.preview.PanelPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * How the composer moves as the emoji panel comes and goes (`docs/DESIGN_SYSTEM.md` §5.1, Q-11): opened with no
 * keyboard, the panel grows in over several frames on the default spatial spring and shrinks away the same way when
 * back closes it; in the keyboard's place it takes the keyboard's height at once, so the composer does not move; with
 * animations off it is there in the next frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerPanelMotionTest {
    /** The system's animator duration scale, as Compose applies it to every animation; 0 turns them off. */
    private val durationScale =
        object : MotionDurationScale {
            override var scaleFactor = 1f
        }

    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private lateinit var panel: ComposerPanelState

    @Test
    fun `opened with no keyboard, the panel grows in over several frames`() {
        setComposer()
        val closed = fieldTop()

        composeRule.mainClock.autoAdvance = false
        openPanel()
        val tops = frames(SETTLE_FRAMES) { fieldTop() }

        assertTrue("$closed, then $tops", tops.last() < closed)
        assertTrue("$tops", tops.distinct().size >= MOVING_FRAMES)
        assertTrue("$tops", tops.all { it <= closed })
    }

    @Test
    fun `back closes the panel, which shrinks away over several frames`() {
        setComposer()
        val closed = fieldTop()
        openPanel()
        composeRule.waitForIdle()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        val tops = frames(SETTLE_FRAMES) { fieldTop() }

        assertEquals(closed, tops.last(), 0.5f)
        assertTrue("$tops", tops.distinct().size >= MOVING_FRAMES)
    }

    @Test
    fun `in the keyboard's place the panel moves the composer by nothing`() {
        setComposer(keyboardHeight = KEYBOARD_HEIGHT)
        setKeyboard(KEYBOARD_HEIGHT)
        composeRule.waitForIdle()
        val typing = fieldTop()

        composeRule.mainClock.autoAdvance = false
        openPanel()
        // The keyboard goes down behind the panel, after the frame the panel opens in, as it takes a while on a device.
        val opening = frames(1) { fieldTop() }
        setKeyboard(0)
        val tops = opening + frames(SETTLE_FRAMES) { fieldTop() }

        assertTrue(panel.isOpen)
        assertEquals(listOf(typing), tops.distinct())
    }

    @Test
    fun `with animations off the panel is there in the next frame`() {
        durationScale.scaleFactor = 0f
        setComposer()
        composeRule.mainClock.autoAdvance = false

        openPanel()
        val tops = frames(SETTLE_FRAMES) { fieldTop() }

        assertEquals("$tops", 1, tops.drop(2).distinct().size)
        assertEquals(tops.last(), tops[1], 0.5f)
    }

    private fun setComposer(keyboardHeight: Int = 0) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                panel = remember { ComposerPanelState(keyboardHeight = keyboardHeight) }
                // At the bottom of the window, as in the conversation, where the panel's place pushes it up.
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    ChatBottomBar(
                        composer =
                            ComposerUiState.Available(
                                canSchedule = true,
                                attachOptions = persistentListOf(AttachOption.Gallery),
                            ),
                        text = ComposerText(),
                        onSend = {},
                        onFocusChanged = {},
                        panel = PanelPreviewData.content,
                        onPanelEvent = {},
                        onSendSticker = {},
                        panelState = panel,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openPanel() {
        composeRule
            .onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.panel_open))
            .performClick()
    }

    /** The field's top in the window: where the composer is. */
    private fun fieldTop(): Float =
        composeRule
            .onNode(hasSetTextAction())
            .fetchSemanticsNode()
            .boundsInRoot.top

    /** [read] after each of [count] frames. */
    private fun <T> frames(
        count: Int,
        read: () -> T,
    ): List<T> =
        List(count) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            read()
        }

    /** The keyboard up at [height] pixels, or down at 0, as the window reports it to the screen. */
    private fun setKeyboard(height: Int) {
        composeRule.runOnUiThread {
            val content = composeRule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                    .setVisible(WindowInsetsCompat.Type.ime(), height > 0)
                    .build()
            ViewCompat.dispatchApplyWindowInsets(content, insets)
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 40

        /** At least this many frames in which the composer stands somewhere else: it moves, it does not jump. */
        const val MOVING_FRAMES = 4

        const val KEYBOARD_HEIGHT = 900
    }
}

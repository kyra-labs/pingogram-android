package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.RecompositionCounter
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The microphone turning into send as typing starts (owner, after 2.2.0): the pill keeps its width in every frame of
 * the morph, the first letter recomposes only the button and the field, never the composer's row, and the morph
 * recomposes nothing between its start and end. Goldens of both states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerMorphTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()
    private val text = ComposerText()
    private var shown by mutableStateOf(false)

    private val composer =
        ComposerUiState.Available(
            canSchedule = true,
            attachOptions = persistentListOf(AttachOption.Gallery, AttachOption.Camera),
        )

    private fun setComposer() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                if (shown) ChatBottomBar(composer = composer, text = text, onSend = {}, onFocusChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        counter.start()
        shown = true
        repeat(FRAMES) { frame() }
        counter.reset()
    }

    private fun typeFirstLetter() {
        text.state.edit { append("H") }
        Snapshot.sendApplyNotifications()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun pillWidth(): Int =
        composeRule
            .onNodeWithTag(COMPOSER_FIELD_TEST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot.width
            .toInt()

    @Test
    fun `the pill keeps its width in every frame as the microphone turns into send`() {
        setComposer()
        val before = pillWidth()

        typeFirstLetter()
        val widths =
            List(FRAMES) {
                frame()
                pillWidth()
            }

        assertEquals("pill widths per frame: $widths", setOf(before), widths.toSet())
    }

    @Test
    fun `the first letter recomposes only the button and the field, and the morph no frame in between`() {
        setComposer()

        typeFirstLetter()
        repeat(FRAMES) { frame() }

        // Without the suffix Kotlin adds to functions that take inline classes ("ComposerField-E1AOrGg").
        val recomposed = counter.recomposedOwnFunctions.map { it.substringAfterLast('.').substringBefore('-') }.toSet()
        assertTrue("recomposed: $recomposed", recomposed.all { it in BUTTON_AND_FIELD })
        assertTrue("recomposed: ${counter.recomposedOwnFunctions}", "Composer" !in recomposed)
        val slot = counter.recomposedOwnFunctions.count { it.endsWith(".SendOrRecord") }
        assertTrue("SendOrRecord recomposed $slot times", slot <= MORPH_RECOMPOSITIONS)
    }

    @Test
    fun `empty, with the microphone`() = capture("empty")

    @Test
    fun `one letter, with send`() {
        text.state.edit { append("H") }
        capture("oneLetter")
    }

    private fun capture(name: String) {
        captureRoboImage(goldenPath(javaClass, name)) {
            PingogramTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    ChatBottomBar(composer = composer, text = text, onSend = {}, onFocusChanged = {})
                }
            }
        }
    }

    private companion object {
        const val FRAMES = 30

        /** The showing changing, then each button joining and leaving the composition as the morph starts and ends. */
        const val MORPH_RECOMPOSITIONS = 3

        /** The button's slot and buttons, the pill and its field, and what draws inside them. */
        val BUTTON_AND_FIELD =
            setOf(
                "ComposerAction",
                "SendOrRecord",
                "MorphLayer",
                "SendButton",
                "RecordButton",
                "ComposerPill",
                "ComposerField",
                // The field's decoration: the pill around the text.
                "Decoration",
                "ComposerPillRow",
                "Appearing",
                "CameraButton",
                "PrimaryAttachButton",
                "PanelButton",
            )
    }
}

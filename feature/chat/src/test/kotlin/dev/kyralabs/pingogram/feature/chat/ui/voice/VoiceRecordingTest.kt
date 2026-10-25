package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The composer's microphone in use: hold to record and release to send, slide toward the start to cancel, slide up to
 * lock, the locked buttons, recording without touch (screen readers), and the press without the permission. The
 * recording state follows the events as the view model's would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class VoiceRecordingTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<VoiceEvent>()

    private fun setComposer(granted: Boolean = true) {
        val application = RuntimeEnvironment.getApplication() as Application
        if (granted) {
            shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        } else {
            shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        }
        composeRule.setContent {
            var recording by remember { mutableStateOf<VoiceRecordingUiState?>(null) }
            PingogramTheme(dynamicColor = false) {
                ChatBottomBar(
                    composer = ComposerUiState.Available(canSchedule = true),
                    text = ComposerText(),
                    onSend = {},
                    onFocusChanged = {},
                    recording = recording,
                    onVoiceEvent = { event ->
                        events += event
                        recording =
                            when (event) {
                                is VoiceEvent.Start -> VoiceRecordingUiState(isLocked = event.locked)
                                VoiceEvent.Lock -> recording?.copy(isLocked = true)
                                VoiceEvent.Pause -> recording?.copy(isPaused = true)
                                VoiceEvent.Resume -> recording?.copy(isPaused = false)
                                VoiceEvent.Send, VoiceEvent.Cancel -> null
                                else -> recording
                            }
                    },
                )
            }
        }
    }

    private fun node(
        @StringRes description: Int,
    ) = composeRule.onNodeWithContentDescription(string(description), useUnmergedTree = true)

    private fun microphone() = node(R.string.voice_record)

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `holding the microphone records, and releasing sends`() {
        setComposer()

        microphone().performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        assertEquals(listOf(VoiceEvent.Start(locked = false), VoiceEvent.Send), events)
    }

    @Test
    fun `sliding toward the start cancels, and the release then sends nothing`() {
        setComposer()

        microphone().performTouchInput {
            down(center)
            moveBy(Offset(-CANCEL_PX / 2, 0f))
            moveBy(Offset(-CANCEL_PX, 0f))
            up()
        }

        assertEquals(listOf(VoiceEvent.Start(locked = false), VoiceEvent.Cancel), events)
    }

    @Test
    fun `sliding up locks, the recording goes on after the release, and the pill sends it`() {
        setComposer()

        microphone().performTouchInput {
            down(center)
            moveBy(Offset(0f, -LOCK_PX / 2))
            moveBy(Offset(0f, -LOCK_PX))
            up()
        }
        composeRule.waitForIdle()
        node(R.string.voice_pause).performClick()
        node(R.string.voice_resume).performClick()
        node(R.string.voice_send).performClick()

        assertEquals(
            listOf(
                VoiceEvent.Start(locked = false),
                VoiceEvent.Lock,
                VoiceEvent.Pause,
                VoiceEvent.Resume,
                VoiceEvent.Send,
            ),
            events,
        )
    }

    @Test
    fun `delete throws a locked recording away`() {
        setComposer()
        microphone().performSemanticsAction(SemanticsActions.OnClick)

        node(R.string.voice_delete).performClick()

        assertEquals(listOf(VoiceEvent.Start(locked = true), VoiceEvent.Cancel), events)
    }

    @Test
    fun `screen readers record locked at once, without holding`() {
        setComposer()

        microphone().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(VoiceEvent.Start(locked = true)), events)
        node(R.string.voice_send).performClick()
        assertEquals(VoiceEvent.Send, events.last())
    }

    @Test
    fun `screen readers hear Recording and Paused as a polite live region`() {
        setComposer()
        microphone().performSemanticsAction(SemanticsActions.OnClick)

        node(R.string.voice_state_recording)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        node(R.string.voice_pause).performClick()

        node(R.string.voice_state_paused)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule
            .onAllNodesWithContentDescription(string(R.string.voice_state_recording), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun `without the permission a press asks for it and records nothing`() {
        setComposer(granted = false)

        microphone().performTouchInput {
            down(center)
            moveBy(Offset(0f, -LOCK_PX * 2))
            up()
        }

        assertEquals(listOf(VoiceEvent.PermissionNeeded), events)
    }

    private companion object {
        const val HOLD_MILLIS = 2_000L

        /** Farther than the cancel and lock distances (112 and 72 dp) at xxhdpi (3 px per dp). */
        const val CANCEL_PX = 112f * 3
        const val LOCK_PX = 72f * 3
    }
}

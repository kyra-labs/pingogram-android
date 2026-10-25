package dev.kyralabs.pingogram.feature.chat.ui.videonote

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.VideoNoteEvent
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.voice.CameraRationale
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordModeUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VideoRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The record button with a camera (`docs/adr/0032-round-video-messages.md`): a tap switches between voice and video, a
 * hold records either, a switch to video without the permissions asks for them first, the locked video's buttons
 * (flip, pause, send), a video stopped as the screen stopped, screen readers' switch and recording, the rationale
 * sheet's "Not now", and names and touch targets throughout. The recording and the mode follow the events as the view
 * model's would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class VideoNoteRecordingTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(
        video: Boolean = false,
        camera: Boolean = true,
        microphone: Boolean = true,
        initial: VoiceRecordingUiState? = null,
        rationale: CameraRationale? = null,
    ) {
        val shadow = shadowOf(RuntimeEnvironment.getApplication() as Application)
        if (camera) {
            shadow.grantPermissions(
                Manifest.permission.CAMERA,
            )
        } else {
            shadow.denyPermissions(Manifest.permission.CAMERA)
        }
        if (microphone) {
            shadow.grantPermissions(Manifest.permission.RECORD_AUDIO)
        } else {
            shadow.denyPermissions(Manifest.permission.RECORD_AUDIO)
        }
        composeRule.setContent {
            var recording by remember { mutableStateOf(initial) }
            var mode by remember { mutableStateOf(RecordModeUiState(video, canSwitch = true, rationale)) }
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = ChatPreviewData.privateChat,
                    onEvent = { event ->
                        // What the list reports as it shows is left out.
                        if (event is VoiceEvent || event is VideoNoteEvent) events += event
                        when (event) {
                            VideoNoteEvent.SwitchMode -> {
                                mode = mode.copy(video = !mode.video)
                            }

                            VideoNoteEvent.DismissRationale -> {
                                mode = mode.copy(rationale = null)
                            }

                            is VoiceEvent.Start -> {
                                val videoState = VideoRecordingUiState(canFlip = true).takeIf { mode.video }
                                recording = VoiceRecordingUiState(isLocked = event.locked, video = videoState)
                            }

                            VoiceEvent.Lock -> {
                                recording = recording?.copy(isLocked = true)
                            }

                            VoiceEvent.Send, VoiceEvent.Cancel -> {
                                recording = null
                            }

                            else -> {}
                        }
                    },
                    onBack = {},
                    recording = recording,
                    recordMode = mode,
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun node(
        @StringRes description: Int,
    ) = composeRule.onNodeWithContentDescription(string(description), useUnmergedTree = true)

    @Test
    fun `a tap switches the microphone to the camera and back, recording nothing`() {
        setScreen()

        node(R.string.voice_record).performTouchInput {
            down(center)
            advanceEventTime(TAP_MILLIS)
            up()
        }
        node(R.string.video_note_record).performTouchInput {
            down(center)
            advanceEventTime(TAP_MILLIS)
            up()
        }

        assertEquals(listOf(VideoNoteEvent.SwitchMode, VideoNoteEvent.SwitchMode), events)
        node(R.string.voice_record).assertExists()
    }

    @Test
    fun `holding the microphone still records a voice message`() {
        setScreen()

        node(R.string.voice_record).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        assertEquals(listOf(VoiceEvent.Start(locked = false), VoiceEvent.Send), events)
    }

    @Test
    fun `holding the camera records a video message, and releasing sends it`() {
        setScreen(video = true)

        node(R.string.video_note_record).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        assertEquals(listOf(VoiceEvent.Start(locked = false), VoiceEvent.Send), events)
    }

    @Test
    fun `sliding up locks a video message, whose camera flips and which sends`() {
        setScreen(video = true)

        node(R.string.video_note_record).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
            moveBy(Offset(0f, -LOCK_PX / 2))
            moveBy(Offset(0f, -LOCK_PX))
            up()
        }
        composeRule.waitForIdle()
        node(R.string.video_note_flip).performClick()
        node(R.string.voice_pause).performClick()
        node(R.string.video_note_send).performClick()

        assertEquals(
            listOf(
                VoiceEvent.Start(locked = false),
                VoiceEvent.Lock,
                VideoNoteEvent.FlipCamera,
                VoiceEvent.Pause,
                VoiceEvent.Send,
            ),
            events,
        )
    }

    @Test
    fun `switching to video without the camera's permission asks for it first`() {
        setScreen(camera = false)

        node(R.string.voice_record).performTouchInput {
            down(center)
            advanceEventTime(TAP_MILLIS)
            up()
        }

        assertEquals(listOf(VideoNoteEvent.PermissionNeeded(switching = true)), events)
    }

    @Test
    fun `holding the camera without the microphone's permission asks for both and records nothing`() {
        setScreen(video = true, microphone = false)

        node(R.string.video_note_record).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MILLIS)
            up()
        }

        assertEquals(listOf(VideoNoteEvent.PermissionNeeded(switching = false)), events)
    }

    @Test
    fun `screen readers switch through a custom action and record locked at once`() {
        setScreen()

        val switch =
            node(R.string.voice_record)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == string(R.string.video_note_switch_to_video) }
        composeRule.runOnIdle { switch.action() }
        node(R.string.video_note_record).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(VideoNoteEvent.SwitchMode, VoiceEvent.Start(locked = true)), events)
        node(R.string.video_note_send).assertExists()
    }

    @Test
    fun `a video message stopped as the screen stopped only sends or deletes`() {
        setScreen(
            video = true,
            initial =
                VoiceRecordingUiState(
                    isLocked = true,
                    isPaused = true,
                    video = VideoRecordingUiState(isStopped = true),
                ),
        )

        composeRule.onNodeWithText(string(R.string.video_note_stopped)).assertExists()
        node(R.string.video_note_flip).assertDoesNotExist()
        node(R.string.voice_resume).assertDoesNotExist()
        node(R.string.voice_delete).performClick()

        assertEquals(listOf(VoiceEvent.Cancel), events)
    }

    @Test
    fun `the round preview says how long it has recorded of the minute`() {
        setScreen(video = true, initial = VoiceRecordingUiState(video = VideoRecordingUiState()))

        composeRule
            .onNodeWithContentDescription("Video message, 0:00 of 1:00")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `not now closes the rationale`() {
        setScreen(rationale = CameraRationale())

        composeRule.onNodeWithText(string(R.string.video_note_permission_continue)).assertExists()
        composeRule.onNodeWithText(string(R.string.video_note_permission_not_now)).performClick()

        assertTrue(VideoNoteEvent.DismissRationale in events)
    }

    @Test
    fun `once Android no longer asks, the rationale offers the settings`() {
        setScreen(rationale = CameraRationale(deniedForGood = true))

        composeRule.onNodeWithText(string(R.string.video_note_permission_settings)).assertExists()
        composeRule.onNodeWithText(string(R.string.video_note_permission_denied_body)).assertExists()
    }

    @Test
    fun `the camera button, a locked video message and the rationale have names and touch targets`() {
        setScreen(video = true)
        composeRule.assertAccessible()

        node(R.string.video_note_record).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.assertAccessible()
    }

    @Test
    fun `the rationale sheet has names and touch targets`() {
        setScreen(rationale = CameraRationale())

        composeRule.assertAccessible()
    }

    private companion object {
        /** Shorter than the hold before recording (150 ms). */
        const val TAP_MILLIS = 50L
        const val HOLD_MILLIS = 2_000L

        /** Farther than the lock distance (72 dp) at xxhdpi (3 px per dp). */
        const val LOCK_PX = 72f * 3
    }
}

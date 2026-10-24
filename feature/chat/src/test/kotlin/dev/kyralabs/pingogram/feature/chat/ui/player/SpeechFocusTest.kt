package dev.kyralabs.pingogram.feature.chat.ui.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A voice message's audio focus: transient, paused on a loss, played on after a transient one only. */
@RunWith(RobolectricTestRunner::class)
class SpeechFocusTest {
    private val audioManager =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)
    private val shadow = shadowOf(audioManager)
    private val events = mutableListOf<String>()
    private val focus =
        SpeechFocus(
            audioManager = audioManager,
            handler = Handler(Looper.getMainLooper()),
            onLoss = { events += "pause" },
            onGain = { events += "play" },
        )

    @Test
    fun `asks for transient focus for speech`() {
        assertTrue(focus.request())

        val request = shadow.lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.focusGain)
        assertTrue(request.willPauseWhenDucked())
    }

    @Test
    fun `a refused request does not play and shows paused`() {
        shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        assertFalse(focus.request())
        assertEquals(listOf("pause"), events)
    }

    @Test
    fun `a transient loss pauses and the focus coming back plays on`() {
        focus.request()
        val listener = shadow.lastAudioFocusRequest.listener

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf("pause", "play"), events)
    }

    @Test
    fun `speech pauses instead of being ducked`() {
        focus.request()

        shadow.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        assertEquals(listOf("pause"), events)
    }

    @Test
    fun `a permanent loss pauses and gives the focus back`() {
        focus.request()
        val listener = shadow.lastAudioFocusRequest.listener

        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf("pause"), events)
        assertEquals(shadow.lastAudioFocusRequest.audioFocusRequest, shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `pausing by hand gives the focus back without playing on later`() {
        focus.request()
        val listener = shadow.lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        focus.abandon()
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(listOf("pause"), events)
        assertEquals(shadow.lastAudioFocusRequest.audioFocusRequest, shadow.lastAbandonedAudioFocusRequest)
    }
}

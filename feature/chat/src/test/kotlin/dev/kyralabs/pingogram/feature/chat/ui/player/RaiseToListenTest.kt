package dev.kyralabs.pingogram.feature.chat.ui.player

import android.os.Looper
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.testing.FakePlaybackSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Raise to listen: the earpiece while the phone is at the ear, never with headphones or a call, a raise only. */
@RunWith(RobolectricTestRunner::class)
class RaiseToListenTest {
    private val sensor = FakeProximitySensor()
    private val route = FakeEarpieceRoute()
    private val settings = FakePlaybackSettingsRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val events = mutableListOf<String>()
    private val raise =
        RaiseToListen(sensor, route, settings, scope).apply {
            listener =
                object : RaiseToListen.Listener {
                    override fun onEarpieceChanged(earpiece: Boolean) {
                        events += if (earpiece) "earpiece" else "speaker"
                    }

                    override fun onPlayingChanged(playing: Boolean) {
                        events += if (playing) "play" else "pause"
                    }
                }
        }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `raising the phone plays at the earpiece, lowering it on the speaker`() {
        raise.start()
        sensor.report(near = false)

        sensor.report(near = true)
        assertTrue(route.atEarpiece)
        sensor.report(near = false)

        assertFalse(route.atEarpiece)
        assertEquals(listOf("earpiece", "speaker"), events)
    }

    @Test
    fun `lowering pauses where the user asked for it, and raising again plays on`() {
        settings.raiseToListen.value = RaiseToListenSettings(pauseWhenLowered = true)
        raise.start()
        sensor.report(near = false)

        sensor.report(near = true)
        sensor.report(near = false)
        sensor.report(near = true)

        assertEquals(listOf("earpiece", "speaker", "pause", "earpiece", "play"), events)
    }

    @Test
    fun `a phone already covered when playback starts waits to be uncovered`() {
        raise.start()
        sensor.report(near = true)
        assertTrue(events.isEmpty())

        sensor.report(near = false)
        sensor.report(near = true)
        assertEquals(listOf("earpiece"), events)
    }

    @Test
    fun `headphones, Bluetooth and calls keep the sound where it is`() {
        raise.start()
        sensor.report(near = false)

        route.privateOutput = true
        sensor.report(near = true)
        route.privateOutput = false
        route.inCall = true
        sensor.report(near = false)
        sensor.report(near = true)

        assertTrue(events.isEmpty())
        assertFalse(route.atEarpiece)
    }

    @Test
    fun `turned off, the sensor stays off`() {
        settings.raiseToListen.value = RaiseToListenSettings(enabled = false)

        raise.start()

        assertFalse(sensor.listening)
    }

    @Test
    fun `the next voice message stays at the ear, and the sensor goes off once none plays`() {
        raise.start()
        sensor.report(near = false)
        sensor.report(near = true)

        raise.stop()
        raise.start()
        idle(RaiseToListen.STOP_GRACE_MILLIS * 2)
        assertTrue(route.atEarpiece)

        raise.stop()
        idle(RaiseToListen.STOP_GRACE_MILLIS)
        assertFalse(sensor.listening)
        assertFalse(route.atEarpiece)
        assertEquals(listOf("earpiece", "speaker"), events)
    }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
}

private class FakeProximitySensor : ProximitySensor {
    private var onChange: ((Boolean) -> Unit)? = null
    val listening get() = onChange != null

    override val isAvailable = true

    override fun start(onChange: (near: Boolean) -> Unit) {
        this.onChange = onChange
    }

    override fun stop() {
        onChange = null
    }

    fun report(near: Boolean) = onChange?.invoke(near)
}

private class FakeEarpieceRoute : EarpieceRoute {
    var privateOutput = false
    var inCall = false
    var atEarpiece = false

    override fun hasPrivateOutput() = privateOutput

    override fun isInCall() = inCall

    override fun toEarpiece(): Boolean {
        atEarpiece = true
        return true
    }

    override fun toSpeaker() {
        atEarpiece = false
    }
}

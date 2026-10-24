package dev.kyralabs.pingogram.feature.chat.ui.player

import android.os.Handler
import android.os.Looper
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Raise to listen (`docs/audit/feature-gaps-octogram.md`, [RaiseToListenSettings]): while a voice message plays, the
 * phone held to the ear plays it through the earpiece, the screen off; lowered, it plays through the speaker again, or
 * pauses until it is raised. Never while headphones or a Bluetooth device are connected, nor during a call.
 *
 * The proximity sensor is on only while voice messages play ([start] to [stop]), which needs no permission. A phone
 * that is already covered when they start (face down on a table, in a pocket) waits to be uncovered first, so only a
 * raise counts. [stop] waits [STOP_GRACE_MILLIS] before it lets go, so the next voice message, which the conversation
 * plays on its own after one ends, stays at the ear.
 *
 * Main thread only, like [ExoAudioPlayer], which tells it what plays and does what [Listener] asks.
 */
internal class RaiseToListen
    @Inject
    constructor(
        private val proximity: ProximitySensor,
        private val route: EarpieceRoute,
        private val settings: PlaybackSettingsRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        /** What raise to listen asks of the player. */
        interface Listener {
            /** Plays through the earpiece ([earpiece]) or as media again. */
            fun onEarpieceChanged(earpiece: Boolean)

            /** Pauses ([playing] false) when the phone is lowered, or plays on when it is raised again. */
            fun onPlayingChanged(playing: Boolean)
        }

        var listener: Listener? = null

        private val handler = Handler(Looper.getMainLooper())
        private val stopNow = Runnable { stopListening() }

        @Volatile
        private var current = RaiseToListenSettings()
        private var settingsJob: Job? = null

        private var listening = false

        /** The sensor's first reading said near: wait for far before anything counts. */
        private var waitForFar = false

        private var atEar = false
        private var pausedByLowering = false

        /** Voice messages started playing, or played on. */
        fun start() {
            handler.removeCallbacks(stopNow)
            if (settingsJob == null) {
                settingsJob = scope.launch { settings.raiseToListen.collect { current = it } }
            }
            if (listening || !current.enabled || !proximity.isAvailable) return
            listening = true
            waitForFar = false
            var first = true
            proximity.start { near ->
                val wasFirst = first
                first = false
                onProximity(near, wasFirst)
            }
        }

        /** Voice messages stopped: lets go of the sensor and the earpiece soon, unless they start again. */
        fun stop() {
            if (!listening) return
            handler.removeCallbacks(stopNow)
            handler.postDelayed(stopNow, STOP_GRACE_MILLIS)
        }

        /** The player is gone for good. */
        fun release() {
            handler.removeCallbacks(stopNow)
            stopListening()
            settingsJob?.cancel()
            settingsJob = null
            listener = null
        }

        private fun onProximity(
            near: Boolean,
            first: Boolean,
        ) {
            when {
                first && near -> {
                    waitForFar = true
                }

                waitForFar -> {
                    if (!near) waitForFar = false
                }

                near && !atEar -> {
                    raised()
                }

                !near && atEar -> {
                    lowered()
                }
            }
        }

        private fun raised() {
            val free = !route.hasPrivateOutput() && !route.isInCall()
            if (!current.enabled || !free || !route.toEarpiece()) return
            atEar = true
            listener?.onEarpieceChanged(true)
            if (pausedByLowering) {
                pausedByLowering = false
                listener?.onPlayingChanged(true)
            }
        }

        private fun lowered() {
            atEar = false
            route.toSpeaker()
            listener?.onEarpieceChanged(false)
            if (current.pauseWhenLowered) {
                pausedByLowering = true
                listener?.onPlayingChanged(false)
            }
        }

        private fun stopListening() {
            if (!listening) return
            listening = false
            proximity.stop()
            pausedByLowering = false
            if (atEar) {
                atEar = false
                route.toSpeaker()
                listener?.onEarpieceChanged(false)
            }
        }

        internal companion object {
            const val STOP_GRACE_MILLIS = 1_500L
        }
    }

/** The proximity sensor: [start] reports near or far now and at every change, until [stop]. */
internal interface ProximitySensor {
    val isAvailable: Boolean

    fun start(onChange: (near: Boolean) -> Unit)

    fun stop()
}

/** Where a voice message plays, and the screen while it plays at the ear. */
internal interface EarpieceRoute {
    /** Headphones, a headset or a Bluetooth device is connected: the sound stays there. */
    fun hasPrivateOutput(): Boolean

    /** A call (Pingogram's or another app's) holds the audio. */
    fun isInCall(): Boolean

    /** Routes to the earpiece and turns the screen off while the phone stays covered; false if it cannot. */
    fun toEarpiece(): Boolean

    /** Routes as media again and lets the screen on. */
    fun toSpeaker()
}

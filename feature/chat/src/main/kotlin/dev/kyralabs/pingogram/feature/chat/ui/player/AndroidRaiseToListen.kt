package dev.kyralabs.pingogram.feature.chat.ui.player

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The earpiece as the communication device (Android 12+), or the speakerphone off in communication mode before. */
private fun AudioManager.routeToEarpiece(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            ?.let(::setCommunicationDevice) == true
    } else {
        @Suppress("DEPRECATION") // The only way before Android 12.
        isSpeakerphoneOn = false
        true
    }

/** [ProximitySensor] on the platform's proximity sensor, reported on the main thread. */
internal class AndroidProximitySensor
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) : ProximitySensor {
        private val sensors = context.getSystemService<SensorManager>()
        private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        private var listener: SensorEventListener? = null

        override val isAvailable: Boolean get() = sensor != null

        override fun start(onChange: (near: Boolean) -> Unit) {
            val sensor = sensor ?: return
            stop()
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        // Most sensors report only near (0) or far (their maximum range).
                        onChange(event.values[0] < minOf(sensor.maximumRange, NEAR_CENTIMETRES))
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            this.listener = listener
            sensors?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        override fun stop() {
            listener?.let { sensors?.unregisterListener(it) }
            listener = null
        }

        private companion object {
            const val NEAR_CENTIMETRES = 5f
        }
    }

/**
 * [EarpieceRoute] on the platform's audio and power managers: the earpiece as the communication device (Android 12+)
 * or the speakerphone turned off in communication mode before, and the proximity wake lock, as the call service holds
 * it at the earpiece.
 */
internal class AndroidEarpieceRoute
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) : EarpieceRoute {
        private val audio = context.getSystemService<AudioManager>()
        private val power = context.getSystemService<PowerManager>()
        private var wakeLock: PowerManager.WakeLock? = null
        private var routed = false

        override fun hasPrivateOutput(): Boolean =
            audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { it.type in PRIVATE_OUTPUTS }

        override fun isInCall(): Boolean = !routed && audio?.mode != AudioManager.MODE_NORMAL

        override fun toEarpiece(): Boolean {
            val audio = audio ?: return false
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            routed = audio.routeToEarpiece()
            if (routed) holdScreenOff() else audio.mode = AudioManager.MODE_NORMAL
            return routed
        }

        /** Turns the screen off while the phone stays covered. */
        private fun holdScreenOff() {
            val power = power ?: return
            if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            val lock = wakeLock ?: power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
            wakeLock = lock
            // Held until lowered, which releases it; at most a long voice message.
            if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        }

        override fun toSpeaker() {
            wakeLock?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            if (!routed) return
            routed = false
            val audio = audio ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
            audio.mode = AudioManager.MODE_NORMAL
        }

        private companion object {
            const val WAKE_LOCK_TAG = "Pingogram:RaiseToListen"
            const val WAKE_LOCK_TIMEOUT_MILLIS = 60 * 60 * 1000L

            val PRIVATE_OUTPUTS =
                setOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                ) +
                    if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
                    ) {
                        setOf(AudioDeviceInfo.TYPE_BLE_HEADSET)
                    } else {
                        emptySet()
                    }
        }
    }

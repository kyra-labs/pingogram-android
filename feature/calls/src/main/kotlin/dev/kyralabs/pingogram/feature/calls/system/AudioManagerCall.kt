package dev.kyralabs.pingogram.feature.calls.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CallAudio
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A call's audio where Telecom takes no call (`TelecomCallSystem`): the audio mode set to voice communication while it
 * is connected, and the route picked directly: Android 12+ `setCommunicationDevice` (earpiece, speaker, wired headset,
 * Bluetooth), before 12 the speakerphone on or off. It holds the audio focus as a call (`AUDIOFOCUS_GAIN_TRANSIENT`
 * for voice communication), as Telecom does for its calls, so music and voice messages pause meanwhile. Restored when
 * the call ends.
 */
internal class AudioManagerCall(
    context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var started = false
    private var selected: AudioRoute? = null
    private var focus: AudioFocusRequest? = null

    val audio: CallAudio
        get() {
            val available = available()
            val route = selected?.takeIf { it in available } ?: available.firstOrNull()
            return CallAudio(route, available.toImmutableList())
        }

    fun start() {
        if (started) return
        started = true
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).build()
        // The call goes on without the focus: it only quiets other apps.
        audioManager.requestAudioFocus(request)
        focus = request
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        selected?.let(::apply)
    }

    fun select(route: AudioRoute) {
        selected = route
        if (started) apply(route)
    }

    fun end() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION") // The only way before Android 12.
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        focus?.let(audioManager::abandonAudioFocusRequest)
        focus = null
    }

    private fun apply(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            communicationDevices().firstOrNull { it.id.toString() == route.id }?.let(
                audioManager::setCommunicationDevice,
            )
        } else {
            @Suppress("DEPRECATION") // The only way before Android 12.
            audioManager.isSpeakerphoneOn = route.type == AudioRouteType.Speaker
        }
    }

    private fun available(): List<AudioRoute> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            communicationDevices().mapNotNull { device ->
                device.routeType()?.let {
                    AudioRoute(
                        device.id.toString(),
                        it,
                        if (it ==
                            AudioRouteType.Bluetooth
                        ) {
                            device.productName.toString()
                        } else {
                            ""
                        },
                    )
                }
            }
        } else {
            persistentListOf(AudioRoute(EARPIECE, AudioRouteType.Earpiece), AudioRoute(SPEAKER, AudioRouteType.Speaker))
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun communicationDevices(): List<AudioDeviceInfo> = audioManager.availableCommunicationDevices

    @RequiresApi(Build.VERSION_CODES.S)
    private fun AudioDeviceInfo.routeType(): AudioRouteType? =
        when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRouteType.Earpiece

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRouteType.Speaker

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> AudioRouteType.WiredHeadset

            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioRouteType.Bluetooth

            else -> null
        }

    private companion object {
        const val EARPIECE = "earpiece"
        const val SPEAKER = "speaker"
    }
}

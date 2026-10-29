package dev.kyralabs.pingogram.feature.chat.ui.player

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler

/**
 * The audio focus of a voice message: transient (`AUDIOFOCUS_GAIN_TRANSIENT`), so the music another app plays pauses
 * for it and plays on after it, where a media app's permanent focus would stop it for good.
 *
 * ExoPlayer derives the focus from the audio attributes, and asks a media usage for permanent focus, so speech asks
 * here instead. Speech is paused rather than ducked when another app talks over it, as Android's guidance asks: a
 * quieter voice is not understood. A call or another app's loss of focus pauses it ([onLoss]); a transient loss plays
 * on once the focus comes back ([onGain]), a permanent one does not.
 *
 * Built and called on one thread, [handler]'s, which the callbacks come on too.
 */
internal class SpeechFocus(
    private val audioManager: AudioManager,
    handler: Handler,
    private val onLoss: () -> Unit,
    private val onGain: () -> Unit,
) {
    private var held = false

    /** Whether a transient loss paused the voice message, so the focus coming back plays it on. */
    private var resumeOnGain = false

    private val request =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(SPEECH)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(::onFocusChange, handler)
            .build()

    /**
     * Asks for the focus before playing; false while Android refuses it, e.g. during a phone call, which counts as a
     * loss ([onLoss]), so the voice message shows paused.
     */
    fun request(): Boolean {
        resumeOnGain = false
        if (held) return true
        held = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!held) onLoss()
        return held
    }

    /** Gives the focus back: the voice message paused, ended or stopped. */
    fun abandon() {
        resumeOnGain = false
        if (!held) return
        held = false
        audioManager.abandonAudioFocusRequest(request)
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnGain) {
                    resumeOnGain = false
                    onGain()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!held) return
                resumeOnGain = true
                onLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                abandon()
                onLoss()
            }
        }
    }

    private companion object {
        val SPEECH: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
    }
}

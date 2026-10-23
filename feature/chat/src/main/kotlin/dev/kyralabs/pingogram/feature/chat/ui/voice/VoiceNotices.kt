package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEffect
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent

/** The snackbar text of [effect]. */
@StringRes
internal fun voiceNoticeText(effect: VoiceEffect): Int =
    when (effect) {
        VoiceEffect.ExplainMicrophone -> R.string.voice_permission_rationale
        VoiceEffect.MicrophoneDenied -> R.string.voice_permission_denied
        VoiceEffect.HoldToRecord -> R.string.voice_hold_to_record
        VoiceEffect.RecordingFailed -> R.string.voice_recording_failed
    }

/** The action of [effect]'s snackbar: asking for the permission, or opening the settings; null for none. */
@StringRes
internal fun voiceNoticeActionLabel(effect: VoiceEffect): Int? =
    when (effect) {
        VoiceEffect.ExplainMicrophone -> R.string.voice_permission_allow
        VoiceEffect.MicrophoneDenied -> R.string.voice_permission_settings
        VoiceEffect.HoldToRecord, VoiceEffect.RecordingFailed -> null
    }

/** What the user does with a voice recording, going to [onEvent]; once one is sent, [onSent] too. */
internal fun voiceEvents(
    onEvent: (ChatEvent) -> Unit,
    onSent: () -> Unit,
): (VoiceEvent) -> Unit =
    { event ->
        onEvent(event)
        if (event == VoiceEvent.Send) onSent()
    }

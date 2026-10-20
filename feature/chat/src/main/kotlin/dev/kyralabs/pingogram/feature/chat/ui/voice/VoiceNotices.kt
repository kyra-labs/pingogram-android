package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
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

/**
 * What the action of a voice snackbar does: [onRequestMicrophone] asks the system for the permission after its reason
 * was shown, [onOpenSettings] opens the app's settings after it was refused.
 */
internal fun voiceNoticeAction(
    onRequestMicrophone: () -> Unit,
    onOpenSettings: () -> Unit,
): (VoiceEffect) -> Unit =
    { effect ->
        when (effect) {
            VoiceEffect.ExplainMicrophone -> {
                onRequestMicrophone()
            }

            VoiceEffect.MicrophoneDenied -> {
                onOpenSettings()
            }

            VoiceEffect.HoldToRecord, VoiceEffect.RecordingFailed -> {}
        }
    }

/**
 * Asks the system for the microphone permission, which voice messages need (`REQUIREMENTS.md` FR-C5), and sends the
 * answer to [onEvent] ([VoiceEvent.PermissionAnswered]); the returned function starts the request.
 */
@Composable
internal fun rememberMicrophoneRequest(onEvent: (ChatEvent) -> Unit): () -> Unit {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnEvent(VoiceEvent.PermissionAnswered(granted))
        }
    return remember(launcher) { { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
}

/** Opens the app's page in the system settings, where the microphone permission is granted once refused. */
internal fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device without the page: there is nothing else to open.
    }
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

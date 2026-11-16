package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.kyralabs.pingogram.feature.chat.ui.VoiceEvent

/**
 * What the Quick compose widget's "Voice" (board 33) starts in the chat it opened: a locked voice recording where the
 * microphone permission is granted, else the snackbar that asks for it, after which the user holds the microphone.
 */
internal fun voiceStartOrAsk(context: Context): VoiceEvent =
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        VoiceEvent.Start(locked = true, voiceOnly = true)
    } else {
        VoiceEvent.PermissionNeeded
    }

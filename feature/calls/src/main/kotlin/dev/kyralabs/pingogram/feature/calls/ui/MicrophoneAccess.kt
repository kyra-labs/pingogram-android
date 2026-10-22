package dev.kyralabs.pingogram.feature.calls.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the app may record audio, which a call needs (`RECORD_AUDIO`); a seam for the call screen's tests. */
fun interface MicrophoneAccess {
    fun isGranted(): Boolean
}

internal class AndroidMicrophoneAccess
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : MicrophoneAccess {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

/**
 * What the call screen asks Android for: the microphone, and on Android 12+ the names of Bluetooth headsets
 * (`BLUETOOTH_CONNECT`), so the call can be moved to one. Only the microphone is required.
 */
internal val CALL_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

package dev.kyralabs.pingogram.feature.chat.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.core.content.ContextCompat

// What the record button's tap-or-hold gesture and its permission checks share (docs/adr/0032-round-video-messages.md).

/**
 * Whether the press of pointer [id] ends within [millis], taking its changes in the initial pass: a tap. False once
 * it lasts longer, when it becomes a hold.
 */
internal suspend fun AwaitPointerEventScope.releasedWithin(
    id: PointerId,
    millis: Long,
): Boolean =
    withTimeoutOrNull(millis) {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id }
            change?.consume()
            if (change == null || !change.pressed) break
        }
        true
    } ?: false

/** The camera's and the microphone's permissions, which recording video messages needs. */
internal val VIDEO_NOTE_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/**
 * How long a press on the record button lasts before it records, where a tap switches between voice and video:
 * shorter is a tap. About Telegram's apps' delay, well under the platform's long-press time.
 */
internal const val HOLD_TO_RECORD_MILLIS = 150L

internal fun Context.has(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Whether video messages may be recorded: the camera's and the microphone's permissions are granted. */
internal fun Context.hasVideoPermissions(): Boolean = VIDEO_NOTE_PERMISSIONS.all(::has)

package dev.kyralabs.pingogram.feature.calls.engine

import android.util.Log
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.VideoDescription
import org.json.JSONException
import org.json.JSONObject

/** NTgCalls' camera metadata: `{"id": "1", "is_front": true}`; null where it cannot be read. */
internal fun String.toCamera(): CallCamera? =
    try {
        val json = JSONObject(this)
        CallCamera(id = json.getString("id"), isFront = json.optBoolean("is_front"), metadata = this)
    } catch (e: JSONException) {
        Log.w("NtgCallsMapping", "Unreadable camera: ${e.javaClass.simpleName}")
        null
    }

/** This camera through NTgCalls' capturer, at most at [quality]. */
internal fun CallCamera.toVideoSource(quality: VideoQuality) =
    VideoDescription(MediaSource.DEVICE, quality.width, quality.height, quality.fps, metadata, true)

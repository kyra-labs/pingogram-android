package dev.kyralabs.pingogram.feature.media.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * How far ahead the media viewer's player buffers. Every video it plays is a file on the phone: one TDLib has, as it
 * is (`file://`), which Media3 already sizes as local playback, or one TDLib is still downloading, read through
 * `tdlib-file://`, a scheme Media3 takes for a stream from a server, buffering 50 s and up to 131 MB of video ahead.
 * Those are sized as local playback too: time first, at least a second ahead
 * ([DefaultLoadControl.DEFAULT_MIN_BUFFER_FOR_LOCAL_PLAYBACK_MS]), then no more than [TARGET_BUFFER_BYTES].
 */
@OptIn(UnstableApi::class)
internal fun localFileLoadControl(): DefaultLoadControl =
    DefaultLoadControl
        .Builder()
        .setBufferDurationsMsForStreaming(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_FOR_LOCAL_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_FOR_LOCAL_PLAYBACK_MS,
        ).setPrioritizeTimeOverSizeThresholdsForStreaming(true)
        .setTargetBufferBytes(TARGET_BUFFER_BYTES)
        .build()

/**
 * The player's renderers, which try the device's next decoder for a format when the first one fails to start (a
 * hardware decoder busy with another app, or unable to take the video's profile), instead of failing the video.
 */
@OptIn(UnstableApi::class)
internal fun videoRenderers(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context).setEnableDecoderFallback(true)

/** The most a video's buffer holds: what Media3 allows local playback, about 19 MB. */
@OptIn(UnstableApi::class)
internal const val TARGET_BUFFER_BYTES = DefaultLoadControl.DEFAULT_VIDEO_BUFFER_SIZE_FOR_LOCAL_PLAYBACK

package dev.kyralabs.pingogram.feature.media.ui.player

import androidx.media3.common.Player

/**
 * Plays one video at a time, a local file or one TDLib streams ([PlaySource]): the platform side of the viewer's
 * playback (`MediaPlayback`), behind an interface so tests use a fake. [ExoVideoPlayer] in the app. Used on the main
 * thread only, where its [listener] is called too. Public only because `MediaViewerViewModel`'s injected constructor
 * takes it.
 */
interface VideoPlayer {
    /** Told what the player does by itself; set by `MediaPlayback`. */
    var listener: Listener?

    /**
     * The Media3 player a video surface shows (`PlayerSurface`); null before the first [play], and in tests, which
     * show no surface.
     */
    val surfacePlayer: Player?

    /** Where the file plays, in milliseconds. */
    val positionMillis: Long

    /** The file's length in milliseconds; null while the player does not know it. */
    val durationMillis: Long?

    /** How far the player has the file, in milliseconds: what plays without waiting. */
    val bufferedMillis: Long get() = positionMillis

    /**
     * Plays [source] from its start, replacing what was in the player: a [VideoKind.Video] once with its sound,
     * holding audio focus; a [VideoKind.Gif] in a loop, silent and without audio focus.
     */
    fun play(
        source: PlaySource,
        kind: VideoKind,
    )

    fun pause()

    /** Plays on from where [pause] stopped, or from the start after the end. */
    fun resume()

    fun seekTo(positionMillis: Long)

    /**
     * Silences what plays, or gives it its sound back, without stopping it: the stories viewer's mute button. Players
     * that only play GIFs ignore it.
     */
    fun setMuted(muted: Boolean) {}

    /** Stops and empties the player; it can [play] again. */
    fun stop()

    /** Frees the player for good. */
    fun release()

    /** What the player does by itself. */
    interface Listener {
        /** It paused itself (lost audio focus, headphones unplugged), or played on. */
        fun onPlayingChanged(isPlaying: Boolean)

        /** The first frame of the file is on the surface. */
        fun onFirstFrame()

        /** The file played to its end; never for a looping GIF. */
        fun onEnded()

        /** The file cannot be played. */
        fun onError()
    }
}

/** What a player plays: a file on the device, or a TDLib file as it downloads (`docs/adr/0028-streaming-media.md`). */
sealed interface PlaySource {
    data class File(
        val path: String,
    ) : PlaySource {
        // The path ends in the file's name, which can be personal.
        override fun toString() = "File(…)"
    }

    data class Stream(
        val fileId: Int,
    ) : PlaySource
}

/** How a file plays: a video once with sound, a GIF in a silent loop. */
enum class VideoKind {
    Video,
    Gif,
}

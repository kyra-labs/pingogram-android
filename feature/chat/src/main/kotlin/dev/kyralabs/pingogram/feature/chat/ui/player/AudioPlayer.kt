package dev.kyralabs.pingogram.feature.chat.ui.player

/**
 * Plays one local audio file at a time: the platform side of a conversation's playback (`ChatPlayback`), behind an
 * interface so tests use a fake. [ExoAudioPlayer] in the app. Used on the main thread only, where its [listener]
 * is called too. Public only because `ChatViewModel`'s injected constructor takes it.
 */
interface AudioPlayer {
    /** Told what the player does by itself; set by `ChatPlayback`. */
    var listener: Listener?

    /** Where the file plays, in milliseconds. */
    val positionMillis: Long

    /** The file's length in milliseconds; null while the player does not know it. */
    val durationMillis: Long?

    /** Plays the file at [path] from [positionMillis], as [kind] for audio focus; replaces what was in the player. */
    fun play(
        path: String,
        kind: AudioKind,
        positionMillis: Long,
    )

    fun pause()

    /** Plays on from where [pause] stopped. */
    fun resume()

    fun seekTo(positionMillis: Long)

    /** Stops and empties the player; it can [play] again. */
    fun stop()

    /** Frees the player for good. */
    fun release()

    /** What the player does by itself. */
    interface Listener {
        /** It paused itself (lost audio focus, headphones unplugged), or played on. */
        fun onPlayingChanged(isPlaying: Boolean)

        /** The file played to its end. */
        fun onEnded()

        /** The file cannot be played. */
        fun onError()
    }
}

/** What plays, for audio focus: speech pauses when another app talks over it, music only gets quieter. */
enum class AudioKind {
    Speech,
    Music,
}

package dev.kyralabs.pingogram.feature.chat.ui.player

/**
 * Where a file plays, as the player last reported it: at [positionMillis] at [atMillis] (`SystemClock.elapsedRealtime`
 * time), moving on in real time while [advancing], up to [durationMillis] when the player knows it. The main thread
 * reads the position from this instead of asking the player, which runs on a thread of its own ([ExoAudioPlayer]), as
 * media sessions publish a position and the time it was taken.
 */
internal data class PlaybackClock(
    val positionMillis: Long,
    val advancing: Boolean,
    val atMillis: Long,
    val durationMillis: Long?,
) {
    /** The position at [nowMillis]. */
    fun positionAt(nowMillis: Long): Long {
        if (!advancing) return positionMillis
        val position = positionMillis + (nowMillis - atMillis).coerceAtLeast(0)
        return durationMillis?.let { position.coerceAtMost(it) } ?: position
    }

    /** Stopped where it got to at [nowMillis]. */
    fun pausedAt(nowMillis: Long): PlaybackClock =
        copy(positionMillis = positionAt(nowMillis), advancing = false, atMillis = nowMillis)

    /** Moved to [positionMillis] at [nowMillis], advancing or not as before. */
    fun movedTo(
        positionMillis: Long,
        nowMillis: Long,
    ): PlaybackClock = copy(positionMillis = positionMillis, atMillis = nowMillis)

    companion object {
        /** Nothing in the player. */
        val Empty = PlaybackClock(positionMillis = 0, advancing = false, atMillis = 0, durationMillis = null)

        /** A file about to play from [positionMillis]: still until the player reports it playing. */
        fun startingAt(
            positionMillis: Long,
            nowMillis: Long,
        ) = PlaybackClock(positionMillis, advancing = false, atMillis = nowMillis, durationMillis = null)
    }
}

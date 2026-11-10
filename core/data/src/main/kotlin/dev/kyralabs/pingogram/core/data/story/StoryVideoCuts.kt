package dev.kyralabs.pingogram.core.data.story

/**
 * Where a video longer than a story takes is cut (`docs/adr/0036-posting-stories.md`): at the latest key frame at most
 * [maxUs] after each part's start, so every part plays from a key frame and lasts at most [maxUs], without encoding
 * again. [syncTimesUs] are the video's key frames' times, in order, [durationUs] its length. Parts as start and end
 * times; null when two key frames, or the last and the end, lie more than [maxUs] apart, which no cut can fix.
 */
internal fun storyVideoCuts(
    syncTimesUs: List<Long>,
    durationUs: Long,
    maxUs: Long,
): List<LongRange>? {
    val cuts = mutableListOf(0L)
    while (durationUs - cuts.last() > maxUs) {
        val start = cuts.last()
        val next = syncTimesUs.lastOrNull { it > start && it - start <= maxUs } ?: return null
        cuts += next
    }
    return cuts.zipWithNext { start, end -> start until end } + listOf(cuts.last() until durationUs)
}

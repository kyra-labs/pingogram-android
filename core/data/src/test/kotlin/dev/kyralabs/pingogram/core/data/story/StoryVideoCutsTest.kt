package dev.kyralabs.pingogram.core.data.story

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Where a long video is cut for stories: at the latest key frame within a story's length of each part's start. */
class StoryVideoCutsTest {
    private val second = 1_000_000L
    private val max = 60 * second

    /** Key frames every [every] seconds up to [length]. */
    private fun keyFrames(
        every: Long,
        length: Long,
    ) = (0 until length step every).map { it * second }

    @Test
    fun `a video no longer than a story stays whole`() {
        assertEquals(listOf(0 until max), storyVideoCuts(keyFrames(2, 60), max, max))
    }

    @Test
    fun `cuts at the latest key frame within each part's length`() {
        val cuts = storyVideoCuts(keyFrames(every = 7, length = 150), 150 * second, max)

        // Key frames at 0, 7, … 56, 63, … 112, 119, …: 56 is the last within 60 s of 0, then 112 within 60 s of 56.
        assertEquals(listOf(0 until 56 * second, 56 * second until 112 * second, 112 * second until 150 * second), cuts)
    }

    @Test
    fun `every part is at most a story's length`() {
        val cuts = storyVideoCuts(keyFrames(every = 1, length = 600), 600 * second, max)!!

        assertEquals(10, cuts.size)
        cuts.forEach { assert(it.last + 1 - it.first <= max) }
    }

    @Test
    fun `cannot cut a video whose key frames lie further apart than a story's length`() {
        assertNull(storyVideoCuts(listOf(0L, 90 * second), 120 * second, max))
    }

    @Test
    fun `cannot cut when the last part from its key frame is too long`() {
        assertNull(storyVideoCuts(listOf(0L, 50 * second), 200 * second, max))
    }
}

package dev.kyralabs.pingogram.core.ui.image

import android.graphics.Canvas
import coil3.Image
import coil3.memory.MemoryCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [largestImageKey]: the media viewer's placeholder, the largest copy of a file already decoded, at any size. */
class LargestImageKeyTest {
    private val cache = MemoryCache.Builder().maxSizeBytes(MAX_BYTES).build()

    @Test
    fun `finds the largest size of the file, and no other file's`() {
        val bubble = cache.put(FILE_ID, width = 400, height = 300)
        cache.put(FILE_ID, width = 80, height = 60)
        cache.put(OTHER_FILE_ID, width = 2_000, height = 1_500)

        assertEquals(bubble, cache.largestImageKey(FILE_ID))
    }

    @Test
    fun `nothing while the file was never decoded`() {
        cache.put(OTHER_FILE_ID, width = 400, height = 300)

        assertNull(cache.largestImageKey(FILE_ID))
    }

    /** Caches an image of [fileId] at [width] × [height] under the key Coil gives it at that size. */
    private fun MemoryCache.put(
        fileId: Int,
        width: Int,
        height: Int,
    ): MemoryCache.Key {
        val key = MemoryCache.Key(TdlibFileImageKeyer.keyOf(fileId), mapOf("coil#size" to "${width}x$height"))
        set(key, MemoryCache.Value(FakeImage(width, height)))
        return key
    }

    private class FakeImage(
        override val width: Int,
        override val height: Int,
    ) : Image {
        override val size: Long get() = width.toLong() * height * BYTES_PER_PIXEL
        override val shareable: Boolean get() = true

        override fun draw(canvas: Canvas) = Unit
    }

    private companion object {
        const val FILE_ID = 7
        const val OTHER_FILE_ID = 8
        const val MAX_BYTES = 64L * 1024 * 1024
        const val BYTES_PER_PIXEL = 4
    }
}

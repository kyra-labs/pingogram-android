package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaWindowTest {
    /** A page of messages [ids] (any order), as the repository answers. */
    private fun page(
        ids: LongRange,
        reachedOldest: Boolean = false,
        reachedNewest: Boolean = false,
        total: Int? = 100,
    ) = ChatMediaPage(
        messages = ids.sortedDescending().map { message(it) }.toImmutableList(),
        filter = ChatMediaFilter.PhotosAndVideos,
        reachedOldest = reachedOldest,
        reachedNewest = reachedNewest,
        totalCount = total,
    )

    private val MediaWindow.ids get() = messages.map { it.id }

    @Test
    fun `the first page is the window, oldest first`() {
        val window = MediaWindow.of(page(48L..52L))

        assertEquals((48L..52L).toList(), window.ids)
        assertEquals(2, window.indexOf(50))
        assertEquals(-1, window.indexOf(47))
    }

    @Test
    fun `older and newer pages merge in by id, without duplicates`() {
        val window =
            MediaWindow
                .of(page(48L..52L))
                .withOlder(page(45L..48L))
                .withNewer(page(52L..55L))

        assertEquals((45L..55L).toList(), window.ids)
    }

    @Test
    fun `the newer copy of a message wins`() {
        val edited = message(50, MediaFixtures.photoContent(50, caption = "Edited"))
        val newer =
            ChatMediaPage(listOf(edited).toImmutableList(), ChatMediaFilter.PhotosAndVideos, false, false, null)

        val window = MediaWindow.of(page(48L..52L)).withNewer(newer)

        val content = window.messages[window.indexOf(50)].content as MessageContent.Photo
        assertEquals("Edited", content.caption.text)
    }

    @Test
    fun `ends only ever get reached`() {
        val window =
            MediaWindow
                .of(page(1L..5L, reachedOldest = true))
                .withOlder(page(LongRange.EMPTY))
                .withNewer(page(6L..7L))

        assertTrue(window.reachedOldest)
        assertFalse(window.reachedNewest)
        assertTrue(window.withNewer(page(LongRange.EMPTY, reachedNewest = true)).reachedNewest)
    }

    @Test
    fun `a page that brings nothing beyond what is loaded ends its side`() {
        val window = MediaWindow.of(page(48L..52L))

        assertTrue(window.withOlder(page(48L..50L)).reachedOldest)
        assertTrue(window.withNewer(page(50L..52L)).reachedNewest)
    }

    @Test
    fun `media the viewer cannot show is left out, and the next page starts past it`() {
        val noSizes = message(47, MediaFixtures.photoContent(47).copy(photo = Photo(persistentListOf(), null)))
        val older =
            ChatMediaPage(
                listOf(noSizes).toImmutableList(),
                ChatMediaFilter.PhotosAndVideos,
                reachedOldest = false,
                reachedNewest = false,
                totalCount = null,
            )

        val window = MediaWindow.of(page(48L..52L)).withOlder(older)

        assertEquals((48L..52L).toList(), window.ids)
        assertEquals(47, window.olderFrom)
        assertFalse(window.reachedOldest)
    }

    @Test
    fun `the position is unknown until TDLib tells one or an end is reached`() {
        assertNull(MediaWindow.of(page(48L..52L)).count())
    }

    @Test
    fun `TDLib's position of a loaded message places the window`() {
        // 50 is the 51st newest of 100: 50 newer, 5 of them loaded.
        val window = MediaWindow.of(page(46L..55L)).withPosition(50, positionFromNewest = 51)

        assertEquals(MediaCount(firstNumber = 46, total = 100), window.count())
        assertEquals(MediaPosition(50, 100), window.count()!!.positionOf(window.indexOf(50)))
    }

    @Test
    fun `newer pages loaded after the position keep it right`() {
        val window =
            MediaWindow
                .of(page(46L..55L))
                .withPosition(50, positionFromNewest = 51)
                .withNewer(page(56L..60L))
                .withOlder(page(40L..45L))

        assertEquals(MediaPosition(57, 100), window.count()!!.positionOf(window.indexOf(57)))
        assertEquals(MediaPosition(40, 100), window.count()!!.positionOf(0))
    }

    @Test
    fun `at the newest end positions count back from the total`() {
        val window = MediaWindow.of(page(96L..100L, reachedNewest = true))

        assertEquals(MediaCount(firstNumber = 96, total = 100), window.count())
    }

    @Test
    fun `at the oldest end positions count from it`() {
        val window = MediaWindow.of(page(1L..5L, reachedOldest = true, total = 100))

        assertEquals(MediaCount(firstNumber = 1, total = 100), window.count())
    }

    @Test
    fun `with both ends reached the total is what is loaded`() {
        val window = MediaWindow.of(page(1L..5L, reachedOldest = true, reachedNewest = true, total = 9))

        assertEquals(MediaCount(firstNumber = 1, total = 5), window.count())
    }

    @Test
    fun `an estimated total below what is loaded counts what is loaded`() {
        val window = MediaWindow.of(page(1L..5L, reachedOldest = true, total = 3))

        assertEquals(5, window.count()!!.total)
    }

    @Test
    fun `positions stay within one and the total`() {
        val count = MediaCount(firstNumber = -2, total = 10)

        assertEquals(MediaPosition(1, 10), count.positionOf(0))
        assertEquals(MediaPosition(10, 10), count.positionOf(40))
    }

    @Test
    fun `deleted messages leave, and the total drops by those it held`() {
        val window = MediaWindow.of(page(1L..5L, reachedOldest = true, total = 50)).without(setOf(2L, 4L, 99L))

        assertEquals(listOf(1L, 3L, 5L), window.ids)
        assertEquals(48, window.totalCount)
    }

    @Test
    fun `deleting nothing it holds changes nothing`() {
        val window = MediaWindow.of(page(1L..5L))

        assertSame(window, window.without(setOf(99L)))
    }
}

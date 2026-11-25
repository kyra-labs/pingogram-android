package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * [ChatItem.key]: made once with the entry, so the list, which reads every entry's key each time the items change,
 * gets the same object back instead of a new string or a boxed id; and the keys are what they were.
 */
class ChatItemKeysTest {
    private val today = UiText.Plain("Today")
    private val message = ChatPreviewData.bubble(MESSAGE_ID, "Hello", time = "9:41 AM")
    private val album = message.copy(album = MessageAlbum(ALBUM_ID, persistentListOf(1L, 2L), persistentListOf()))
    private val date = ChatItem.DateSeparator(EPOCH_DAY, today)
    private val sponsored = ChatItem.Sponsored(SponsoredPreviewData.text, today)

    @Test
    fun `every read of a key returns the same object`() {
        listOf(message, album, date, sponsored, ChatItem.UnreadDivider(today)).forEach { item ->
            assertSame(item.key, item.key, "$item")
        }
    }

    @Test
    fun `the keys are the message id, or a string for the album and the other entries`() {
        assertEquals(MESSAGE_ID, message.key)
        assertEquals("album:$ALBUM_ID", album.key)
        assertEquals("date:$EPOCH_DAY", date.key)
        assertEquals("sponsored:${SponsoredPreviewData.text.id}", sponsored.key)
        assertEquals(ChatItem.UNREAD_DIVIDER_KEY, ChatItem.UnreadDivider(today).key)
    }

    @Test
    fun `a copy keys itself anew, from what it holds`() {
        val moved = date.copy(epochDay = EPOCH_DAY + 1)

        assertEquals("date:${EPOCH_DAY + 1}", moved.key)
        assertNotSame(date.key, moved.key)
        // Equal entries stay equal: the key is no part of what they compare.
        assertEquals(date, date.copy())
    }

    private companion object {
        /** Outside the JVM's cache of boxed longs (-128..127), where each boxing would make a new object. */
        const val MESSAGE_ID = 1_048_576L
        const val ALBUM_ID = 77L
        const val EPOCH_DAY = 20_417L
    }
}

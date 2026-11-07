package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RichMediaTest {
    private fun message(
        id: Long,
        content: MessageContent,
    ) = Message(
        id = id,
        chatId = -100,
        sender = MessageSender.Chat(-100),
        date = 1_800_000_000,
        editDate = 0,
        isOutgoing = false,
        sendingState = null,
        isPinned = false,
        isChannelPost = true,
        containsUnreadMention = false,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = null,
        forwardInfo = null,
        interactionInfo = null,
        selfDestruct = null,
        content = content,
    )

    @Test
    fun `a rich message's media ids are negative, in the media's order, and lead back to the message`() {
        val message = 42L shl 20
        val ids = (0 until 5).map { richMediaId(message, it) }

        assertTrue(ids.all(::isRichMediaId))
        assertEquals(ids.sorted(), ids)
        assertTrue(ids.all { richMediaMessageId(it) == message })
        assertFalse(isRichMediaId(message))
    }

    @Test
    fun `two messages' media ids never meet`() {
        val first = 42L shl 20
        val next = 43L shl 20

        assertTrue(richMediaId(first, 0) > richMediaId(next, (1 shl 19)))
        assertEquals(next, richMediaMessageId(richMediaId(next, 0)))
    }

    @Test
    fun `a rich message's media become messages of their own, the message's sender and date kept`() {
        val photo = Photo(persistentListOf(), null)
        val caption = FormattedText("Home", persistentListOf())
        val rich =
            RichMessage(
                persistentListOf(
                    RichBlock.Title(caption),
                    RichBlock.Photo(0, photo, RichCaption(caption, FormattedText.Empty), "", hasSpoiler = true),
                ),
                isRtl = false,
                isFull = true,
            )
        val id = 5L shl 20
        val message = message(id, MessageContent.RichMessage(rich))

        val media = message.richMediaMessages().single()

        assertEquals(richMediaId(id, 0), media.id)
        assertEquals(message.date, media.date)
        assertEquals(MessageContent.Photo(photo, caption, true, false, false), media.content)
        assertEquals(emptyList<Message>(), message(id, MessageContent.Text(caption, null)).richMediaMessages())
    }
}

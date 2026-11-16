package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.PaidMediaItem
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.domain.model.richMediaMessages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A paid post: its previews while not paid for, its photos and videos once paid for, and what the viewer pages. */
class PaidMediaMappersTest {
    @Test
    fun `a post not paid for carries its price and the previews of its media`() {
        val content =
            TdApi
                .MessagePaidMedia(
                    25,
                    arrayOf(
                        TdApi.PaidMediaPreview(1280, 960, 0, TdApiFixtures.minithumbnail()),
                        TdApi.PaidMediaPreview(1920, 1080, 12, null),
                    ),
                    TdApiFixtures.formattedText("Golden hour"),
                    false,
                ).map() as MessageContent.PaidMedia

        assertEquals(25, content.starCount)
        assertEquals("Golden hour", content.caption.text)
        assertFalse(content.isUnlocked)
        assertEquals(listOf(false, true), content.media.map { (it as PaidMediaItem.Preview).isVideo })
        assertEquals(1280 to 960, (content.media[0] as PaidMediaItem.Preview).let { it.width to it.height })
        assertTrue(
            TdApiFixtures
                .message(content = messagePaidMedia())
                .toMessage()
                .richMediaMessages()
                .isEmpty(),
        )
    }

    @Test
    fun `a post paid for carries its photos and videos, which the viewer pages as messages of their own`() {
        val message =
            TdApiFixtures
                .message(
                    id = 1L shl 20,
                    content =
                        TdApi.MessagePaidMedia(
                            25,
                            arrayOf(
                                TdApi.PaidMediaPhoto(TdApiFixtures.photo(), null),
                                TdApi.PaidMediaVideo(TdApiFixtures.video(), TdApiFixtures.photo(), 7),
                                TdApi.PaidMediaUnsupported(),
                            ),
                            TdApiFixtures.formattedText("Thanks"),
                            true,
                        ),
                ).toMessage()
        val content = message.content as MessageContent.PaidMedia

        assertTrue(content.isUnlocked)
        assertTrue(content.media[0] is PaidMediaItem.Photo)
        assertEquals(7, (content.media[1] as PaidMediaItem.Video).startTimestamp)
        assertEquals(PaidMediaItem.Unsupported, content.media[2])
        val media = message.richMediaMessages()
        assertEquals(listOf(richMediaId(message.id, 0), richMediaId(message.id, 1)), media.map { it.id })
        assertEquals(
            listOf("Thanks", "Thanks"),
            media.map {
                (it.content as? MessageContent.Photo)?.caption?.text
                    ?: (it.content as MessageContent.Video).caption.text
            },
        )
        assertTrue((media[1].content as MessageContent.Video).showCaptionAboveMedia)
    }

    private fun messagePaidMedia() =
        TdApi.MessagePaidMedia(
            25,
            arrayOf(TdApi.PaidMediaPreview(1280, 960, 0, null)),
            TdApiFixtures.formattedText(""),
            false,
        )

    private fun TdApi.MessageContent.map() = TdApiFixtures.message(content = this).toMessage().content
}

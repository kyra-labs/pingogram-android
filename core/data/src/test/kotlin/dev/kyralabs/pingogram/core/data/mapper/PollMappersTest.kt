package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PollMedia
import dev.kyralabs.pingogram.core.testing.PollFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What polls carry since 2026: a photo, answers with links and authors, a closing time, and adding answers. */
class PollMappersTest {
    @Test
    fun `an answer keeps its id, its link titled by its preview, and who added it`() {
        val option =
            PollFixtures.pollOption("Klara", 0, 0).apply {
                id = "b"
                media = TdApi.PollMediaLink("https://example.org/klara", null)
                author = TdApi.MessageSenderUser(42)
            }

        val mapped =
            PollFixtures
                .poll(options = arrayOf(option))
                .toPoll()
                .options
                .single()

        assertEquals("b", mapped.id)
        assertEquals(PollMedia.Link("https://example.org/klara", ""), mapped.media)
        assertEquals(MessageSender.User(42), mapped.author)
    }

    @Test
    fun `a sticker answer is media Pingogram does not show`() {
        val sticker = TdApi.PollMediaSticker(TdApi.Sticker())

        assertEquals(PollMedia.Other, sticker.toPollMedia())
    }

    @Test
    fun `a poll keeps when it closes`() {
        val poll =
            PollFixtures.poll().apply {
                closeDate = 1_796_580_000
                openPeriod = 3_600
            }

        val mapped = poll.toPoll()

        assertEquals(1_796_580_000L to 3_600, mapped.closeDate to mapped.openPeriod)
    }

    @Test
    fun `a poll message keeps its photo and whether answers may be added`() {
        val message =
            PollFixtures.messagePoll(PollFixtures.poll(), TdApiFixtures.formattedText("")).apply {
                media = TdApi.PollMediaPhoto(TdApiFixtures.photo(), null)
                canAddOption = true
            }

        val content = TdApiFixtures.message(content = message).toMessage().content as MessageContent.Poll

        assertTrue(content.media is PollMedia.Photo)
        assertTrue(content.canAddOption)
    }
}

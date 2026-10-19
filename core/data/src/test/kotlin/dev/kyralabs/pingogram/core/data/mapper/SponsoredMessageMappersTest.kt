package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AdvertisementSponsor
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.testing.SponsoredFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** How sponsored messages map: every field, and what TDLib may leave out or add later; never a message dropped. */
class SponsoredMessageMappersTest {
    private val photo =
        Photo(
            persistentListOf(
                PhotoSize("m", 320, 240, FileRef(10, 1_024)),
                PhotoSize("x", 800, 600, FileRef(11, 1_024)),
            ),
            Minithumbnail(40, 30, byteArrayOf(1, 2, 3)),
        )

    @Test
    fun `a text ad keeps its entities, the sponsor and its texts`() {
        val message =
            SponsoredFixtures.sponsoredMessage(
                id = 42,
                content =
                    TdApiFixtures.messageText(
                        "Fly with us",
                        arrayOf(TdApiFixtures.textEntity(0, 3, TdApi.TextEntityTypeBold())),
                    ),
                title = "Acme",
                buttonText = "Book",
                isRecommended = true,
                canBeReported = false,
                sponsor =
                    SponsoredFixtures.advertisementSponsor(
                        "https://acme.example",
                        TdApiFixtures.photo(),
                        "Acme Ltd",
                    ),
                additionalInfo = "Paid for by Acme",
            )

        assertEquals(
            SponsoredMessage(
                id = 42,
                isRecommended = true,
                canBeReported = false,
                title = "Acme",
                content =
                    MessageContent.Text(
                        FormattedText("Fly with us", persistentListOf(TextEntity(0, 3, TextEntityType.Bold))),
                        linkPreview = null,
                    ),
                sponsor = AdvertisementSponsor("https://acme.example", photo, "Acme Ltd"),
                buttonText = "Book",
                accentColorId = 0,
                additionalInfo = "Paid for by Acme",
            ),
            message.toSponsoredMessage(),
        )
    }

    @Test
    fun `a photo ad keeps its caption as the text`() {
        val message =
            SponsoredFixtures.sponsoredMessage(
                content = TdApiFixtures.messagePhoto(caption = TdApiFixtures.formattedText("See the view")),
            )

        val content = message.toSponsoredMessage().content as MessageContent.Photo

        assertEquals("See the view", content.caption.text)
        assertEquals(photo, content.photo)
    }

    @Test
    fun `missing texts, sponsor and photo become empty`() {
        val message =
            TdApi.SponsoredMessage(7, false, true, TdApiFixtures.messageText("Hi"), null, null, null, 2, 0L, null)

        val mapped = message.toSponsoredMessage()

        assertEquals(AdvertisementSponsor(url = "", photo = null, info = ""), mapped.sponsor)
        assertEquals(listOf("", "", ""), listOf(mapped.title, mapped.buttonText, mapped.additionalInfo))
        assertEquals(2, mapped.accentColorId)
    }

    @Test
    fun `content td_api does not allow for ads is unsupported, and the ad stays`() {
        val messages =
            SponsoredFixtures.sponsoredMessages(
                SponsoredFixtures.sponsoredMessage(id = 1, content = TdApi.MessageDice(null, null, "🎲", 3, 0)),
                SponsoredFixtures.sponsoredMessage(id = 2),
                messagesBetween = -1,
            )

        val mapped = messages.toSponsoredMessages()

        assertEquals(listOf(1L, 2L), mapped.messages.map { it.id })
        assertEquals(MessageContent.Unsupported, mapped.messages.first().content)
        assertEquals(0, mapped.messagesBetween)
    }
}

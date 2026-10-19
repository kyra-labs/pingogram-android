package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SponsoredMessageModelTest {
    @Test
    fun `report option ids with the same bytes are equal`() {
        val id = ReportOptionId(byteArrayOf(4, 2))

        assertEquals(ReportOptionId(byteArrayOf(4, 2)), id)
        assertEquals(ReportOptionId(byteArrayOf(4, 2)).hashCode(), id.hashCode())
        assertNotEquals(ReportOptionId(byteArrayOf(4, 3)), id)
        assertEquals(ReportOptionId(ByteArray(0)), ReportOptionId.Initial)
    }

    @Test
    fun `toString leaves out the title, the texts and the link`() {
        val printed =
            SponsoredMessage(
                id = 7,
                isRecommended = false,
                canBeReported = true,
                title = "Acme Rockets",
                content = MessageContent.Text(FormattedText("Fly to the moon", persistentListOf()), null),
                sponsor =
                    AdvertisementSponsor(
                        "https://acme.example/ads",
                        photo = null,
                        info = "Acme Ltd, Springfield",
                    ),
                buttonText = "Book now",
                accentColorId = 3,
                additionalInfo = "Paid for by Acme",
            ).toString()

        val leaked = listOf("Acme", "moon", "acme.example", "Springfield", "Book").filter { it in printed }
        assertEquals(emptyList<String>(), leaked, printed)
    }
}

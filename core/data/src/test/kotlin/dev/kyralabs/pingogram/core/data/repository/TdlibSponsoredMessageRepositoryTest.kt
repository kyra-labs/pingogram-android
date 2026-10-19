package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.SponsoredFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The requests [TdlibSponsoredMessageRepository] sends for the sponsored messages of a channel or bot
 * (`REQUIREMENTS.md` §2) and how it maps TDLib's answers: loading them, opening one, and reporting one.
 */
class TdlibSponsoredMessageRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSponsoredMessageRepository(client)

    @Test
    fun `asks for the chat's sponsored messages and maps them`() =
        runTest {
            val answer =
                SponsoredFixtures.sponsoredMessages(
                    SponsoredFixtures.sponsoredMessage(id = 10, title = "Acme"),
                    SponsoredFixtures.sponsoredMessage(
                        id = 11,
                        content = TdApiFixtures.messagePhoto(caption = TdApiFixtures.formattedText("A view")),
                        sponsor = SponsoredFixtures.advertisementSponsor(info = "Acme Ltd"),
                    ),
                    messagesBetween = 5,
                )
            client.on(TdApi.GetChatSponsoredMessages::class, TdResult.Success(answer))

            val result = (repository.sponsoredMessages(CHAT) as Outcome.Success).value

            assertEquals(CHAT, client.sentOf<TdApi.GetChatSponsoredMessages>().single().chatId)
            assertEquals(listOf(10L, 11L), result.messages.map { it.id })
            assertEquals("Acme", result.messages.first().title)
            assertEquals("A view", (result.messages.last().content as MessageContent.Photo).caption.text)
            assertEquals(
                "Acme Ltd",
                result.messages
                    .last()
                    .sponsor.info,
            )
            assertEquals(5, result.messagesBetween)
        }

    @Test
    fun `no sponsored messages is none`() =
        runTest {
            client.on(TdApi.GetChatSponsoredMessages::class, TdResult.Success(SponsoredFixtures.sponsoredMessages()))

            assertEquals(Outcome.Success(SponsoredMessages.None), repository.sponsoredMessages(CHAT))
        }

    @Test
    fun `a flood wait fails with FloodWait`() =
        runTest {
            val floodWait = TdApiFixtures.floodWait(seconds = 12)
            client.on(
                TdApi.GetChatSponsoredMessages::class,
                TdResult.Failure(TdError(floodWait.code, floodWait.message)),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(12)), repository.sponsoredMessages(CHAT))
        }

    @Test
    fun `a click sends the message and whether its media was clicked`() =
        runTest {
            client.on(TdApi.ClickChatSponsoredMessage::class, TdResult.Success(TdApi.Ok()))

            val result = repository.click(CHAT, MESSAGE, isMediaClick = true)

            val request = client.sentOf<TdApi.ClickChatSponsoredMessage>().single()
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(listOf(CHAT, MESSAGE), listOf(request.chatId, request.messageId))
            assertEquals(listOf(true, false), listOf(request.isMediaClick, request.fromFullscreen))
        }

    @Test
    fun `the first report sends an empty option, a chosen option its id`() =
        runTest {
            client.on(TdApi.ReportChatSponsoredMessage::class, TdResult.Success(TdApi.ReportSponsoredResultOk()))

            repository.report(CHAT, MESSAGE)
            repository.report(CHAT, MESSAGE, ReportOptionId(byteArrayOf(7, 8)))

            val requests = client.sentOf<TdApi.ReportChatSponsoredMessage>()
            assertEquals(listOf(CHAT, CHAT), requests.map { it.chatId })
            assertEquals(listOf(MESSAGE, MESSAGE), requests.map { it.messageId })
            assertArrayEquals(ByteArray(0), requests[0].optionId)
            assertArrayEquals(byteArrayOf(7, 8), requests[1].optionId)
        }

    @Test
    fun `each report result maps`() =
        runTest {
            val options =
                arrayOf(
                    SponsoredFixtures.reportOption(byteArrayOf(1), "Spam"),
                    SponsoredFixtures.reportOption(byteArrayOf(2), "Scam"),
                )
            val answers =
                listOf(
                    TdApi.ReportSponsoredResultOk() to SponsoredReportResult.Reported,
                    TdApi.ReportSponsoredResultAdsHidden() to SponsoredReportResult.AdsHidden,
                    TdApi.ReportSponsoredResultPremiumRequired() to SponsoredReportResult.PremiumRequired,
                    TdApi.ReportSponsoredResultFailed() to SponsoredReportResult.Failed,
                    TdApi.ReportSponsoredResultOptionRequired("Why?", options) to
                        SponsoredReportResult.OptionRequired(
                            "Why?",
                            persistentListOf(
                                ReportOption(ReportOptionId(byteArrayOf(1)), "Spam"),
                                ReportOption(ReportOptionId(byteArrayOf(2)), "Scam"),
                            ),
                        ),
                )

            for ((answer, expected) in answers) {
                client.on(TdApi.ReportChatSponsoredMessage::class, TdResult.Success(answer))

                assertEquals(Outcome.Success(expected), repository.report(CHAT, MESSAGE))
            }
        }

    private companion object {
        const val CHAT = -1_000_000_000_200L
        const val MESSAGE = 1L shl 30
    }
}

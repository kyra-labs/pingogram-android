package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParameters
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Giveaways, their winners and what TDLib knows of them for the user. */
class GiveawayMappersTest {
    @Test
    fun `a giveaway carries how it is run and its prize`() {
        val content =
            TdApi
                .MessageGiveaway(
                    TdApi.GiveawayParameters(
                        -100L,
                        longArrayOf(-200L),
                        1_764_612_000,
                        true,
                        true,
                        arrayOf("GB"),
                        "A mug",
                    ),
                    3,
                    TdApi.GiveawayPrizePremium(3),
                    null,
                ).map()

        assertEquals(
            MessageContent.Giveaway(
                parameters =
                    GiveawayParameters(
                        boostedChatId = -100L,
                        additionalChatIds = persistentListOf(-200L),
                        winnersSelectionDate = 1_764_612_000L,
                        onlyNewMembers = true,
                        hasPublicWinners = true,
                        countryCodes = persistentListOf("GB"),
                        prizeDescription = "A mug",
                    ),
                winnerCount = 3,
                prize = GiveawayPrize.Premium(3),
                sticker = null,
            ),
            content,
        )
        assertEquals(listOf(-100L, -200L), (content as MessageContent.Giveaway).parameters.chatIds)
    }

    @Test
    fun `the winners carry the prize, the winners and what nobody took`() {
        val content =
            TdApi
                .MessageGiveawayWinners(
                    -100L,
                    5L,
                    1,
                    1_764_612_000,
                    false,
                    false,
                    TdApi.GiveawayPrizeStars(500),
                    null,
                    3,
                    longArrayOf(1, 2, 3),
                    1,
                ).map()

        assertEquals(
            MessageContent.GiveawayWinners(
                boostedChatId = -100L,
                giveawayMessageId = 5L,
                additionalChatCount = 1,
                actualWinnersSelectionDate = 1_764_612_000L,
                onlyNewMembers = false,
                wasRefunded = false,
                prize = GiveawayPrize.Stars(500),
                prizeDescription = "",
                winnerCount = 3,
                winnerUserIds = persistentListOf(1L, 2L, 3L),
                unclaimedPrizeCount = 1,
            ),
            content,
        )
    }

    @Test
    fun `a prize of a kind TDLib adds later keeps the message unsupported`() {
        val prize =
            object : TdApi.GiveawayPrize() {
                override fun getConstructor() = 0
            }

        val giveaway =
            TdApi.MessageGiveaway(
                TdApi.GiveawayParameters(-100L, longArrayOf(), 1, false, false, arrayOf(), ""),
                1,
                prize,
                null,
            )
        val winners = TdApi.MessageGiveawayWinners(-100L, 5L, 0, 1, false, false, prize, "", 1, longArrayOf(), 0)

        assertEquals(
            listOf(MessageContent.Unsupported, MessageContent.Unsupported),
            listOf(giveaway.map(), winners.map()),
        )
    }

    @Test
    fun `the info says how the user stands while it runs, and what came of it once over`() {
        val ongoing =
            TdApi.GiveawayInfoOngoing(
                1_764_000_000,
                TdApi.GiveawayParticipantStatusAlreadyWasMember(1_700_000_000),
                false,
            )
        val completed = TdApi.GiveawayInfoCompleted(1_764_000_000, 1_764_612_000, false, true, 3, 2, "ABC", 0)

        assertEquals(
            GiveawayInfo.Ongoing(1_764_000_000L, GiveawayParticipantStatus.AlreadyWasMember(1_700_000_000L), false),
            ongoing.toGiveawayInfo(),
        )
        assertEquals(
            GiveawayInfo.Completed(
                1_764_000_000L,
                1_764_612_000L,
                wasRefunded = false,
                isWinner = true,
                winnerCount = 3,
                activationCount = 2,
                giftCode = "ABC",
                wonStarCount = 0,
            ),
            completed.toGiveawayInfo(),
        )
    }

    @Test
    fun `every participant status is kept`() {
        val statuses =
            listOf(
                TdApi.GiveawayParticipantStatusEligible() to GiveawayParticipantStatus.Eligible,
                TdApi.GiveawayParticipantStatusParticipating() to GiveawayParticipantStatus.Participating,
                TdApi.GiveawayParticipantStatusAdministrator(-100L) to GiveawayParticipantStatus.Administrator(-100L),
                TdApi.GiveawayParticipantStatusDisallowedCountry("US") to
                    GiveawayParticipantStatus.DisallowedCountry("US"),
            )

        assertEquals(statuses.map { it.second }, statuses.map { it.first.toParticipantStatus() })
    }

    private fun TdApi.MessageContent.map() = TdApiFixtures.message(content = this).toMessage().content
}

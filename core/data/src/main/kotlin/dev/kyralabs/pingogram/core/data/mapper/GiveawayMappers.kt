package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParameters
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

// Giveaways (td_api.tl messageGiveaway, messageGiveawayWinners, giveawayInfo); dates are Unix time in seconds.

/** A giveaway a channel runs; null for a prize of a kind a newer TDLib added, which stays unsupported. */
internal fun TdApi.MessageGiveaway.toGiveaway(): MessageContent.Giveaway? =
    MessageContent.Giveaway(
        parameters = parameters.toGiveawayParameters(),
        winnerCount = winnerCount,
        prize = prize.toGiveawayPrize() ?: return null,
        sticker = sticker?.toSticker(),
    )

/** The winners of a giveaway; null for a prize of a kind a newer TDLib added. */
internal fun TdApi.MessageGiveawayWinners.toGiveawayWinners(): MessageContent.GiveawayWinners? =
    MessageContent.GiveawayWinners(
        boostedChatId = boostedChatId,
        giveawayMessageId = giveawayMessageId,
        additionalChatCount = additionalChatCount,
        actualWinnersSelectionDate = actualWinnersSelectionDate.toLong(),
        onlyNewMembers = onlyNewMembers,
        wasRefunded = wasRefunded,
        prize = prize.toGiveawayPrize() ?: return null,
        prizeDescription = prizeDescription.orEmpty(),
        winnerCount = winnerCount,
        winnerUserIds = winnerUserIds.toList().toImmutableList(),
        unclaimedPrizeCount = unclaimedPrizeCount,
    )

internal fun TdApi.GiveawayParameters.toGiveawayParameters(): GiveawayParameters =
    GiveawayParameters(
        boostedChatId = boostedChatId,
        additionalChatIds = additionalChatIds.toList().toImmutableList(),
        winnersSelectionDate = winnersSelectionDate.toLong(),
        onlyNewMembers = onlyNewMembers,
        hasPublicWinners = hasPublicWinners,
        countryCodes = countryCodes.toList().toImmutableList(),
        prizeDescription = prizeDescription.orEmpty(),
    )

/** Telegram Premium or Telegram Stars; null for a kind a newer TDLib added. */
internal fun TdApi.GiveawayPrize.toGiveawayPrize(): GiveawayPrize? =
    when (this) {
        is TdApi.GiveawayPrizePremium -> GiveawayPrize.Premium(monthCount)
        is TdApi.GiveawayPrizeStars -> GiveawayPrize.Stars(starCount)
        else -> null
    }

/** What TDLib knows of a giveaway for the user; null for a kind a newer TDLib added. */
internal fun TdApi.GiveawayInfo.toGiveawayInfo(): GiveawayInfo? =
    when (this) {
        is TdApi.GiveawayInfoOngoing -> {
            GiveawayInfo.Ongoing(creationDate.toLong(), status.toParticipantStatus(), isEnded)
        }

        is TdApi.GiveawayInfoCompleted -> {
            GiveawayInfo.Completed(
                creationDate = creationDate.toLong(),
                actualWinnersSelectionDate = actualWinnersSelectionDate.toLong(),
                wasRefunded = wasRefunded,
                isWinner = isWinner,
                winnerCount = winnerCount,
                activationCount = activationCount,
                giftCode = giftCode.orEmpty(),
                wonStarCount = wonStarCount,
            )
        }

        else -> {
            null
        }
    }

/** How the user stands in a giveaway; a status a newer TDLib added reads as eligible, which promises nothing. */
internal fun TdApi.GiveawayParticipantStatus.toParticipantStatus(): GiveawayParticipantStatus =
    when (this) {
        is TdApi.GiveawayParticipantStatusParticipating -> {
            GiveawayParticipantStatus.Participating
        }

        is TdApi.GiveawayParticipantStatusAlreadyWasMember -> {
            GiveawayParticipantStatus.AlreadyWasMember(joinedChatDate.toLong())
        }

        is TdApi.GiveawayParticipantStatusAdministrator -> {
            GiveawayParticipantStatus.Administrator(chatId)
        }

        is TdApi.GiveawayParticipantStatusDisallowedCountry -> {
            GiveawayParticipantStatus.DisallowedCountry(userCountryCode.orEmpty())
        }

        else -> {
            GiveawayParticipantStatus.Eligible
        }
    }

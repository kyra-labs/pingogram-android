package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

// Giveaways (td_api.tl messageGiveaway, messageGiveawayWinners, giveawayInfo): a channel raffles Telegram Premium
// subscriptions or Telegram Stars among its subscribers. Pingogram shows them and asks TDLib how the user takes part;
// it never creates one and never buys anything (Telegram's API terms, REQUIREMENTS.md §1.2).

/** What a giveaway's winners get. */
sealed interface GiveawayPrize {
    /** Telegram Premium for [monthCount] months, for each winner. */
    data class Premium(
        val monthCount: Int,
    ) : GiveawayPrize

    /** [starCount] Telegram Stars, shared among the winners. */
    data class Stars(
        val starCount: Long,
    ) : GiveawayPrize
}

/** How a giveaway is run (td_api.tl `giveawayParameters`). */
data class GiveawayParameters(
    /** The channel or supergroup that was boosted to create it. */
    val boostedChatId: Long,
    /** Other chats whose subscribers take part too. */
    val additionalChatIds: ImmutableList<Long>,
    /** When the winners are picked, in Unix time. */
    val winnersSelectionDate: Long,
    /** Only members who joined after the giveaway started take part. */
    val onlyNewMembers: Boolean,
    /** The winners are named in a [MessageContent.GiveawayWinners] message once picked. */
    val hasPublicWinners: Boolean,
    /** ISO 3166-1 alpha-2 codes of the countries whose users take part; empty for every country. */
    val countryCodes: ImmutableList<String>,
    /** What the channel says it gives away, beyond the prize; may be empty. */
    val prizeDescription: String,
) {
    /** The boosted chat, then the other chats taking part. */
    val chatIds: List<Long> get() = listOf(boostedChatId) + additionalChatIds
}

/** How the user stands in a giveaway (td_api.tl `GiveawayParticipantStatus`). */
sealed interface GiveawayParticipantStatus {
    /** The user may take part by joining the chats. */
    data object Eligible : GiveawayParticipantStatus

    data object Participating : GiveawayParticipantStatus

    /** The user joined chat [chatId] before the giveaway started, which it leaves out, on [joinedChatDate]. */
    data class AlreadyWasMember(
        val joinedChatDate: Long,
    ) : GiveawayParticipantStatus

    /** Administrators of chat [chatId] do not take part. */
    data class Administrator(
        val chatId: Long,
    ) : GiveawayParticipantStatus

    /** The user's phone number is of [userCountryCode], which the giveaway leaves out. */
    data class DisallowedCountry(
        val userCountryCode: String,
    ) : GiveawayParticipantStatus
}

/** What TDLib knows of a giveaway for the user (td_api.tl `GiveawayInfo`), asked for with `getGiveawayInfo`. */
sealed interface GiveawayInfo {
    /** The winners are not picked yet (or Telegram has not picked them though the date passed, [isEnded]). */
    data class Ongoing(
        val creationDate: Long,
        val status: GiveawayParticipantStatus,
        val isEnded: Boolean,
    ) : GiveawayInfo

    /** The winners were picked on [actualWinnersSelectionDate]. */
    data class Completed(
        val creationDate: Long,
        val actualWinnersSelectionDate: Long,
        /** The channel got its boosts back and nobody won, as it did not keep its part. */
        val wasRefunded: Boolean,
        val isWinner: Boolean,
        val winnerCount: Int,
        /** How many winners took their prize already. */
        val activationCount: Int,
        /** The user's gift code for Telegram Premium, if they won it; empty otherwise. Shown, never applied. */
        val giftCode: String,
        /** The Telegram Stars the user won, if any. */
        val wonStarCount: Long,
    ) : GiveawayInfo
}

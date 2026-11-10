package dev.kyralabs.pingogram.feature.chat.ui.content

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GiveawayParameters
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.PaidMediaItem
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Sample paid posts and giveaways for the previews and screenshot tests (the owner's boards 2 and 3): a post of three
 * photos for 25 Telegram Stars, locked or paid for; a giveaway of Telegram Premium among the subscribers of two
 * channels; and the message naming its three winners.
 */
internal object PaidPostSamples {
    const val CHANNEL = -1_000_000_000_100L
    const val OTHER_CHANNEL = -1_000_000_000_200L
    const val WINNER_RAVI = 71L
    const val WINNER_MEERA = 72L
    const val WINNER_JONAS = 73L

    /** Three photos (one portrait) for 25 Telegram Stars, as previews while [unlocked] is false, else as photos. */
    fun paidMedia(
        unlocked: Boolean,
        minithumbnail: Minithumbnail? = null,
        caption: String = "Golden hour in Hampi: the full set of 3, uncropped.",
    ): MessageContent.PaidMedia {
        val shapes = listOf(LANDSCAPE to PORTRAIT, PORTRAIT to LANDSCAPE, LANDSCAPE to PORTRAIT)
        val media =
            shapes.mapIndexed { index, (width, height) ->
                if (unlocked) {
                    PaidMediaItem.Photo(
                        ContentPreviewData
                            .photo(
                                minithumbnail,
                                width,
                                height,
                                firstFileId = FIRST_FILE_ID + index * 3,
                            ).photo,
                    )
                } else {
                    PaidMediaItem.Preview(width, height, duration = 0, minithumbnail = minithumbnail)
                }
            }
        return MessageContent.PaidMedia(
            starCount = STARS,
            media = media.toImmutableList(),
            caption = FormattedText(caption, persistentListOf()),
            showCaptionAboveMedia = false,
        )
    }

    /** Three subscriptions to Telegram Premium for three months, among two channels' subscribers, picked in December. */
    fun giveaway(
        onlyNewMembers: Boolean = false,
        countryCodes: List<String> = emptyList(),
        prizeDescription: String = "",
    ) = MessageContent.Giveaway(
        parameters =
            GiveawayParameters(
                boostedChatId = CHANNEL,
                additionalChatIds = persistentListOf(OTHER_CHANNEL),
                winnersSelectionDate = DRAW_DATE,
                onlyNewMembers = onlyNewMembers,
                hasPublicWinners = true,
                countryCodes = countryCodes.toImmutableList(),
                prizeDescription = prizeDescription,
            ),
        winnerCount = WINNERS,
        prize = GiveawayPrize.Premium(MONTHS),
        sticker = null,
    )

    /** The same giveaway's winners: three people, one prize not claimed. */
    fun giveawayWinners(
        winnerIds: List<Long> = listOf(WINNER_RAVI, WINNER_MEERA, WINNER_JONAS),
        wasRefunded: Boolean = false,
        unclaimed: Int = 0,
    ) = MessageContent.GiveawayWinners(
        boostedChatId = CHANNEL,
        giveawayMessageId = 1,
        additionalChatCount = 1,
        actualWinnersSelectionDate = DRAW_DATE,
        onlyNewMembers = false,
        wasRefunded = wasRefunded,
        prize = GiveawayPrize.Premium(MONTHS),
        prizeDescription = "",
        winnerCount = WINNERS,
        winnerUserIds = winnerIds.toImmutableList(),
        unclaimedPrizeCount = unclaimed,
    )

    /** The two channels named and the three winners named, as the mapper finds them. */
    fun giveawayUi() =
        GiveawayUi(
            channels =
                persistentListOf(
                    SenderAvatar(CHANNEL, UiText.Plain("Android Devs Weekly"), photo = null, placeholderIcon = null),
                    SenderAvatar(OTHER_CHANNEL, UiText.Plain("Kotlin Daily"), photo = null, placeholderIcon = null),
                ),
            winners =
                persistentListOf(
                    SenderAvatar(WINNER_RAVI, UiText.Plain("Ravi K"), photo = null, placeholderIcon = null),
                    SenderAvatar(WINNER_MEERA, UiText.Plain("Meera"), photo = null, placeholderIcon = null),
                    SenderAvatar(WINNER_JONAS, UiText.Plain("Jonas"), photo = null, placeholderIcon = null),
                ),
        )

    const val STARS = 25L
    const val WINNERS = 3
    const val MONTHS = 3

    /** 2026-12-01 18:00 UTC. */
    const val DRAW_DATE = 1_796_580_000L
    private const val LANDSCAPE = 1280
    private const val PORTRAIT = 960
    private const val FIRST_FILE_ID = 700
}

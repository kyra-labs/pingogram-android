package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.content.PaidPostSamples
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayInfoSheet
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayInfoState
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayUi
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today

/**
 * Giveaways for previews and screenshot tests (`REQUIREMENTS.md` FR-C4, the owner's board 3): a channel's conversation
 * with a running giveaway (the prize card, two participating channels, the draw's date) over the message naming its
 * three winners, and the details sheet loaded while it runs, loaded once over, loading and failed.
 */
internal object GiveawayPreviewData {
    private val header =
        ChatHeader(
            chatId = PaidPostSamples.CHANNEL,
            title = UiText.Plain("Android Devs Weekly"),
            photo = null,
            placeholderIcon = null,
            subtitle =
                ChatSubtitle.Status(
                    UiText.plural(R.plurals.chat_subtitle_subscribers, SUBSCRIBERS, SUBSCRIBERS),
                ),
        )

    /** The winners message over the running giveaway. */
    val posts: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        post(3, PaidPostSamples.giveawayWinners(unclaimed = 1), "10:41 AM"),
                        post(2, PaidPostSamples.giveaway(countryCodes = listOf("IN", "DE")), "9:20 AM"),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** The details of a giveaway the user takes part in, while it runs. */
    val infoOngoing =
        GiveawayInfoSheet(
            messageId = 2,
            prize = GiveawayPrize.Premium(PaidPostSamples.MONTHS),
            winnerCount = PaidPostSamples.WINNERS,
            state =
                GiveawayInfoState.Loaded(
                    GiveawayInfo.Ongoing(CREATED, GiveawayParticipantStatus.Participating, isEnded = false),
                ),
        )

    /** The details once the winners were picked: the user won, two of three took their prize. */
    val infoCompleted =
        infoOngoing.copy(
            messageId = 3,
            state =
                GiveawayInfoState.Loaded(
                    GiveawayInfo.Completed(
                        creationDate = CREATED,
                        actualWinnersSelectionDate = PaidPostSamples.DRAW_DATE,
                        wasRefunded = false,
                        isWinner = true,
                        winnerCount = PaidPostSamples.WINNERS,
                        activationCount = 2,
                        giftCode = "GIFT-CODE",
                        wonStarCount = 0,
                    ),
                ),
        )

    val infoLoading = infoOngoing.copy(state = GiveawayInfoState.Loading)

    val infoFailed = infoOngoing.copy(state = GiveawayInfoState.Failed(DomainError.Network))

    private fun post(
        id: Long,
        content: MessageContent,
        time: String,
    ): ChatItem.Bubble =
        bubble(
            id = id,
            text = "",
            time = time,
            sender = UiText.Plain("Android Devs Weekly"),
            senderId = PaidPostSamples.CHANNEL,
            position = GroupPosition.Single,
            content = content,
        ).copy(giveaway = PaidPostSamples.giveawayUi(), isChannelPost = true, views = VIEWS)

    private const val SUBSCRIBERS = 48_210
    private const val VIEWS = 22_000
    private const val CREATED = 1_795_975_200L
}

/** The giveaway UI of previews: both channels and every winner named. */
internal val previewGiveawayUi: GiveawayUi get() = PaidPostSamples.giveawayUi()

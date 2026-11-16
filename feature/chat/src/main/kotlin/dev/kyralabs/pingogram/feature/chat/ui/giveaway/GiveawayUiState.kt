package dev.kyralabs.pingogram.feature.chat.ui.giveaway

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ItemInputs
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.memberIdsOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a giveaway's bubble shows besides its content (`docs/DESIGN_SYSTEM.md` §6 "Giveaways"): the participating
 * [channels] (the boosted chat first) as the chats TDLib announced them, and the [winners] named in a winners message,
 * as the users TDLib announced them; a chat or user it has not announced yet is named generically.
 */
@Immutable
data class GiveawayUi(
    val channels: ImmutableList<SenderAvatar> = persistentListOf(),
    val winners: ImmutableList<SenderAvatar> = persistentListOf(),
)

/**
 * The details sheet of the giveaway of message [messageId] ("Learn more"): its [prize] for [winnerCount] winners, and
 * what TDLib knows of it for the user ([state]).
 */
@Immutable
data class GiveawayInfoSheet(
    val messageId: Long,
    val prize: GiveawayPrize,
    val winnerCount: Int,
    val state: GiveawayInfoState = GiveawayInfoState.Loading,
)

/** What the details sheet shows: a loading indicator, the info, or why it failed with "Try again". */
sealed interface GiveawayInfoState {
    data object Loading : GiveawayInfoState

    /** [chatTitle] names the chat whose administrators do not take part, where that is why the user does not. */
    data class Loaded(
        val info: GiveawayInfo,
        val chatTitle: String? = null,
    ) : GiveawayInfoState

    data class Failed(
        val error: DomainError,
    ) : GiveawayInfoState
}

/**
 * The [GiveawayUi] of [content] for its bubble: the participating channels from the chats looked up for the item
 * ([ItemInputs.chats]) and the winners from its users ([ItemInputs.members]); null for other content.
 */
internal fun ItemInputs.giveawayUi(content: MessageContent): GiveawayUi? =
    when (content) {
        is MessageContent.Giveaway -> {
            GiveawayUi(channels = channelAvatars(content.parameters.chatIds))
        }

        is MessageContent.GiveawayWinners -> {
            GiveawayUi(channels = channelAvatars(listOf(content.boostedChatId)), winners = winnerAvatars(content))
        }

        else -> {
            null
        }
    }

private fun ItemInputs.channelAvatars(chatIds: List<Long>): ImmutableList<SenderAvatar> =
    chatIds
        .mapIndexed { index, chatId ->
            val ref = chats.getOrNull(index)
            SenderAvatar(
                id = chatId,
                name = ref?.let { UiText.Plain(it.title) } ?: UiText.resource(R.string.giveaway_channel_unknown),
                photo = ref?.photo,
                placeholderIcon = if (ref == null) UiR.drawable.ic_campaign else null,
            )
        }.toImmutableList()

private fun ItemInputs.winnerAvatars(content: MessageContent.GiveawayWinners): ImmutableList<SenderAvatar> =
    memberIdsOf(content)
        .zip(members)
        .map { (userId, user) ->
            SenderAvatar(
                id = userId,
                name = user?.fullName() ?: UiText.resource(R.string.giveaway_winner_unknown),
                photo = user?.profilePhoto,
                placeholderIcon = if (user == null || user.isDeleted) UiR.drawable.ic_person_filled else null,
            )
        }.toImmutableList()

/** The chats the content names and shows: a giveaway's participating channels, the boosted chat first. */
internal fun chatIdsOf(content: MessageContent): List<Long> =
    when (content) {
        is MessageContent.Giveaway -> content.parameters.chatIds
        is MessageContent.GiveawayWinners -> listOf(content.boostedChatId)
        else -> emptyList()
    }

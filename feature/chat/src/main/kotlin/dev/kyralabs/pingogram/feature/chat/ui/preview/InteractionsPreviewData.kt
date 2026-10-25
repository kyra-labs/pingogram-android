package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import dev.kyralabs.pingogram.feature.chat.ui.interactions.InteractionRow
import dev.kyralabs.pingogram.feature.chat.ui.interactions.InteractionsContent
import dev.kyralabs.pingogram.feature.chat.ui.interactions.InteractionsTab
import dev.kyralabs.pingogram.feature.chat.ui.interactions.InteractionsTabItem
import dev.kyralabs.pingogram.feature.chat.ui.interactions.MessageInteractionsSheet
import dev.kyralabs.pingogram.feature.chat.ui.interactions.MoreInteractions
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Who saw a group message and who reacted to it, for previews and screenshot tests: four members saw it, six reacted
 * (four hearts, two thumbs up, one of them as a channel); the tabs of the viewers, of every reaction (a next page on
 * its way) and of the hearts; and a tab loading, empty and failed; and a group message's actions with "Seen by" and
 * "Reactions".
 */
internal object InteractionsPreviewData {
    private const val ADA = 101L
    private const val GRACE = 102L
    private const val LINUS = 103L
    private const val MARGARET = 104L
    private const val CHANNEL = -1_000_042L

    private val heart = ReactionLabel.Emoji("❤")
    private val thumbsUp = ReactionLabel.Emoji("👍")

    private val tabs =
        persistentListOf(
            InteractionsTabItem(InteractionsTab.Seen, 4),
            InteractionsTabItem(InteractionsTab.AllReactions, 6),
            InteractionsTabItem(InteractionsTab.OneReaction(heart), 4),
            InteractionsTabItem(InteractionsTab.OneReaction(thumbsUp), 2),
        )

    val seen =
        MessageInteractionsSheet(
            tabs = tabs,
            selected = InteractionsTab.Seen,
            content =
                InteractionsContent.Rows(
                    rows =
                        persistentListOf(
                            row(ADA, "Ada Lovelace", time(R.string.interactions_seen_today, "9:41 AM")),
                            row(GRACE, "Grace Hopper", time(R.string.interactions_seen_today, "8:15 AM")),
                            row(LINUS, "Linus Torvalds", time(R.string.interactions_seen_yesterday, "11:02 PM")),
                            row(
                                MARGARET,
                                "Margaret Hamilton",
                                time(R.string.interactions_seen_on, "Mar 17", "6:30 PM"),
                            ),
                        ),
                    more = MoreInteractions.None,
                ),
        )

    val allReactions =
        MessageInteractionsSheet(
            tabs = tabs,
            selected = InteractionsTab.AllReactions,
            content =
                InteractionsContent.Rows(
                    rows =
                        persistentListOf(
                            row(ADA, "Ada Lovelace", time(R.string.interactions_reacted_today, "9:42 AM"), heart),
                            row(GRACE, "Grace Hopper", time(R.string.interactions_reacted_today, "8:20 AM"), thumbsUp),
                            chatRow(
                                CHANNEL,
                                "Pingogram News",
                                time(R.string.interactions_reacted_today, "8:01 AM"),
                                heart,
                            ),
                            row(
                                LINUS,
                                "Linus Torvalds",
                                time(R.string.interactions_reacted_yesterday, "11:05 PM"),
                                heart,
                            ),
                        ),
                    more = MoreInteractions.Loading,
                ),
        )

    val oneReaction =
        MessageInteractionsSheet(
            tabs = tabs,
            selected = InteractionsTab.OneReaction(heart),
            content =
                InteractionsContent.Rows(
                    rows =
                        persistentListOf(
                            row(ADA, "Ada Lovelace", time(R.string.interactions_reacted_today, "9:42 AM")),
                            chatRow(CHANNEL, "Pingogram News", time(R.string.interactions_reacted_today, "8:01 AM")),
                            row(LINUS, "Linus Torvalds", time(R.string.interactions_reacted_yesterday, "11:05 PM")),
                            row(MARGARET, "Margaret Hamilton", time = null),
                        ),
                    more = MoreInteractions.None,
                ),
        )

    val loading = seen.copy(content = InteractionsContent.Loading)

    /** Nobody saw the message yet: the viewers' tab alone, without tabs. */
    val empty =
        MessageInteractionsSheet(
            tabs = persistentListOf(InteractionsTabItem(InteractionsTab.Seen, 0)),
            selected = InteractionsTab.Seen,
            content = InteractionsContent.Empty,
        )

    val error = seen.copy(content = InteractionsContent.Error(DomainError.MessageViewersUnavailable))

    /** The next page of every reaction failed. */
    val moreFailed =
        allReactions.copy(
            content =
                (allReactions.content as InteractionsContent.Rows).copy(
                    more = MoreInteractions.Failed(DomainError.Network),
                ),
        )

    /** [ChatPreviewData.groupChat] with the actions of Grace's message, which the user may see the viewers of. */
    val groupActionsSheet: ChatUiState =
        ChatPreviewData.groupChat.copy(
            messageActions =
                ChatPreviewData.incomingMessageActions.copy(
                    actions =
                        (
                            listOf(MessageAction.SeenBy, MessageAction.Reactions) +
                                ChatPreviewData.incomingMessageActions.actions
                        ).toImmutableList(),
                ),
        )

    private fun time(
        id: Int,
        vararg args: Any,
    ) = UiText.resource(id, *args)

    private fun row(
        userId: Long,
        name: String,
        time: UiText?,
        reaction: ReactionLabel? = null,
    ) = InteractionRow(
        key = "u$userId${reaction?.let { ":$it" }.orEmpty()}",
        sender = MessageSender.User(userId),
        name = UiText.Plain(name),
        photo = null,
        avatarId = userId,
        time = time,
        reaction = reaction,
    )

    private fun chatRow(
        chatId: Long,
        title: String,
        time: UiText?,
        reaction: ReactionLabel? = null,
    ) = InteractionRow(
        key = "c$chatId${reaction?.let { ":$it" }.orEmpty()}",
        sender = MessageSender.Chat(chatId),
        name = UiText.Plain(title),
        photo = null,
        avatarId = chatId,
        time = time,
        reaction = reaction,
    )
}

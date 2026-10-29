package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PollVoteRestriction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVoterRow
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVoterSection
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVotersSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Polls for previews and screenshot tests (`REQUIREMENTS.md` FR-C4): a group's conversations with polls in each state
 * (open to vote, several answers checked before "Vote", a vote on its way, results of a public poll, a quiz answered
 * wrong with its explanation, closed, one the user may not vote in), and the voters' sheet loaded, loading and failed.
 */
internal object PollPreviewData {
    private const val GROUP = -300L
    private const val GRACE = 41L
    private const val LINUS = 43L
    private const val ADA = 42L

    private val header =
        ChatHeader(
            chatId = GROUP,
            title = UiText.Plain("Lunch Club"),
            photo = null,
            placeholderIcon = null,
            subtitle = ChatSubtitle.Status(UiText.plural(R.plurals.chat_subtitle_members, 12, 12)),
        )

    /** A poll to vote in with one tap, below a poll with two answers checked before "Vote". */
    val voting: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        poll(3, ContentPreviewData.poll(voted = false), sender = "Grace Hopper", senderId = GRACE),
                        poll(
                            2,
                            ContentPreviewData.poll(voted = false, multipleAnswers = true),
                            sender = "Linus Torvalds",
                            senderId = LINUS,
                        ).copy(pollChoices = persistentSetOf(1, 2)),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** The user's public poll with its results and "View votes", below a quiz they answered wrong. */
    val results: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        poll(
                            3,
                            ContentPreviewData.poll(voted = true, publicVoters = true),
                            isOutgoing = true,
                        ),
                        poll(
                            2,
                            ContentPreviewData.poll(voted = true, quiz = true, chosenIndex = 1),
                            sender = "Grace Hopper",
                            senderId = GRACE,
                        ),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** A vote on its way to Telegram, below a closed poll and a poll only members may vote in. */
    val states: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        poll(
                            4,
                            ContentPreviewData.poll(voted = false, sendingIndex = 2),
                            sender = "Ada",
                            senderId = ADA,
                        ),
                        poll(
                            3,
                            ContentPreviewData.poll(
                                voted = false,
                                restriction = PollVoteRestriction.MembershipRequired(GROUP),
                            ),
                            sender = "Linus Torvalds",
                            senderId = LINUS,
                        ),
                        poll(
                            2,
                            ContentPreviewData.poll(voted = true, closed = true),
                            sender = "Grace Hopper",
                            senderId = GRACE,
                        ),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** Who voted for each answer of the public poll: summer's first page with more to load, autumn's all. */
    val voters =
        PollVotersSheet(
            messageId = 3,
            question = "Best season?",
            sections =
                persistentListOf(
                    PollVoterSection(
                        optionIndex = 1,
                        text = "Summer",
                        count = 30,
                        voters =
                            persistentListOf(
                                voter(GRACE, "Grace Hopper", 1_700_000_000),
                                voter(LINUS, "Linus Torvalds", 1_700_003_600),
                            ),
                    ),
                    PollVoterSection(
                        optionIndex = 2,
                        text = "Autumn",
                        count = 2,
                        voters =
                            persistentListOf(
                                voter(ADA, "Ada Lovelace", 1_700_007_200),
                                voter(44, "Margaret Hamilton", 0),
                            ),
                        reachedEnd = true,
                    ),
                ),
        )

    /** The sheet as it opens, each answer's first page on its way. */
    val votersLoading =
        voters.copy(
            sections =
                persistentListOf(
                    PollVoterSection(optionIndex = 1, text = "Summer", count = 30, isLoading = true),
                    PollVoterSection(optionIndex = 2, text = "Autumn", count = 2, isLoading = true),
                ),
        )

    /** Summer's voters could not be loaded, autumn's could. */
    val votersFailed =
        voters.copy(
            sections =
                persistentListOf(
                    PollVoterSection(optionIndex = 1, text = "Summer", count = 30, error = DomainError.Network),
                    voters.sections[1],
                ),
        )

    private fun poll(
        id: Long,
        content: MessageContent.Poll,
        sender: String = "",
        senderId: Long = 0,
        isOutgoing: Boolean = false,
    ): ChatItem.Bubble =
        bubble(
            id = id,
            text = "",
            time = "12:0$id PM",
            isOutgoing = isOutgoing,
            sender = if (isOutgoing) UiText.resource(UiR.string.preview_sender_you) else UiText.Plain(sender),
            senderId = senderId,
            groupChat = true,
            position = GroupPosition.Single,
            status = if (isOutgoing) OutgoingStatus.Read else null,
            content = content,
            contentLabel = contentKindText(content),
        )

    private fun voter(
        userId: Long,
        name: String,
        date: Long,
    ) = PollVoterRow(MessageSender.User(userId), userId, UiText.Plain(name), photo = null, date = date)
}

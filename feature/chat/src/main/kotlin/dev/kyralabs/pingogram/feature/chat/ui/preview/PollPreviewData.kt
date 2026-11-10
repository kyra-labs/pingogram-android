package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollMedia
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
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
import dev.kyralabs.pingogram.feature.chat.ui.PollAdding
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
    private const val TWO_HOURS = 7_200L

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

    /**
     * What polls show since 2026 (boards 4a, 4b): a poll whose field for a new answer is open, below the user's poll
     * that closes in two hours with its description, an answer that links somewhere and its results hidden until the
     * end.
     */
    val closing: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        poll(3, booksPoll(), sender = "Ada", senderId = ADA).copy(pollAdding = PollAdding.Open),
                        poll(2, lunchPoll(), isOutgoing = true).copy(pollClosesIn = TWO_HOURS),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** A quiz with two correct answers, one of them found, with its score and explanation (board 4b). */
    val quizScore: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        poll(2, twoAnswerQuiz(), sender = "Linus Torvalds", senderId = LINUS),
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

    private fun text(value: String) = FormattedText(value, persistentListOf())

    private fun option(
        index: Int,
        value: String,
        votes: Int = 0,
        percentage: Int = 0,
        chosen: Boolean = false,
        correct: Boolean = false,
        media: PollMedia? = null,
    ) = PollOption(index, text(value), votes, percentage, chosen, correct, media = media)

    private fun lunchPoll() =
        MessageContent.Poll(
            poll =
                Poll(
                    id = 21,
                    question = text("Where do we eat on Friday?"),
                    options =
                        persistentListOf(
                            option(0, "Noodle bar", chosen = true),
                            option(1, "Green kitchen", media = PollMedia.Link("https://example.org/menu", "Menu")),
                            option(2, "Pizza by the river"),
                        ),
                    totalVoterCount = 9,
                    isAnonymous = true,
                    allowsMultipleAnswers = false,
                    canSeeResults = false,
                    isClosed = false,
                    type = PollType.Regular,
                ),
            description = text("Pick before Thursday noon so we can book a table."),
        )

    private fun twoAnswerQuiz() =
        MessageContent.Poll(
            poll =
                Poll(
                    id = 22,
                    question = text("Which of these are prime?"),
                    options =
                        persistentListOf(
                            option(0, "21", votes = 3, percentage = 20),
                            option(1, "23", votes = 6, percentage = 40, chosen = true, correct = true),
                            option(2, "27", votes = 2, percentage = 13, chosen = true),
                            option(index = 3, value = "29", votes = 4, percentage = 27, correct = true),
                        ),
                    totalVoterCount = 8,
                    isAnonymous = true,
                    allowsMultipleAnswers = true,
                    canSeeResults = true,
                    isClosed = false,
                    type = PollType.Quiz(text("A prime has no divisor but 1 and itself: 21 = 3 × 7, 27 = 3 × 9.")),
                ),
            description = FormattedText.Empty,
        )

    private fun booksPoll() =
        MessageContent.Poll(
            poll =
                Poll(
                    id = 23,
                    question = text("Next month's book?"),
                    options = persistentListOf(option(0, "Piranesi"), option(1, "Klara and the Sun")),
                    totalVoterCount = 4,
                    isAnonymous = false,
                    allowsMultipleAnswers = false,
                    canSeeResults = false,
                    isClosed = false,
                    type = PollType.Regular,
                ),
            description = FormattedText.Empty,
            canAddOption = true,
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

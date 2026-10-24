package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A poll or a quiz (`REQUIREMENTS.md` FR-C4). */
data class Poll(
    val id: Long,
    val question: FormattedText,
    /** In the order in which they must be shown, which can differ from [PollOption.index]. */
    val options: ImmutableList<PollOption>,
    val totalVoterCount: Int,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    /** The user may see the results: they voted, the poll is closed, or they created it. */
    val canSeeResults: Boolean,
    val isClosed: Boolean,
    val type: PollType,
    /** Who voted for each answer can be listed (td_api.tl `poll.can_get_voters`, `getPollVoters`). */
    val canGetVoters: Boolean = false,
    /**
     * The user may change or take back their vote while the poll is open (td_api.tl `poll.allows_revoting`); never in
     * a quiz, whose first answer counts.
     */
    val allowsRevoting: Boolean = false,
    /** Why the user may not vote in the poll now; null where they may (td_api.tl `poll.vote_restriction_reason`). */
    val voteRestriction: PollVoteRestriction? = null,
) {
    /** The user voted: one of the answers is theirs. */
    val hasVoted: Boolean get() = options.any { it.isChosen }

    /** A vote of the user's is on its way to Telegram ([PollOption.isBeingChosen]). */
    val isVoting: Boolean get() = options.any { it.isBeingChosen }

    /**
     * The user may vote now: the poll is open, nothing forbids it, and they have not voted yet (a vote waiting for
     * Telegram counts as one).
     */
    val canVote: Boolean get() = !isClosed && voteRestriction == null && !hasVoted && !isVoting

    /** The user may take their vote back: they voted in an open poll that allows it, which a quiz never does. */
    val canRetractVote: Boolean
        get() = hasVoted && !isClosed && allowsRevoting && type is PollType.Regular && voteRestriction == null
}

/**
 * One answer of a [Poll]. [index] identifies it when voting (0-based, TDLib's order). [voterCount] and
 * [votePercentage] (0–100) are known only when the results may be seen.
 */
data class PollOption(
    val index: Int,
    val text: FormattedText,
    val voterCount: Int,
    val votePercentage: Int,
    /** The user chose this answer. */
    val isChosen: Boolean,
    /** A correct answer of a quiz, once the user answered it or it is closed; false in regular polls. */
    val isCorrect: Boolean,
    /** The user's vote for this answer is on its way to Telegram (td_api.tl `pollOption.is_being_chosen`). */
    val isBeingChosen: Boolean = false,
)

sealed interface PollType {
    data object Regular : PollType

    /** A quiz with correct answers; [explanation] is shown after a wrong answer, empty until the quiz was answered. */
    data class Quiz(
        val explanation: FormattedText,
    ) : PollType
}

/** Why the user may not vote in a poll (td_api.tl `PollVoteRestrictionReason`). */
sealed interface PollVoteRestriction {
    /** The poll is closed. */
    data object Closed : PollVoteRestriction

    /** The message is scheduled: no one votes before it is sent. */
    data object Scheduled : PollVoteRestriction

    /** The message is still being sent. */
    data object NotSent : PollVoteRestriction

    /** Only members of chat [chatId] may vote, and the user is not one. */
    data class MembershipRequired(
        val chatId: Long,
    ) : PollVoteRestriction

    /** Only users of some countries may vote, and the user's country ([countryCode], ISO 3166-1 alpha-2) is not one. */
    data class CountryRestricted(
        val countryCode: String,
    ) : PollVoteRestriction

    /** Another reason, which TDLib does not name. */
    data object Other : PollVoteRestriction
}

/** Someone who voted for an answer of a public poll: a user, or a chat (a channel voting as itself), and when. */
data class PollVoter(
    val sender: MessageSender,
    /** When they voted (Unix time, seconds); 0 where Telegram does not say. */
    val date: Long,
)

/** One page of the voters of an answer ([PollVoter]), and how many voted for it in all ([totalCount]). */
data class PollVoters(
    val totalCount: Int,
    val voters: ImmutableList<PollVoter> = persistentListOf(),
)

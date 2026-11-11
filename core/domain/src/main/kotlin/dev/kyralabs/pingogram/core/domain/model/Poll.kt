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
    /** When the poll closes by itself (Unix time, seconds); 0 where it stays open until its author stops it. */
    val closeDate: Long = 0,
    /** For how long after it was sent the poll stays open (seconds); 0 where [closeDate] says, or it does not close. */
    val openPeriod: Int = 0,
) {
    /** The user voted: one of the answers is theirs. */
    val hasVoted: Boolean get() = options.any { it.isChosen }

    /**
     * The user voted but may not see the results until the poll closes (td_api.tl `inputMessagePoll`
     * `hide_results_until_closes`, which `poll` tells only through `can_see_results`): before voting it cannot tell.
     */
    val hidesResults: Boolean get() = hasVoted && !canSeeResults && !isClosed

    /** How many answers of a quiz are correct, once the user answered it or it closed; 0 before and in polls. */
    val correctOptionCount: Int get() = options.count { it.isCorrect }

    /** When the poll closes by itself, if it was sent at [sentAt] (Unix time); null where it stays open. */
    fun closesAt(sentAt: Long): Long? =
        when {
            isClosed -> null
            closeDate > 0 -> closeDate
            openPeriod > 0 -> sentAt + openPeriod
            else -> null
        }

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
    /** TDLib's identifier of the answer (td_api.tl `pollOption.id`); empty until Telegram assigned one. */
    val id: String = "",
    /** What the answer carries beside its text, a link most often; null for none. */
    val media: PollMedia? = null,
    /** Who added the answer after the poll was sent (td_api.tl `pollOption.author`); null for its first answers. */
    val author: MessageSender? = null,
)

sealed interface PollType {
    data object Regular : PollType

    /**
     * A quiz with one or more correct answers ([PollOption.isCorrect]); [explanation] is shown once the quiz was
     * answered, empty before.
     */
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

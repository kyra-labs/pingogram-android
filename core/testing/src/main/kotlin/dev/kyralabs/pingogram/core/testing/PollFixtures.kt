package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/** TDLib's polls for tests (`REQUIREMENTS.md` FR-C4): options, polls and quizzes, and the message content of one. */
object PollFixtures {
    fun pollOption(
        text: String,
        voterCount: Int = 0,
        votePercentage: Int = 0,
        isChosen: Boolean = false,
        isBeingChosen: Boolean = false,
    ) = TdApi.PollOption(
        "",
        TdApiFixtures.formattedText(text),
        null,
        voterCount,
        votePercentage,
        emptyArray(),
        isChosen,
        isBeingChosen,
        null,
        0,
    )

    /** An open anonymous poll; pass a `TdApi.PollTypeQuiz` as [type] for a quiz. */
    fun poll(
        question: String = "Lunch?",
        options: Array<TdApi.PollOption> = arrayOf(pollOption("Pizza"), pollOption("Salad")),
        type: TdApi.PollType = TdApi.PollTypeRegular(),
        totalVoterCount: Int = 0,
        canSeeResults: Boolean = false,
        isAnonymous: Boolean = true,
        allowsMultipleAnswers: Boolean = false,
        isClosed: Boolean = false,
        optionOrder: IntArray = intArrayOf(),
        id: Long = 1,
        canGetVoters: Boolean = false,
        allowsRevoting: Boolean = false,
        voteRestrictionReason: TdApi.PollVoteRestrictionReason? = null,
    ) = TdApi.Poll(
        id,
        TdApiFixtures.formattedText(question),
        options,
        totalVoterCount,
        emptyArray(),
        canGetVoters,
        canSeeResults,
        isAnonymous,
        allowsMultipleAnswers,
        allowsRevoting,
        false,
        emptyArray(),
        optionOrder,
        type,
        0,
        0,
        isClosed,
        voteRestrictionReason,
    )

    fun messagePoll(
        poll: TdApi.Poll = poll(),
        description: TdApi.FormattedText = TdApiFixtures.formattedText(""),
    ) = TdApi.MessagePoll(poll, description, null, false)
}

package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.MessageSender

/** What the user does with the polls of the conversation's messages (`REQUIREMENTS.md` FR-C4), for [ChatViewModel]. */
sealed interface PollEvent : ChatEvent {
    /** Answer [optionIndex] (`PollOption.index`) of the poll of message [messageId] was tapped: the vote goes out. */
    data class Vote(
        val messageId: Long,
        val optionIndex: Int,
    ) : PollEvent

    /** In a poll with several answers, answer [optionIndex] of message [messageId] was checked or unchecked. */
    data class Toggle(
        val messageId: Long,
        val optionIndex: Int,
    ) : PollEvent

    /** "Vote" under a poll with several answers: the checked answers of message [messageId] go out. */
    data class SubmitVotes(
        val messageId: Long,
    ) : PollEvent

    /** "Retract vote" in the message actions of message [messageId]. */
    data class RetractVote(
        val messageId: Long,
    ) : PollEvent

    /** "Stop poll" in the message actions of message [messageId]: a question asks first. */
    data class StopPoll(
        val messageId: Long,
    ) : PollEvent

    /** The question whether to stop the poll was answered with "Stop": it closes for everyone. */
    data object ConfirmStop : PollEvent

    /** The question whether to stop the poll was dismissed: nothing happens. */
    data object DismissStop : PollEvent

    /** "View votes" under the poll of message [messageId]: the voters' sheet opens. */
    data class OpenVoters(
        val messageId: Long,
    ) : PollEvent

    /** The next voters of answer [optionIndex] in the voters' sheet: "Show more", or "Try again" after a failure. */
    data class LoadMoreVoters(
        val optionIndex: Int,
    ) : PollEvent

    /** Voter [sender] was tapped in the voters' sheet: the sheet closes, and their profile or chat opens. */
    data class OpenVoter(
        val sender: MessageSender,
    ) : PollEvent

    /** The voters' sheet was dismissed. */
    data object DismissVoters : PollEvent
}

/** One-off effects of polls. */
sealed interface PollEffect : ChatEffect {
    /**
     * Telegram took the user's answer to a quiz, [correct] or not: a `Confirm` or `Reject` haptic on Android 14 and
     * later; the bubble shows the rest.
     */
    data class QuizAnswered(
        val correct: Boolean,
    ) : PollEffect
}

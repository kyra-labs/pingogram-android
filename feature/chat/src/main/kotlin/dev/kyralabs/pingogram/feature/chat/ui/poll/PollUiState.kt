package dev.kyralabs.pingogram.feature.chat.ui.poll

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The voters' sheet of the public poll of message [messageId] (`docs/DESIGN_SYSTEM.md` §6 "Poll voters"): its
 * [question], then a section per answer someone chose, in the poll's order; empty while no one voted.
 */
@Immutable
data class PollVotersSheet(
    val messageId: Long,
    val question: String,
    val sections: ImmutableList<PollVoterSection> = persistentListOf(),
) {
    override fun toString() = "PollVotersSheet(messageId=$messageId, sections=$sections)"
}

/**
 * One answer in a [PollVotersSheet]: its [text] (user content) and how many chose it ([count]), with the [voters]
 * loaded so far, a page at a time. [isLoading] while a page is on its way, [error] when the last one failed (with
 * "Try again"); [reachedEnd] once Telegram has no more.
 */
@Immutable
data class PollVoterSection(
    val optionIndex: Int,
    val text: String,
    val count: Int,
    val voters: ImmutableList<PollVoterRow> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: DomainError? = null,
    val reachedEnd: Boolean = false,
) {
    /** More voters can be loaded: "Show more". */
    val hasMore: Boolean get() = !isLoading && error == null && !reachedEnd && voters.size < count

    override fun toString() =
        "PollVoterSection(optionIndex=$optionIndex, count=$count, voters=${voters.size}, isLoading=$isLoading, " +
            "error=$error, reachedEnd=$reachedEnd)"
}

/**
 * A voter in a [PollVoterSection]: [name] and [photo] (or [placeholderIcon] on the placeholder), [id] for the
 * placeholder's colors, and when they voted ([date], Unix time in seconds; 0 where Telegram does not say).
 */
@Immutable
data class PollVoterRow(
    val sender: MessageSender,
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val date: Long,
    @param:DrawableRes val placeholderIcon: Int? = null,
) {
    override fun toString() = "PollVoterRow(sender=$sender, …)"
}

/** The question before the poll of message [messageId] is stopped for everyone, an `AlertDialog` (destructive). */
@Immutable
data class StopPollDialog(
    val messageId: Long,
)

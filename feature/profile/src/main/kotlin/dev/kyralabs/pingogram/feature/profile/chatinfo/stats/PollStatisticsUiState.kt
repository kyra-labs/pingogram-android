package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** What the statistics page of a poll shows. */
@Immutable
sealed interface PollStatisticsUiState {
    data object Loading : PollStatisticsUiState

    data class Error(
        val error: DomainError,
    ) : PollStatisticsUiState

    /**
     * @property question the poll's question, as plain text.
     * @property votes how many voted, "120 votes".
     * @property options each answer's totals, in the poll's order.
     * @property graph the votes over time, loading, drawn, or not made yet.
     */
    data class Content(
        val question: String,
        val votes: UiText,
        val options: ImmutableList<PollTotal>,
        val graph: GraphState,
    ) : PollStatisticsUiState
}

/** An answer's [text], its [votes] ("41 votes") and its share of them, [percent] ("41%"). */
@Immutable
data class PollTotal(
    val text: String,
    val votes: UiText,
    val percent: String,
) {
    override fun toString() = "PollTotal(…)"
}

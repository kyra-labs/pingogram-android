package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reporting messages of chat [chatId] to Telegram's moderators (`REQUIREMENTS.md` FR-C6 "report"), for
 * `ChatViewModel`: the report [sheet], its [comment], and the requests, TDLib's current flow (`reportChat`).
 *
 * - **Start** ([start]): the sheet opens at once, showing that it asks, and the first request goes out without a
 *   reason ([ReportOptionId.Initial]); TDLib answers with the reasons Telegram offers.
 * - **Steps**: choosing a reason ([choose]) reports again with it; Telegram may ask for another, or for a comment
 *   ([sendComment]), required unless TDLib says it is optional. Each request is one tap of the user's, one at a
 *   time, never repeated on its own (Telegram's API terms: no automation).
 * - **Done**: the sheet closes, a snackbar thanks the user ([SelectionEffect.Reported]) and [onReported] runs
 *   (selection mode ends). A failure is a snackbar; the sheet stays at its step so the user can try again, or closes if
 *   the reasons never came.
 * - **Closing** the sheet ([dismiss]) stops waiting; a request TDLib already has cannot be taken back.
 *
 * Runs in [scope], the view model's, on the main thread; [comment] is touched there only, as its text field does.
 */
internal class ChatReporting(
    private val chatId: Long,
    private val repository: MessageActionRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
    private val onReported: () -> Unit = {},
) {
    /** The comment of a report that asks for one; emptied whenever a report starts. */
    val comment = TextFieldState()

    private val mutableSheet = MutableStateFlow<ReportSheet?>(null)

    /** The open report sheet; null while none is. */
    val sheet: StateFlow<ReportSheet?> = mutableSheet.asStateFlow()

    private var messageIds: List<Long> = emptyList()

    /** The reasons of the step the sheet shows, by index. */
    private var options: List<ReportOption> = emptyList()

    /** The option a comment goes with, while the sheet asks for one. */
    private var commentOption: ReportOptionId? = null

    private var reporting: Job? = null

    /** Reports [messageIds]: opens the sheet and asks TDLib for the reasons. Nothing while a report is open. */
    fun start(messageIds: List<Long>) {
        if (messageIds.isEmpty() || mutableSheet.value != null) return
        this.messageIds = messageIds.distinct().sorted()
        options = emptyList()
        commentOption = null
        comment.clearText()
        mutableSheet.value = ReportSheet()
        send(ReportOptionId.Initial)
    }

    /** Reports again with the reason at [index] of the step shown. */
    fun choose(index: Int) {
        if (mutableSheet.value?.step !is ReportStep.Options) return
        val option = options.getOrNull(index) ?: return
        send(option.id)
    }

    /** Reports with the comment written, unless it is empty where TDLib requires one. */
    fun sendComment() {
        val step = mutableSheet.value?.step as? ReportStep.Comment ?: return
        val option = commentOption
        val text = comment.text.toString().trim()
        if (option != null && (text.isNotEmpty() || step.isOptional)) send(option, text)
    }

    fun dismiss() {
        reporting?.cancel()
        mutableSheet.value = null
    }

    /** One request at a time: the sheet shows it is on its way, then what TDLib asks for next, or closes. */
    private fun send(
        option: ReportOptionId,
        text: String = "",
    ) {
        if (reporting?.isActive == true) return
        mutableSheet.update { it?.copy(isReporting = true) }
        reporting =
            scope.launch {
                when (val result = repository.report(chatId, messageIds, option, text)) {
                    is Outcome.Success -> onAnswer(result.value)
                    is Outcome.Failure -> onFailure(result.error)
                }
            }
    }

    private fun onAnswer(result: ChatReportResult) {
        when (result) {
            ChatReportResult.Reported -> {
                mutableSheet.value = null
                onEffect(SelectionEffect.Reported)
                onReported()
            }

            is ChatReportResult.OptionRequired -> {
                options = result.options
                val step = ReportStep.Options(result.title, result.options.map { it.text }.toImmutableList())
                mutableSheet.update { it?.copy(step = step, isReporting = false) }
            }

            is ChatReportResult.TextRequired -> {
                commentOption = result.option
                mutableSheet.update { it?.copy(step = ReportStep.Comment(result.isOptional), isReporting = false) }
            }

            // Messages are always sent along.
            ChatReportResult.MessagesRequired -> {
                mutableSheet.value = null
                onEffect(ChatEffect.ActionUnavailable)
            }
        }
    }

    private fun onFailure(error: DomainError) {
        onEffect(ChatEffect.ShowError(error))
        mutableSheet.update { sheet -> sheet?.takeIf { it.step != null }?.copy(isReporting = false) }
    }
}

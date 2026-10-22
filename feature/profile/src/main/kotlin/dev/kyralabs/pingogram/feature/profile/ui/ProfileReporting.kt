package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The report sheet of the profile: the step of TDLib's flow it shows, null until the reasons came. */
@Immutable
data class ProfileReportSheet(
    val step: ProfileReportStep? = null,
    val isReporting: Boolean = false,
)

/** What a report asks the user for next. */
@Immutable
sealed interface ProfileReportStep {
    /** A reason: [title] asks, and the user picks one of [options]. */
    data class Options(
        val title: String,
        val options: ImmutableList<String>,
    ) : ProfileReportStep {
        override fun toString() = "Options(${options.size})"
    }

    /** A comment that says more, which may be left empty if [isOptional]. */
    data class Comment(
        val isOptional: Boolean,
    ) : ProfileReportStep
}

/**
 * Reporting the account of the profile to Telegram's moderators (`REQUIREMENTS.md` FR-P2 "report"), for
 * [ProfileViewModel]: the report [sheet], its [comment], and the requests, TDLib's `reportChat` on the private chat
 * without messages (`MessageActionRepository.report` with none), the conversation's report flow
 * (`feature:chat`'s `ChatReporting`) for the whole chat.
 *
 * - **Start** ([start]): the sheet opens at once, showing that it asks, and the first request goes out without a
 *   reason ([ReportOptionId.Initial]); TDLib answers with the reasons Telegram offers.
 * - **Steps**: choosing a reason ([choose]) reports again with it; Telegram may ask for another, or for a comment
 *   ([sendComment]). Each request is one tap of the user's, one at a time, never repeated on its own (Telegram's API
 *   terms: no automation).
 * - **Done**: the sheet closes and a snackbar thanks the user ([ProfileEffect.Reported]). Telegram may want messages to
 *   go with the report instead ([ChatReportResult.MessagesRequired]): the sheet closes and a snackbar says to report
 *   them from the chat. A failure is a snackbar; the sheet stays at its step, or closes if the reasons never came.
 * - **Closing** the sheet ([dismiss]) stops waiting; a request TDLib already has cannot be taken back.
 *
 * Runs in [scope], the view model's, on the main thread; [comment] is touched there only, as its text field does.
 */
internal class ProfileReporting(
    private val repository: MessageActionRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ProfileEffect) -> Unit,
) {
    /** The comment of a report that asks for one; emptied whenever a report starts. */
    val comment = TextFieldState()

    private val mutableSheet = MutableStateFlow<ProfileReportSheet?>(null)

    /** The open report sheet; null while none is. */
    val sheet: StateFlow<ProfileReportSheet?> = mutableSheet.asStateFlow()

    private var chatId = 0L

    /** The reasons of the step the sheet shows, by index. */
    private var options: List<ReportOption> = emptyList()

    /** The option a comment goes with, while the sheet asks for one. */
    private var commentOption: ReportOptionId? = null

    private var reporting: Job? = null

    /** Reports private chat [chatId]: opens the sheet and asks TDLib for the reasons; nothing while one is open. */
    fun start(chatId: Long) {
        if (mutableSheet.value != null) return
        this.chatId = chatId
        options = emptyList()
        commentOption = null
        comment.clearText()
        mutableSheet.value = ProfileReportSheet()
        send(ReportOptionId.Initial)
    }

    /** Reports again with the reason at [index] of the step shown. */
    fun choose(index: Int) {
        if (mutableSheet.value?.step !is ProfileReportStep.Options) return
        val option = options.getOrNull(index) ?: return
        send(option.id)
    }

    /** Reports with the comment written, unless it is empty where TDLib requires one. */
    fun sendComment() {
        val step = mutableSheet.value?.step as? ProfileReportStep.Comment ?: return
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
                when (val result = repository.report(chatId, emptyList(), option, text)) {
                    is Outcome.Success -> onAnswer(result.value)
                    is Outcome.Failure -> onFailure(result.error)
                }
            }
    }

    private fun onAnswer(result: ChatReportResult) {
        when (result) {
            ChatReportResult.Reported -> {
                mutableSheet.value = null
                onEffect(ProfileEffect.Reported)
            }

            is ChatReportResult.OptionRequired -> {
                options = result.options
                val step = ProfileReportStep.Options(result.title, result.options.map { it.text }.toImmutableList())
                mutableSheet.update { it?.copy(step = step, isReporting = false) }
            }

            is ChatReportResult.TextRequired -> {
                commentOption = result.option
                mutableSheet.update {
                    it?.copy(step = ProfileReportStep.Comment(result.isOptional), isReporting = false)
                }
            }

            ChatReportResult.MessagesRequired -> {
                mutableSheet.value = null
                onEffect(ProfileEffect.ReportNeedsMessages)
            }
        }
    }

    private fun onFailure(error: DomainError) {
        onEffect(ProfileEffect.ShowError(error))
        mutableSheet.update { sheet -> sheet?.takeIf { it.step != null }?.copy(isReporting = false) }
    }
}

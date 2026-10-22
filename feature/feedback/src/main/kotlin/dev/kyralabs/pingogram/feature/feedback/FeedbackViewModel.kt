package dev.kyralabs.pingogram.feature.feedback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import dev.kyralabs.pingogram.core.domain.repository.DiagnosticsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the feedback page shows. */
sealed interface FeedbackUiState {
    /** The report is being read from the device. */
    data object Loading : FeedbackUiState

    /**
     * The report, ready to be read and shared.
     *
     * @property report what the device holds.
     * @property description what the user wrote about what happened.
     * @property showReport whether the whole report's text shows.
     * @property reportText the report as it is copied and shared, with [description].
     */
    @Immutable
    data class Content(
        val report: DiagnosticReport,
        val description: String,
        val showReport: Boolean,
        val reportText: String,
    ) : FeedbackUiState
}

/** What the user does on the feedback page, handled by [FeedbackViewModel.onEvent]. */
sealed interface FeedbackEvent {
    /** The text of "What happened?" changed. */
    data class DescriptionChanged(
        val text: String,
    ) : FeedbackEvent

    /** "Show report" or "Hide report". */
    data object ToggleReport : FeedbackEvent

    /** "Share report": the share sheet, to an app the user picks. */
    data object Share : FeedbackEvent

    /** "Open GitHub issue": the browser, with a new issue filled from the report. */
    data object OpenIssue : FeedbackEvent

    /** "Copy": the report to the clipboard. */
    data object Copy : FeedbackEvent

    /** "Delete crash report": forgets the kept crash. */
    data object DeleteCrash : FeedbackEvent
}

/** One-off side effects of [FeedbackViewModel], handled once by its route. */
sealed interface FeedbackEffect {
    /** Shares [text] through the share sheet. */
    data class Share(
        val text: String,
    ) : FeedbackEffect

    /** Opens [url] in the browser. */
    data class OpenUrl(
        val url: String,
    ) : FeedbackEffect

    /** Copies [text] to the clipboard. */
    data class Copy(
        val text: String,
    ) : FeedbackEffect

    /** The kept crash was deleted. */
    data object CrashDeleted : FeedbackEffect
}

/**
 * The feedback page (`docs/TESTING_GUIDE.md`): reads the report from [DiagnosticsRepository] once the page shows, keeps
 * what the user writes in the [SavedStateHandle], and hands the report to the share sheet, the browser or the
 * clipboard only when the user asks. Nothing is sent by the app itself (`CLAUDE.md` rule 10).
 */
@HiltViewModel
class FeedbackViewModel
    @Inject
    constructor(
        private val diagnostics: DiagnosticsRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val report = MutableStateFlow<DiagnosticReport?>(null)

        val uiState: StateFlow<FeedbackUiState> =
            combine(
                report,
                savedState.getStateFlow(DESCRIPTION, ""),
                savedState.getStateFlow(SHOW_REPORT, false),
            ) { report, description, showReport ->
                if (report == null) {
                    FeedbackUiState.Loading
                } else {
                    FeedbackUiState.Content(report, description, showReport, report.toText(description))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeedbackUiState.Loading)

        private val effectChannel = Channel<FeedbackEffect>(Channel.BUFFERED)
        val effects: Flow<FeedbackEffect> = effectChannel.receiveAsFlow()

        init {
            // Dispatched, so the page's first frame does not wait for the files (docs/PERFORMANCE.md §4).
            viewModelScope.launch { report.value = diagnostics.report() }
        }

        fun onEvent(event: FeedbackEvent) {
            val report = report.value
            val description = savedState.get<String>(DESCRIPTION).orEmpty()
            when (event) {
                is FeedbackEvent.DescriptionChanged -> {
                    savedState[DESCRIPTION] = event.text
                }

                FeedbackEvent.ToggleReport -> {
                    savedState[SHOW_REPORT] = savedState.get<Boolean>(SHOW_REPORT) != true
                }

                FeedbackEvent.Share -> {
                    report?.let {
                        effectChannel.trySend(
                            FeedbackEffect.Share(it.toText(description)),
                        )
                    }
                }

                FeedbackEvent.Copy -> {
                    report?.let { effectChannel.trySend(FeedbackEffect.Copy(it.toText(description))) }
                }

                FeedbackEvent.OpenIssue -> {
                    report?.let {
                        effectChannel.trySend(
                            FeedbackEffect.OpenUrl(it.issueUrl(description)),
                        )
                    }
                }

                FeedbackEvent.DeleteCrash -> {
                    deleteCrash()
                }
            }
        }

        private fun deleteCrash() {
            viewModelScope.launch {
                diagnostics.clearCrash()
                report.value = report.value?.copy(crash = null)
                effectChannel.send(FeedbackEffect.CrashDeleted)
            }
        }

        private companion object {
            const val DESCRIPTION = "description"
            const val SHOW_REPORT = "showReport"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

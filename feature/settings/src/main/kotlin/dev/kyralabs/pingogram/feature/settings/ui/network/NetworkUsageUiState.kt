package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage

/** What the Network usage page shows (`REQUIREMENTS.md` FR-S1, Data and storage). */
@Immutable
sealed interface NetworkUsageUiState {
    data object Loading : NetworkUsageUiState

    data class Error(
        val error: DomainError,
    ) : NetworkUsageUiState

    /**
     * @property network the network whose usage shows, kept in the saved state.
     * @property isResetDialogOpen the confirmation to reset the statistics is open.
     * @property isResetting resetting was confirmed and runs.
     */
    data class Content(
        val usage: NetworkUsage,
        val network: AutoDownloadNetwork,
        val isResetDialogOpen: Boolean = false,
        val isResetting: Boolean = false,
    ) : NetworkUsageUiState
}

/** What the user does on the page, handled by [NetworkUsageViewModel.onEvent]. */
sealed interface NetworkUsageEvent {
    data class SelectNetwork(
        val network: AutoDownloadNetwork,
    ) : NetworkUsageEvent

    data object Reset : NetworkUsageEvent

    data object ConfirmReset : NetworkUsageEvent

    data object DismissReset : NetworkUsageEvent

    data object Retry : NetworkUsageEvent
}

/** One-off side effects of [NetworkUsageViewModel], handled once by its route. */
sealed interface NetworkUsageEffect {
    data class ShowError(
        val error: DomainError,
    ) : NetworkUsageEffect

    /** The statistics were reset, said in a snackbar. */
    data object WasReset : NetworkUsageEffect
}

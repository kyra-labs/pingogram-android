package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules

/**
 * What the automatic download page of one kind of network shows (`docs/PERFORMANCE.md` §7).
 *
 * @property network the kind of network the page is about.
 * @property rules what downloads by itself on it; null until the settings are read.
 * @property isDataSaverOn Android's Data Saver holds back this network: nothing downloads by itself on it meanwhile.
 *   Only for mobile data and roaming, which are metered.
 */
@Immutable
data class AutoDownloadUiState(
    val network: AutoDownloadNetwork,
    val rules: AutoDownloadRules? = null,
    val isDataSaverOn: Boolean = false,
)

/** What the user does on the automatic download page, handled by [AutoDownloadViewModel.onEvent]. */
sealed interface AutoDownloadEvent {
    /** A switch or a size limit changed: the page's rules become [rules]. */
    data class Change(
        val rules: AutoDownloadRules,
    ) : AutoDownloadEvent
}

/** One-off side effects of [AutoDownloadViewModel], handled once by its route. */
sealed interface AutoDownloadEffect {
    /** The change could not be saved, shown in a snackbar; the page shows what was saved before. */
    data class ShowError(
        val error: DomainError,
    ) : AutoDownloadEffect
}

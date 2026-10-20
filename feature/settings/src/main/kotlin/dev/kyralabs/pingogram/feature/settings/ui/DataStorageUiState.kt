package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.StorageUsage

/**
 * What the Data and storage page shows (`REQUIREMENTS.md` FR-S1).
 *
 * @property autoDownload what downloads by itself on each kind of network; null until the settings are read.
 * @property isDataSaverOn Android's Data Saver is on: nothing downloads by itself on mobile data meanwhile.
 * @property usage how much space Pingogram takes, as TDLib reports it.
 * @property isClearCacheDialogOpen the confirmation to clear the cache is open.
 * @property isClearingCache clearing the cache was confirmed and runs.
 */
@Immutable
data class DataStorageUiState(
    val autoDownload: AutoDownloadSettings? = null,
    val isDataSaverOn: Boolean = false,
    val usage: StorageUsageState = StorageUsageState.Loading,
    val isClearCacheDialogOpen: Boolean = false,
    val isClearingCache: Boolean = false,
)

/** The storage usage card: loading, TDLib's figures, or why they could not be loaded. */
@Immutable
sealed interface StorageUsageState {
    data object Loading : StorageUsageState

    data class Content(
        val usage: StorageUsage,
    ) : StorageUsageState

    data class Error(
        val error: DomainError,
    ) : StorageUsageState
}

/** What the user does on the Data and storage page, handled by [DataStorageViewModel.onEvent]. */
sealed interface DataStorageEvent {
    /** Load the storage usage again after it failed. */
    data object Retry : DataStorageEvent

    /** "Clear cache" was tapped: ask for confirmation. */
    data object ClearCache : DataStorageEvent

    data object ConfirmClearCache : DataStorageEvent

    data object DismissClearCache : DataStorageEvent
}

/** One-off side effects of [DataStorageViewModel], handled once by its route. */
sealed interface DataStorageEffect {
    /** The cache was cleared, freeing [freedBytes]; shown in a snackbar. */
    data class CacheCleared(
        val freedBytes: Long,
    ) : DataStorageEffect

    /** Clearing the cache failed, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : DataStorageEffect
}

package dev.kyralabs.pingogram.feature.settings.ui

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The Data and storage page: hoists [DataStorageViewModel] and shows its results in snackbars: how much clearing the
 * cache freed, or why it failed. A network's automatic download row calls [onOpenAutoDownload]; [onBack] leaves the
 * page.
 */
@Composable
internal fun DataStorageRoute(
    onOpenAutoDownload: (AutoDownloadNetwork) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataStorageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is DataStorageEffect.CacheCleared -> {
                        if (effect.freedBytes > 0) {
                            resources.getString(
                                R.string.settings_clear_cache_done,
                                Formatter.formatShortFileSize(context, effect.freedBytes),
                            )
                        } else {
                            resources.getString(R.string.settings_clear_cache_nothing)
                        }
                    }

                    is DataStorageEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    DataStorageScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenAutoDownload = onOpenAutoDownload,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Data and storage (`REQUIREMENTS.md` FR-S1): what downloads by itself on mobile data, Wi-Fi and roaming, each a row
 * that opens its page ([onOpenAutoDownload]), with a note while Data Saver is on; how much space Pingogram's downloaded
 * files and databases take, as TDLib reports it, and "Clear cache", which deletes the downloaded files after a
 * destructive confirmation ([ClearCacheDialog]) and shows progress while it runs.
 */
@Composable
internal fun DataStorageScreen(
    state: DataStorageUiState,
    onEvent: (DataStorageEvent) -> Unit,
    onOpenAutoDownload: (AutoDownloadNetwork) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_data_storage),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        state.autoDownload?.let { settings ->
            AutoDownloadGroup(settings, state.isDataSaverOn, onOpenAutoDownload)
        }
        SettingsGroup(title = stringResource(R.string.settings_section_storage_usage)) {
            when (val usage = state.usage) {
                StorageUsageState.Loading -> {
                    item { shapes ->
                        SettingsSegment(shapes) {
                            SettingsLoading(stringResource(R.string.settings_storage_usage_loading))
                        }
                    }
                }

                is StorageUsageState.Content -> {
                    item { shapes -> FilesItem(usage.usage, shapes) }
                    item { shapes -> DatabaseItem(usage.usage, shapes) }
                }

                is StorageUsageState.Error -> {
                    item { shapes ->
                        SettingsSegment(shapes) {
                            SettingsError(usage.error, onRetry = { onEvent(DataStorageEvent.Retry) })
                        }
                    }
                }
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_section_cache)) {
            item { shapes ->
                ClearCacheItem(
                    isClearing = state.isClearingCache,
                    shapes = shapes,
                    onClick = { onEvent(DataStorageEvent.ClearCache) },
                )
            }
        }
    }
    if (state.isClearCacheDialogOpen) {
        ClearCacheDialog(
            onConfirm = { onEvent(DataStorageEvent.ConfirmClearCache) },
            onDismiss = { onEvent(DataStorageEvent.DismissClearCache) },
        )
    }
}

/** The downloaded files: their size and how many there are. */
@Composable
private fun FilesItem(
    usage: StorageUsage,
    shapes: ListItemShapes,
) {
    val context = LocalContext.current
    val count = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(usage.fileCount)
    SettingsRow(
        title = stringResource(R.string.settings_storage_files),
        shapes = shapes,
        icon = R.drawable.ic_folder,
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_storage_files_size,
                    Formatter.formatShortFileSize(context, usage.filesSize),
                    pluralStringResource(R.plurals.settings_storage_file_count, usage.fileCount, count),
                ),
            )
        },
    )
}

/** The databases and their size. */
@Composable
private fun DatabaseItem(
    usage: StorageUsage,
    shapes: ListItemShapes,
) {
    val context = LocalContext.current
    SettingsRow(
        title = stringResource(R.string.settings_storage_database),
        shapes = shapes,
        icon = R.drawable.ic_database,
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_storage_database_size,
                    Formatter.formatShortFileSize(context, usage.databaseSize),
                ),
            )
        },
    )
}

@PingogramPreviews
@Composable
private fun DataStorageScreenPreview() {
    PingogramPreview {
        DataStorageScreen(state = SettingsPreviewData.storage, onEvent = {}, onOpenAutoDownload = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenLoadingPreview() {
    PingogramPreview {
        DataStorageScreen(
            state = DataStorageUiState(),
            onEvent = {},
            onOpenAutoDownload = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenErrorPreview() {
    PingogramPreview {
        DataStorageScreen(state = SettingsPreviewData.storageError, onEvent = {}, onOpenAutoDownload = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenDialogPreview() {
    PingogramPreview {
        DataStorageScreen(
            state = SettingsPreviewData.storage.copy(isClearCacheDialogOpen = true),
            onEvent = {},
            onOpenAutoDownload = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenClearingPreview() {
    PingogramPreview {
        DataStorageScreen(state = SettingsPreviewData.storage.copy(isClearingCache = true), onEvent = {
        }, onOpenAutoDownload = {}, onBack = {})
    }
}

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsEvent
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsUiState
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsViewModel
import dev.kyralabs.pingogram.feature.settings.ui.network.NetworkGroup
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Data and storage page: hoists [DataStorageViewModel], [CallDataSavingViewModel] and [DataOptionsViewModel] and
 * shows their results in snackbars: how much clearing the cache freed, the reset automatic download, the deleted
 * drafts, or why something failed. A network's automatic download row calls [onOpenAutoDownload]; [onBack] leaves the
 * page.
 */
@Suppress("LongParameterList") // The pages it opens, back, and its three hoisted view models.
@Composable
internal fun DataStorageRoute(
    onOpenAutoDownload: (AutoDownloadNetwork) -> Unit,
    onOpenProxies: () -> Unit,
    onOpenNetworkUsage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataStorageViewModel = hiltViewModel(),
    callsViewModel: CallDataSavingViewModel = hiltViewModel(),
    optionsViewModel: DataOptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val options by optionsViewModel.uiState.collectAsStateWithLifecycle()
    val callDataSaving by callsViewModel.dataSaving.collectAsStateWithLifecycle()
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

                    DataStorageEffect.AutoDownloadReset -> {
                        resources.getString(R.string.settings_auto_download_reset_done)
                    }

                    is DataStorageEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    DataOptionsSnackbars(optionsViewModel, snackbarHostState)
    LaunchedEffect(callsViewModel) {
        callsViewModel.errors.collect { error ->
            launch { snackbarHostState.showSnackbar(errorMessage(resources, error, showErrorDetails)) }
        }
    }
    DataStorageScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenAutoDownload = onOpenAutoDownload,
        onOpenProxies = onOpenProxies,
        onOpenNetworkUsage = onOpenNetworkUsage,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        callDataSaving = callDataSaving,
        onCallDataSavingChange = callsViewModel::onSelect,
        options = options,
        onOptionsEvent = optionsViewModel::onEvent,
    )
}

/**
 * Data and storage (`REQUIREMENTS.md` FR-S1): what downloads by itself on mobile data, Wi-Fi and roaming, each a row
 * that opens its page ([onOpenAutoDownload]), with a note while Data Saver is on; how much space Pingogram's downloaded
 * files and databases take, as TDLib reports it, and "Clear cache", which deletes the downloaded files after a
 * destructive confirmation ([ClearCacheDialog]) and shows progress while it runs; and the proxy in use, a row that
 * opens the proxies ([onOpenProxies]), with network usage ([onOpenNetworkUsage]) and "Prefer IPv6" below it; when
 * calls use less data ([callDataSaving], [CallsGroup]); and "Delete cloud drafts" after "Clear cache" ([options]).
 */
@Suppress("LongParameterList") // The page's state and events, the three pages it opens, back, and its scaffold's parts.
@Composable
internal fun DataStorageScreen(
    state: DataStorageUiState,
    onEvent: (DataStorageEvent) -> Unit,
    onOpenAutoDownload: (AutoDownloadNetwork) -> Unit,
    onOpenProxies: () -> Unit,
    onOpenNetworkUsage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    callDataSaving: CallDataSaving? = null,
    onCallDataSavingChange: (CallDataSaving) -> Unit = {},
    options: DataOptionsUiState = DataOptionsUiState(),
    onOptionsEvent: (DataOptionsEvent) -> Unit = {},
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_data_storage),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        state.autoDownload?.let { settings ->
            AutoDownloadGroup(settings, state.isDataSaverOn, onOpenAutoDownload) {
                onEvent(DataStorageEvent.ResetAutoDownload)
            }
        }
        state.photosInOriginalQuality?.let { original ->
            SendingGroup(original) { onEvent(DataStorageEvent.SetPhotosInOriginalQuality(it)) }
        }
        NetworkGroup(state.proxy, onOpenProxies, onOpenNetworkUsage, options.prefersIpv6) {
            onOptionsEvent(DataOptionsEvent.SetPrefersIpv6(it))
        }
        CallsGroup(callDataSaving, onCallDataSavingChange)
        SettingsGroup(
            title = stringResource(R.string.settings_section_storage_usage),
            iconTone = SettingsIconTone.Secondary,
        ) {
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
        CacheGroup(state, options, onEvent, onOptionsEvent)
    }
    CacheDialogs(state, options, onEvent, onOptionsEvent)
}

/**
 * "Sending": whether picked photos and videos go without compression by default, as files with their metadata removed;
 * the attach preview changes it per send.
 */
@Composable
private fun SendingGroup(
    original: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SettingsGroup(title = stringResource(R.string.settings_section_sending)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_photos_original),
                shapes = shapes,
                icon = UiR.drawable.ic_image,
                supportingContent = { Text(stringResource(R.string.settings_photos_original_summary)) },
                trailingContent = { CheckSwitch(checked = original) },
                onClick = {
                    haptics.performToggle(!original)
                    onChange(!original)
                },
                modifier =
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(original)
                    },
            )
        }
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
        DataStorageScreen(
            state = SettingsPreviewData.storage,
            onEvent = {},
            onOpenAutoDownload = {},
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
        )
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
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenErrorPreview() {
    PingogramPreview {
        DataStorageScreen(
            state = SettingsPreviewData.storageError,
            onEvent = {},
            onOpenAutoDownload = {},
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
        )
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
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenClearingPreview() {
    PingogramPreview {
        DataStorageScreen(
            state = SettingsPreviewData.storage.copy(isClearingCache = true),
            onEvent = {},
            onOpenAutoDownload = {},
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
        )
    }
}

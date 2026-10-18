package dev.kyralabs.pingogram.feature.settings.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The Data and storage page: hoists [DataStorageViewModel] and shows its results in snackbars: how much clearing the
 * cache freed, or why it failed. [onBack] leaves the page.
 */
@Composable
internal fun DataStorageRoute(
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
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Data and storage (`REQUIREMENTS.md` FR-S1): how much space Pingogram's downloaded files and databases take, as
 * TDLib reports it, and "Clear cache", which deletes the downloaded files after a destructive confirmation
 * ([ClearCacheDialog]) and shows progress while it runs.
 */
@Composable
internal fun DataStorageScreen(
    state: DataStorageUiState,
    onEvent: (DataStorageEvent) -> Unit,
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
        SettingsSection(stringResource(R.string.settings_section_storage_usage)) {
            when (val usage = state.usage) {
                StorageUsageState.Loading -> UsageLoading()
                is StorageUsageState.Content -> UsageItems(usage.usage)
                is StorageUsageState.Error -> UsageError(usage, onRetry = { onEvent(DataStorageEvent.Retry) })
            }
        }
        SettingsSection(stringResource(R.string.settings_section_cache)) {
            ClearCacheItem(isClearing = state.isClearingCache, onClick = { onEvent(DataStorageEvent.ClearCache) })
        }
    }
    if (state.isClearCacheDialogOpen) {
        ClearCacheDialog(
            onConfirm = { onEvent(DataStorageEvent.ConfirmClearCache) },
            onDismiss = { onEvent(DataStorageEvent.DismissClearCache) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UsageLoading() {
    val loading = stringResource(R.string.settings_storage_usage_loading)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

/** The downloaded files and the databases, each with its size. */
@Composable
private fun UsageItems(usage: StorageUsage) {
    val context = LocalContext.current
    val count = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(usage.fileCount)
    ListItem(
        leadingContent = { SettingsItemIcon(R.drawable.ic_folder) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_storage_files_size,
                    Formatter.formatShortFileSize(context, usage.filesSize),
                    pluralStringResource(R.plurals.settings_storage_file_count, usage.fileCount, count),
                ),
            )
        },
        colors = settingsItemColors(),
    ) {
        Text(stringResource(R.string.settings_storage_files))
    }
    ListItem(
        leadingContent = { SettingsItemIcon(R.drawable.ic_database) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_storage_database_size,
                    Formatter.formatShortFileSize(context, usage.databaseSize),
                ),
            )
        },
        colors = settingsItemColors(),
    ) {
        Text(stringResource(R.string.settings_storage_database))
    }
}

/** Why the usage could not be loaded, and a button to try again. */
@Composable
private fun UsageError(
    state: StorageUsageState.Error,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = errorText(state.error),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        FilledTonalButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.settings_retry),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenPreview() {
    PingogramPreview { DataStorageScreen(state = SettingsPreviewData.storage, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenLoadingPreview() {
    PingogramPreview { DataStorageScreen(state = DataStorageUiState(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenErrorPreview() {
    PingogramPreview { DataStorageScreen(state = SettingsPreviewData.storageError, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenDialogPreview() {
    PingogramPreview {
        DataStorageScreen(
            state = SettingsPreviewData.storage.copy(isClearCacheDialogOpen = true),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun DataStorageScreenClearingPreview() {
    PingogramPreview {
        DataStorageScreen(state = SettingsPreviewData.storage.copy(isClearingCache = true), onEvent = {}, onBack = {})
    }
}

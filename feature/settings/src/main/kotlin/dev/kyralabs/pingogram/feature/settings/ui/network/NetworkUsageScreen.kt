package dev.kyralabs.pingogram.feature.settings.ui.network

import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.DataAmount
import dev.kyralabs.pingogram.core.domain.model.DataKind
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import dev.kyralabs.pingogram.feature.settings.ui.titleRes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Network usage: hoists [NetworkUsageViewModel] and shows a reset and errors in snackbars. */
@Composable
internal fun NetworkUsageRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkUsageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    NetworkUsageEffect.WasReset -> resources.getString(R.string.settings_network_usage_reset_done)
                    is NetworkUsageEffect.ShowError -> errorMessage(resources, effect.error, showErrorDetails)
                }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    NetworkUsageScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * How much data Pingogram used (`docs/DESIGN_SYSTEM.md` §6, "Network usage"): the networks as connected toggle
 * buttons, then the chosen network's total and what each kind of data took, sent and received, under "Since Oct 1";
 * last "Reset statistics" in `error`, behind an `AlertDialog`.
 */
@Composable
internal fun NetworkUsageScreen(
    state: NetworkUsageUiState,
    onEvent: (NetworkUsageEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_network_usage),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            NetworkUsageUiState.Loading -> SettingsLoading(stringResource(R.string.settings_network_usage_loading))
            is NetworkUsageUiState.Error -> SettingsError(state.error, onRetry = { onEvent(NetworkUsageEvent.Retry) })
            is NetworkUsageUiState.Content -> UsageContent(state, onEvent)
        }
    }
    if ((state as? NetworkUsageUiState.Content)?.isResetDialogOpen == true) {
        DestructiveDialog(
            icon = null,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_network_usage_reset_title),
                    text = stringResource(R.string.settings_network_usage_reset_text),
                    confirm = stringResource(R.string.settings_network_usage_reset_confirm),
                ),
            onConfirm = { onEvent(NetworkUsageEvent.ConfirmReset) },
            onDismiss = { onEvent(NetworkUsageEvent.DismissReset) },
        )
    }
}

@Composable
private fun UsageContent(
    state: NetworkUsageUiState.Content,
    onEvent: (NetworkUsageEvent) -> Unit,
) {
    SettingsGroup {
        item { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = AutoDownloadNetwork.entries.toImmutableList(),
                    selected = state.network,
                    label = { stringResource(it.titleRes()) },
                    onSelect = { onEvent(NetworkUsageEvent.SelectNetwork(it)) },
                    contentPadding = PaddingValues(MaterialTheme.spacing.small),
                )
            }
        }
    }
    val kinds = state.usage.usage[state.network].orEmpty()
    val total = kinds.values.fold(DataAmount(0, 0), DataAmount::plus)
    SettingsGroup(
        title = stringResource(R.string.settings_network_usage_since, sinceDate(state.usage.sinceDate)),
        footer = stringResource(R.string.settings_network_usage_footer),
    ) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_network_usage_total),
                shapes = shapes,
                supportingContent = { Text(amountText(total)) },
            )
        }
        DataKind.entries.forEach { kind ->
            val amount = kinds[kind] ?: return@forEach
            item(key = kind) { shapes ->
                SettingsRow(
                    title = dataKindName(kind),
                    shapes = shapes,
                    supportingContent = { Text(amountText(amount)) },
                )
            }
        }
    }
    SettingsGroup {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_network_usage_reset),
                shapes = shapes,
                onClick = { onEvent(NetworkUsageEvent.Reset) },
                enabled = !state.isResetting,
                colors = settingsRowColors(contentColor = MaterialTheme.colorScheme.error),
            )
        }
    }
    if (state.isResetting) {
        val resetting = stringResource(R.string.settings_network_usage_resetting)
        LinearWavyProgressIndicator(Modifier.semantics { contentDescription = resetting })
    }
}

/** "Sent 1.2 MB · Received 34 MB". */
@Composable
private fun amountText(amount: DataAmount): String {
    val context = LocalContext.current
    return stringResource(
        R.string.settings_network_usage_amount,
        Formatter.formatShortFileSize(context, amount.sentBytes),
        Formatter.formatShortFileSize(context, amount.receivedBytes),
    )
}

@Composable
private fun dataKindName(kind: DataKind): String =
    stringResource(
        when (kind) {
            DataKind.Messages -> R.string.settings_network_usage_messages
            DataKind.Photos -> R.string.settings_network_usage_photos
            DataKind.Videos -> R.string.settings_network_usage_videos
            DataKind.VoiceMessages -> R.string.settings_network_usage_voice
            DataKind.Music -> R.string.settings_network_usage_music
            DataKind.Files -> R.string.settings_network_usage_files
            DataKind.Other -> R.string.settings_network_usage_other
            DataKind.Calls -> R.string.settings_network_usage_calls
        },
    )

@Composable
private fun sinceDate(seconds: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))
}

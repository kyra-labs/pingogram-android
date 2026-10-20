package dev.kyralabs.pingogram.feature.settings.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSizeLimits
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The automatic download page of [network]: hoists [AutoDownloadViewModel] and shows why a change could not be saved
 * in a snackbar. [onBack] leaves the page.
 */
@Composable
internal fun AutoDownloadRoute(
    network: AutoDownloadNetwork,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoDownloadViewModel =
        hiltViewModel<AutoDownloadViewModel, AutoDownloadViewModel.Factory>(key = "auto-download-$network") {
            it.create(network)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is AutoDownloadEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    AutoDownloadScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * What downloads by itself on one kind of network (`docs/PERFORMANCE.md` §7): a switch each for photos, videos and
 * GIFs, files and voice and video messages, videos and files with a size limit below their switch, an expressive
 * [Slider] through [AutoDownloadSizeLimits] that ticks at each step (`SegmentTick`) and is disabled while its switch is
 * off. While Data Saver holds the network back, a note on top says that nothing downloads by itself meanwhile. A
 * footer says what always downloads. Nothing shows until the settings are read, a matter of milliseconds.
 */
@Composable
internal fun AutoDownloadScreen(
    state: AutoDownloadUiState,
    onEvent: (AutoDownloadEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(state.network.titleRes()),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val rules = state.rules ?: return@SettingsPageScaffold
        if (state.isDataSaverOn) {
            SettingsGroup {
                item { shapes ->
                    SettingsRow(
                        title = stringResource(R.string.settings_auto_download_data_saver),
                        shapes = shapes,
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_auto_download_data_saver_summary),
                            )
                        },
                    )
                }
            }
        }
        RulesGroup(rules) { changed -> onEvent(AutoDownloadEvent.Change(changed)) }
    }
}

@PingogramPreviews
@Composable
private fun AutoDownloadScreenPreview() {
    PingogramPreview { AutoDownloadScreen(state = SettingsPreviewData.autoDownloadWiFi, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun AutoDownloadScreenDataSaverPreview() {
    PingogramPreview {
        AutoDownloadScreen(state = SettingsPreviewData.autoDownloadMobileDataSaver, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun AutoDownloadScreenLoadingPreview() {
    PingogramPreview {
        AutoDownloadScreen(state = AutoDownloadUiState(AutoDownloadNetwork.Roaming), onEvent = {}, onBack = {})
    }
}

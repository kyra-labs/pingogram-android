package dev.kyralabs.pingogram.feature.settings.ui.raisetolisten

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsHeroSwitch
import dev.kyralabs.pingogram.feature.settings.ui.SettingsInfoFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.coroutines.launch

/** The Raise to listen page: hoists [RaiseToListenViewModel] and shows in snackbars what could not be saved. */
@Composable
internal fun RaiseToListenRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RaiseToListenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is RaiseToListenEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    RaiseToListenScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Settings → Chats → Raise to listen (`docs/DESIGN_SYSTEM.md` §13.10 "Pages with a master switch", owner decision
 * 2026-11-19): "Use raise to listen" as the page's master switch ([SettingsHeroSwitch]), "Pause when lowered" below
 * it, dimmed and not tappable while raise to listen is off, and what raising does in the info footer
 * ([SettingsInfoFooter]).
 */
@Composable
internal fun RaiseToListenScreen(
    state: RaiseToListenUiState,
    onEvent: (RaiseToListenEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_raise_to_listen),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            RaiseToListenUiState.Loading -> {
                val loading = stringResource(R.string.settings_appearance_loading)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                }
            }

            is RaiseToListenUiState.Content -> {
                SettingsHeroSwitch(
                    title = stringResource(R.string.settings_raise_to_listen_use),
                    checked = state.settings.enabled,
                    onCheckedChange = { onEvent(RaiseToListenEvent.SetEnabled(it)) },
                )
                SettingsGroup {
                    item { shapes ->
                        SwitchItem(
                            title = stringResource(R.string.settings_pause_when_lowered),
                            summary = stringResource(R.string.settings_pause_when_lowered_summary),
                            checked = state.settings.pauseWhenLowered,
                            shapes = shapes,
                            enabled = state.pauseWhenLoweredEnabled,
                            onCheckedChange = { onEvent(RaiseToListenEvent.SetPauseWhenLowered(it)) },
                        )
                    }
                }
                SettingsInfoFooter(stringResource(R.string.settings_raise_to_listen_info))
            }
        }
    }
}

@PingogramPreviews
@Composable
private fun RaiseToListenScreenOnPreview() {
    PingogramPreview {
        RaiseToListenScreen(
            state = RaiseToListenUiState.Content(RaiseToListenSettings(enabled = true, pauseWhenLowered = true)),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun RaiseToListenScreenOffPreview() {
    PingogramPreview {
        RaiseToListenScreen(
            state = RaiseToListenUiState.Content(RaiseToListenSettings(enabled = false)),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun RaiseToListenScreenLoadingPreview() {
    PingogramPreview { RaiseToListenScreen(state = RaiseToListenUiState.Loading, onEvent = {}, onBack = {}) }
}

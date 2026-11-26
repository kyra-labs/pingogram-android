package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

/** Cleanup suggestions' page: hoists [CleanupViewModel], and says in a snackbar what a choice did or why it failed. */
@Composable
internal fun CleanupRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CleanupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is CleanupEffect.ShowError -> errorMessage(resources, effect.error, showErrorDetails)
                    is CleanupEffect.Done -> resources.getString(effect.action.doneText())
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbar.showSnackbar(message) }
        }
    }
    CleanupScreen(state, viewModel::onEvent, onBack, modifier, snackbar)
}

/**
 * Cleanup suggestions (`REQUIREMENTS.md` FR-X24, `docs/DESIGN_SYSTEM.md` §6 "Cleanup suggestions"), a settings page:
 * its settings ([CleanupSettingsGroup]), then the chat suggested now ([CleanupSuggestionGroup]). Nothing shows until
 * the settings are read.
 */
@Composable
internal fun CleanupScreen(
    state: CleanupUiState,
    onEvent: (CleanupEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_cleanup),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val settings = state.settings ?: return@SettingsPageScaffold
        CleanupSettingsGroup(settings, onEvent)
        CleanupSuggestionGroup(state, onEvent)
    }
}

/** What the snackbar says once [this] went through. */
private fun CleanupAction.doneText(): Int =
    when (this) {
        CleanupAction.Muted -> R.string.settings_cleanup_muted
        CleanupAction.Archived -> R.string.settings_cleanup_archived
        CleanupAction.Left -> R.string.settings_cleanup_left
    }

@PingogramPreviews
@Composable
private fun CleanupScreenPreview(
    @PreviewParameter(CleanupPreviewStates::class) state: CleanupUiState,
) {
    PingogramPreview { CleanupScreen(state, onEvent = {}, onBack = {}) }
}

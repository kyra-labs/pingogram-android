package dev.kyralabs.pingogram.feature.settings.ui.personal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

/** The Personal channel page: hoists [PersonalChannelViewModel]; a refused choice is a snackbar. */
@Composable
internal fun PersonalChannelRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonalChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PersonalChannelEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    PersonalChannelScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Edit profile → Personal channel (`docs/DESIGN_SYSTEM.md` §6, "Personal channel"), a settings page without row icons:
 * "None" and the user's public channels as radio rows in one group, the shown one selected, with a footer on who sees
 * it; a `LinearWavyProgressIndicator` while Telegram takes a choice. Without channels, a note says which can be shown.
 */
@Composable
internal fun PersonalChannelScreen(
    state: PersonalChannelUiState,
    onEvent: (PersonalChannelEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_personal_channel),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            PersonalChannelUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_personal_channel_loading))
            }

            is PersonalChannelUiState.Failed -> {
                SettingsError(state.error, onRetry = { onEvent(PersonalChannelEvent.Retry) })
            }

            is PersonalChannelUiState.Content -> {
                Channels(state, onChoose = { onEvent(PersonalChannelEvent.Choose(it)) })
            }
        }
    }
}

@Composable
private fun Channels(
    state: PersonalChannelUiState.Content,
    onChoose: (Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val choose = { chatId: Long ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChoose(chatId)
    }
    SettingsGroup(
        title = stringResource(R.string.settings_section_personal_channel),
        footer =
            stringResource(
                if (state.channels.isEmpty()) {
                    R.string.settings_personal_channel_none_owned
                } else {
                    R.string.settings_personal_channel_footer
                },
            ),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        item(key = NONE) { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_personal_channel_off),
                selected = state.chosen == 0L,
                enabled = !state.isSaving,
                shapes = shapes,
                onClick = { choose(0L) },
            )
        }
        state.channels.forEach { channel ->
            item(key = channel.id) { shapes ->
                RadioItem(
                    label = channel.title,
                    selected = state.chosen == channel.id,
                    enabled = !state.isSaving,
                    shapes = shapes,
                    onClick = { choose(channel.id) },
                )
            }
        }
    }
    if (state.isSaving) {
        val saving = stringResource(R.string.settings_personal_channel_saving)
        LinearWavyProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .semantics { contentDescription = saving },
        )
    }
}

private const val NONE = "none"

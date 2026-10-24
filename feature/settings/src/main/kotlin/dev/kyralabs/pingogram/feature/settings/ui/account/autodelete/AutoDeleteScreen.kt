package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** Auto-delete messages: hoists [AutoDeleteViewModel] and shows its errors in snackbars. */
@Composable
internal fun AutoDeleteRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoDeleteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AutoDeleteEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    AutoDeleteScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The self-destruct timer of new chats (`docs/DESIGN_SYSTEM.md` §6, "Auto-delete messages"): a settings page with
 * the choices as radio rows in one group, saved as soon as one is picked, with what the timer does as its footer.
 */
@Composable
internal fun AutoDeleteScreen(
    state: AutoDeleteUiState,
    onEvent: (AutoDeleteEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_auto_delete),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            AutoDeleteUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_auto_delete_loading))
            }

            is AutoDeleteUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(AutoDeleteEvent.Retry) })
            }

            is AutoDeleteUiState.Content -> {
                SettingsGroup(
                    title = stringResource(R.string.settings_auto_delete_section),
                    footer = stringResource(R.string.settings_auto_delete_footer),
                    rowsModifier = Modifier.selectableGroup(),
                ) {
                    state.options.forEach { seconds ->
                        item(key = seconds) { shapes ->
                            RadioItem(
                                label = autoDeleteText(seconds),
                                selected = seconds == state.seconds,
                                enabled = !state.isSaving,
                                shapes = shapes,
                                onClick = { onEvent(AutoDeleteEvent.Select(seconds)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A self-destruct timer of [seconds] in words: "Off", "1 day", "1 week", "1 month", or its hours or days. */
@Composable
internal fun autoDeleteText(seconds: Int): String =
    when (seconds) {
        0 -> {
            stringResource(R.string.settings_auto_delete_off)
        }

        SECONDS_PER_DAY -> {
            stringResource(R.string.settings_auto_delete_day)
        }

        SECONDS_PER_WEEK -> {
            stringResource(R.string.settings_auto_delete_week)
        }

        SECONDS_PER_MONTH -> {
            stringResource(R.string.settings_auto_delete_month)
        }

        in 1 until SECONDS_PER_DAY -> {
            durationText(seconds.toLong())
        }

        else -> {
            val days = (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY
            val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
            pluralStringResource(R.plurals.settings_auto_delete_days, days, numbers.format(days))
        }
    }

private const val SECONDS_PER_DAY = 86_400
private const val SECONDS_PER_WEEK = 604_800
private const val SECONDS_PER_MONTH = 2_678_400

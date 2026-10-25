package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.core.domain.repository.CallSettingsRepository
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Data and storage's "Use less data for calls" (`docs/audit/feature-gaps-octogram.md`, P3; [CallSettingsRepository]):
 * null until read, then the choice; a change that cannot be saved is an error.
 */
@HiltViewModel
class CallDataSavingViewModel
    @Inject
    constructor(
        private val repository: CallSettingsRepository,
    ) : ViewModel() {
        val dataSaving: StateFlow<CallDataSaving?> =
            repository.dataSaving.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private val errorChannel = Channel<DomainError>(Channel.BUFFERED)
        val errors: Flow<DomainError> = errorChannel.receiveAsFlow()

        fun onSelect(saving: CallDataSaving) {
            viewModelScope.launch { repository.setDataSaving(saving).onFailure { errorChannel.trySend(it) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * "Calls": "Use less data for calls", the video a call sends at most 360p, as one segment with the choices below it as
 * a connected button group ([ChoiceRow]: Never, Mobile data, Always); nothing until the setting is read.
 */
@Composable
internal fun CallsGroup(
    saving: CallDataSaving?,
    onSelect: (CallDataSaving) -> Unit,
) {
    if (saving == null) return
    SettingsGroup(title = stringResource(R.string.settings_section_calls)) {
        item { shapes ->
            SettingsSegment(shapes) {
                SegmentedListItem(
                    shapes = ListItemDefaults.shapes(),
                    leadingContent = { SettingsItemIcon(UiR.drawable.ic_call) },
                    supportingContent = { Text(stringResource(R.string.settings_call_data_saving_summary)) },
                    colors = nestedItemColors(),
                ) {
                    Text(stringResource(R.string.settings_call_data_saving))
                }
                ChoiceRow(
                    options = CallDataSaving.entries.toImmutableList(),
                    selected = saving,
                    label = { stringResource(it.labelRes()) },
                    onSelect = onSelect,
                    contentPadding =
                        PaddingValues(
                            start = MaterialTheme.spacing.large,
                            end = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.large,
                        ),
                )
            }
        }
    }
}

private fun CallDataSaving.labelRes(): Int =
    when (this) {
        CallDataSaving.Never -> R.string.settings_call_data_saving_never
        CallDataSaving.OnMobileData -> R.string.settings_call_data_saving_mobile
        CallDataSaving.Always -> R.string.settings_call_data_saving_always
    }

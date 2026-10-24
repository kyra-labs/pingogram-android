package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Privacy and security's "Hide phone numbers" (`docs/audit/feature-gaps-octogram.md`, P2), off by default
 * ([PhoneNumberVisibilityRepository]): null until read, then the setting; a change that cannot be saved is an error.
 */
@HiltViewModel
class PhoneNumberVisibilityViewModel
    @Inject
    constructor(
        private val repository: PhoneNumberVisibilityRepository,
    ) : ViewModel() {
        val hidden: StateFlow<Boolean?> =
            repository.hidden.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private val errorChannel = Channel<DomainError>(Channel.BUFFERED)
        val errors: Flow<DomainError> = errorChannel.receiveAsFlow()

        fun onCheckedChange(hidden: Boolean) {
            viewModelScope.launch { repository.setHidden(hidden).onFailure { errorChannel.trySend(it) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * "On screen": "Hide phone numbers" as a switch row whose summary says what it shows instead and what for; nothing
 * until the setting is read ([hidden] null).
 */
@Composable
internal fun OnScreenSection(
    hidden: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (hidden == null) return
    SettingsGroup(title = stringResource(R.string.settings_section_on_screen)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_hide_phone_numbers),
                summary = stringResource(R.string.settings_hide_phone_numbers_summary),
                checked = hidden,
                shapes = shapes,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

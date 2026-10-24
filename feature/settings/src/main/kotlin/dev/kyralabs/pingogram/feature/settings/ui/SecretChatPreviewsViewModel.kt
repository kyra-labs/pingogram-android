package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
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
 * Privacy and security's "Link previews in secret chats" (`docs/audit/feature-gaps-octogram.md`, P2), off by default
 * ([LinkPreviewSettingsRepository]): null until read, then the setting; a change that cannot be saved is an error.
 */
@HiltViewModel
class SecretChatPreviewsViewModel
    @Inject
    constructor(
        private val repository: LinkPreviewSettingsRepository,
    ) : ViewModel() {
        val enabled: StateFlow<Boolean?> =
            repository.secretChatPreviews.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                null,
            )

        private val errorChannel = Channel<DomainError>(Channel.BUFFERED)
        val errors: Flow<DomainError> = errorChannel.receiveAsFlow()

        fun onCheckedChange(enabled: Boolean) {
            viewModelScope.launch { repository.setSecretChatPreviews(enabled).onFailure { errorChannel.trySend(it) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * "Secret chats": "Link previews in secret chats" as a switch row whose summary says that Telegram's servers make the
 * previews; nothing until the setting is read ([enabled] null).
 */
@Composable
internal fun SecretChatsSection(
    enabled: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    if (enabled == null) return
    SettingsGroup(title = stringResource(R.string.settings_section_secret_chats)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_secret_chat_previews),
                summary = stringResource(R.string.settings_secret_chat_previews_summary),
                checked = enabled,
                shapes = shapes,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

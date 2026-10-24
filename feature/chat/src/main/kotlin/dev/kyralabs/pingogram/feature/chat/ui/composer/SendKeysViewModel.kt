package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Which Enter sends the composer's message (Appearance → Chats, [ComposerSettingsRepository]), for the conversation's
 * route to hand its fields through [LocalSendKeys]; apart from `ChatViewModel`, as the setting is the device's, not
 * the chat's.
 */
@HiltViewModel
class SendKeysViewModel
    @Inject
    constructor(
        repository: ComposerSettingsRepository,
    ) : ViewModel() {
        val sendKeys: StateFlow<SendKeys> =
            repository.sendKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SendKeys())

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * Which Enter sends in the [ComposerField]s below: the conversation's route provides the setting; elsewhere (the
 * previews, the sheets of other screens) the defaults hold, a hardware keyboard's Enter sending.
 */
internal val LocalSendKeys = staticCompositionLocalOf { SendKeys() }

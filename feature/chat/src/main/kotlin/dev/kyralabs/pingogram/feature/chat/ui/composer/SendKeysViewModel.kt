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
 * Which Enter sends the composer's message (Settings → Chats, [ComposerSettingsRepository]), and whether messages go
 * without sound by default ([sendsSilently]), for the conversation's route to hand its fields through [LocalSendKeys]
 * and its send buttons through [LocalSendsSilently]; apart from `ChatViewModel`, as the settings are the device's, not
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

        /** "Send silently by default" (`REQUIREMENTS.md` FR-X11). */
        val sendsSilently: StateFlow<Boolean> =
            repository.sendsSilently.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * Which Enter sends in the [ComposerField]s below: the conversation's route provides the setting; elsewhere (the
 * previews, the sheets of other screens) the defaults hold, a hardware keyboard's Enter sending.
 */
internal val LocalSendKeys = staticCompositionLocalOf { SendKeys() }

/**
 * Whether messages go without sound by default ("Send silently by default", FR-X11), so the send menus offer "Send
 * with sound" in place of "Send silently": the conversation's route provides the setting; elsewhere it is off.
 */
internal val LocalSendsSilently = staticCompositionLocalOf { false }

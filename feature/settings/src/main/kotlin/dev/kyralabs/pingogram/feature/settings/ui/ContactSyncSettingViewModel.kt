package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncController
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * "Sync contacts" on the Privacy and security page (`docs/adr/0011-contacts-sync.md`): where contacts sync is
 * ([status]), and its switch, which turns it on or off through [contactSync], the same dialogs and permission request
 * as on the Contacts tab. The page tells it when it shows again ([onStarted]), e.g. back from Android's settings, so a
 * permission granted or taken back there shows at once.
 */
@HiltViewModel
class ContactSyncSettingViewModel
    @Inject
    constructor(
        private val repository: ContactSyncRepository,
    ) : ViewModel() {
        /** Turning sync on and off, with its dialogs and Android's permission request. */
        val contactSync = ContactSyncController(repository, viewModelScope)

        val status: StateFlow<ContactSyncStatus> =
            repository.status.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ContactSyncStatus.Off,
            )

        fun onStarted() = repository.refreshPermission()

        /** The switch was turned to [checked]: the rationale before turning on, the question before turning off. */
        fun onCheckedChange(checked: Boolean) =
            contactSync.onEvent(if (checked) ContactSyncEvent.TurnOn else ContactSyncEvent.TurnOff)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

package dev.kyralabs.pingogram.core.ui.contactsync

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Turning contacts sync on and off (`docs/adr/0011-contacts-sync.md`), the same on the Contacts tab and in Settings →
 * Privacy and security: a view model holds one, with its own [scope], and hands its [controls] and [effects] to
 * [ContactSyncHost].
 *
 * - **On**: the rationale first ([ContactSyncDialog.Rationale]), then Android's request for the permission
 *   ([ContactSyncEffect.RequestPermission]); granted, the repository turns sync on and syncs. Refused for good,
 *   [ContactSyncControls.permanentlyDenied] offers the app's settings instead. Asked again while sync is paused for
 *   the permission ([ContactSyncEvent.Allow]), it skips the rationale, which the user accepted already.
 * - **Off**: a question first ([ContactSyncDialog.TurnOff]), then the repository turns it off, and a snackbar says how
 *   to take the permission back in Android's settings ([ContactSyncEffect.TurnedOff]).
 * - **Sync now**, and errors as snackbars ([ContactSyncEffect.ShowError]).
 */
class ContactSyncController(
    private val repository: ContactSyncRepository,
    private val scope: CoroutineScope,
) {
    private val mutableControls = MutableStateFlow(ContactSyncControls())

    /** The dialog that shows, whether Android refused for good, and whether a change is on its way. */
    val controls: StateFlow<ContactSyncControls> = mutableControls.asStateFlow()

    private val effectChannel = Channel<ContactSyncEffect>(Channel.BUFFERED)

    /** One-off effects: Android's permission request, the app's settings, and snackbars. */
    val effects: Flow<ContactSyncEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: ContactSyncEvent) {
        when (event) {
            ContactSyncEvent.TurnOn -> setDialog(ContactSyncDialog.Rationale)
            ContactSyncEvent.Continue -> continueToPermission()
            ContactSyncEvent.Allow -> requestPermission()
            is ContactSyncEvent.PermissionResult -> onPermission(event.granted, event.canAskAgain)
            ContactSyncEvent.TurnOff -> setDialog(ContactSyncDialog.TurnOff)
            ContactSyncEvent.ConfirmTurnOff -> turnOff()
            ContactSyncEvent.DismissDialog -> setDialog(null)
            ContactSyncEvent.SyncNow -> run(ContactSyncRepository::syncNow) {}
            ContactSyncEvent.OpenSettings -> effectChannel.trySend(ContactSyncEffect.OpenAppSettings)
        }
    }

    private fun setDialog(dialog: ContactSyncDialog?) = mutableControls.update { it.copy(dialog = dialog) }

    private fun continueToPermission() {
        setDialog(null)
        requestPermission()
    }

    private fun requestPermission() {
        val deniedForGood = controls.value.permanentlyDenied
        effectChannel.trySend(
            if (deniedForGood) ContactSyncEffect.OpenAppSettings else ContactSyncEffect.RequestPermission,
        )
    }

    private fun onPermission(
        granted: Boolean,
        canAskAgain: Boolean,
    ) {
        mutableControls.update { it.copy(permanentlyDenied = !granted && !canAskAgain) }
        if (granted) run(ContactSyncRepository::turnOn) {}
    }

    private fun turnOff() {
        setDialog(null)
        run(ContactSyncRepository::turnOff) { effectChannel.trySend(ContactSyncEffect.TurnedOff) }
    }

    /** Runs [action] with the working flag up, then [onSuccess], or a snackbar for its error. */
    private fun run(
        action: suspend ContactSyncRepository.() -> Outcome<Unit>,
        onSuccess: () -> Unit,
    ) {
        if (controls.value.isWorking) return
        mutableControls.update { it.copy(isWorking = true) }
        scope.launch {
            val outcome = repository.action()
            mutableControls.update { it.copy(isWorking = false) }
            when (outcome) {
                is Outcome.Success -> {
                    onSuccess()
                }

                is Outcome.Failure -> {
                    if (outcome.error != DomainError.Silent) {
                        effectChannel.send(ContactSyncEffect.ShowError(outcome.error))
                    }
                }
            }
        }
    }
}

/**
 * What [ContactSyncController] shows: its [dialog]; whether Android refused the permission for good
 * ([permanentlyDenied]), so only its settings can grant it; and whether a change is on its way ([isWorking]).
 */
@Immutable
data class ContactSyncControls(
    val dialog: ContactSyncDialog? = null,
    val permanentlyDenied: Boolean = false,
    val isWorking: Boolean = false,
)

/** The dialogs of contacts sync. */
enum class ContactSyncDialog {
    /** What turning it on does, before Android asks for the permission. */
    Rationale,

    /** Whether to turn it off. */
    TurnOff,
}

/** What the user does with contacts sync, sent to [ContactSyncController.onEvent]. */
sealed interface ContactSyncEvent {
    /** "Turn on": shows the rationale. */
    data object TurnOn : ContactSyncEvent

    /** "Continue" in the rationale: Android's request for the permission. */
    data object Continue : ContactSyncEvent

    /** "Allow" while sync is paused for the permission: Android's request again, or its settings. */
    data object Allow : ContactSyncEvent

    /** Android's answer: [granted], and whether it would ask again ([canAskAgain], its rationale flag). */
    data class PermissionResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : ContactSyncEvent

    /** "Turn off": asks first. */
    data object TurnOff : ContactSyncEvent

    data object ConfirmTurnOff : ContactSyncEvent

    data object DismissDialog : ContactSyncEvent

    data object SyncNow : ContactSyncEvent

    /** The app's settings, where the permission is granted or taken back. */
    data object OpenSettings : ContactSyncEvent
}

/** One-off effects of [ContactSyncController], handled by [ContactSyncHost]. */
sealed interface ContactSyncEffect {
    /** Android's request for `READ_CONTACTS`. */
    data object RequestPermission : ContactSyncEffect

    /** The app's page in Android's settings. */
    data object OpenAppSettings : ContactSyncEffect

    /** Sync was turned off: a snackbar says so, with the app's settings to take the permission back. */
    data object TurnedOff : ContactSyncEffect

    data class ShowError(
        val error: DomainError,
    ) : ContactSyncEffect
}

package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Has the chat list offer to restore a backup after each sign-in on this phone
 * (`docs/adr/0039-local-backup-and-restore.md`, "Restoring"; `BackupSettingsRepository.offerRestorePrompt`): an
 * account that asked for a login (a phone number, a code, a QR code, a password, a name or an email) and is then
 * ready. An account that starts ready, as every signed-in account does after an update, offers nothing; nor does the
 * demo, which is not among the accounts.
 */
@Singleton
class SignInRestoreOffer internal constructor(
    private val steps: Flow<Map<Int, Flow<AuthStep>>>,
    private val settings: BackupSettingsRepository,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        graphs: AccountGraphs,
        settings: BackupSettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        graphs.accounts.map { list -> list.associate { it.slot to it.repositories.authRepository().authStep } },
        settings,
        scope,
    )

    /** The slots of the accounts that asked for a login and are not ready yet. */
    private val signingIn = ConcurrentHashMap.newKeySet<Int>()

    /** Follows every account's sign-in; `PingogramApplication` calls it once per process. Returns at once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            steps
                .flatMapLatest { bySlot -> bySlot.map { (slot, step) -> step.map { slot to it } }.merge() }
                .collect { (slot, step) ->
                    if (step == AuthStep.Ready) {
                        if (signingIn.remove(slot)) settings.offerRestorePrompt()
                    } else if (step.asksForLogin()) {
                        signingIn += slot
                    }
                }
        }
    }
}

private fun AuthStep.asksForLogin(): Boolean =
    this == AuthStep.WaitPhoneNumber ||
        this == AuthStep.WaitEmailAddress ||
        this is AuthStep.WaitCode ||
        this is AuthStep.WaitOtherDeviceConfirmation ||
        this is AuthStep.WaitPassword ||
        this is AuthStep.WaitRegistration ||
        this is AuthStep.WaitEmailCode

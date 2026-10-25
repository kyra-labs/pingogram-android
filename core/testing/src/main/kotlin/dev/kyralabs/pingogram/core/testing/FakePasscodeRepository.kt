package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PasscodeRepository] in memory, for tests of what follows the passcode lock. It keeps the passcode as it is, which
 * only a test may do: the real repository keeps a hash. [passcode] sets one at once.
 */
class FakePasscodeRepository(
    initial: PasscodeSettings = PasscodeSettings(),
    passcode: String? = null,
) : PasscodeRepository {
    private val mutableSettings = MutableStateFlow(initial.copy(isSet = passcode != null))

    /** The passcode that is set, as entered; null without one. */
    var passcode: String? = passcode
        private set

    /** What the writes fail with; null saves. */
    var failure: DomainError? = null

    override val settings: StateFlow<PasscodeSettings> = mutableSettings.asStateFlow()

    override suspend fun setPasscode(
        passcode: String,
        type: PasscodeType,
    ): Outcome<Unit> =
        change {
            this.passcode = passcode
            it.copy(isSet = true, type = type, failedAttempts = 0)
        }

    override suspend fun checkPasscode(passcode: String): Outcome<Boolean> {
        failure?.let { return Outcome.Failure(it) }
        val matches = passcode == this.passcode
        if (this.passcode != null) {
            mutableSettings.value =
                mutableSettings.value.run { copy(failedAttempts = if (matches) 0 else failedAttempts + 1) }
        }
        return Outcome.Success(matches)
    }

    override suspend fun resetFailedAttempts() = change { it.copy(failedAttempts = 0) }

    override suspend fun removePasscode() =
        change {
            passcode = null
            it.copy(isSet = false, failedAttempts = 0)
        }

    override suspend fun setAutoLock(autoLock: AutoLock) = change { it.copy(autoLock = autoLock) }

    override suspend fun setBiometricUnlock(enabled: Boolean) = change { it.copy(biometricUnlock = enabled) }

    override suspend fun setDeviceCredentialUnlock(enabled: Boolean) =
        change { it.copy(deviceCredentialUnlock = enabled) }

    override suspend fun setHideContent(enabled: Boolean) = change { it.copy(hideContent = enabled) }

    private fun change(transform: (PasscodeSettings) -> PasscodeSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        mutableSettings.value = transform(mutableSettings.value)
        return Outcome.Success(Unit)
    }
}

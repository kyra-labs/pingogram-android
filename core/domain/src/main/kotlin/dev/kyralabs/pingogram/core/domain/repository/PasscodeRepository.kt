package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import kotlinx.coroutines.flow.Flow

/**
 * The passcode lock's settings and the passcode (`REQUIREMENTS.md` FR-S1), kept on this device like the other
 * settings: they stay when the user logs out. The passcode is saved only as a salted, slow hash, and never returned;
 * [checkPasscode] compares an entered one with it. Whether the app is locked right now is [AppLockRepository]'s.
 *
 * Every write fails with `DomainError.Unknown` if it cannot be saved (a full disk).
 */
interface PasscodeRepository {
    /**
     * The settings, then every change. Never fails: settings that cannot be read count as the defaults, without a
     * passcode.
     */
    val settings: Flow<PasscodeSettings>

    /**
     * Sets [passcode] of [type], replacing the one set before, and forgets earlier wrong attempts; the other settings
     * stay. [passcode] must be one [type] accepts (`PasscodeType.accepts`).
     */
    suspend fun setPasscode(
        passcode: String,
        type: PasscodeType,
    ): Outcome<Unit>

    /**
     * Whether [passcode] is the passcode that is set (false without one). A wrong one counts as a failed attempt
     * ([PasscodeSettings.failedAttempts]), the right one forgets them.
     */
    suspend fun checkPasscode(passcode: String): Outcome<Boolean>

    /** Forgets the failed attempts, e.g. after the user unlocked with a fingerprint. */
    suspend fun resetFailedAttempts(): Outcome<Unit>

    /** Removes the passcode, which turns the lock off; the other settings stay for the next passcode. */
    suspend fun removePasscode(): Outcome<Unit>

    suspend fun setAutoLock(autoLock: AutoLock): Outcome<Unit>

    suspend fun setBiometricUnlock(enabled: Boolean): Outcome<Unit>

    suspend fun setHideContent(enabled: Boolean): Outcome<Unit>
}

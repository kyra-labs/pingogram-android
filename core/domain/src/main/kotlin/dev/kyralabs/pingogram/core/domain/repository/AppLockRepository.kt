package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Whether the app is locked right now (`REQUIREMENTS.md` FR-S1, §6.2): the passcode lock's state, which the app follows
 * by showing the lock screen instead of everything else. The passcode and the settings are [PasscodeRepository]'s.
 *
 * The app starts locked whenever a passcode is set, and locks again when it was out of sight for longer than the
 * auto-lock delay ([onAppHidden], [onAppShown]). Without an account logged in there is nothing to protect, so it is
 * unlocked then, and stays unlocked after the next login.
 *
 * Wrong passcodes make the next attempts wait: after five, half a minute, doubling with each further one, up to
 * half an hour. The count is saved, so restarting the app starts the wait over rather than ending it.
 */
interface AppLockRepository {
    /** [LockState.Unknown] until the settings are read (a few milliseconds), then whether the app is locked. */
    val state: StateFlow<LockState>

    /** The app left the screen (not for a configuration change): an immediate auto-lock locks it now. */
    fun onAppHidden()

    /** The app is on screen again: it locks if it was hidden for at least the auto-lock delay. */
    fun onAppShown()

    /** Checks [passcode] without changing the lock, e.g. before the passcode settings open; counts as an attempt. */
    suspend fun checkPasscode(passcode: String): PasscodeCheck

    /** Checks [passcode] as [checkPasscode] does, and unlocks the app if it is right. */
    suspend fun unlock(passcode: String): PasscodeCheck

    /**
     * Unlocks the app after the system confirmed the user's fingerprint or face, if biometric unlock is on (it does
     * nothing otherwise), and forgets the failed attempts.
     */
    suspend fun unlockWithBiometrics()

    /** How long the next attempt must wait after too many wrong passcodes; zero if it need not. */
    fun remainingWait(): Duration

    /**
     * For a forgotten passcode: logs out, which deletes the account's data on this device, then removes the passcode.
     * Fails as logging out does, and the passcode stays then.
     */
    suspend fun forgetPasscode(): Outcome<Unit>
}

/** Whether the app is locked. */
enum class LockState {
    /** The passcode settings are still being read: nothing may show yet. */
    Unknown,
    Unlocked,
    Locked,
}

/** The answer to an entered passcode. */
sealed interface PasscodeCheck {
    data object Correct : PasscodeCheck

    /** Wrong, and the next attempt may follow at once. */
    data object Wrong : PasscodeCheck

    /** Wrong, or not checked at all, because of too many wrong attempts: the next must wait [remaining]. */
    data class Wait(
        val remaining: Duration,
    ) : PasscodeCheck

    /** Could not be checked, because of [error] (the settings could not be read or written). */
    data class Failed(
        val error: DomainError,
    ) : PasscodeCheck
}

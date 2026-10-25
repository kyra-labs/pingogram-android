package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.data.di.combinedAuthStep
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeSettings
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * [AppLockRepository] as a small state machine over the passcode settings ([PasscodeRepository]) and the login steps
 * of the device's accounts together ([combinedAuthStep]: signed in while one is), which it follows in the [scope] of
 * the app from its creation on. The lock is the app's, not an account's (`docs/adr/0031-multiple-accounts.md`), so
 * switching accounts neither locks nor unlocks it. Times are measured on
 * [timeSource], since boot with deep sleep included in the app, so a phone that slept through the auto-lock delay
 * locks when it wakes up.
 *
 * - The first settings lock the app if a passcode is set; later settings only unlock it, when the passcode is removed
 *   (setting one leaves the app open: the user is using it).
 * - Any login step but `Ready` (and TDLib's `Initializing`) unlocks it: nothing is left to protect, and the next
 *   login starts unlocked.
 * - Hidden for at least the auto-lock delay, it locks; with [AutoLock.Immediately] as soon as it is hidden.
 *
 * One function per [AppLockRepository] call, and the transitions of the state machine they share (hence
 * `TooManyFunctions` is suppressed).
 */
@Suppress("TooManyFunctions")
@Singleton
class DefaultAppLockRepository internal constructor(
    private val passcodeRepository: PasscodeRepository,
    private val authSteps: Flow<AuthStep>,
    /** Logs every account out, which forgetting the passcode does: what it protected goes. */
    private val logOut: suspend () -> Outcome<Unit>,
    scope: CoroutineScope,
    private val timeSource: TimeSource,
) : AppLockRepository {
    @Inject
    constructor(
        passcodeRepository: PasscodeRepository,
        accounts: Accounts,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        passcodeRepository = passcodeRepository,
        authSteps = accounts.combinedAuthSteps(),
        logOut = accounts::logOutAll,
        scope = scope,
        timeSource = ElapsedRealtimeTimeSource,
    )

    // Changed only in transition(), under its lock, so the state always follows the latest machine.
    private var machine = Machine()
    private val mutableState = MutableStateFlow(LockState.Unknown)

    override val state: StateFlow<LockState> = mutableState.asStateFlow()

    init {
        scope.launch { passcodeRepository.settings.collect(::onSettings) }
        scope.launch { authSteps.collect(::onAuthStep) }
    }

    private fun onSettings(settings: PasscodeSettings) {
        transition { current ->
            val first = current.settings == null
            // After a restart with attempts to wait for, the wait starts over now.
            val restartWait = first && waitAfter(settings.failedAttempts) > Duration.ZERO
            current.copy(
                settings = settings,
                locked = if (first) settings.isSet && !current.signedOut else current.locked && settings.isSet,
                lastFailure = current.lastFailure ?: if (restartWait) timeSource.markNow() else null,
            )
        }
    }

    private fun onAuthStep(step: AuthStep) {
        when (step) {
            AuthStep.Initializing -> Unit
            AuthStep.Ready -> transition { it.copy(signedOut = false) }
            else -> transition { it.copy(signedOut = true, locked = false) }
        }
    }

    private fun current(): Machine = synchronized(this) { machine }

    /** Changes the machine by [change] and publishes the lock state it leads to, at once. */
    private fun transition(change: (Machine) -> Machine) {
        synchronized(this) {
            machine = change(machine)
            mutableState.value = machine.lockState()
        }
    }

    override fun onAppHidden() {
        transition { current ->
            val immediately = current.settings?.autoLock == AutoLock.Immediately
            current.copy(
                hiddenAt = timeSource.markNow(),
                locked = current.locked || (immediately && current.canLock()),
            )
        }
    }

    override fun onAppShown() {
        transition { current ->
            val hiddenAt = current.hiddenAt
            val delay = current.settings?.autoLock?.delay
            val expired = hiddenAt != null && delay != null && hiddenAt.elapsedNow() >= delay
            current.copy(hiddenAt = null, locked = current.locked || (expired && current.canLock()))
        }
    }

    override suspend fun checkPasscode(passcode: String): PasscodeCheck {
        val remaining = remainingWait()
        if (remaining > Duration.ZERO) return PasscodeCheck.Wait(remaining)
        return when (val checked = passcodeRepository.checkPasscode(passcode)) {
            is Outcome.Failure -> {
                PasscodeCheck.Failed(checked.error)
            }

            is Outcome.Success -> {
                // Read back: the repository counted the attempt, and its settings flow may not have told yet.
                val failed = passcodeRepository.settings.first().failedAttempts
                transition { current ->
                    current.copy(
                        settings = current.settings?.copy(failedAttempts = failed),
                        lastFailure = if (checked.value) null else timeSource.markNow(),
                    )
                }
                val wait = waitAfter(failed)
                when {
                    checked.value -> PasscodeCheck.Correct
                    wait > Duration.ZERO -> PasscodeCheck.Wait(wait)
                    else -> PasscodeCheck.Wrong
                }
            }
        }
    }

    override suspend fun unlock(passcode: String): PasscodeCheck =
        checkPasscode(passcode).also { check ->
            if (check == PasscodeCheck.Correct) transition { it.copy(locked = false) }
        }

    override suspend fun unlockWithBiometrics() {
        if (current().settings?.biometricUnlock != true) return
        transition { it.copy(locked = false, lastFailure = null) }
        passcodeRepository.resetFailedAttempts()
    }

    override fun remainingWait(): Duration {
        val current = current()
        val since = current.lastFailure ?: return Duration.ZERO
        val wait = waitAfter(current.settings?.failedAttempts ?: 0)
        return (wait - since.elapsedNow()).coerceAtLeast(Duration.ZERO)
    }

    override suspend fun forgetPasscode(): Outcome<Unit> =
        logOut().onSuccess {
            passcodeRepository.removePasscode()
            transition { it.copy(locked = false, lastFailure = null) }
        }

    /**
     * Everything the lock follows, in one value, so each event changes it atomically.
     *
     * @property settings the passcode settings; null until first read.
     * @property signedOut whether TDLib reported a step other than `Ready` last.
     * @property locked whether the app is locked, while signed in.
     * @property hiddenAt when the app left the screen; null while it shows.
     * @property lastFailure when the last wrong passcode was entered, or the app started with attempts to wait for.
     */
    private data class Machine(
        val settings: PasscodeSettings? = null,
        val signedOut: Boolean = false,
        val locked: Boolean = false,
        val hiddenAt: TimeMark? = null,
        val lastFailure: TimeMark? = null,
    ) {
        fun canLock(): Boolean = settings?.isSet == true && !signedOut

        fun lockState(): LockState =
            when {
                settings == null -> LockState.Unknown
                locked && !signedOut -> LockState.Locked
                else -> LockState.Unlocked
            }
    }

    internal companion object {
        /** Wrong passcodes that need no wait after them. */
        const val FREE_ATTEMPTS = 5
        val FIRST_WAIT = 30.seconds
        val LONGEST_WAIT = 30.minutes

        /** How long the next attempt waits after [failedAttempts] wrong ones in a row. */
        fun waitAfter(failedAttempts: Int): Duration {
            if (failedAttempts < FREE_ATTEMPTS) return Duration.ZERO
            // Doubling from FIRST_WAIT; the exponent stops growing long before it could overflow.
            val doublings = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
            return (FIRST_WAIT * (1 shl doublings)).coerceAtMost(LONGEST_WAIT)
        }

        private const val MAX_DOUBLINGS = 10
    }
}

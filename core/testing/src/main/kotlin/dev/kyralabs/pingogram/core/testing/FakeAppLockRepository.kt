package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * [AppLockRepository] in memory, for tests of the screens that follow the lock: [passcode] is the right one, [state]
 * and [wait] are set directly, and the calls are recorded.
 */
class FakeAppLockRepository(
    state: LockState = LockState.Unlocked,
    var passcode: String = "1234",
) : AppLockRepository {
    private val mutableState = MutableStateFlow(state)

    override val state: StateFlow<LockState> = mutableState.asStateFlow()

    /** What [remainingWait] answers, and what makes every check answer [PasscodeCheck.Wait]. */
    var wait: Duration = Duration.ZERO

    /** What the checks fail with; null checks. */
    var failure: DomainError? = null

    /** What [forgetPasscode] answers. */
    var forgetOutcome: Outcome<Unit> = Outcome.Success(Unit)

    /** The passcodes checked, in order. */
    val checked = mutableListOf<String>()

    var hiddenCount = 0
        private set
    var shownCount = 0
        private set
    var forgetCount = 0
        private set

    fun setState(state: LockState) {
        mutableState.value = state
    }

    override fun onAppHidden() {
        hiddenCount++
    }

    override fun onAppShown() {
        shownCount++
    }

    override suspend fun checkPasscode(passcode: String): PasscodeCheck {
        checked += passcode
        failure?.let { return PasscodeCheck.Failed(it) }
        return when {
            wait > Duration.ZERO -> PasscodeCheck.Wait(wait)
            passcode == this.passcode -> PasscodeCheck.Correct
            else -> PasscodeCheck.Wrong
        }
    }

    override suspend fun unlock(passcode: String): PasscodeCheck =
        checkPasscode(passcode).also { if (it == PasscodeCheck.Correct) mutableState.value = LockState.Unlocked }

    override suspend fun unlockWithBiometrics() {
        mutableState.value = LockState.Unlocked
    }

    override fun remainingWait(): Duration = wait

    override suspend fun forgetPasscode(): Outcome<Unit> {
        forgetCount++
        if (forgetOutcome is Outcome.Success) mutableState.value = LockState.Unlocked
        return forgetOutcome
    }
}

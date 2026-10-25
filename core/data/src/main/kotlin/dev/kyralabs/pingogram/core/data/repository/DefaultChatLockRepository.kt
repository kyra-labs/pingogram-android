package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.LockState
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * [ChatLockRepository] over the stored ids ([LockedChatIdsRepository]), the passcode ([PasscodeRepository]) and the
 * app's lock ([AppLockRepository]), which counts the attempts and waits for both locks. From its creation on it
 * follows, in the [scope] of the app: the app's lock, whose locking closes the chats again (as does a minute out of
 * sight, [RELOCK_DELAY]); the passcode, whose removal
 * unlocks every chat for good; and the login steps, where logging out does the same.
 */
@AccountScoped
class DefaultChatLockRepository internal constructor(
    private val storage: LockedChatIdsRepository,
    private val passcodeRepository: PasscodeRepository,
    private val appLock: AppLockRepository,
    authRepository: AuthRepository,
    scope: CoroutineScope,
    private val timeSource: TimeSource,
) : ChatLockRepository {
    @Inject
    constructor(
        storage: LockedChatIdsRepository,
        passcodeRepository: PasscodeRepository,
        appLock: AppLockRepository,
        authRepository: AuthRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(storage, passcodeRepository, appLock, authRepository, scope, ElapsedRealtimeTimeSource)

    private val mutableUnlocked = MutableStateFlow(false)
    private val writes = Mutex()

    /** When the app left the screen, while it is out of sight. */
    @Volatile private var hiddenAt: TimeMark? = null

    override val unlocked: StateFlow<Boolean> = mutableUnlocked.asStateFlow()

    override val lockedChatIds: Flow<Set<Long>> =
        combine(storage.ids, passcodeRepository.settings) { ids, settings ->
            if (settings.isSet) ids else emptySet()
        }.distinctUntilChanged()

    override val hiddenChatIds: StateFlow<Set<Long>?> =
        combine(lockedChatIds, mutableUnlocked) { ids, open -> if (open) emptySet() else ids }
            .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { appLock.state.collect { if (it == LockState.Locked) mutableUnlocked.value = false } }
        scope.launch {
            passcodeRepository.settings.collect { settings -> if (!settings.isSet) clear() }
        }
        scope.launch {
            authRepository.authStep.collect { step ->
                if (step == AuthStep.LoggingOut || step == AuthStep.Closed) clear()
            }
        }
    }

    override suspend fun setLocked(
        chatIds: Set<Long>,
        locked: Boolean,
    ): Outcome<Unit> =
        writes.withLock {
            if (locked && !passcodeRepository.settings.first().isSet) {
                return@withLock Outcome.Failure(DomainError.Unknown(code = 0, message = "No passcode is set"))
            }
            val current = storage.ids.first()
            storage.setIds(if (locked) current + chatIds else current - chatIds)
        }

    override suspend fun unlock(passcode: String): PasscodeCheck =
        appLock.checkPasscode(passcode).also { check ->
            if (check == PasscodeCheck.Correct) mutableUnlocked.value = true
        }

    override suspend fun unlockWithBiometrics() {
        if (!passcodeRepository.settings.first().biometricUnlock) return
        mutableUnlocked.value = true
        passcodeRepository.resetFailedAttempts()
    }

    override fun onAppHidden() {
        hiddenAt = timeSource.markNow()
    }

    override fun onAppShown() {
        val since = hiddenAt ?: return
        hiddenAt = null
        if (since.elapsedNow() >= RELOCK_DELAY) mutableUnlocked.value = false
    }

    private suspend fun clear() {
        mutableUnlocked.value = false
        writes.withLock { if (storage.ids.first().isNotEmpty()) storage.setIds(emptySet()) }
    }

    internal companion object {
        /**
         * How long the app may be out of sight before the chats lock again: long enough for the photo picker, the
         * camera or a call, short enough that a phone put down keeps them closed. The app's lock, when it locks
         * sooner, closes them with it.
         */
        val RELOCK_DELAY = 1.minutes
    }
}

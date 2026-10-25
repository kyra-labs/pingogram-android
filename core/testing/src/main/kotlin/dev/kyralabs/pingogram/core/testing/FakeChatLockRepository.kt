package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ChatLockRepository] in memory, for tests of what follows locked chats: the locked chats and [unlocked] are set
 * directly ([lock], [setUnlocked]; [known] false leaves [hiddenChatIds] unread), [passcode] is the right one, and
 * [failure] makes the writes fail.
 */
class FakeChatLockRepository(
    locked: Set<Long> = emptySet(),
    unlocked: Boolean = false,
    var passcode: String = "1234",
    known: Boolean = true,
) : ChatLockRepository {
    private val locked = MutableStateFlow(locked)
    override val lockedChatIds: Flow<Set<Long>> = this.locked.asStateFlow()

    private val mutableUnlocked = MutableStateFlow(unlocked)
    override val unlocked: StateFlow<Boolean> = mutableUnlocked.asStateFlow()

    private val hidden = MutableStateFlow<Set<Long>?>(if (known) hiddenOf(locked, unlocked) else null)
    override val hiddenChatIds: StateFlow<Set<Long>?> = hidden.asStateFlow()

    /** What the writes fail with; null saves. */
    var failure: DomainError? = null

    /** What [unlock] answers for a wrong passcode; [PasscodeCheck.Wrong] unless set. */
    var wrongCheck: PasscodeCheck = PasscodeCheck.Wrong

    var hiddenCount = 0
        private set

    fun setUnlocked(unlocked: Boolean) {
        mutableUnlocked.value = unlocked
        refresh()
    }

    /** Locks exactly [chatIds], as if read from the settings. */
    fun lock(chatIds: Set<Long>) {
        locked.value = chatIds
        refresh()
    }

    private fun refresh() {
        hidden.value = hiddenOf(locked.value, mutableUnlocked.value)
    }

    override suspend fun setLocked(
        chatIds: Set<Long>,
        locked: Boolean,
    ): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        lock(if (locked) this.locked.value + chatIds else this.locked.value - chatIds)
        return Outcome.Success(Unit)
    }

    override suspend fun unlock(passcode: String): PasscodeCheck =
        if (passcode == this.passcode) {
            setUnlocked(true)
            PasscodeCheck.Correct
        } else {
            wrongCheck
        }

    override suspend fun unlockWithBiometrics() {
        setUnlocked(true)
    }

    override fun onAppHidden() {
        hiddenCount++
    }

    /** Locks the chats again, as after a while out of sight. */
    override fun onAppShown() {
        if (hiddenCount > 0) setUnlocked(false)
    }
}

private fun hiddenOf(
    locked: Set<Long>,
    unlocked: Boolean,
): Set<Long> = if (unlocked) emptySet() else locked

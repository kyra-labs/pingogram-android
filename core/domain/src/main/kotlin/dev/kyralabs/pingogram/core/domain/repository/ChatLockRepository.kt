package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Locked chats (`docs/adr/0029-locked-chats.md`): chats the user keeps behind the app's passcode, which only works
 * while a passcode is set ([PasscodeRepository]). Locked chats never show in the chat list, its folders or the archive:
 * they have a list of their own, behind the passcode. While locked they show nowhere else either (search, the share
 * and forward sheets), and opening one asks for the passcode or biometrics first. Their notifications say only "New
 * message", and the widget, conversation shortcuts and Direct Share leave them out, whether unlocked or not.
 *
 * One unlock opens all of them until the app was out of sight for a while ([onAppHidden], [onAppShown]) or locks.
 * Turning the passcode off or logging out unlocks every chat for good. Kept on this device only: Telegram has no
 * locked chats, so the user's other apps show these chats as usual.
 */
interface ChatLockRepository {
    /** The locked chats, then every change; empty while no passcode is set. */
    val lockedChatIds: Flow<Set<Long>>

    /** Whether the locked chats are open now. */
    val unlocked: StateFlow<Boolean>

    /**
     * The locked chats hidden right now: every locked chat until they are unlocked, none while they are; null until
     * the settings are read. Kept current from the app's start, so a screen opening later reads it at once.
     */
    val hiddenChatIds: StateFlow<Set<Long>?>

    /** Locks or unlocks [chatIds]; fails as the settings fail to save. Locking needs a passcode set. */
    suspend fun setLocked(
        chatIds: Set<Long>,
        locked: Boolean,
    ): Outcome<Unit>

    /** Checks [passcode] as the app's lock does (attempts and waits counted with it), opening the chats if right. */
    suspend fun unlock(passcode: String): PasscodeCheck

    /** Opens the chats after the system confirmed the user's biometrics, where biometric unlock is on. */
    suspend fun unlockWithBiometrics()

    /** The app left the screen, not for a configuration change. */
    fun onAppHidden()

    /** The app is on screen again: the chats lock again if it was away for a while. */
    fun onAppShown()
}

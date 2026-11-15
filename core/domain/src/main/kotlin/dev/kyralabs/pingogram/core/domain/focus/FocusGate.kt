package dev.kyralabs.pingogram.core.domain.focus

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ActiveFocus
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.coroutines.flow.first

/**
 * What Focus lets through right now (board 31), asked as a notification or a call comes in. Focus changes
 * notifications only: a chat it holds back still shows its notification, silently, and keeps its place and badge in
 * the chat list.
 *
 * Reminders (`docs/DESIGN_SYSTEM.md` §13.13) ring through Focus: they do not ask [allowsChat]; [active] tells them
 * whether Focus is on, should they say they rang through it.
 */
interface FocusGate {
    /** The profile on now, worked out from the clock; null while Focus is off. */
    suspend fun active(): ActiveFocus?

    /** Whether a new message in [chat] may alert now: always while Focus is off. */
    suspend fun allowsChat(chat: Chat): Boolean

    /** Whether a call may ring now from a user who is a contact ([isContact]) or not. */
    suspend fun allowsCall(isContact: Boolean): Boolean
}

/** [FocusGate] over the profiles of [repository], at [clock]'s time in its zone. */
class DefaultFocusGate(
    private val repository: FocusRepository,
    private val clock: WallClock,
) : FocusGate {
    override suspend fun active(): ActiveFocus? =
        FocusClock.active(repository.profiles.first(), repository.manual.first(), clock.now(), clock.zone())

    override suspend fun allowsChat(chat: Chat): Boolean {
        val profile = active()?.profile ?: return true
        return chat.id in profile.allowedChatIds ||
            chat.positions.any { position ->
                (position.list as? ChatListType.Folder)?.id in profile.allowedFolderIds
            }
    }

    override suspend fun allowsCall(isContact: Boolean): Boolean {
        val profile = active()?.profile ?: return true
        return isContact && profile.allowsCallsFromContacts
    }
}

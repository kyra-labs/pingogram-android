package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UnansweredDismissalRepository
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chats.ui.userId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * The chats of the Unanswered page (`REQUIREMENTS.md` FR-X22), as [UnansweredRule] picks them from the main list
 * (archived chats are not in it), the longest waiting first; and [loaded], which loads the rest of the main list, as
 * TDLib knows only the chats loaded so far.
 */
class UnansweredChats
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        userRepository: UserRepository,
        chatLock: ChatLockRepository,
        dismissals: UnansweredDismissalRepository,
        settings: UnansweredSettingsRepository,
        clock: WallClock,
    ) {
        /** The unanswered chats, the oldest last message first, then every change and every minute. */
        val chats: Flow<List<Chat>> =
            combine(
                chatRepository.chatList(ChatListType.Main),
                combine(userRepository.users, userRepository.myUserId, ::Pair),
                combine(chatLock.lockedChatIds, dismissals.dismissed, ::Pair),
                settings.settings,
                clock.minutes,
            ) { list, (users, me), (locked, dismissed), unanswered, now ->
                val rule = UnansweredRule(me, locked, dismissed, unanswered.afterDays, now)
                list
                    .filter { chat -> rule.matches(chat, chat.userId?.let(users::get)) }
                    .sortedBy { it.lastMessage?.date }
            }

        /**
         * Once collected, false while the rest of the main list loads, page by page, until it ends or a page fails;
         * then true.
         */
        val loaded: Flow<Boolean> =
            flow {
                emit(false)
                // Each page reaches the list's flow by itself.
                do {
                    val more = (chatRepository.loadChats(ChatListType.Main) as? Outcome.Success)?.value == true
                } while (more)
                emit(true)
            }
    }

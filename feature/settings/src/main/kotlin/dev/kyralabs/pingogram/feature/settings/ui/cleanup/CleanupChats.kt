package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * The chats Cleanup suggestions offer (`REQUIREMENTS.md` FR-X24), as [CleanupRule] picks them from the main list, the
 * longest unopened first; none while they are turned off. [loaded] loads the rest of the main list, as TDLib knows
 * only the chats loaded so far.
 */
class CleanupChats internal constructor(
    private val sources: CleanupSources,
    settings: Flow<CleanupSettings>,
    clock: WallClock,
) {
    @Inject
    constructor(
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        chatLock: ChatLockRepository,
        openings: ChatOpeningsRepository,
        settings: CleanupSettingsRepository,
        clock: WallClock,
    ) : this(
        CleanupSources(
            chats = chatRepository.chatList(ChatListType.Main),
            users = userRepository.users,
            me = userRepository.myUserId,
            locked = chatLock.lockedChatIds,
            openings = openings.openings,
            loadMore = { (chatRepository.loadChats(ChatListType.Main) as? Outcome.Success)?.value == true },
        ),
        settings.settings,
        clock,
    )

    /** The suggested chats, the longest unopened first, then every change and every minute. */
    val chats: Flow<List<Chat>> =
        combine(
            sources.chats,
            combine(sources.users, sources.me, ::Pair),
            combine(sources.locked, sources.openings, ::Pair),
            settings,
            clock.minutes,
        ) { list, (users, me), (locked, openings), cleanup, now ->
            if (!cleanup.enabled) {
                emptyList()
            } else {
                val rule = CleanupRule(me, locked, openings, CleanupRule.cutoffOf(now, clock.zone(), cleanup.months))
                list
                    .filter { chat -> rule.suggests(chat, (chat.type as? ChatType.Private)?.userId?.let(users::get)) }
                    .sortedBy { openings.lastOpenedOf(it.id) }
            }
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
                val more = sources.loadMore()
            } while (more)
            emit(true)
        }
}

/** What [CleanupChats] reads: the main list, the users, the current user, locked chats and when chats were opened. */
internal class CleanupSources(
    val chats: Flow<List<Chat>>,
    val users: Flow<Map<Long, User>>,
    val me: Flow<Long?>,
    val locked: Flow<Set<Long>>,
    val openings: Flow<ChatOpenings>,
    val loadMore: suspend () -> Boolean = { false },
)

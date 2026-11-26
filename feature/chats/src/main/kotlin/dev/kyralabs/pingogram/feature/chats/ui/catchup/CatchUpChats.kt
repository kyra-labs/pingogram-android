package dev.kyralabs.pingogram.feature.chats.ui.catchup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.catchUpOrder
import dev.kyralabs.pingogram.core.domain.model.isToCatchUp
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The chats Catch-up goes through (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", the owner's board 32): the main list's chats
 * with unread messages, without the locked ones, and without muted chats and channels unless the user included them
 * (owner decision 2026-11-15, `ChatListSettings.catchUpIncludesMuted`); those that mention the user first, then the
 * most recent (owner decision 2026-11-15). The chat list's header offers Catch-up from [MIN_CHATS] of them.
 *
 * Only reads: nothing here marks a chat as read.
 */
class CatchUpChats
    @Inject
    constructor(
        chatRepository: ChatRepository,
        private val settingsRepository: ChatListSettingsRepository,
        chatLock: ChatLockRepository,
    ) {
        /** Whether muted chats and channels are included, then every change. */
        internal val includesMuted: Flow<Boolean> =
            settingsRepository.settings.map { it.catchUpIncludesMuted }.distinctUntilChanged()

        /** The chats to catch up on, in Catch-up's order, then every change. */
        internal val chats: Flow<List<Chat>> =
            combine(
                chatRepository.chatList(ChatListType.Main),
                includesMuted,
                chatLock.lockedChatIds,
            ) { chats, includeMuted, locked -> catchUpOrder(chats, includeMuted, locked) }

        /** How many chats there are to catch up on, then every change: counted, not sorted ([chats]). */
        internal val count: Flow<Int> =
            combine(
                chatRepository.chatList(ChatListType.Main),
                includesMuted,
                chatLock.lockedChatIds,
            ) { chats, includeMuted, locked -> chats.count { it.isToCatchUp(includeMuted, locked) } }
                .distinctUntilChanged()

        /** Saves whether muted chats and channels are included; fails as the settings fail to save. */
        internal suspend fun setIncludesMuted(include: Boolean): Outcome<Unit> =
            settingsRepository.setCatchUpIncludesMuted(include)

        companion object {
            /** The header offers Catch-up from this many chats to catch up on (owner decision 2026-11-15). */
            const val MIN_CHATS = 5
        }
    }

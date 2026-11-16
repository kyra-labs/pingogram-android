package dev.kyralabs.pingogram.feature.chats.ui.keyword

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import dev.kyralabs.pingogram.core.domain.repository.KeywordHitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The chat list's "Keyword alerts" tab (board 30, owner decision 2026-11-15): the chats with a match of the user's
 * keyword alerts in the last week, each with its newest match, the newest first, without locked chats. Empty while
 * Settings → Keyword alerts' "Show Keyword alerts in the chat list" is off, so the tab shows only while there are
 * matches and the user asked for it. Kept on the phone: nothing here asks Telegram for more than the chats.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeywordAlertChats
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        hitRepository: KeywordHitRepository,
        alertsRepository: KeywordAlertsRepository,
        chatLock: ChatLockRepository,
    ) {
        /** Each chat's newest match, the newest first, then every change; empty while the tab is off. */
        internal val matches: Flow<List<KeywordHit>> =
            combine(
                alertsRepository.settings.map { it.showInChatList }.distinctUntilChanged(),
                hitRepository.recent,
                chatLock.lockedChatIds,
            ) { shows, hits, locked ->
                if (!shows) {
                    emptyList()
                } else {
                    hits
                        .sortedWith(compareByDescending<KeywordHit> { it.date }.thenByDescending { it.messageId })
                        .distinctBy { it.chatId }
                        .filterNot { it.chatId in locked }
                }
            }.distinctUntilChanged()

        /** The chats of [matches], in their order, those TDLib knows. */
        internal val chats: Flow<List<Chat>> =
            matches
                .map { hits -> hits.map { it.chatId } }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(ids.map(chatRepository::chat)) { chats -> chats.filterNotNull() }
                    }
                }
    }

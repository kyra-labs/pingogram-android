package dev.kyralabs.pingogram.core.data.repository

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The chats whose conversation is on screen: opened with `openChat` and not closed yet, counted per chat as TDLib
 * counts them (a conversation pushed above the same one in the detail pane opens it twice). TDLib reports no update
 * for this, so [TdlibChatRepository] keeps it as it opens and closes chats, and [TdlibNotificationRepository] reads it:
 * notifications of a chat on screen must not alert.
 */
@Singleton
class OpenChats
    @Inject
    constructor() {
        private val counts = MutableStateFlow<PersistentMap<Long, Int>>(persistentMapOf())

        /** The open chats, then every change. */
        val chatIds: Flow<ImmutableSet<Long>> = counts.map { it.keys.toImmutableSet() }.distinctUntilChanged()

        fun open(chatId: Long) = counts.update { it.putting(chatId, (it[chatId] ?: 0) + 1) }

        /** Closes one opening of [chatId]; a chat not open stays closed. */
        fun close(chatId: Long) =
            counts.update { counts ->
                val count = counts[chatId] ?: return@update counts
                if (count > 1) counts.putting(chatId, count - 1) else counts.removing(chatId)
            }
    }

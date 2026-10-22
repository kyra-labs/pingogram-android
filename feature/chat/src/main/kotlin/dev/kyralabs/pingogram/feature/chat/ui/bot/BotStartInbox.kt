package dev.kyralabs.pingogram.feature.chat.ui.bot

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The start parameters of bot start links (`t.me/bot?start=…`, `docs/adr/0013-deep-links.md`) on their way to the
 * bot's conversation, in memory only: the app [deliver]s one as it opens the chat, and the conversation takes it
 * ([take]), already open or opened by the link, and shows "Start", which sends it once tapped. A later link to the same
 * bot replaces one not taken yet. Nothing is ever sent from here.
 */
@Singleton
class BotStartInbox
    @Inject
    constructor() {
        private val parameters = MutableStateFlow<PersistentMap<Long, String>>(persistentMapOf())

        /** Hands [parameter] to the conversation of bot chat [chatId], whose "Start" sends it. */
        fun deliver(
            chatId: Long,
            parameter: String,
        ) {
            parameters.update { it.putting(chatId, parameter) }
        }

        /** The parameter waiting for chat [chatId]'s conversation, as it comes; null while none does. */
        fun deliveredTo(chatId: Long): Flow<String?> = parameters.map { it[chatId] }.distinctUntilChanged()

        /** Takes the parameter waiting for chat [chatId] if it is still [parameter]: true for the one that took it. */
        internal fun take(
            chatId: Long,
            parameter: String,
        ): Boolean {
            var taken = false
            parameters.update { waiting ->
                taken = waiting[chatId] == parameter
                if (taken) waiting.removing(chatId) else waiting
            }
            return taken
        }
    }

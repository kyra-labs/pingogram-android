package dev.kyralabs.pingogram.feature.chat.ui.share

import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the user shared into Pingogram (`REQUIREMENTS.md` FR-N2, direct share) on its way to the screen that takes it,
 * in memory only: a [SharedContent] holds `content://` URIs whose read grant ends with the process, so nothing of it
 * outlives it.
 *
 * - For the share sheet ([offer], `ShareRoute`): kept by id until its sheet takes it; the [MAX_OFFERS] newest only.
 * - For a chat ([deliver]): from a Direct Share target, or the one chat picked in the sheet. The chat's conversation
 *   takes it into its composer once it is on screen ([claim]), so a conversation behind another screen leaves it to
 *   the one shown; a later share to the same chat replaces one not taken yet.
 * - A reply in another chat ([deliverReply], `REQUIREMENTS.md` FR-C6): a message of one conversation, picked to be
 *   replied to from another chat, which that chat's conversation takes into its composer once on screen
 *   ([claimReply]), as a share; a later reply to the same chat replaces one not taken yet. Kept here rather than in an
 *   inbox of its own, as it goes the same way, from one screen to a chat's conversation, in memory only.
 */
@Singleton
class ShareInbox
    @Inject
    constructor() {
        private val offers = MutableStateFlow<PersistentMap<Long, SharedContent>>(persistentMapOf())
        private val deliveries = MutableStateFlow<PersistentMap<Long, SharedContent>>(persistentMapOf())
        private val replies = MutableStateFlow<PersistentMap<Long, ReplyTarget>>(persistentMapOf())
        private var nextId = 1L

        /** Keeps [content] for the share sheet and returns its id, for `ShareRoute`. */
        @Synchronized
        fun offer(content: SharedContent): Long {
            val id = nextId++
            offers.update { offers ->
                val kept = if (offers.size < MAX_OFFERS) offers else offers.remove(offers.keys.min())
                kept.put(id, content)
            }
            return id
        }

        /** Hands [content] to the conversation of chat [chatId], which puts it into its composer once on screen. */
        fun deliver(
            chatId: Long,
            content: SharedContent,
        ) {
            deliveries.update { it.put(chatId, content) }
        }

        /** The share offered as [shareId], which only one sheet takes; null once taken, or after process death. */
        internal fun take(shareId: Long): SharedContent? = offers.getAndUpdate { it.remove(shareId) }[shareId]

        /** What waits for chat [chatId]'s conversation, as it comes; null while nothing does. */
        internal fun deliveredTo(chatId: Long): Flow<SharedContent?> =
            deliveries.map { it[chatId] }.distinctUntilChanged()

        /** Takes [content], delivered to chat [chatId], if it still waits: true for the one conversation it goes to. */
        internal fun claim(
            chatId: Long,
            content: SharedContent,
        ): Boolean {
            var claimed = false
            deliveries.update { deliveries ->
                claimed = deliveries[chatId] === content
                if (claimed) deliveries.remove(chatId) else deliveries
            }
            return claimed
        }

        /** Hands [reply], a message of another chat, to the conversation of chat [chatId], to reply to it there. */
        fun deliverReply(
            chatId: Long,
            reply: ReplyTarget,
        ) {
            replies.update { it.putting(chatId, reply) }
        }

        /** The reply that waits for chat [chatId]'s conversation, as it comes; null while none does. */
        internal fun repliesTo(chatId: Long): Flow<ReplyTarget?> = replies.map { it[chatId] }.distinctUntilChanged()

        /** Takes [reply], delivered to chat [chatId], if it still waits: true for the one conversation it goes to. */
        internal fun claimReply(
            chatId: Long,
            reply: ReplyTarget,
        ): Boolean {
            var claimed = false
            replies.update { waiting ->
                claimed = waiting[chatId] === reply
                if (claimed) waiting.removing(chatId) else waiting
            }
            return claimed
        }

        private companion object {
            /** Shares offered and not taken (a sheet that never opened) are forgotten beyond this many. */
            const val MAX_OFFERS = 4
        }
    }

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.DemoRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user left each conversation, for as long as the process lives: the newest message its list showed, by
 * account, chat and topic. A conversation opened again opens there ([ChatWindow]), unless something new is unread.
 * Two accounts in the same chat each keep their own place, and the demo its own, so switching accounts never carries
 * one account's place over to another; the [account] is the one the screens show when the conversation opens.
 */
@Singleton
class ChatPlaces internal constructor(
    private val account: () -> Int = { 0 },
) {
    @Inject
    constructor(graphs: AccountGraphs, demo: DemoRepository) : this(
        account = { if (demo.isDemo.value) DEMO_ACCOUNT else graphs.activeSlot.value },
    )

    private val places = ConcurrentHashMap<Key, Long>()

    /**
     * The place of chat [chatId]'s conversation (of its [topic], if not null) in the account the screens show now,
     * also kept in [savedState].
     */
    internal fun place(
        chatId: Long,
        topic: MessageTopic?,
        savedState: SavedStateHandle,
    ): Place = Place(Key(account(), chatId, topic), savedState)

    /**
     * One conversation's place: the newest message its list shows, kept in its [savedState] for after process death
     * ([ChatWindow.KEY_SHOWN_MESSAGE]) and among the [places] for when it opens again in this process.
     */
    internal inner class Place(
        private val key: Key,
        private val savedState: SavedStateHandle,
    ) {
        /** The newest message the list showed when the user last left the conversation; null if never shown. */
        val left: Long? get() = places[key]

        /** The newest message the list shows is [messageId]. */
        fun shown(messageId: Long) {
            savedState[ChatWindow.KEY_SHOWN_MESSAGE] = messageId
            places[key] = messageId
        }
    }

    /** A conversation of [account] (a slot, or [DEMO_ACCOUNT]): chat [chatId], and its [topic] if any. */
    internal data class Key(
        val account: Int,
        val chatId: Long,
        val topic: MessageTopic?,
    )

    internal companion object {
        /** The demo's key, apart from every account's slot (0 and up). */
        const val DEMO_ACCOUNT = -1
    }
}

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user left each conversation, for as long as the process lives: the newest message its list showed, by
 * chat and topic. A conversation opened again opens there ([ChatWindow]), unless something new is unread.
 */
@Singleton
class ChatPlaces
    @Inject
    constructor() {
        private val places = ConcurrentHashMap<Key, Long>()

        /** The place of chat [chatId]'s conversation (of its [topic], if not null), also kept in [savedState]. */
        internal fun place(
            chatId: Long,
            topic: MessageTopic?,
            savedState: SavedStateHandle,
        ): Place = Place(Key(chatId, topic), savedState)

        /**
         * One conversation's place: the newest message its list shows, kept in its [savedState] for after process
         * death ([ChatWindow.KEY_SHOWN_MESSAGE]) and among the [places] for when it opens again in this process.
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

        internal data class Key(
            val chatId: Long,
            val topic: MessageTopic?,
        )
    }

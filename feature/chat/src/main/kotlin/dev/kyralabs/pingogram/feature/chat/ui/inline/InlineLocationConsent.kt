package dev.kyralabs.pingogram.feature.chat.ui.inline

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
 * Whether the user lets each inline bot that asks for it know their location (td_api.tl `userTypeBot.need_location`),
 * by bot user id, as they answered the question before the bot's first query: in memory, for the process, shared by
 * every conversation, so a bot is asked again after Pingogram restarts. Nothing is ever sent to a bot from here; an
 * answer only says whether its queries carry the location.
 */
@Singleton
class InlineLocationConsent
    @Inject
    constructor() {
        private val answers = MutableStateFlow<PersistentMap<Long, Boolean>>(persistentMapOf())

        /** Whether bot [botUserId] may know the location: true or false once answered, null before. */
        fun answer(botUserId: Long): Boolean? = answers.value[botUserId]

        /** The answer for bot [botUserId] as it comes; null until it does. */
        fun answers(botUserId: Long): Flow<Boolean?> = answers.map { it[botUserId] }.distinctUntilChanged()

        /** Keeps the user's answer for bot [botUserId]: [share] lets its queries carry the location. */
        fun set(
            botUserId: Long,
            share: Boolean,
        ) {
            answers.update { it.putting(botUserId, share) }
        }
    }

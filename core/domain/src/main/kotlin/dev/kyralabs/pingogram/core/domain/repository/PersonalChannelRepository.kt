package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.collections.immutable.ImmutableList

/**
 * The channel the user shows on their profile (Edit profile): which can be shown and which is. The one shown is
 * `EditableProfile.personalChatId`, read with the profile.
 */
interface PersonalChannelRepository {
    /**
     * The channels the user may show on their profile (`getSuitablePersonalChats`): public channels they own, by chat
     * id, in Telegram's order.
     */
    suspend fun candidates(): Outcome<ImmutableList<Long>>

    /** Shows channel [chatId] on the user's profile (`setPersonalChat`); 0 shows none. */
    suspend fun setPersonalChannel(chatId: Long): Outcome<Unit>
}

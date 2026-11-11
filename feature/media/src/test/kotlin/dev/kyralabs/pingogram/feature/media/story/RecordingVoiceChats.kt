package dev.kyralabs.pingogram.feature.media.story

import dev.kyralabs.pingogram.core.ui.component.VoiceChats
import kotlinx.coroutines.flow.flowOf

/** Voice chats that join nothing and record the live stories watched ([watched]: chat and group call). */
internal class RecordingVoiceChats : VoiceChats {
    val watched = mutableListOf<Pair<Long, Int>>()

    override fun voiceChat(chatId: Long) = flowOf(null)

    override fun join(chatId: Long) = Unit

    override fun start(chatId: Long) = Unit

    override fun joinConference(link: String) = Unit

    override fun joinInvitation(
        chatId: Long,
        messageId: Long,
    ) = Unit

    override fun watchLiveStory(
        chatId: Long,
        groupCallId: Int,
    ) {
        watched += chatId to groupCallId
    }
}

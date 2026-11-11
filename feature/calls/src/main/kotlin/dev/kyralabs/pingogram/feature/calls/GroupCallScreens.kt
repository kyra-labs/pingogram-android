package dev.kyralabs.pingogram.feature.calls

import android.content.Context
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.ui.component.VoiceChatSummary
import dev.kyralabs.pingogram.core.ui.component.VoiceChats
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallActivity
import kotlinx.coroutines.flow.Flow

/**
 * [Calls]' [VoiceChats]: a chat's voice chat as its screens show it, and the voice chat's screen ([GroupCallActivity])
 * opened to join or start one, a conference call, or to watch a live story (board 12). The screen asks for the
 * microphone where needed, then joins.
 */
internal class GroupCallScreens(
    private val context: Context,
    private val groupCalls: GroupCallRepository,
    private val manager: GroupCallManager,
) : VoiceChats {
    override fun voiceChat(chatId: Long): Flow<VoiceChatSummary?> = voiceChatSummary(chatId, groupCalls, manager.call)

    override fun join(chatId: Long) = GroupCallActivity.open(context, chatId)

    override fun start(chatId: Long) = GroupCallActivity.open(context, chatId, start = true)

    override fun joinConference(link: String) = GroupCallActivity.openConference(context, link)

    override fun joinInvitation(
        chatId: Long,
        messageId: Long,
    ) = GroupCallActivity.openInvitation(context, chatId, messageId)

    override fun watchLiveStory(
        chatId: Long,
        groupCallId: Int,
    ) = GroupCallActivity.openLiveStory(context, chatId, groupCallId)
}

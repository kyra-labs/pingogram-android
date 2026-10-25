package dev.kyralabs.pingogram.feature.chat.ui.thread

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// The repositories of a conversation that shows a forum topic or a message thread (REQUIREMENTS.md FR-C9): the same
// calls, for the messages there. Every message sent goes there, whatever the part of the conversation that sends it.

/**
 * [messages] of [topic]: its history and pages, with [head] (the messages that start a message thread) as its oldest
 * messages once the history reaches the start, and every text sent into it.
 */
internal class TopicMessageRepository(
    private val messages: MessageRepository,
    private val topic: MessageTopic,
    private val head: Flow<List<Message>>,
) : MessageRepository by messages {
    override fun history(
        chatId: Long,
        topic: MessageTopic?,
    ): Flow<ChatHistory> = combine(messages.history(chatId, this.topic), head, ::withHead)

    override suspend fun loadOlder(
        chatId: Long,
        topic: MessageTopic?,
    ) = messages.loadOlder(chatId, this.topic)

    override suspend fun loadNewer(
        chatId: Long,
        topic: MessageTopic?,
    ) = messages.loadNewer(chatId, this.topic)

    override suspend fun loadAround(
        chatId: Long,
        messageId: Long,
        topic: MessageTopic?,
    ) = messages.loadAround(chatId, messageId, this.topic)

    override suspend fun loadNewest(
        chatId: Long,
        topic: MessageTopic?,
    ) = messages.loadNewest(chatId, this.topic)

    override suspend fun sendText(
        chatId: Long,
        text: FormattedText,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = messages.sendText(chatId, text, options.copy(topic = topic), replyTo)
}

/** The history with [head] after its oldest message, once it reaches the start: older than every reply. */
private fun withHead(
    history: ChatHistory,
    head: List<Message>,
): ChatHistory {
    if (!history.reachedStart || head.isEmpty()) return history
    val oldestId = history.messages.lastOrNull()?.id ?: Long.MAX_VALUE
    val older = head.filter { it.id < oldestId }.sortedByDescending { it.id }
    return history.copy(messages = (history.messages + older).toImmutableList())
}

/** [attachments] sent into [topic]. */
internal class TopicAttachmentRepository(
    private val attachments: AttachmentRepository,
    private val topic: MessageTopic,
) : AttachmentRepository by attachments {
    override suspend fun send(
        chatId: Long,
        attachments: List<Attachment>,
        caption: FormattedText,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = this.attachments.send(chatId, attachments, caption, options.copy(topic = topic), replyTo)

    override suspend fun sendContact(
        chatId: Long,
        contact: Contact,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = attachments.sendContact(chatId, contact, options.copy(topic = topic), replyTo)

    override suspend fun sendLocation(
        chatId: Long,
        location: DeviceLocation,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = attachments.sendLocation(chatId, location, options.copy(topic = topic), replyTo)

    override suspend fun sendSticker(
        chatId: Long,
        sticker: Sticker,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = attachments.sendSticker(chatId, sticker, options.copy(topic = topic), replyTo)

    override suspend fun sendAnimation(
        chatId: Long,
        animation: Animation,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = attachments.sendAnimation(chatId, animation, options.copy(topic = topic), replyTo)

    override suspend fun sendPoll(
        chatId: Long,
        poll: NewPoll,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = attachments.sendPoll(chatId, poll, options.copy(topic = topic), replyTo)
}

/** [voiceNotes] sent into [topic]. */
internal class TopicVoiceNoteRepository(
    private val voiceNotes: VoiceNoteRepository,
    private val topic: MessageTopic,
) : VoiceNoteRepository by voiceNotes {
    override suspend fun sendVoiceNote(
        chatId: Long,
        voiceNote: RecordedVoiceNote,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = voiceNotes.sendVoiceNote(chatId, voiceNote, options.copy(topic = topic), replyTo)
}

/** [videoNotes] sent into [topic]. */
internal class TopicVideoNoteRepository(
    private val videoNotes: VideoNoteRepository,
    private val topic: MessageTopic,
) : VideoNoteRepository by videoNotes {
    override suspend fun sendVideoNote(
        chatId: Long,
        videoNote: RecordedVideoNote,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = videoNotes.sendVideoNote(chatId, videoNote, options.copy(topic = topic), replyTo)
}

/** [inlineBots] whose results are sent into [topic]. */
internal class TopicInlineBotRepository(
    private val inlineBots: InlineBotRepository,
    private val topic: MessageTopic,
) : InlineBotRepository by inlineBots {
    override suspend fun send(
        chatId: Long,
        queryId: Long,
        resultId: String,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ) = inlineBots.send(chatId, queryId, resultId, options.copy(topic = topic), replyTo)
}

/** [chats] whose messages viewed on screen are viewed in [topic], which reads them there. */
internal class TopicChatRepository(
    private val chats: ChatRepository,
    private val topic: MessageTopic,
) : ChatRepository by chats {
    override suspend fun viewMessages(
        chatId: Long,
        messageIds: List<Long>,
        topic: MessageTopic?,
    ) = chats.viewMessages(chatId, messageIds, this.topic)
}

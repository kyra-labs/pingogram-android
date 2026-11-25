package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ChecklistRepository
import dev.kyralabs.pingogram.core.domain.repository.GiveawayRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChatChecklists
import dev.kyralabs.pingogram.feature.chat.ui.dates.ChatDateTimes
import dev.kyralabs.pingogram.feature.chat.ui.dates.DateTimeSheet
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.ChatGiveaways
import dev.kyralabs.pingogram.feature.chat.ui.poll.ChatPolls
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollOptionAdding
import dev.kyralabs.pingogram.feature.chat.ui.rich.ChatRichMessages
import dev.kyralabs.pingogram.feature.chat.ui.transcription.ChatTranscripts
import dev.kyralabs.pingogram.feature.chat.ui.transcription.TranscriptionRepositories
import dev.kyralabs.pingogram.feature.chat.ui.translation.ChatSummaries
import dev.kyralabs.pingogram.feature.chat.ui.translation.ChatTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslationRepositories
import kotlinx.coroutines.flow.Flow

/**
 * The parts of a conversation that act on what single messages hold: translations and summaries, transcripts of voice
 * messages, polls and the options added to them, checklists, rich messages, giveaways and tapped dates. Each is a class
 * of its own; this holds them, made in the order [ChatViewModel] made them, which reads them from here.
 */
internal class ChatMessageFeatures(
    parts: ChatPartContext,
    repositories: MessageFeatureRepositories,
    users: UserRepository,
    chats: ChatRepository,
    chat: Flow<Chat>,
    describeDate: (unixTime: Long) -> DateTimeSheet,
) {
    /** The copies the overlay stages of the conversation's state keep from one state to the next. */
    val overlays = OverlayStages()

    val translation =
        ChatTranslation(
            chatId = parts.chatId,
            repositories = repositories.translation,
            chat = chat,
            history = parts.history,
            messageOf = parts.messageOf,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val summaries =
        ChatSummaries(
            chatId = parts.chatId,
            repositories = repositories.translation,
            history = parts.history,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    /** Voice message transcription on the device; none where tests leave it out. */
    val transcripts =
        repositories.transcription?.let { transcription ->
            ChatTranscripts(
                chatId = parts.chatId,
                repositories = transcription,
                history = parts.history,
                savedState = parts.savedState,
                scope = parts.scope,
                onEffect = parts.onEffect,
            )
        }

    val polls =
        ChatPolls(
            chatId = parts.chatId,
            repository = repositories.polls,
            users = users,
            chats = chats,
            history = parts.history,
            messageOf = parts.messageOf,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val pollOptions =
        PollOptionAdding(
            chatId = parts.chatId,
            repository = repositories.polls,
            messageOf = parts.messageOf,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val checklists =
        ChatChecklists(
            chatId = parts.chatId,
            repository = repositories.checklists,
            messageOf = parts.messageOf,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val richMessages =
        ChatRichMessages(
            chatId = parts.chatId,
            messages = repositories.messages,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val giveaways =
        ChatGiveaways(
            chatId = parts.chatId,
            repository = repositories.giveaways,
            chats = chats,
            history = parts.history,
            savedState = parts.savedState,
            scope = parts.scope,
            onEffect = parts.onEffect,
        )

    val dateTimes = ChatDateTimes(describe = describeDate, savedState = parts.savedState, onEffect = parts.onEffect)
}

/** Where [ChatMessageFeatures]' parts read and write: the history and texts sent, and what each feature reaches. */
internal class MessageFeatureRepositories(
    val messages: MessageRepository,
    val translation: TranslationRepositories,
    val transcription: TranscriptionRepositories?,
    val polls: PollRepository,
    val checklists: ChecklistRepository,
    val giveaways: GiveawayRepository,
)

package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.component.LocalLockedChatGate
import dev.kyralabs.pingogram.feature.chat.ui.topics.ChatOpening
import dev.kyralabs.pingogram.feature.chat.ui.topics.ChatOpeningViewModel
import dev.kyralabs.pingogram.feature.chat.ui.topics.ForumTopicsRoute

/**
 * What opening chat [chatId] shows (`REQUIREMENTS.md` FR-C9): with a [topic], that forum topic's or message thread's
 * conversation; otherwise what [ChatOpeningViewModel] finds: the conversation ([ConversationRoute]), a forum's topic
 * list ([ForumTopicsRoute]), whose topics open through [onOpenThread], or the conversation of the topic a found message
 * ([messageId]) is in. A message's thread (its comments or replies) opens through [onOpenThread] too. Until that is
 * known, a frame or two, it draws the conversation's own first frame (its top bar and empty sheet), so the screen does
 * not change as it decides. Profiles (a private chat's title, a sender, a mention) open through [onOpenProfile],
 * a group's or channel's info page (its title) through [onOpenChatInfo], the chat's scheduled messages (its bar)
 * through [onOpenScheduledMessages]. With [focusComposer], the conversation's composer takes the focus once it shows.
 * The next unread channel, which a read channel offers, opens through [onOpenNextChat] in this chat's place, or
 * through [onOpenChat] without it.
 *
 * A locked chat (`docs/adr/0029-locked-chats.md`) shows the passcode entry in its place until it is opened, through
 * `core:ui`'s `LocalLockedChatGate`, which `:app` provides.
 */
@Suppress("LongParameterList") // The chat and where in it, and what it opens elsewhere.
@Composable
internal fun ChatRoute(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    topic: MessageTopic? = null,
    onOpenThread: (chatId: Long, topic: MessageTopic) -> Unit = { _, _ -> },
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    focusComposer: Boolean = false,
    onOpenScheduledMessages: ((chatId: Long) -> Unit)? = null,
    onOpenNextChat: ((chatId: Long) -> Unit)? = null,
) {
    LocalLockedChatGate.current(chatId) {
        OpenedChat(
            chatId = chatId,
            onBack = onBack,
            onOpenMedia = onOpenMedia,
            modifier = modifier,
            onOpenChat = onOpenChat,
            messageId = messageId,
            topic = topic,
            onOpenThread = onOpenThread,
            onOpenProfile = onOpenProfile,
            onOpenChatInfo = onOpenChatInfo,
            focusComposer = focusComposer,
            onOpenScheduledMessages = onOpenScheduledMessages,
            onOpenNextChat = onOpenNextChat,
        )
    }
}

/** [ChatRoute] once the chat may show. */
@Suppress("LongParameterList") // As ChatRoute's.
@Composable
private fun OpenedChat(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    topic: MessageTopic? = null,
    onOpenThread: (chatId: Long, topic: MessageTopic) -> Unit = { _, _ -> },
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    focusComposer: Boolean = false,
    onOpenScheduledMessages: ((chatId: Long) -> Unit)? = null,
    onOpenNextChat: ((chatId: Long) -> Unit)? = null,
) {
    val conversation: @Composable (MessageTopic?) -> Unit = { shown ->
        ConversationRoute(
            chatId,
            onBack,
            onOpenMedia,
            modifier,
            onOpenChat,
            messageId,
            shown,
            onOpenThread,
            onOpenProfile,
            onOpenChatInfo,
            focusComposer = focusComposer,
            onOpenScheduledMessages = onOpenScheduledMessages,
            onOpenNextChat = onOpenNextChat,
        )
    }
    if (topic != null) {
        conversation(topic)
        return
    }
    val opening: ChatOpeningViewModel =
        hiltViewModel<ChatOpeningViewModel, ChatOpeningViewModel.Factory>(key = "opening-$chatId") {
            it.create(chatId, messageId)
        }
    val state by opening.opening.collectAsStateWithLifecycle()
    when (val shown = state) {
        ChatOpening.Unknown -> {
            ChatScreen(state = ChatUiState(), onEvent = {}, onBack = onBack, modifier = modifier)
        }

        ChatOpening.Conversation -> {
            conversation(null)
        }

        ChatOpening.Topics -> {
            ForumTopicsRoute(
                chatId = chatId,
                onBack = onBack,
                onOpenTopic = { topicId -> onOpenThread(chatId, MessageTopic.Forum(topicId)) },
                modifier = modifier,
            )
        }

        is ChatOpening.Topic -> {
            conversation(shown.topic)
        }
    }
}

/**
 * Tells [viewModel] when the conversation is started and stopped, which opens and closes the chat in TDLib
 * (`docs/PERFORMANCE.md` §3), following the screen's lifecycle ([ConversationRoute]).
 */
@Composable
internal fun ChatStartedEffect(viewModel: ChatViewModel) {
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ChatEvent.Started)
        onStopOrDispose { viewModel.onEvent(ChatEvent.Stopped) }
    }
}

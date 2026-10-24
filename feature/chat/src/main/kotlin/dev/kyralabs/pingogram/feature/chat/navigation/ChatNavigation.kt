package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chat.ui.ChatRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute as ChatDestination

/**
 * Adds the conversation of a chat, [ChatDestination], to the graph, opened at its message if the route names one, of
 * its forum topic or message thread if it names one (a forum without opens its topic list); [onBack] leaves it, and
 * [onOpenMedia] opens a tapped photo, video or GIF of the chat in the media viewer, which it grows into (the
 * destination provides its animated visibility scope for that container transform). [onOpenChat] opens the chat
 * messages were forwarded to, from the snackbar after forwarding, and a group or channel a mention names;
 * [onOpenProfile] a user's profile, from the title of a private chat, a sender's avatar or name, or a mention;
 * [onOpenChatInfo] the info page of a group or channel, from its title; [onOpenThread] pushes a forum topic or a
 * message thread opened from it, a [ChatDestination] too. [onOpenNextChat] opens the next unread channel, which a
 * read channel offers, in the conversation's place ([onOpenChat] without it).
 */
fun NavGraphBuilder.chatScreen(
    onBack: () -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit = {},
    onOpenThread: (ChatDestination) -> Unit = {},
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    onOpenNextChat: ((chatId: Long) -> Unit)? = null,
) {
    composable<ChatDestination> { entry ->
        val route = entry.toRoute<ChatDestination>()
        ProvideNavAnimatedVisibilityScope(this) {
            ChatRoute(
                chatId = route.chatId,
                messageId = route.messageId,
                topic = route.topic,
                onBack = onBack,
                onOpenMedia = onOpenMedia,
                onOpenChat = onOpenChat,
                onOpenThread = { chatId, topic -> onOpenThread(destinationOf(chatId, topic)) },
                onOpenProfile = onOpenProfile,
                onOpenChatInfo = onOpenChatInfo,
                onOpenNextChat = onOpenNextChat,
            )
        }
    }
}

/**
 * The conversation of chat [chatId] in the detail pane of the Chats tab's list-detail layout (`docs/ARCHITECTURE.md`,
 * Navigation): the same screen as [ChatDestination], with the top bar's back button only where [onBack] is given,
 * i.e. when the pane shows alone, opened at [messageId] if not null (a message found by search). [onOpenMedia] opens a
 * tapped photo, video or GIF in the media viewer, full screen above the whole layout; [onOpenChat] the chat messages
 * were forwarded to; [onOpenProfile] a user's profile and [onOpenChatInfo] a group's or channel's info page, which the
 * layout pushes above itself or shows beside the conversation.
 *
 * Each chat, and each message it is opened at, gets a fresh composition and view model: its own saved UI state (the
 * scroll position), and a [ViewModelStoreOwner][androidx.lifecycle.ViewModelStoreOwner] of its own
 * ([rememberViewModelStoreOwner]), which keeps the view model across rotations and clears it as soon as the pane shows
 * another chat or leaves, closing the chat first. In the store of the Chats tab every chat ever shown would keep its
 * view model, and its messages, until logout (`docs/PERFORMANCE.md` §3). The cost: the view model's `SavedStateHandle`
 * (where the unread divider was) survives rotations but not process death, since the tab's saved state holds only the
 * handles of its own store.
 *
 * A forum topic or message thread opened in the pane (`REQUIREMENTS.md` FR-C9: a topic of the forum's topic list, a
 * post's comments) shows in the pane, in place of what opened it, on Material's shared axis X ([PaneThreads]); back,
 * and its top bar's back button, return to it. They are saved with the pane, so they survive rotation and process
 * death.
 *
 * With [focusComposer] (the chat was opened from the list with a hardware keyboard, `docs/DESIGN_SYSTEM.md` §9), the
 * composer's text field takes the focus once it shows, as Google Messages does on tablets; a chat without a composer
 * leaves the focus where it was.
 */
@Suppress("LongParameterList") // The chat and where in it, what it opens elsewhere, and the composer's focus.
@Composable
fun ChatPane(
    chatId: Long,
    onBack: (() -> Unit)?,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (chatId: Long) -> Unit = {},
    messageId: Long? = null,
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    focusComposer: Boolean = false,
) {
    key(chatId, messageId) {
        var threads by rememberSaveable(stateSaver = PaneThreads.Saver) { mutableStateOf(PaneThreads()) }
        BackHandler(enabled = threads.entries.isNotEmpty()) { threads = threads.popped() }
        val motionScheme = MaterialTheme.motionScheme
        val distance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
        AnimatedContent(
            targetState = threads,
            transitionSpec = {
                val forward = targetState.entries.size >= initialState.entries.size
                sharedAxisXEnter(motionScheme, distance, forward) togetherWith
                    sharedAxisXExit(motionScheme, distance, forward)
            },
            contentKey = { it.entries.lastOrNull() },
            label = "paneThreads",
        ) { shown ->
            val top = shown.entries.lastOrNull()
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
                ChatRoute(
                    chatId = top?.chatId ?: chatId,
                    messageId = messageId.takeIf { top == null },
                    topic = top?.topic,
                    onBack = if (top == null) onBack else ({ threads = threads.popped() }),
                    onOpenMedia = onOpenMedia,
                    modifier = modifier,
                    onOpenChat = onOpenChat,
                    onOpenThread = { opened, topic -> threads = threads.pushed(PaneThread(opened, topic)) },
                    onOpenProfile = onOpenProfile,
                    onOpenChatInfo = onOpenChatInfo,
                    focusComposer = focusComposer && top == null,
                )
            }
        }
    }
}

/** The forum topic or message thread of a [ChatDestination]; null for the whole chat. */
private val ChatDestination.topic: MessageTopic?
    get() =
        topicId?.let(MessageTopic::Forum) ?: threadId?.let(MessageTopic::Thread)

/** The destination of [topic] of chat [chatId]. */
private fun destinationOf(
    chatId: Long,
    topic: MessageTopic,
) = when (topic) {
    is MessageTopic.Forum -> ChatDestination(chatId, topicId = topic.topicId)
    is MessageTopic.Thread -> ChatDestination(chatId, threadId = topic.threadId)
}

/** A forum topic or message thread [topic] of chat [chatId], opened in the detail pane. */
private data class PaneThread(
    val chatId: Long,
    val topic: MessageTopic,
)

/** The forum topics and message threads opened in the detail pane, oldest first: the last one shows. */
private data class PaneThreads(
    val entries: List<PaneThread> = emptyList(),
) {
    fun pushed(entry: PaneThread) = PaneThreads(entries + entry)

    fun popped() = PaneThreads(entries.dropLast(1))

    companion object {
        /** Saves them as a flat list of chat id, kind (0 topic, 1 thread) and id. */
        val Saver: Saver<PaneThreads, LongArray> =
            Saver(
                save = { threads ->
                    threads.entries
                        .flatMap { (chatId, topic) ->
                            when (topic) {
                                is MessageTopic.Forum -> listOf(chatId, KIND_TOPIC, topic.topicId.toLong())
                                is MessageTopic.Thread -> listOf(chatId, KIND_THREAD, topic.threadId)
                            }
                        }.toLongArray()
                },
                restore = { saved ->
                    PaneThreads(
                        saved.toList().chunked(SAVED_FIELDS).map { (chatId, kind, id) ->
                            val topic =
                                if (kind == KIND_TOPIC) MessageTopic.Forum(id.toInt()) else MessageTopic.Thread(id)
                            PaneThread(chatId, topic)
                        },
                    )
                },
            )

        private const val KIND_TOPIC = 0L
        private const val KIND_THREAD = 1L
        private const val SAVED_FIELDS = 3
    }
}

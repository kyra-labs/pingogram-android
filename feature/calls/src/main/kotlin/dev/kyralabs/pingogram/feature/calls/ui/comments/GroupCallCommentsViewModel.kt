package dev.kyralabs.pingogram.feature.calls.ui.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallComment
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.GroupCallComments
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.GroupCallPhase
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallChat
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallChats
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The comments of the voice chat or conference call the user is in (`docs/DESIGN_SYSTEM.md` §6, "Voice chat
 * comments"): the call from [GroupCallManager], its comments and whether the user may write from
 * [GroupCallRepository], the senders named from [UserRepository] and [GroupCallChats]. A view model beside
 * `GroupCallViewModel`, so comments, which may come several a second, recompose nothing but their pane.
 *
 * Whether the pane shows is the user's choice on this device, kept in [SavedStateHandle]; what is typed stays in the
 * field. Nothing is paid: a call whose comments cost Telegram Stars only says that Pingogram can't pay yet.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GroupCallCommentsViewModel
    @Inject
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        private val manager: GroupCallManager,
        private val repository: GroupCallRepository,
        private val chats: GroupCallChats,
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val shown = MutableStateFlow(savedStateHandle[KEY_SHOWN] ?: true)

        /** The ids of the comments there were when the screen opened: their reactions do not float again. */
        private var seenAtStart: Set<Int>? = null

        private val effectChannel = Channel<CommentEffect>(Channel.BUFFERED)

        val effects: Flow<CommentEffect> = effectChannel.receiveAsFlow()

        /** The call's id while the user is in one that may have comments (not a live stream, not over). */
        private val callId: Flow<Int?> =
            manager.call
                .map { call ->
                    call?.groupCallId?.takeIf { it != 0 && !call.isStream && call.phase !is GroupCallPhase.Ended }
                }.distinctUntilChanged()

        private val content: Flow<Content?> =
            callId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(null)
                } else {
                    combine(
                        repository.groupCall(id),
                        repository.comments(id),
                        selfSender(id),
                        userRepository.users,
                    ) { info, comments, self, users -> Content(info, comments, self, users) }
                }
            }

        val uiState: StateFlow<GroupCallCommentsUiState> =
            combine(content, shown) { content, shown -> content to shown }
                .mapLatest { (content, shown) -> content?.toUiState(shown) ?: GroupCallCommentsUiState.Unavailable }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    GroupCallCommentsUiState.Unavailable,
                )

        /**
         * New comments for screen readers, at most once every [ANNOUNCE_INTERVAL_MS]: how many came since the last
         * announcement and the newest, politely (`docs/DESIGN_SYSTEM.md` §9). Null until one came, and while the pane
         * is hidden.
         */
        val announcement: StateFlow<CommentAnnouncement?> =
            flow {
                // The count announced last (or seen when the pane showed): what came since is new.
                var baseline: Int? = null
                uiState
                    .map { state -> (state as? GroupCallCommentsUiState.Available)?.takeIf { it.isShown } }
                    .map { state -> state?.let { it.received to it.comments.lastOrNull() } }
                    .distinctUntilChanged()
                    // While an announcement holds, newer comments wait here, the newest kept.
                    .conflate()
                    .collect { current ->
                        if (current == null) {
                            baseline = null
                            emit(null)
                            return@collect
                        }
                        val (received, newest) = current
                        val before = baseline
                        baseline = received
                        if (before != null && received > before && newest != null && !newest.isOwn) {
                            emit(CommentAnnouncement(received - before, newest.name, newest.text))
                            delay(ANNOUNCE_INTERVAL_MS)
                        }
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onEvent(event: CommentEvent) {
            when (event) {
                CommentEvent.ToggleShown -> {
                    shown.value = !shown.value
                    savedStateHandle[KEY_SHOWN] = shown.value
                }

                is CommentEvent.Send -> {
                    send(event.text.trim())
                }

                is CommentEvent.React -> {
                    if (event.emoji in QUICK_REACTIONS) send(event.emoji)
                }

                is CommentEvent.Delete -> {
                    act { repository.deleteComments(it, listOf(event.id), event.reportSpam) }
                }

                is CommentEvent.DeleteAllFrom -> {
                    val sender = senderOf(event.senderKey) ?: return
                    act { repository.deleteCommentsBy(it, sender, reportSpam = false) }
                }

                is CommentEvent.SetAllowed -> {
                    act { repository.setCommentsAllowed(it, event.allowed) }
                }
            }
        }

        private fun send(text: String) {
            val state = uiState.value as? GroupCallCommentsUiState.Available ?: return
            if (text.isEmpty() || !state.canComment || state.isPaid) return
            act { repository.sendComment(it, FormattedText(text, persistentListOf())) }
        }

        private fun act(action: suspend (Int) -> Outcome<Unit>) {
            viewModelScope.launch {
                val id = callId.first() ?: return@launch
                val result = action(id)
                if (result is Outcome.Failure) effectChannel.send(CommentEffect.Failed(result.error))
            }
        }

        /** The user in the call, as TDLib marks them among the participants; null until announced. */
        private fun selfSender(id: Int): Flow<MessageSender?> =
            repository
                .participants(id)
                .map { list -> list.firstOrNull { it.isCurrentUser }?.participant }
                .distinctUntilChanged()

        private data class Content(
            val info: GroupCall?,
            val comments: GroupCallComments,
            val self: MessageSender?,
            val users: ImmutableMap<Long, User>,
        )

        private suspend fun Content.toUiState(shown: Boolean): GroupCallCommentsUiState {
            val call = info ?: return GroupCallCommentsUiState.Unavailable
            if (!call.areCommentsAllowed && !call.canToggleComments) return GroupCallCommentsUiState.Unavailable
            val start = seenAtStart ?: comments.items.mapTo(mutableSetOf()) { it.id }.also { seenAtStart = it }
            val rows = comments.items.map { it.toRow(call, self, users) }
            return GroupCallCommentsUiState.Available(
                isShown = shown,
                areAllowed = call.areCommentsAllowed,
                canComment = call.canComment,
                isPaid = call.commentStarCount > 0,
                canToggle = call.canToggleComments,
                canModerate = call.canDeleteComments,
                received = comments.received,
                comments = rows.toImmutableList(),
                reactions =
                    comments.items
                        .filter { it.id !in start && it.text.text in QUICK_REACTIONS && it.sendError == null }
                        .takeLast(MAX_FLOATING)
                        .map { FloatingReaction(it.id, it.text.text) }
                        .toImmutableList(),
            )
        }

        private suspend fun GroupCallComment.toRow(
            call: GroupCall,
            self: MessageSender?,
            users: ImmutableMap<Long, User>,
        ): CommentUi {
            val own = sender == self
            val (id, name, photo) =
                when (val from = sender) {
                    is MessageSender.User -> {
                        val user = users[from.userId]
                        Triple(from.userId, user?.let(::displayName).orEmpty(), user?.profilePhoto)
                    }

                    is MessageSender.Chat -> {
                        val chat: GroupCallChat? = chats.chat(from.chatId).first()
                        Triple(from.chatId, chat?.title.orEmpty(), chat?.photo)
                    }
                }
            return CommentUi(
                id = this.id,
                senderKey = sender.key(),
                senderId = id,
                name = name,
                photo = photo,
                text = text.text,
                paidStarCount = paidStarCount,
                isFromOwner = isFromOwner,
                isOwn = own,
                canDelete = canBeDeleted || call.canDeleteComments,
                isFailed = sendError != null,
            )
        }

        private fun senderOf(key: String): MessageSender? {
            val state = uiState.value as? GroupCallCommentsUiState.Available ?: return null
            if (!state.canModerate) return null
            val (kind, value) = key.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
            val id = value.toLongOrNull() ?: return null
            return if (kind == "user") MessageSender.User(id) else MessageSender.Chat(id)
        }

        companion object {
            /** The quick reactions the emoji button offers, which float up when they come as comments of their own. */
            val QUICK_REACTIONS = listOf("❤", "🔥", "👏", "😂", "😮", "👍")

            const val MAX_FLOATING = 6
            const val ANNOUNCE_INTERVAL_MS = 5_000L
            private const val KEY_SHOWN = "comments_shown"
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** A sender's key: "user:42" or "chat:-100". */
private fun MessageSender.key(): String =
    when (this) {
        is MessageSender.User -> "user:$userId"
        is MessageSender.Chat -> "chat:$chatId"
    }

private fun displayName(user: User): String =
    listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")

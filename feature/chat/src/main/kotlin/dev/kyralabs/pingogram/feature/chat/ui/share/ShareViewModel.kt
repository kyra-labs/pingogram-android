package dev.kyralabs.pingogram.feature.chat.ui.share

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.ShareRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ShareRoute
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatTargets
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The share sheet ([ShareRoute], `REQUIREMENTS.md` FR-N2, direct share): what another app shared, or a `t.me/share`
 * link, held by [ShareInbox] under the route's id, and the chats it can go to ([ChatTargets]: Saved Messages first,
 * the chat list, search; only chats where the user can send messages). Nothing is ever sent without the user's tap
 * (Telegram's API terms).
 *
 * - **One chat picked**: [ShareEvent.Send] hands the share to that chat's conversation ([ShareInbox.deliver]), which
 *   puts the text into its composer, or the files into its attach preview with the text as their caption, for the
 *   user to send there; the sheet closes with [ShareClose.OpenChat].
 * - **Several**: [ShareEvent.Send] sends the share to each, the comment first ([ShareRepository.share]), in
 *   [applicationScope], as the sheet and this view model go as it closes; what came of it goes to [results], whose
 *   snackbar the app shell shows.
 * - [closed] is null while the sheet shows; [ShareClose.Done] once dismissed or sent, and at once when the share is
 *   gone (taken by another sheet, or after process death, which ends the read grant of its files).
 *
 * The picked chats and the comment live in this view model, which survives configuration changes; after process death
 * there is nothing left to share, so nothing is restored.
 *
 * It takes the repositories the targets combine, plus the dispatcher, scope and inbox that tests replace, hence
 * `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@HiltViewModel
class ShareViewModel internal constructor(
    route: ShareRoute,
    private val inbox: ShareInbox,
    chats: ChatRepository,
    search: SearchRepository,
    users: UserRepository,
    groups: GroupRepository,
    private val shares: ShareRepository,
    dispatcher: CoroutineDispatcher,
    private val applicationScope: CoroutineScope,
    private val results: ShareResults,
    hiddenChats: Flow<Set<Long>> = flowOf(emptySet()),
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        inbox: ShareInbox,
        chats: ChatRepository,
        search: SearchRepository,
        users: UserRepository,
        groups: GroupRepository,
        shares: ShareRepository,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
        results: ShareResults,
        chatLock: ChatLockRepository,
    ) : this(
        savedStateHandle.toRoute<ShareRoute>(),
        inbox,
        chats,
        search,
        users,
        groups,
        shares,
        dispatcher,
        applicationScope,
        results,
        chatLock.hiddenChatIds.filterNotNull(),
    )

    /** What is shared; null when the share was gone. */
    private val content: SharedContent? = inbox.take(route.shareId)?.takeUnless { it.isEmpty }

    private val closedState = MutableStateFlow<ShareClose?>(if (content == null) ShareClose.Done else null)

    /** How the sheet closed; null while it shows. */
    val closed: StateFlow<ShareClose?> = closedState.asStateFlow()

    /** The comment sent before the share to several chats, formatting included. */
    val comment = ComposerText()

    private val selected = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    private val chatTargets = ChatTargets(chats, search, users, groups, viewModelScope, hiddenChats = hiddenChats)

    /** The sheet; null when there is nothing to share. */
    val sheet: StateFlow<ShareSheet?> =
        if (content == null) {
            MutableStateFlow(null)
        } else {
            val targets = chatTargets.targets { _, access, _ -> access == WriteAccess.Allowed }
            combine(targets, selected) { found, selected ->
                ShareSheet(
                    text = ShareSummary.of(content.text.text),
                    fileCount = content.files.size,
                    fileKind = content.kind,
                    targets = found.targets,
                    selectedIds = selected,
                    isSearching = found.isSearching,
                    isLoading = found.isLoading,
                    searchError = found.error,
                )
            }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
        }

    /** Handles what the user does in the sheet. */
    fun onEvent(event: ShareEvent) {
        if (closedState.value != null) return
        when (event) {
            is ShareEvent.QueryChanged -> chatTargets.setQuery(event.query)
            is ShareEvent.ToggleTarget -> toggle(event.chatId)
            ShareEvent.LoadMore -> chatTargets.loadMore()
            ShareEvent.Send -> send()
            ShareEvent.Dismiss -> closedState.value = ShareClose.Done
        }
    }

    private fun toggle(chatId: Long) {
        selected.update { if (chatId in it) it.remove(chatId) else it.add(chatId) }
    }

    /** The one chat picked opens with the share; several get it, after the comment. Nothing while none is picked. */
    private fun send() {
        val content = content ?: return
        val chatIds = selected.value.toList()
        when (chatIds.size) {
            0 -> {
                return
            }

            1 -> {
                val chatId = chatIds.single()
                inbox.deliver(chatId, content)
                closedState.value = ShareClose.OpenChat(chatId)
            }

            else -> {
                val text = comment.content.takeUnless { it.text.isBlank() } ?: FormattedText.Empty
                closedState.value = ShareClose.Done
                applicationScope.launch { results.send(shares.share(chatIds, content, text)) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

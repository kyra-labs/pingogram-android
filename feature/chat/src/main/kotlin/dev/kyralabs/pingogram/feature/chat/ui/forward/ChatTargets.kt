package dev.kyralabs.pingogram.feature.chat.ui.forward

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.chat.ui.userId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The chats a sheet that picks chats offers (the forward sheet, `ChatForwarding`; the share sheet, `ShareViewModel`):
 * without a query Saved Messages first (created on the device if needed), then the main chat list in TDLib's order,
 * as far as it is loaded, whose next page [loadMore] loads; with a query ([setQuery]), the chats
 * [SearchRepository.searchChats] finds once it has not changed for [DEBOUNCE_MILLIS], the previous search cancelled.
 * Either way only the chats the sheet's filter lets through, each once, as [ForwardTarget]s. Runs in [scope], the
 * view model's.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ChatTargets(
    private val chats: ChatRepository,
    private val search: SearchRepository,
    private val users: UserRepository,
    private val groups: GroupRepository,
    private val scope: CoroutineScope,
) {
    private val query = MutableStateFlow("")
    private val pages = MutableStateFlow(PageLoad())

    /** What the search field holds now; it is searched once it has not changed for [DEBOUNCE_MILLIS]. */
    fun setQuery(text: String) {
        query.value = text
    }

    /**
     * Loads the next page of the main chat list, one at a time, until TDLib has no more; not while a query shows
     * search results, which come whole.
     */
    fun loadMore() {
        val load = pages.value
        if (query.value.isNotBlank() || load.isLoading || load.isComplete) return
        pages.value = load.copy(isLoading = true)
        scope.launch {
            val result = chats.loadChats(ChatListType.Main)
            pages.update { it.copy(isLoading = false, isComplete = (result as? Outcome.Success)?.value == false) }
        }
    }

    /**
     * The targets as the query and the chats change: those [canReceive] lets through, given the user's [WriteAccess]
     * to each ([writeAccess]).
     */
    fun targets(canReceive: (chat: Chat, access: WriteAccess) -> Boolean): Flow<TargetList> {
        val found =
            query
                .map { it.trim() }
                .debounce { if (it.isEmpty()) 0L else DEBOUNCE_MILLIS }
                .distinctUntilChanged()
                .flatMapLatest { text -> if (text.isEmpty()) mainChats() else searchChats(text) }
        val people = combine(users.users, users.myUserId, groups.basicGroups, groups.supergroups, ::People)
        val targets = combine(found, people) { found, people -> found to people.targetsOf(found.chats, canReceive) }
        return combine(targets, pages) { (found, targets), pages ->
            TargetList(
                targets = targets,
                isSearching = found.isSearch,
                isLoading = found.isLoading || (!found.isSearch && targets.isEmpty() && pages.isLoading),
                error = found.error,
            )
        }
    }

    /** Saved Messages, then the main chat list without it, in TDLib's order. */
    private fun mainChats(): Flow<Found> =
        combine(chats.chatList(ChatListType.Main), savedMessages()) { list, saved ->
            val ordered = if (saved == null) list else listOf(saved) + list.filter { it.id != saved.id }
            Found(ordered, isSearch = false)
        }

    /**
     * The current user's Saved Messages, then every change; null until the user and the chat are known. A chat TDLib
     * never had is created on the device ([ChatRepository.privateChat]), which announces it.
     */
    private fun savedMessages(): Flow<Chat?> =
        users.myUserId.flatMapLatest { me ->
            if (me == null) {
                flowOf(null)
            } else {
                channelFlow {
                    if (chats.chat(me).first() == null) launch { chats.privateChat(me) }
                    chats.chat(me).collect { send(it) }
                }
            }
        }

    /** The chats [text] finds, local ones first; loading until the first answer, an error in place of none. */
    private fun searchChats(text: String): Flow<Found> =
        search
            .searchChats(text)
            .map { result ->
                when (result) {
                    is Outcome.Success -> Found(result.value, isSearch = true)
                    is Outcome.Failure -> Found(emptyList(), isSearch = true, error = result.error)
                }
            }.onStart { emit(Found(emptyList(), isSearch = true, isLoading = true)) }

    /** Whether a page of the main chat list loads, and whether TDLib has no more. */
    private data class PageLoad(
        val isLoading: Boolean = false,
        val isComplete: Boolean = false,
    )

    /** The chats found, before they are checked and mapped. */
    private class Found(
        val chats: List<Chat>,
        val isSearch: Boolean,
        val isLoading: Boolean = false,
        val error: DomainError? = null,
    )

    /** What checking and mapping a chat takes besides the chat. */
    private class People(
        val users: ImmutableMap<Long, User>,
        val myUserId: Long?,
        val basicGroups: ImmutableMap<Long, BasicGroup>,
        val supergroups: ImmutableMap<Long, Supergroup>,
    ) {
        /** [chats] that [canReceive] lets through, as targets, in their order and each once. */
        fun targetsOf(
            chats: List<Chat>,
            canReceive: (Chat, WriteAccess) -> Boolean,
        ): ImmutableList<ForwardTarget> {
            if (chats.isEmpty()) return persistentListOf()
            return chats
                .distinctBy { it.id }
                .filter { canReceive(it, accessTo(it)) }
                .map(::targetOf)
                .toImmutableList()
        }

        /** Whether the user can send messages in [chat]. */
        private fun accessTo(chat: Chat): WriteAccess {
            val type = chat.type
            return chat.writeAccess(
                user = chat.userId?.let(users::get),
                basicGroup = (type as? ChatType.BasicGroup)?.let { basicGroups[it.basicGroupId] },
                supergroup = (type as? ChatType.Supergroup)?.let { supergroups[it.supergroupId] },
            )
        }

        private fun targetOf(chat: Chat): ForwardTarget {
            val isSavedMessages = chat.type is ChatType.Private && chat.userId == myUserId
            val isDeletedAccount = chat.userId?.let(users::get)?.isDeleted == true
            return ForwardTarget(
                chatId = chat.id,
                title =
                    when {
                        isSavedMessages -> UiText.resource(R.string.chat_saved_messages)
                        isDeletedAccount -> UiText.resource(R.string.user_deleted_account)
                        else -> UiText.Plain(chat.title)
                    },
                photo = if (isSavedMessages) null else chat.photo,
                placeholderIcon =
                    when {
                        isSavedMessages -> R.drawable.ic_bookmark_filled
                        isDeletedAccount -> R.drawable.ic_person_filled
                        else -> null
                    },
                isSecret = chat.type is ChatType.Secret,
            )
        }
    }

    internal companion object {
        /** How long the query must stay unchanged before it is searched. */
        const val DEBOUNCE_MILLIS = 300L
    }
}

/**
 * The chats a picking sheet shows ([ChatTargets.targets]): [targets] in their order; [isSearching] while they are what
 * a query found; [isLoading] while the first chats, or a query's results, are on their way; [error] a failed search.
 */
internal data class TargetList(
    val targets: ImmutableList<ForwardTarget>,
    val isSearching: Boolean,
    val isLoading: Boolean,
    val error: DomainError?,
)

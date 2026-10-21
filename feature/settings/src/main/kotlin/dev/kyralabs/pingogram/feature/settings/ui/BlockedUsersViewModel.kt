package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Blocked users page (`REQUIREMENTS.md` FR-S1): the users and chats on TDLib's main block list, a page at a time,
 * and unblocking them, which can be undone.
 *
 * - **Pages** of `BlockListRepository.PAGE_SIZE` load as the end of the list comes near, one at a time; the first
 *   page's failure fails the page, a later one's shows at the end of the list with "Try again".
 * - **Rows** name a user as `UserRepository` knows them now, a chat by its title; the users and chats of a page are
 *   looked up before it shows, so every row has a name.
 * - **Unblocking** waits for TDLib, then takes the row out and offers to undo it ([BlockedUsersEffect.Unblocked]);
 *   undoing blocks again and puts the row back where it was. Telegram has it in another place then (first or last), so
 *   after an undo the next page starts that much earlier, and rows already loaded are left out of it.
 */
@HiltViewModel
class BlockedUsersViewModel
    @Inject
    constructor(
        private val blockListRepository: BlockListRepository,
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository,
        groupRepository: GroupRepository,
    ) : ViewModel() {
        private val list = MutableStateFlow(BlockedList())

        /** The users and chats of the loaded pages, as they were looked up; users then follow `UserRepository`. */
        private val lookedUp = MutableStateFlow(LookedUp())

        val uiState: StateFlow<BlockedUsersUiState> =
            combine(
                list,
                lookedUp,
                userRepository.users,
                groupRepository.supergroups,
            ) { list, lookedUp, users, groups ->
                list.toUiState { sender -> item(sender, lookedUp, users, groups, sender in list.unblocking) }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, BlockedUsersUiState.Loading)

        private val effectChannel = Channel<BlockedUsersEffect>(Channel.BUFFERED)
        val effects: Flow<BlockedUsersEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        /** Where each unblocked sender was in the list, to put it back on undo. */
        private val unblockedAt = mutableMapOf<MessageSender, Int>()

        /** How many senders were blocked again here, which Telegram moved elsewhere in its list. */
        private var blockedAgain = 0

        init {
            loadFirstPage()
        }

        fun onEvent(event: BlockedUsersEvent) {
            when (event) {
                BlockedUsersEvent.Retry -> if (list.value.first is FirstPage.Failed) loadFirstPage()
                BlockedUsersEvent.LoadMore -> loadNextPage()
                is BlockedUsersEvent.Unblock -> unblock(event.sender)
                is BlockedUsersEvent.Undo -> undo(event.sender)
            }
        }

        private fun loadFirstPage() {
            loadJob?.cancel()
            blockedAgain = 0
            list.value = BlockedList()
            loadJob = viewModelScope.launch { loadPage(isFirst = true) }
        }

        private fun loadNextPage() {
            val current = list.value
            val canLoad = current.first == FirstPage.Loaded && !current.endReached && loadJob?.isActive != true
            if (!canLoad) return
            list.update { it.copy(moreError = null, isLoadingMore = true) }
            loadJob = viewModelScope.launch { loadPage(isFirst = false) }
        }

        private suspend fun loadPage(isFirst: Boolean) {
            val offset = (list.value.senders.size - blockedAgain).coerceAtLeast(0)
            when (val page = blockListRepository.blockedSenders(offset)) {
                is Outcome.Success -> {
                    lookUp(page.value.senders)
                    list.update { current ->
                        val added = page.value.senders.filterNot { it in current.senders }
                        current.copy(
                            senders = current.senders.addingAll(added),
                            totalCount = page.value.totalCount,
                            first = FirstPage.Loaded,
                            isLoadingMore = false,
                            endReached = added.isEmpty() || current.senders.size + added.size >= page.value.totalCount,
                        )
                    }
                }

                is Outcome.Failure -> {
                    list.update {
                        if (isFirst) {
                            it.copy(first = FirstPage.Failed(page.error))
                        } else {
                            it.copy(isLoadingMore = false, moreError = page.error)
                        }
                    }
                }
            }
        }

        /** Looks up the users and chats of a page, from memory where they are, else from TDLib. */
        private suspend fun lookUp(senders: List<MessageSender>) =
            coroutineScope {
                val users =
                    senders.filterIsInstance<MessageSender.User>().map {
                        async { userRepository.getUser(it.userId).getOrNull() }
                    }
                val chats =
                    senders.filterIsInstance<MessageSender.Chat>().map {
                        async { chatRepository.getChat(it.chatId).getOrNull() }
                    }
                val foundUsers = users.awaitAll().filterNotNull()
                val foundChats = chats.awaitAll().filterNotNull()
                lookedUp.update { current ->
                    current.copy(
                        users = current.users.puttingAll(foundUsers.associateBy { it.id }),
                        chats = current.chats.puttingAll(foundChats.associateBy { it.id }),
                    )
                }
            }

        /** Unblocks one sender at a time per row; tapping the row's button again meanwhile does nothing. */
        private fun unblock(sender: MessageSender) {
            val current = list.value
            if (sender !in current.senders || sender in current.unblocking) return
            val name = (uiState.value as? BlockedUsersUiState.Content)?.items?.firstOrNull { it.sender == sender }?.name
            list.update { it.copy(unblocking = it.unblocking.adding(sender)) }
            viewModelScope.launch {
                when (val unblocked = blockListRepository.unblock(sender)) {
                    is Outcome.Success -> {
                        unblockedAt[sender] = list.value.senders.indexOf(sender)
                        list.update {
                            it.copy(
                                senders = it.senders.removing(sender),
                                unblocking = it.unblocking.removing(sender),
                                totalCount = (it.totalCount - 1).coerceAtLeast(0),
                            )
                        }
                        val shownName = name ?: UiText.resource(UiR.string.user_deleted_account)
                        effectChannel.trySend(BlockedUsersEffect.Unblocked(sender, shownName))
                    }

                    is Outcome.Failure -> {
                        list.update { it.copy(unblocking = it.unblocking.removing(sender)) }
                        showError(unblocked.error)
                    }
                }
            }
        }

        /** Blocks [sender] again and puts its row back where it was; nothing for a sender not unblocked here. */
        private fun undo(sender: MessageSender) {
            val index = unblockedAt.remove(sender) ?: return
            viewModelScope.launch {
                when (val blocked = blockListRepository.block(sender)) {
                    is Outcome.Success -> {
                        blockedAgain++
                        list.update {
                            if (sender in it.senders) {
                                it
                            } else {
                                it.copy(
                                    senders = it.senders.addingAt(index.coerceIn(0, it.senders.size), sender),
                                    totalCount = it.totalCount + 1,
                                )
                            }
                        }
                    }

                    is Outcome.Failure -> {
                        showError(blocked.error)
                    }
                }
            }
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(BlockedUsersEffect.ShowError(error))
        }
    }

/**
 * The list as loaded: [senders] in TDLib's order, minus the unblocked ones.
 *
 * @property endReached TDLib has no more: a page added nobody new, or every blocked sender is loaded.
 * @property moreError why the last page after the first could not be loaded; null if none failed.
 * @property unblocking the senders being unblocked.
 */
private data class BlockedList(
    val senders: PersistentList<MessageSender> = persistentListOf(),
    val totalCount: Int = 0,
    val first: FirstPage = FirstPage.Loading,
    val isLoadingMore: Boolean = false,
    val moreError: DomainError? = null,
    val endReached: Boolean = false,
    val unblocking: PersistentSet<MessageSender> = persistentSetOf(),
) {
    fun toUiState(item: (MessageSender) -> BlockedSenderItem): BlockedUsersUiState =
        when (first) {
            FirstPage.Loading -> {
                BlockedUsersUiState.Loading
            }

            is FirstPage.Failed -> {
                BlockedUsersUiState.Error(first.error)
            }

            FirstPage.Loaded -> {
                if (senders.isEmpty() && endReached) {
                    BlockedUsersUiState.Empty
                } else {
                    BlockedUsersUiState.Content(
                        items = senders.map(item).toImmutableList(),
                        totalCount = totalCount,
                        more =
                            when {
                                endReached -> MoreBlockedSenders.None
                                isLoadingMore -> MoreBlockedSenders.Loading
                                moreError != null -> MoreBlockedSenders.Failed(moreError)
                                else -> MoreBlockedSenders.Available
                            },
                    )
                }
            }
        }
}

private sealed interface FirstPage {
    data object Loading : FirstPage

    data object Loaded : FirstPage

    data class Failed(
        val error: DomainError,
    ) : FirstPage
}

/** The users and chats of the loaded pages, by id. */
private data class LookedUp(
    val users: PersistentMap<Long, User> = persistentMapOf(),
    val chats: PersistentMap<Long, Chat> = persistentMapOf(),
)

/** A loaded sender's row: [users] as they are now, else as looked up; a chat by its title. */
private fun item(
    sender: MessageSender,
    lookedUp: LookedUp,
    users: Map<Long, User>,
    groups: Map<Long, Supergroup>,
    isUnblocking: Boolean,
): BlockedSenderItem =
    when (sender) {
        is MessageSender.User -> {
            userItem(sender, users[sender.userId] ?: lookedUp.users[sender.userId], isUnblocking)
        }

        is MessageSender.Chat -> {
            chatItem(sender, lookedUp.chats[sender.chatId], groups, isUnblocking)
        }
    }

private fun userItem(
    sender: MessageSender.User,
    user: User?,
    isUnblocking: Boolean,
): BlockedSenderItem {
    val username = user?.usernames?.firstOrNull()
    val phone = user?.phoneNumber?.takeIf { it.isNotEmpty() }
    return BlockedSenderItem(
        sender = sender,
        name = user?.fullName() ?: UiText.resource(UiR.string.user_deleted_account),
        detail = username?.let { UiText.resource(R.string.settings_blocked_username, it) },
        phoneNumber = if (username == null) phone?.let { "+$it" } else null,
        photo = user?.profilePhoto,
        avatarId = sender.userId,
        placeholderIcon = if (user == null || user.isDeleted) UiR.drawable.ic_person_filled else null,
        isUnblocking = isUnblocking,
    )
}

private fun chatItem(
    sender: MessageSender.Chat,
    chat: Chat?,
    groups: Map<Long, Supergroup>,
    isUnblocking: Boolean,
): BlockedSenderItem {
    val supergroupId = (chat?.type as? ChatType.Supergroup)?.supergroupId
    val username = supergroupId?.let { groups[it] }?.usernames?.firstOrNull()
    return BlockedSenderItem(
        sender = sender,
        name = UiText.Plain(chat?.title.orEmpty()),
        detail = username?.let { UiText.resource(R.string.settings_blocked_username, it) },
        photo = chat?.photo,
        avatarId = sender.chatId,
        isUnblocking = isUnblocking,
    )
}

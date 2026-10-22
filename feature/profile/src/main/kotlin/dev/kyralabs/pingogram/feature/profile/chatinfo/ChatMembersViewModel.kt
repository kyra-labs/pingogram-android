package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.isAdministrator
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ChatMembersRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.core.ui.time.UserStatusFormatter
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The members page of group or channel [chatId] (`REQUIREMENTS.md` FR-P3) as [uiState]: its members by filter (every
 * member, administrators, restricted or removed users), searched, a page at a time, and what administrators do to
 * them, one member and one request at a time (Telegram's API terms: nothing in bulk).
 *
 * - **Filters**: [initialFilter] first (the route's); every member and the administrators for everyone who may list
 *   them; restricted (supergroups) and removed users (supergroups and channels) for those who may restrict members. The
 *   filter and the query are kept in the [SavedStateHandle].
 * - **Pages** of [GroupInfoRepository.PAGE_SIZE] load one at a time as the end of the list comes near; the first
 *   page's failure fails the list with "Try again", a later one's shows at its end. A new query waits [searchDelay]
 *   after the last keystroke, then starts over.
 * - **Rows** name a user as `UserRepository` knows them now (TDLib announces the users of a page before it answers),
 *   a chat by its title, looked up once; the actions are those [memberItem] allows the current user.
 * - **Actions**: promoting, editing rights and restricting open the rights page; taking rights away, lifting
 *   restrictions and letting a removed user back are one request each; removing asks first. A change shows in the row
 *   at once once TDLib took it (or takes the row out where it no longer matches the filter), with a snackbar. After
 *   the rights page saved ([MEMBERS_CHANGED_RESULT] in the [SavedStateHandle]), the list loads again.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Its repositories and fakes; a function per action.
@HiltViewModel
class ChatMembersViewModel internal constructor(
    private val chatId: Long,
    initialFilter: MemberFilter,
    private val savedStateHandle: SavedStateHandle,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    private val searchDelay: Duration = SEARCH_DELAY,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
        userRepository: UserRepository,
        chatRepository: ChatRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<ChatMembersRoute>().chatId,
        filterOf(savedStateHandle.toRoute<ChatMembersRoute>().filter) ?: MemberFilter.Recent,
        savedStateHandle,
        groupInfoRepository,
        administrationRepository,
        userRepository,
        chatRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    private val effectChannel = Channel<ChatMembersEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: navigation and snackbars. */
    val effects: Flow<ChatMembersEffect> = effectChannel.receiveAsFlow()

    private val filter = MutableStateFlow(savedStateHandle.get<String>(KEY_FILTER)?.let(::filterOf) ?: initialFilter)
    private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
    private val list = MutableStateFlow(LoadedMembers())
    private val group = MutableStateFlow<GroupInfo?>(null)
    private val chats = MutableStateFlow<PersistentMap<Long, Chat>>(persistentMapOf())
    private val removing = MutableStateFlow<MessageSender?>(null)

    private var loadJob: Job? = null
    private var statusFormatter: UserStatusFormatter? = null

    val uiState: StateFlow<ChatMembersUiState> =
        combine(filter, query, list, group, removing) { filter, query, list, group, removing ->
            Sources(filter, query, list, group, removing)
        }.combine(userRepository.users) { sources, users -> sources to users }
            .combine(userRepository.myUserId) { (sources, users), me -> Triple(sources, users, me) }
            .combine(chats) { (sources, users, me), chats -> Pair(Triple(sources, users, me), chats) }
            .combine(clock.minutes) { (parts, chats), now ->
                val (sources, users, me) = parts
                sources.toUiState(users, chats, me, now)
            }.flowOn(dispatcher)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ChatMembersUiState(filter = filter.value),
            )

    init {
        viewModelScope.launch(Dispatchers.Main) {
            launch {
                groupInfoRepository.groupInfo(chatId).collect { outcome ->
                    group.value = outcome.getOrNull() ?: group.value
                }
            }
            loadFirstPage()
            // A rights page above saved a change: the list may no longer be what it was.
            savedStateHandle.getStateFlow(MEMBERS_CHANGED_RESULT, false).collect { changed ->
                if (changed) {
                    savedStateHandle[MEMBERS_CHANGED_RESULT] = false
                    loadFirstPage()
                }
            }
        }
    }

    fun onEvent(event: ChatMembersEvent) {
        when (event) {
            is ChatMembersEvent.SelectFilter -> selectFilter(event.filter)
            is ChatMembersEvent.Search -> search(event.query)
            ChatMembersEvent.Retry -> if (list.value.first is FirstPage.Failed) loadFirstPage()
            ChatMembersEvent.LoadMore -> loadNextPage()
            is ChatMembersEvent.Open -> open(event.sender)
            is ChatMembersEvent.Act -> act(event.sender, event.action)
            ChatMembersEvent.ConfirmRemove -> confirmRemove()
            ChatMembersEvent.DismissDialog -> removing.value = null
        }
    }

    private fun selectFilter(selected: MemberFilter) {
        if (filter.value == selected) return
        filter.value = selected
        savedStateHandle[KEY_FILTER] = selected.name
        loadFirstPage()
    }

    private fun search(text: String) {
        if (query.value == text) return
        query.value = text
        savedStateHandle[KEY_QUERY] = text
        if (filter.value == MemberFilter.Administrators) return
        loadFirstPage(after = searchDelay)
    }

    private fun loadFirstPage(after: Duration = Duration.ZERO) {
        loadJob?.cancel()
        list.value = LoadedMembers()
        loadJob =
            viewModelScope.launch {
                if (after > Duration.ZERO) delay(after)
                loadPage(isFirst = true)
            }
    }

    private fun loadNextPage() {
        val current = list.value
        val canLoad = current.first == FirstPage.Loaded && !current.endReached && loadJob?.isActive != true
        if (!canLoad) return
        list.update { it.copy(moreError = null, isLoadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(isFirst = false) }
    }

    private suspend fun loadPage(isFirst: Boolean) {
        val shownFilter = filter.value
        val search = if (shownFilter == MemberFilter.Administrators) "" else query.value.trim()
        val offset = list.value.members.size
        when (val page = groupInfoRepository.members(chatId, shownFilter, search, offset)) {
            is Outcome.Success -> {
                lookUpChats(page.value.members)
                list.update { current ->
                    val known = current.members.map { it.sender }.toSet()
                    val added = page.value.members.filterNot { it.sender in known }
                    current.copy(
                        members = current.members.addingAll(added),
                        totalCount = page.value.totalCount,
                        first = FirstPage.Loaded,
                        isLoadingMore = false,
                        endReached = added.isEmpty() || current.members.size + added.size >= page.value.totalCount,
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

    /** Looks up the chats of a page (channels among removed users), which `ChatRepository` keeps. */
    private suspend fun lookUpChats(members: List<GroupMember>) {
        val unknown =
            members.mapNotNull { (it.sender as? MessageSender.Chat)?.chatId }.filterNot { it in chats.value }
        for (id in unknown) {
            chatRepository.getChat(id).getOrNull()?.let { found -> chats.update { it.putting(id, found) } }
        }
    }

    private fun open(sender: MessageSender) {
        when (sender) {
            is MessageSender.User -> effectChannel.trySend(ChatMembersEffect.OpenProfile(sender.userId))
            is MessageSender.Chat -> effectChannel.trySend(ChatMembersEffect.OpenChat(sender.chatId))
        }
    }

    private fun act(
        sender: MessageSender,
        action: MemberAction,
    ) {
        val userId = (sender as? MessageSender.User)?.userId
        when (action) {
            MemberAction.Promote, MemberAction.EditRights -> {
                userId?.let { effectChannel.trySend(ChatMembersEffect.OpenRights(it, restrict = false)) }
            }

            MemberAction.Restrict -> {
                userId?.let { effectChannel.trySend(ChatMembersEffect.OpenRights(it, restrict = true)) }
            }

            MemberAction.Remove -> {
                removing.value = sender
            }

            MemberAction.Dismiss -> {
                change(sender, MemberRole.Member, R.string.chat_info_member_dismissed)
            }

            MemberAction.Unrestrict -> {
                change(sender, MemberRole.Member, R.string.chat_info_member_unrestricted)
            }

            MemberAction.Unban -> {
                change(sender, MemberRole.Left, R.string.chat_info_member_unbanned)
            }
        }
    }

    private fun confirmRemove() {
        val sender = removing.value ?: return
        removing.value = null
        change(sender, MemberRole.Banned(untilDate = 0), R.string.chat_info_member_removed)
    }

    /** Gives [sender] [role]; once TDLib took it, the row shows it (or leaves a filter it no longer matches). */
    private fun change(
        sender: MessageSender,
        role: MemberRole,
        @StringRes done: Int,
    ) {
        val name = itemOf(sender)?.name ?: UiText.resource(UiR.string.user_deleted_account)
        viewModelScope.launch {
            when (val changed = administrationRepository.setMemberRole(chatId, sender, role)) {
                is Outcome.Success -> {
                    list.update { current -> current.withRole(sender, role, filter.value) }
                    effectChannel.trySend(ChatMembersEffect.Done(done, name))
                }

                is Outcome.Failure -> {
                    if (changed.error != DomainError.Silent) {
                        effectChannel.trySend(ChatMembersEffect.ShowError(changed.error))
                    }
                }
            }
        }
    }

    private fun itemOf(sender: MessageSender): MemberItem? =
        (uiState.value.list as? MembersList.Content)?.items?.firstOrNull { it.sender == sender }

    private fun Sources.toUiState(
        users: Map<Long, User>,
        chats: Map<Long, Chat>,
        myUserId: Long?,
        now: Instant,
    ): ChatMembersUiState {
        val kind = group?.kind
        val powers =
            group?.let {
                MemberPowers(
                    it.kind,
                    if (it.myRole.isAdministrator) it.myRights else NO_RIGHTS,
                    myUserId,
                )
            }
        val formatter = statusFormatter()
        val items =
            list.members.map { member ->
                val userId = (member.sender as? MessageSender.User)?.userId
                val chatId = (member.sender as? MessageSender.Chat)?.chatId
                memberItem(
                    member = member,
                    user = userId?.let(users::get),
                    chat = chatId?.let(chats::get),
                    powers = powers ?: MemberPowers(kind ?: GroupKind.Supergroup, NO_RIGHTS, myUserId),
                    statusFormatter = formatter,
                    now = now,
                )
            }
        val removingItem = removing?.let { sender -> items.firstOrNull { it.sender == sender } }
        return ChatMembersUiState(
            kind = kind,
            filter = filter,
            filters = filtersFor(group),
            query = query,
            list =
                when (val first = list.first) {
                    FirstPage.Loading -> {
                        MembersList.Loading
                    }

                    is FirstPage.Failed -> {
                        MembersList.Error(first.error)
                    }

                    FirstPage.Loaded -> {
                        if (items.isEmpty() && list.endReached) {
                            MembersList.Empty
                        } else {
                            MembersList.Content(items.toImmutableList(), list.totalCount, list.more())
                        }
                    }
                },
            removing = removingItem,
        )
    }

    private fun statusFormatter(): UserStatusFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return statusFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: UserStatusFormatter(zone, locale, is24HourClock, timePatterns).also { statusFormatter = it }
    }

    /** What the page is made from, as of one emission. */
    private class Sources(
        val filter: MemberFilter,
        val query: String,
        val list: LoadedMembers,
        val group: GroupInfo?,
        val removing: MessageSender?,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_FILTER = "chatMembers.filter"
        const val KEY_QUERY = "chatMembers.query"

        /** How long typing pauses before Telegram is searched. */
        val SEARCH_DELAY = 300.milliseconds

        val NO_RIGHTS = AdministratorRights.None
    }
}

/** The filter named [name]; null for a name of no filter. */
private fun filterOf(name: String): MemberFilter? = MemberFilter.entries.firstOrNull { it.name == name }

/**
 * The filters the user may choose: everyone and the administrators; restricted users in supergroups and removed users
 * in supergroups and channels for those who may restrict members.
 */
private fun filtersFor(group: GroupInfo?) =
    buildList {
        add(MemberFilter.Recent)
        add(MemberFilter.Administrators)
        val restrict = group != null && group.myRole.isAdministrator && group.myRights.canRestrictMembers
        if (restrict && group.kind == GroupKind.Supergroup) add(MemberFilter.Restricted)
        if (restrict && group.kind != GroupKind.BasicGroup) add(MemberFilter.Banned)
    }.toImmutableList()

/**
 * The members as loaded: [members] in TDLib's order.
 *
 * @property endReached TDLib has no more: a page added nobody new, or every member is loaded.
 * @property moreError why the last page after the first could not be loaded; null if none failed.
 */
private data class LoadedMembers(
    val members: PersistentList<GroupMember> = persistentListOf(),
    val totalCount: Int = 0,
    val first: FirstPage = FirstPage.Loading,
    val isLoadingMore: Boolean = false,
    val moreError: DomainError? = null,
    val endReached: Boolean = false,
) {
    fun more(): MoreMembers =
        when {
            endReached -> MoreMembers.None
            isLoadingMore -> MoreMembers.Loading
            moreError != null -> MoreMembers.Failed(moreError)
            else -> MoreMembers.Available
        }

    /** [sender] with [role]: in place where it still matches [filter], out of the list where it does not. */
    fun withRole(
        sender: MessageSender,
        role: MemberRole,
        filter: MemberFilter,
    ): LoadedMembers {
        val index = members.indexOfFirst { it.sender == sender }
        if (index < 0) return this
        val matches =
            when (filter) {
                MemberFilter.Recent -> role.isMember
                MemberFilter.Administrators -> role.isAdministrator
                MemberFilter.Restricted -> role is MemberRole.Restricted
                MemberFilter.Banned -> role is MemberRole.Banned
            }
        return if (matches) {
            copy(members = members.mutate { it[index] = it[index].copy(role = role) })
        } else {
            copy(members = members.removingAt(index), totalCount = (totalCount - 1).coerceAtLeast(0))
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

/** The result a rights page leaves for the members page below it: true once it changed a member. */
internal const val MEMBERS_CHANGED_RESULT = "chatInfo.membersChanged"

/** The result the link editor leaves for the invite links page below it: true once it saved a link. */
internal const val INVITE_LINKS_CHANGED_RESULT = "chatInfo.inviteLinksChanged"

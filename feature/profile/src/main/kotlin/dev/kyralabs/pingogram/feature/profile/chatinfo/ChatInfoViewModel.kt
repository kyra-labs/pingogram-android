package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.GroupInfo
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.model.isAdministrator
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ChatInfoRoute
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.core.ui.time.UserStatusFormatter
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileHeader
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSharedMedia
import dev.kyralabs.pingogram.feature.profile.ui.ProfileWarning
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaEvent
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaMapper
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaUiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * The info page of group or channel [chatId] (`REQUIREMENTS.md` FR-P3, FR-P4) as [uiState], its shared media as
 * [media]. Events come in through [onEvent] and [onMediaEvent].
 *
 * - **The chat** comes from [ChatRepository.chat], or [ChatRepository.getChat] while TDLib has not announced it:
 *   [ChatInfoUiState.Error] for a chat TDLib does not know, with a retry. The group and its full info follow
 *   [GroupInfoRepository.groupInfo]; while the full info is unknown, or if it fails, what it fills (description,
 *   counts, the invite link) is left out, and a flood wait or a network failure is a snackbar.
 * - **Header**: the title, "Public group · 1,024 members" (subscribers for channels) and the badges.
 * - **Link**: the public `t.me/username`, else the primary invite link for those who may invite; copied, shared, or
 *   shown as a QR code.
 * - **Members**: the first [MEMBERS_PREVIEW] of [GroupInfoRepository.members] where the user may list them (a basic
 *   group's from its full info), named as `UserRepository` knows them now, with the actions the user's rights allow.
 * - **Administration**: rows for what the user's rights allow ([ChatAdministration]), which open other pages.
 * - **Actions**: muting ([ChatRepository.setMuted]); leaving and, for its owner, deleting the chat for everyone
 *   ([ChatRepository.deleteChat], whose `deleteOptions` say whether it may be deleted), and clearing the history
 *   ([GroupAdministrationRepository.clearHistory]), each after a confirmation and one request per tap.
 * - **Starting**: what the view model starts runs once the frame that created it is done (dispatched).
 *
 * It takes one repository per kind of data the page shows or changes, plus the clock, time patterns, dispatcher and
 * language tests replace, hence `LongParameterList` is suppressed. The shared media tab shown is kept in the
 * [SavedStateHandle].
 */
@Suppress("LongParameterList", "TooManyFunctions") // Its repositories and fakes; a function per action of the page.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatInfoViewModel internal constructor(
    private val chatId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val groupInfoRepository: GroupInfoRepository,
    private val administrationRepository: GroupAdministrationRepository,
    private val userRepository: UserRepository,
    sharedMediaRepository: SharedMediaRepository,
    private val searchRepository: SearchRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chatRepository: ChatRepository,
        groupInfoRepository: GroupInfoRepository,
        administrationRepository: GroupAdministrationRepository,
        userRepository: UserRepository,
        sharedMediaRepository: SharedMediaRepository,
        searchRepository: SearchRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<ChatInfoRoute>().chatId,
        savedStateHandle,
        chatRepository,
        groupInfoRepository,
        administrationRepository,
        userRepository,
        sharedMediaRepository,
        searchRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    private val effectChannel = Channel<ChatInfoEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: navigation, snackbars, copying and sharing. */
    val effects: Flow<ChatInfoEffect> = effectChannel.receiveAsFlow()

    private val attempts = MutableStateFlow(0)
    private val started = MutableStateFlow(false)
    private val overlay = MutableStateFlow(Overlay())

    /** The first members of a supergroup or channel; null until loaded (a basic group's come with its full info). */
    private val previewMembers = MutableStateFlow<List<GroupMember>?>(null)
    private var isLoadingPreview = false

    /** Whether the chat may be deleted for everyone; null until known. */
    private val canDeleteForEveryone = MutableStateFlow<Boolean?>(null)

    private var statusFormatter: UserStatusFormatter? = null
    private var mediaMapper: SharedMediaMapper? = null

    private val sharedMedia =
        ProfileSharedMedia(
            repository = sharedMediaRepository,
            scope = viewModelScope,
            initial =
                savedStateHandle.get<String>(KEY_MEDIA_TAB)?.let { name ->
                    SharedMediaKind.entries.firstOrNull { it.name == name }
                } ?: SharedMediaKind.PhotosAndVideos,
            onSelected = { savedStateHandle[KEY_MEDIA_TAB] = it.name },
            mapItems = { kind, messages -> mediaMapper().items(kind, messages) },
        )

    /** The chat's shared media, then every change. */
    val media: StateFlow<SharedMediaUiState> =
        sharedMedia.state
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SharedMediaUiState())

    private val chat: Flow<Lookup<Chat>> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (!isStarted) flowOf(Lookup.Loading) else lookUpChat()
        }

    private val group: Flow<Lookup<GroupInfo>> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (!isStarted) {
                flowOf(Lookup.Loading)
            } else {
                groupInfoRepository.groupInfo(chatId).map { outcome ->
                    when (outcome) {
                        is Outcome.Success -> {
                            onGroup(outcome.value)
                            Lookup.Found(outcome.value)
                        }

                        is Outcome.Failure -> {
                            Lookup.Failed(outcome.error)
                        }
                    }
                }
            }
        }

    val uiState: StateFlow<ChatInfoUiState> =
        combine(chat, group, previewMembers, userRepository.users, userRepository.myUserId) { c, g, m, u, me ->
            Sources(c, g, m, u, me)
        }.combine(clock.minutes) { sources, now -> sources to now }
            .combine(canDeleteForEveryone) { (sources, now), canDelete -> Triple(sources, now, canDelete) }
            .combine(overlay) { (sources, now, canDelete), overlay -> sources.toUiState(now, canDelete, overlay) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatInfoUiState.Loading)

    init {
        // The screen creates the view model in the frame that first composes it; its work starts after that frame.
        viewModelScope.launch(Dispatchers.Main) {
            started.value = true
            sharedMedia.start(chatId)
        }
    }

    fun onEvent(event: ChatInfoEvent) {
        when (event) {
            ChatInfoEvent.Retry -> attempts.update { it + 1 }
            ChatInfoEvent.ToggleMute -> toggleMute()
            ChatInfoEvent.Leave -> showDialog(ChatInfoDialog.Leave)
            ChatInfoEvent.Delete -> showDialog(ChatInfoDialog.Delete)
            ChatInfoEvent.ClearHistory -> showDialog(ChatInfoDialog.ClearHistory)
            ChatInfoEvent.Confirm -> confirm()
            ChatInfoEvent.DismissDialog -> overlay.update { it.copy(dialog = null) }
            ChatInfoEvent.CopyLink -> link()?.let { send(ChatInfoEffect.CopyText(it.url, CopiedChatText.Link)) }
            ChatInfoEvent.ShareLink -> link()?.let { send(ChatInfoEffect.Share(it.url)) }
            ChatInfoEvent.ShowQrCode -> link()?.let { showDialog(ChatInfoDialog.QrCode(it.url)) }
            ChatInfoEvent.CopyDescription -> copyDescription()
            is ChatInfoEvent.OpenMention -> openMention(event.target)
            is ChatInfoEvent.OpenMember -> openMember(event.sender)
            is ChatInfoEvent.OpenMembers -> send(ChatInfoEffect.OpenMembers(event.filter))
            is ChatInfoEvent.Open -> send(ChatInfoEffect.Open(event.page))
        }
    }

    fun onMediaEvent(event: SharedMediaEvent) {
        when (event) {
            is SharedMediaEvent.Select -> sharedMedia.select(event.kind)
            SharedMediaEvent.LoadMore -> sharedMedia.loadMore()
            SharedMediaEvent.Retry -> sharedMedia.retry()
        }
    }

    private fun lookUpChat(): Flow<Lookup<Chat>> =
        flow {
            emit(Lookup.Loading)
            when (val found = chatRepository.getChat(chatId)) {
                is Outcome.Failure -> emit(Lookup.Failed(found.error))
                is Outcome.Success -> emitAll(chatRepository.chat(chatId).map { Lookup.Found(it ?: found.value) })
            }
        }

    /**
     * Once the group is known: whether it may be deleted (asked once), and the first members of a supergroup or
     * channel the user may list (asked once its full info says so). A failed full info is a snackbar when the user
     * can do something about it.
     */
    private fun onGroup(info: GroupInfo) {
        val error = info.detailsError
        if (error is DomainError.FloodWait || error == DomainError.Network) send(ChatInfoEffect.ShowError(error))
        viewModelScope.launch {
            if (canDeleteForEveryone.value == null && info.myRole is MemberRole.Owner) {
                val options = chatRepository.deleteOptions(chatId)
                canDeleteForEveryone.value = (options as? Outcome.Success)?.value?.canDeleteForEveryone == true
            }
            val details = info.details
            val wantsPreview = info.kind != GroupKind.BasicGroup && details?.canGetMembers == true
            if (wantsPreview && previewMembers.value == null && !isLoadingPreview) {
                isLoadingPreview = true
                val page = groupInfoRepository.members(chatId, MemberFilter.Recent, limit = MEMBERS_PREVIEW)
                isLoadingPreview = false
                // Without them, the page shows the count and "All members" alone.
                previewMembers.value = (page as? Outcome.Success)?.value?.members.orEmpty()
            }
        }
    }

    private fun toggleMute() {
        val isMuted = (uiState.value as? ChatInfoUiState.Content)?.actions?.isMuted ?: return
        viewModelScope.launch {
            val changed = chatRepository.setMuted(chatId, !isMuted)
            if (changed is Outcome.Failure) showError(changed.error)
        }
    }

    private fun confirm() {
        val dialog = overlay.value.dialog
        overlay.update { it.copy(dialog = null) }
        if (overlay.value.isBusy) return
        val request: suspend () -> Outcome<Unit> =
            when (dialog) {
                ChatInfoDialog.Leave -> ({ chatRepository.deleteChat(chatId, forEveryone = false) })
                ChatInfoDialog.Delete -> ({ chatRepository.deleteChat(chatId, forEveryone = true) })
                ChatInfoDialog.ClearHistory -> ({ administrationRepository.clearHistory(chatId) })
                is ChatInfoDialog.QrCode, null -> return
            }
        overlay.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val done = request()
            overlay.update { it.copy(isBusy = false) }
            when (done) {
                is Outcome.Success -> {
                    send(
                        if (dialog == ChatInfoDialog.ClearHistory) {
                            ChatInfoEffect.HistoryCleared
                        } else {
                            ChatInfoEffect.Left
                        },
                    )
                }

                is Outcome.Failure -> {
                    showError(done.error)
                }
            }
        }
    }

    private fun openMember(sender: MessageSender) {
        when (sender) {
            is MessageSender.User -> send(ChatInfoEffect.OpenProfile(sender.userId))
            is MessageSender.Chat -> send(ChatInfoEffect.OpenChat(sender.chatId))
        }
    }

    /** Opens who a mention names: a user's profile, or the chat of a group or channel with that username. */
    private fun openMention(target: MentionTarget) {
        when (target) {
            is MentionTarget.User -> {
                send(ChatInfoEffect.OpenProfile(target.userId))
            }

            is MentionTarget.Username -> {
                viewModelScope.launch {
                    when (val owner = searchRepository.publicChat(target.username)) {
                        is Outcome.Success -> {
                            when (val found = owner.value) {
                                is UsernameOwner.User -> send(ChatInfoEffect.OpenProfile(found.userId))
                                is UsernameOwner.Chat -> send(ChatInfoEffect.OpenChat(found.chatId))
                            }
                        }

                        is Outcome.Failure -> {
                            showError(owner.error)
                        }
                    }
                }
            }
        }
    }

    private fun copyDescription() {
        val text = (uiState.value as? ChatInfoUiState.Content)?.description?.text?.takeIf { it.isNotBlank() } ?: return
        send(ChatInfoEffect.CopyText(text, CopiedChatText.Description))
    }

    private fun link(): ChatLink? = (uiState.value as? ChatInfoUiState.Content)?.link

    private fun showDialog(dialog: ChatInfoDialog) {
        overlay.update { it.copy(dialog = dialog) }
    }

    private fun send(effect: ChatInfoEffect) {
        effectChannel.trySend(effect)
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) send(ChatInfoEffect.ShowError(error))
    }

    private fun Sources.toUiState(
        now: Instant,
        canDelete: Boolean?,
        overlay: Overlay,
    ): ChatInfoUiState =
        when {
            chat is Lookup.Failed -> ChatInfoUiState.Error(chat.error)
            group is Lookup.Failed -> ChatInfoUiState.Error(group.error)
            chat is Lookup.Found && group is Lookup.Found -> content(chat.value, group.value, now, canDelete, overlay)
            else -> ChatInfoUiState.Loading
        }

    @Suppress("LongParameterList") // What the page is made from.
    private fun Sources.content(
        chat: Chat,
        info: GroupInfo,
        now: Instant,
        canDelete: Boolean?,
        overlay: Overlay,
    ): ChatInfoUiState.Content {
        val details = info.details
        val rights = info.myRights
        val isAdmin = info.myRole.isAdministrator
        val restrict = rights.canRestrictMembers
        return ChatInfoUiState.Content(
            header = header(chat, info),
            kind = info.kind,
            description = details?.description?.takeIf { it.text.isNotBlank() },
            link = link(info),
            actions =
                ChatInfoActions(
                    isMuted = chat.isMuted,
                    canLeave = info.myRole.isMember,
                    canDelete = info.myRole is MemberRole.Owner && canDelete == true,
                    canClearHistory = info.kind != GroupKind.Channel,
                    isBusy = overlay.isBusy,
                ),
            members = membersPreview(info, now),
            administration =
                ChatAdministration(
                    canEditInfo = rights.canChangeInfo,
                    canEditPermissions = restrict && info.kind != GroupKind.Channel,
                    administratorCount = details?.administratorCount?.takeIf { isAdmin },
                    canManageInviteLinks = rights.canInviteUsers,
                    bannedCount = details?.bannedCount?.takeIf { restrict && info.kind != GroupKind.BasicGroup },
                    restrictedCount =
                        details?.restrictedCount?.takeIf {
                            restrict && info.kind == GroupKind.Supergroup
                        },
                ).takeIf { isAdmin && !it.isEmpty },
            dialog = overlay.dialog,
        )
    }

    private fun header(
        chat: Chat,
        info: GroupInfo,
    ): ProfileHeader {
        val isPublic = info.usernames.isNotEmpty()
        val type =
            when (info.kind) {
                GroupKind.Channel -> {
                    if (isPublic) R.string.chat_info_public_channel else R.string.chat_info_private_channel
                }

                else -> {
                    if (isPublic) R.string.chat_info_public_group else R.string.chat_info_private_group
                }
            }
        val count = info.details?.memberCount?.takeIf { it > 0 } ?: info.memberCount
        val status =
            if (count > 0) {
                val counted =
                    UiText.plural(
                        if (info.kind == GroupKind.Channel) {
                            R.plurals.chat_info_subscribers
                        } else {
                            R.plurals.chat_info_members
                        },
                        count,
                        NumberFormat.getIntegerInstance(locale()).format(count),
                    )
                UiText.resource(R.string.chat_info_status, UiText.resource(type), counted)
            } else {
                UiText.resource(type)
            }
        return ProfileHeader(
            userId = chat.id,
            name = UiText.Plain(chat.title),
            photo = chat.photo,
            status = status,
            isVerified = info.isVerified,
            warning =
                when {
                    info.isScam -> ProfileWarning.Scam
                    info.isFake -> ProfileWarning.Fake
                    else -> null
                },
        )
    }

    /** The public link, else the primary invite link for those who may invite. */
    private fun link(info: GroupInfo): ChatLink? {
        val username = info.usernames.firstOrNull()
        val invite = info.details?.primaryInviteLink?.takeIf { info.myRights.canInviteUsers }
        return when {
            username != null -> ChatLink(url = "https://t.me/$username", text = "t.me/$username", isPublic = true)
            invite != null -> ChatLink(url = invite.link, text = invite.link.removePrefix("https://"), isPublic = false)
            else -> null
        }
    }

    /** The first members, where the user may list them; the count alone until they are loaded. */
    private fun Sources.membersPreview(
        info: GroupInfo,
        now: Instant,
    ): MembersPreview? {
        val details = info.details ?: return null
        return if (details.canGetMembers) {
            val members = if (info.kind == GroupKind.BasicGroup) details.members else previewMembers.orEmpty()
            val powers = MemberPowers(info.kind, info.myRights, myUserId)
            val formatter = statusFormatter()
            val items =
                members.take(MEMBERS_PREVIEW).map { member ->
                    val userId = (member.sender as? MessageSender.User)?.userId
                    memberItem(member, userId?.let(users::get), null, powers, formatter, now)
                }
            MembersPreview(items.toImmutableList(), details.memberCount)
        } else {
            MembersPreview(totalCount = details.memberCount, isHidden = true).takeIf { details.hasHiddenMembers }
        }
    }

    private fun statusFormatter(): UserStatusFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return statusFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: UserStatusFormatter(zone, locale, is24HourClock, timePatterns).also { statusFormatter = it }
    }

    private fun mediaMapper(): SharedMediaMapper {
        val zone = clock.zone()
        val locale = locale()
        val year = clock.now().atZone(zone).year
        return mediaMapper?.takeIf { it.isFor(zone, locale, year) }
            ?: SharedMediaMapper(zone, locale, timePatterns, year).also { mediaMapper = it }
    }

    /** What the page is made from, as of one emission. */
    private class Sources(
        val chat: Lookup<Chat>,
        val group: Lookup<GroupInfo>,
        val previewMembers: List<GroupMember>?,
        val users: Map<Long, User>,
        val myUserId: Long?,
    )

    /** Something looked up, as far as found. */
    private sealed interface Lookup<out T> {
        data object Loading : Lookup<Nothing>

        data class Found<T>(
            val value: T,
        ) : Lookup<T>

        data class Failed(
            val error: DomainError,
        ) : Lookup<Nothing>
    }

    /** What the user started on the page: the dialog shown, and whether a request is on its way. */
    private data class Overlay(
        val dialog: ChatInfoDialog? = null,
        val isBusy: Boolean = false,
    )

    internal companion object {
        /** Keeps the state flowing through a configuration change, as the profile does. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        const val KEY_MEDIA_TAB = "chatInfo.mediaTab"

        /** The members the info page shows before "All members". */
        const val MEMBERS_PREVIEW = 5
    }
}

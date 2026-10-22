package dev.kyralabs.pingogram.feature.profile.ui

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
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserFullInfo
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.UserProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ProfileRoute
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.core.ui.time.UserStatusFormatter
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile of user [userId] (`REQUIREMENTS.md` FR-P2) as [uiState], its shared media as [media] and its report
 * sheet as [reportSheet]. Events come in through [onEvent] and [onMediaEvent].
 *
 * - **The user** comes from [UserRepository.user], or [UserRepository.getUser] while TDLib has not announced them:
 *   [ProfileUiState.Error] for a user TDLib does not know, with a retry. Their full info (bio, birthday, block state,
 *   a bot's description) follows [UserProfileRepository.fullInfo]; while it is unknown, or if it fails, the rows it
 *   fills are left out, and a flood wait or a network failure is a snackbar.
 * - **Header**: the name ("Deleted Account" for a deleted one), the status as the conversation says it
 *   ([UserStatusFormatter], on the minute of [WallClock.minutes]), "bot" for bots, and the badges.
 * - **The private chat** is made on the device once the user is known ([ChatRepository.privateChat], as the official
 *   apps do when a profile opens): it is what "Message" opens, what the mute toggle and reporting act on, and where
 *   the shared media are searched ([ProfileSharedMedia]).
 * - **Actions**: "Message" opens the chat (Saved Messages on one's own profile); a secret chat starts after a
 *   confirmation ([SecretChatRepository.createSecretChat]) and opens; the mute toggle mutes the chat forever or
 *   unmutes it ([ChatRepository.setMuted]); blocking (after a confirmation) and unblocking go through
 *   [BlockListRepository], shown at once and kept until the full info says the same; reporting is [ProfileReporting].
 *   Each is one request per tap of the user's; a failure is a snackbar and leaves the state as it was.
 * - **Photos**: the user's profile photos a page at a time ([UserProfileRepository.profilePhotos]), the next one when
 *   the carousel reaches the last.
 * - **Copying and sharing** usernames and the phone number, and dialing it, go out as effects; a mention tapped in the
 *   bio opens the profile it names, or the group or channel of an `@username` ([SearchRepository.publicChat]).
 * - **Starting**: the view model is created in the frame that first composes the profile; what it starts runs once
 *   that frame is done (dispatched), so the frame does not wait for it.
 *
 * It takes one repository per kind of data the profile shows or changes, plus the clock, time patterns, dispatcher
 * and language that tests replace, hence `LongParameterList` is suppressed. The shared media tab shown is kept in the
 * [SavedStateHandle].
 */
@Suppress("LongParameterList", "TooManyFunctions") // Its repositories and fakes; a function per action of the profile.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel internal constructor(
    private val userId: Long,
    private val savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileRepository,
    private val secretChatRepository: SecretChatRepository,
    private val chatRepository: ChatRepository,
    private val blockListRepository: BlockListRepository,
    messageActionRepository: MessageActionRepository,
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
        userRepository: UserRepository,
        profileRepository: UserProfileRepository,
        secretChatRepository: SecretChatRepository,
        chatRepository: ChatRepository,
        blockListRepository: BlockListRepository,
        messageActionRepository: MessageActionRepository,
        sharedMediaRepository: SharedMediaRepository,
        searchRepository: SearchRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        savedStateHandle.toRoute<ProfileRoute>().userId,
        savedStateHandle,
        userRepository,
        profileRepository,
        secretChatRepository,
        chatRepository,
        blockListRepository,
        messageActionRepository,
        sharedMediaRepository,
        searchRepository,
        clock,
        timePatterns,
        dispatcher,
        Locale::getDefault,
    )

    private val effectChannel = Channel<ProfileEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: opening a chat, snackbars, copying, sharing and dialing. */
    val effects: Flow<ProfileEffect> = effectChannel.receiveAsFlow()

    /** Each retry of finding the user starts it again. */
    private val attempts = MutableStateFlow(0)

    /** Set once the view model's work starts, after the first frame. */
    private val started = MutableStateFlow(false)

    private val chatId = MutableStateFlow<Long?>(null)
    private val overlay = MutableStateFlow(Overlay())
    private val photos = MutableStateFlow(PhotosLoad())

    private var statusFormatter: UserStatusFormatter? = null
    private var mediaMapper: SharedMediaMapper? = null

    private val reporting = ProfileReporting(messageActionRepository, viewModelScope) { effectChannel.trySend(it) }

    /** The comment of the report sheet, while it asks for one. */
    val reportComment get() = reporting.comment

    /** The open report sheet; null while none is. */
    val reportSheet: StateFlow<ProfileReportSheet?> = reporting.sheet

    private val sharedMedia =
        ProfileSharedMedia(
            repository = sharedMediaRepository,
            scope = viewModelScope,
            initial =
                savedStateHandle
                    .get<String>(
                        KEY_MEDIA_TAB,
                    )?.let(::mediaKindOf) ?: SharedMediaKind.PhotosAndVideos,
            onSelected = { savedStateHandle[KEY_MEDIA_TAB] = it.name },
            mapItems = { kind, messages -> mediaMapper().items(kind, messages) },
        )

    /** The profile's shared media, then every change. */
    val media: StateFlow<SharedMediaUiState> =
        sharedMedia.state
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SharedMediaUiState())

    private val user: Flow<UserLookup> =
        combine(started, attempts) { isStarted, _ -> isStarted }.flatMapLatest { isStarted ->
            if (!isStarted) flowOf(UserLookup.Loading) else lookUpUser()
        }

    private val fullInfo: Flow<UserFullInfo?> =
        started.flatMapLatest { isStarted ->
            if (!isStarted) {
                flowOf(null)
            } else {
                profileRepository.fullInfo(userId).map { outcome ->
                    when (outcome) {
                        is Outcome.Success -> {
                            outcome.value
                        }

                        is Outcome.Failure -> {
                            if (outcome.error.isWorthTelling()) {
                                effectChannel.trySend(
                                    ProfileEffect.ShowError(outcome.error),
                                )
                            }
                            null
                        }
                    }
                }
            }
        }

    private val chat: Flow<Chat?> = chatId.flatMapLatest { id -> id?.let(chatRepository::chat) ?: flowOf(null) }

    val uiState: StateFlow<ProfileUiState> =
        combine(user, fullInfo, chat, userRepository.myUserId, clock.minutes) { user, info, chat, myId, now ->
            Sources(user, info, chat, myId, now)
        }.combine(overlay) { sources, overlay -> sources.toUiState(overlay) }
            .combine(photos) { state, photos -> state.withPhotos(photos) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileUiState.Loading)

    init {
        // The screen creates the view model in the frame that first composes it. What it starts runs once that frame is
        // done (dispatched, not at once as the main thread's immediate dispatcher would), so the frame does not wait.
        viewModelScope.launch(Dispatchers.Main) { started.value = true }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Retry -> attempts.update { it + 1 }
            ProfileEvent.SendMessage -> openChat()
            ProfileEvent.StartSecretChat -> showDialog(ProfileDialog.SecretChat)
            ProfileEvent.ConfirmSecretChat -> startSecretChat()
            ProfileEvent.ToggleMute -> toggleMute()
            ProfileEvent.Block -> showDialog(ProfileDialog.Block(nameOf(uiState.value)))
            ProfileEvent.ConfirmBlock -> setBlocked(true)
            ProfileEvent.Unblock -> setBlocked(false)
            ProfileEvent.DismissDialog -> overlay.update { it.copy(dialog = null) }
            ProfileEvent.LoadMorePhotos -> loadPhotos()
            is ProfileEvent.OpenMention -> openMention(event.target)
            else -> onRowEvent(event)
        }
    }

    /** The events of the information rows and the report sheet, which [onEvent] hands on. */
    private fun onRowEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Report -> {
                chatId.value?.let(reporting::start)
            }

            is ProfileEvent.ChooseReportOption -> {
                reporting.choose(event.index)
            }

            ProfileEvent.SendReportComment -> {
                reporting.sendComment()
            }

            ProfileEvent.DismissReport -> {
                reporting.dismiss()
            }

            is ProfileEvent.CopyUsername -> {
                copy("@${event.username}", CopiedText.Username)
            }

            is ProfileEvent.ShareUsername -> {
                effectChannel.trySend(
                    ProfileEffect.Share("https://t.me/${event.username}"),
                )
            }

            ProfileEvent.CopyPhone -> {
                phoneNumber()?.let { copy(it, CopiedText.Phone) }
            }

            ProfileEvent.CallPhone -> {
                phoneNumber()?.let { effectChannel.trySend(ProfileEffect.Dial(it)) }
            }

            ProfileEvent.CopyBio -> {
                bioText()?.let { copy(it, CopiedText.Bio) }
            }

            // The other events are handled by onEvent.
            else -> {}
        }
    }

    fun onMediaEvent(event: SharedMediaEvent) {
        when (event) {
            is SharedMediaEvent.Select -> sharedMedia.select(event.kind)
            SharedMediaEvent.LoadMore -> sharedMedia.loadMore()
            SharedMediaEvent.Retry -> sharedMedia.retry()
        }
    }

    /**
     * The user from memory, else from TDLib, then every change; once found, the private chat is made and the photos
     * load.
     */
    private fun lookUpUser(): Flow<UserLookup> =
        flow {
            emit(UserLookup.Loading)
            when (val found = userRepository.getUser(userId)) {
                is Outcome.Failure -> {
                    emit(UserLookup.Failed(found.error))
                }

                is Outcome.Success -> {
                    onUserFound(found.value)
                    emitAll(userRepository.user(userId).map { UserLookup.Found(it ?: found.value) })
                }
            }
        }

    /** Makes the private chat on the device (its shared media then load) and loads the photos, on the main thread. */
    private fun onUserFound(user: User) {
        viewModelScope.launch {
            if (user.profilePhoto != null && photos.value == PhotosLoad()) loadPhotos()
            if (chatId.value != null) return@launch
            when (val chat = chatRepository.privateChat(userId)) {
                is Outcome.Success -> {
                    chatId.value = chat.value.id
                    sharedMedia.start(chat.value.id)
                }

                // A deleted account Telegram has no chat with: no messages, no shared media.
                is Outcome.Failure -> {
                    if (!user.isDeleted && chat.error.isWorthTelling()) showError(chat.error)
                }
            }
        }
    }

    private fun loadPhotos() {
        val current = photos.value
        val loadedAll = current.totalCount != null && current.items.size >= current.totalCount
        if (current.isLoading || loadedAll || current.failed) return
        photos.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val page = profileRepository.profilePhotos(userId, offset = current.items.size)) {
                is Outcome.Success -> {
                    photos.update { loaded ->
                        val known = loaded.items.map { it.id }.toSet()
                        val added =
                            page.value.photos.filterNot { it.id in known }.map {
                                ProfilePhotoItem(
                                    it.id,
                                    it.photo,
                                )
                            }
                        loaded.copy(
                            items = loaded.items + added,
                            // A page that adds nothing ends the carousel, whatever the count says.
                            totalCount = if (added.isEmpty()) loaded.items.size else page.value.totalCount,
                            isLoading = false,
                        )
                    }
                }

                // The carousel is extra: without its photos the avatar still shows.
                is Outcome.Failure -> {
                    photos.update { it.copy(isLoading = false, failed = true) }
                }
            }
        }
    }

    private fun openChat() {
        val id = chatId.value
        if (id != null) {
            effectChannel.trySend(ProfileEffect.OpenChat(id))
            return
        }
        viewModelScope.launch {
            when (val chat = chatRepository.privateChat(userId)) {
                is Outcome.Success -> effectChannel.trySend(ProfileEffect.OpenChat(chat.value.id))
                is Outcome.Failure -> showError(chat.error)
            }
        }
    }

    /** Opens who a mention names: a user's profile, or the chat of a group or channel with that username. */
    private fun openMention(target: MentionTarget) {
        when (target) {
            is MentionTarget.User -> {
                effectChannel.trySend(ProfileEffect.OpenProfile(target.userId))
            }

            is MentionTarget.Username -> {
                viewModelScope.launch {
                    when (val owner = searchRepository.publicChat(target.username)) {
                        is Outcome.Success -> {
                            when (val found = owner.value) {
                                is UsernameOwner.User -> effectChannel.trySend(ProfileEffect.OpenProfile(found.userId))
                                is UsernameOwner.Chat -> effectChannel.trySend(ProfileEffect.OpenChat(found.chatId))
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

    private fun showDialog(dialog: ProfileDialog) {
        overlay.update { it.copy(dialog = dialog) }
    }

    private fun startSecretChat() {
        overlay.update { it.copy(dialog = null) }
        if (overlay.value.isStartingSecretChat) return
        overlay.update { it.copy(isStartingSecretChat = true) }
        viewModelScope.launch {
            val created = secretChatRepository.createSecretChat(userId)
            overlay.update { it.copy(isStartingSecretChat = false) }
            when (created) {
                is Outcome.Success -> effectChannel.trySend(ProfileEffect.OpenChat(created.value))
                is Outcome.Failure -> showError(created.error)
            }
        }
    }

    /** Mutes the chat forever or unmutes it; the chat's update shows the change. */
    private fun toggleMute() {
        val id = chatId.value
        val isMuted = (uiState.value as? ProfileUiState.Content)?.actions?.isMuted
        if (id == null || isMuted == null) return
        viewModelScope.launch {
            val changed = chatRepository.setMuted(id, !isMuted)
            if (changed is Outcome.Failure) showError(changed.error)
        }
    }

    /** Blocks or unblocks the user, shown at once ([Overlay.blocked]) once TDLib took it. */
    private fun setBlocked(blocked: Boolean) {
        overlay.update { it.copy(dialog = null) }
        if (overlay.value.isChangingBlock) return
        val name = nameOf(uiState.value)
        overlay.update { it.copy(isChangingBlock = true) }
        viewModelScope.launch {
            val sender = MessageSender.User(userId)
            val changed = if (blocked) blockListRepository.block(sender) else blockListRepository.unblock(sender)
            when (changed) {
                is Outcome.Success -> {
                    overlay.update { it.copy(isChangingBlock = false, blocked = blocked) }
                    effectChannel.trySend(ProfileEffect.BlockChanged(name, blocked))
                }

                is Outcome.Failure -> {
                    overlay.update { it.copy(isChangingBlock = false) }
                    showError(changed.error)
                }
            }
        }
    }

    private fun copy(
        text: String,
        what: CopiedText,
    ) {
        effectChannel.trySend(ProfileEffect.CopyText(text, what))
    }

    private fun phoneNumber(): String? = (uiState.value as? ProfileUiState.Content)?.info?.phoneNumber

    private fun bioText(): String? =
        (uiState.value as? ProfileUiState.Content)
            ?.info
            ?.bio
            ?.text
            ?.takeIf { it.isNotBlank() }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(ProfileEffect.ShowError(error))
    }

    private fun Sources.toUiState(overlay: Overlay): ProfileUiState =
        when (user) {
            UserLookup.Loading -> ProfileUiState.Loading
            is UserLookup.Failed -> ProfileUiState.Error(user.error)
            is UserLookup.Found -> content(user.user, overlay)
        }

    private fun Sources.content(
        user: User,
        overlay: Overlay,
    ): ProfileUiState.Content {
        val isBot = user.type == UserType.Bot
        return ProfileUiState.Content(
            header = header(user),
            info =
                ProfileInfo(
                    bio = bio(info, isBot),
                    isBot = isBot,
                    usernames = user.usernames,
                    phoneNumber = user.phoneNumber.takeIf { it.isNotBlank() }?.let { "+$it" },
                    birthday = info?.birthdate?.let(::birthday),
                ),
            actions = actions(user, overlay),
            dialog = overlay.dialog,
        )
    }

    /** What can be done on the profile of [user]: nothing to block, report or start a secret chat with oneself. */
    private fun Sources.actions(
        user: User,
        overlay: Overlay,
    ): ProfileActions {
        val isSelf = user.id == myUserId
        val isDeleted = user.isDeleted
        val isRegular = user.type == UserType.Regular && !isDeleted
        val blockedNow = overlay.blocked?.takeIf { info == null || info.isBlocked != it } ?: info?.isBlocked
        return ProfileActions(
            isSelf = isSelf,
            canMessage = user.haveAccess && (!isDeleted || chat != null),
            isMuted = chat?.isMuted?.takeIf { !isSelf },
            canStartSecretChat = isRegular && !isSelf && user.haveAccess,
            isStartingSecretChat = overlay.isStartingSecretChat,
            contact = contactAction(user).takeIf { isRegular && !isSelf },
            isBlocked = blockedNow.takeIf { !isSelf && !isDeleted },
            isChangingBlock = overlay.isChangingBlock,
            canReport = !isSelf && !isDeleted && chat != null,
            callUserId = user.id.takeIf { isRegular && !isSelf && info?.canBeCalled == true },
        )
    }

    private fun Sources.header(user: User): ProfileHeader {
        val isDeleted = user.isDeleted
        return ProfileHeader(
            userId = user.id,
            name = if (isDeleted) UiText.resource(UiR.string.user_deleted_account) else user.fullName(),
            photo = user.profilePhoto,
            placeholderIcon = if (isDeleted) UiR.drawable.ic_person_filled else null,
            status =
                when {
                    isDeleted -> null
                    user.type == UserType.Bot -> UiText.resource(R.string.profile_status_bot)
                    user.isSupport -> UiText.resource(R.string.profile_status_support)
                    else -> statusFormatter().status(user.status, now)
                },
            isOnline = !isDeleted && user.type != UserType.Bot && user.status.isOnlineAt(now.epochSecond),
            isVerified = user.isVerified,
            isPremium = user.isPremium,
            warning =
                when {
                    user.isScam -> ProfileWarning.Scam
                    user.isFake -> ProfileWarning.Fake
                    else -> null
                },
        )
    }

    /** The bio, or a bot's description as plain text; null without one. */
    private fun bio(
        info: UserFullInfo?,
        isBot: Boolean,
    ): FormattedText? {
        if (info == null) return null
        val description = info.botDescription
        return when {
            isBot && description != null -> FormattedText(description, persistentListOf())
            info.bio.text.isNotBlank() -> info.bio
            else -> null
        }
    }

    /**
     * "December 10, 1815", or "December 10" without a year, in the user's language; null for a date that does not
     * exist.
     */
    private fun birthday(birthdate: Birthdate): String? {
        val locale = locale()
        val year = birthdate.year
        return runCatching {
            val date = LocalDate.of(year ?: LEAP_YEAR, birthdate.month, birthdate.day)
            val formatter =
                runCatching {
                    DateTimeFormatter.ofPattern(
                        timePatterns.bestPattern(
                            locale,
                            if (year !=
                                null
                            ) {
                                "yMMMMd"
                            } else {
                                "MMMMd"
                            },
                        ),
                        locale,
                    )
                }.getOrElse { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
            formatter.format(date)
        }.getOrNull()
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

    /** What the profile is made from, as of one emission. */
    private class Sources(
        val user: UserLookup,
        val info: UserFullInfo?,
        val chat: Chat?,
        val myUserId: Long?,
        val now: Instant,
    )

    /** The user as far as found. */
    private sealed interface UserLookup {
        data object Loading : UserLookup

        data class Found(
            val user: User,
        ) : UserLookup

        data class Failed(
            val error: DomainError,
        ) : UserLookup
    }

    /**
     * What the user started on the profile: the dialog shown, and the requests on their way.
     *
     * @property blocked what blocking or unblocking set, shown until the full info says the same; null before.
     */
    private data class Overlay(
        val dialog: ProfileDialog? = null,
        val isStartingSecretChat: Boolean = false,
        val isChangingBlock: Boolean = false,
        val blocked: Boolean? = null,
    )

    /** The carousel's photos as far as loaded; [totalCount] null before the first page. */
    private data class PhotosLoad(
        val items: List<ProfilePhotoItem> = emptyList(),
        val totalCount: Int? = null,
        val isLoading: Boolean = false,
        val failed: Boolean = false,
    )

    private fun ProfileUiState.withPhotos(load: PhotosLoad): ProfileUiState =
        if (this is ProfileUiState.Content) {
            copy(photos = ProfilePhotos(load.items.toImmutableList(), load.totalCount ?: 0))
        } else {
            this
        }

    private companion object {
        /** Keeps the state flowing through a configuration change, as the conversation does. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        const val KEY_MEDIA_TAB = "profile.mediaTab"

        /** A year with February 29, for a birthday shown without its year. */
        const val LEAP_YEAR = 2000
    }
}

/** "Edit contact" for a contact, "Add to contacts" for anyone else. */
private fun contactAction(user: User): ContactAction = if (user.isContact) ContactAction.Edit else ContactAction.Add

/** The name the dialogs and snackbars use. */
private fun nameOf(state: ProfileUiState): UiText =
    (state as? ProfileUiState.Content)?.header?.name ?: UiText.resource(UiR.string.user_deleted_account)

private fun mediaKindOf(name: String): SharedMediaKind? = SharedMediaKind.entries.firstOrNull { it.name == name }

/** Errors a snackbar tells about: those the user can do something about, unlike a user Telegram hides. */
private fun DomainError.isWorthTelling(): Boolean = this is DomainError.FloodWait || this == DomainError.Network

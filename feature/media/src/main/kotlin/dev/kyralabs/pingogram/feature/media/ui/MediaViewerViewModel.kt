package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onSuccess
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ChatMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.media.ui.files.MediaFileActions
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * The full-screen media viewer (`REQUIREMENTS.md` FR-C10) of the photo, video or GIF the route opens it on, among the
 * chat's media of that kind ([ChatMediaRepository]), as [uiState]. Events come in through [onEvent].
 *
 * - **Loading**: the page around the opened message, then older or newer pages while the current item is within
 *   [PREFETCH_DISTANCE] of either end of what is loaded, one at a time on each side, until each end ([MediaWindow]).
 *   A failed first page shows [MediaViewerUiState.Error] until [MediaViewerEvent.Retry]; a deleted or unshowable
 *   message [MediaViewerUiState.Unavailable]. A failed page on a side is a snackbar ([MediaViewerEffect.ShowError])
 *   and is not retried by itself: turning the page, or [MediaViewerEvent.Retry], tries again.
 * - **The current item** is the page the pager settled on ([MediaViewerEvent.PageSettled]), kept in the
 *   [SavedStateHandle]: after process death the viewer loads around it again.
 * - **Position**: "N of total" ([MediaCount]) from TDLib's position of the opened message and its counts, or from the
 *   loaded media once an end is reached (secret chats, where TDLib cannot tell a position).
 * - **Deletions** ([ChatMediaRepository.deletedMessages]) drop items; a deleted current item gives way to the one
 *   that takes its place, and the viewer shows [MediaViewerUiState.Unavailable] once nothing is left.
 * - **Files** ([MediaDownloads]): the shown item's full file downloads at visible priority, its neighbours' at
 *   prefetch priority; downloads of items more than two pages away, and those left when the viewer closes, are
 *   cancelled. The files of the shown item and its neighbours are followed into [MediaViewerUiState.Content.files].
 * - **Playback** ([MediaPlayback]): the current item's video or GIF plays on the one [VideoPlayer] once its file is
 *   on the device and the screen is started; it pauses when the page changes or the screen stops, and the player is
 *   released with the view model.
 * - **Spoilers**: hidden until tapped ([MediaViewerEvent.RevealSpoiler]), except the opened item's.
 * - **Share and Save** ([MediaSharing]): offered where Telegram lets the content leave it; the current item's file goes
 *   to the share sheet ([MediaViewerEffect.ShareFile]) or into the gallery through [MediaFileActions], once it is
 *   downloaded, after the storage permission on Android 8–9.
 * - **Items** are mapped on [dispatcher] by a [MediaItemMapper]: senders' names, and dates in the zone, the language
 *   and the 12- or 24-hour setting of the moment. The state runs while the screen collects it, and 5 s after.
 *
 * It takes one repository per kind of data, plus the player, file actions, clock, dispatcher, scope, language and
 * time patterns that tests replace, hence `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaViewerViewModel internal constructor(
    route: MediaViewerRoute,
    private val savedStateHandle: SavedStateHandle,
    private val mediaRepository: ChatMediaRepository,
    fileRepository: FileRepository,
    userRepository: UserRepository,
    chatRepository: ChatRepository,
    clock: WallClock,
    timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val applicationScope: CoroutineScope,
    locale: () -> Locale,
    private val videoPlayer: VideoPlayer,
    fileActions: MediaFileActions,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        mediaRepository: ChatMediaRepository,
        fileRepository: FileRepository,
        userRepository: UserRepository,
        chatRepository: ChatRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
        videoPlayer: VideoPlayer,
        fileActions: MediaFileActions,
    ) : this(
        savedStateHandle.toRoute<MediaViewerRoute>(),
        savedStateHandle,
        mediaRepository,
        fileRepository,
        userRepository,
        chatRepository,
        clock,
        timePatterns,
        dispatcher,
        applicationScope,
        Locale::getDefault,
        videoPlayer,
        fileActions,
    )

    private val chatId = route.chatId

    /** What is loaded and which item is current, changed together, on the main thread only. */
    private val viewer =
        MutableStateFlow(Viewer(MediaLoad.Loading, savedStateHandle.get<Long>(KEY_CURRENT) ?: route.messageId))

    private val revealedSpoilers = MutableStateFlow<PersistentSet<Long>>(persistentSetOf(route.messageId))

    /** The sides loading a page, and those whose last page failed; main thread only. */
    private val loadingEdges = mutableSetOf<Edge>()
    private val failedEdges = mutableSetOf<Edge>()
    private var firstPage: Job? = null

    private val mapper = MediaItemMapper(clock, timePatterns, locale)

    private val effectChannel = Channel<MediaViewerEffect>(Channel.BUFFERED)

    /** One-off effects for the screen: snackbars. */
    val effects: Flow<MediaViewerEffect> = effectChannel.receiveAsFlow()

    private val sharing =
        MediaSharing(
            canBeSaved = { messageId -> mediaRepository.canBeSaved(chatId, messageId) },
            isProtected = chatRepository.chat(chatId).map { chat -> chat?.hasProtectedContent != false },
            fileActions = fileActions,
            savedStateHandle = savedStateHandle,
            scope = viewModelScope,
            effects = effectChannel,
        )

    private val downloads =
        MediaDownloads(fileRepository, viewModelScope) { error ->
            sharing.onDownloadFailed()
            effectChannel.showError(error)
        }

    private val playback =
        MediaPlayback(videoPlayer, viewModelScope) { effectChannel.trySend(MediaViewerEffect.PlaybackFailed) }

    /** The last [files], whose states carry over while the next ones are subscribed; main thread only. */
    private var knownFiles: Map<Int, FileState> = emptyMap()

    /** The states of the files of the current item and its neighbours, while they are those. */
    private val files: StateFlow<ImmutableMap<Int, FileState>> =
        viewer
            .map { it.nearbyFileIds() }
            .distinctUntilChanged()
            .flatMapLatest { ids -> fileRepository.statesOf(ids, knownFiles) }
            .onEach { knownFiles = it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, persistentMapOf())

    /** The titles of the chats that sent loaded media (channels, groups' anonymous admins), by id. */
    private val senderChatTitles: Flow<ImmutableMap<Long, String>> =
        viewer
            .map { it.senderChatIds() }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(persistentMapOf())
                } else {
                    combine(ids.map { id -> chatRepository.chat(id).map { chat -> id to chat?.title } }) { titles ->
                        titles.mapNotNull { (id, title) -> title?.let { id to it } }.toMap().toImmutableMap()
                    }
                }
            }.distinctUntilChanged()

    /** The items, mapped on [dispatcher]. */
    private val listing: Flow<Listing> =
        combine(viewer, revealedSpoilers, userRepository.users, senderChatTitles) { viewer, revealed, users, titles ->
            val items = viewer.window?.let { mapper.map(it, revealed, users, titles) } ?: persistentListOf()
            Listing(viewer.load, items, viewer.currentId)
        }.flowOn(dispatcher)

    /**
     * The player for the video surface of the item in the player ([MediaViewerUiState.Content.playback]); null before
     * anything played. Not state: it exists once the playback in [uiState] does.
     */
    internal val surfacePlayer: Player? get() = videoPlayer.surfacePlayer

    /** The current item's message id while it is loaded. */
    private val currentId: Flow<Long?> =
        viewer
            .map { viewer ->
                viewer.currentId.takeIf { (viewer.window?.indexOf(it) ?: -1) >= 0 }
            }.distinctUntilChanged()

    val uiState: StateFlow<MediaViewerUiState> =
        combine(listing, files, playback.state, sharing.state(currentId), ::uiStateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MediaViewerUiState.Loading)

    init {
        addCloseable {
            playback.release()
            downloads.cancelAll(files.value, applicationScope)
        }
        viewModelScope.launch { mediaRepository.deletedMessages(chatId).collect(::onDeleted) }
        viewModelScope.launch {
            combine(viewer, files, ::Pair).collect { (viewer, files) -> focus(viewer, files) }
        }
        loadAround(viewer.value.currentId)
    }

    fun onEvent(event: MediaViewerEvent) {
        when (event) {
            MediaViewerEvent.Started -> playback.setStarted(true)
            MediaViewerEvent.Stopped -> playback.setStarted(false)
            is MediaViewerEvent.PageSettled -> onPageSettled(event.messageId)
            MediaViewerEvent.Retry -> retry()
            is MediaViewerEvent.RevealSpoiler -> revealedSpoilers.update { it.adding(event.messageId) }
            MediaViewerEvent.PlayPause -> playback.playPause()
            is MediaViewerEvent.Seek -> playback.seek(event.fraction)
            MediaViewerEvent.Share -> sharing.start(MediaAction.Share)
            MediaViewerEvent.Save -> sharing.start(MediaAction.Save)
            is MediaViewerEvent.StoragePermissionChecked -> sharing.permission.onChecked(event.showRationale)
            is MediaViewerEvent.StorageRationaleAnswered -> sharing.permission.onRationaleAnswered(event.proceed)
            is MediaViewerEvent.StoragePermissionResult -> sharing.permission.onResult(event.granted)
        }
    }

    /** The settled page's item becomes the current one; a side that failed is tried again as the user moves on. */
    private fun onPageSettled(messageId: Long) {
        val state = viewer.value
        val isLoaded = (state.window?.indexOf(messageId) ?: -1) >= 0
        if (messageId == state.currentId || !isLoaded) return
        viewer.value = state.copy(currentId = messageId)
        savedStateHandle[KEY_CURRENT] = messageId
        failedEdges.clear()
        loadMoreIfNear()
    }

    private fun retry() {
        failedEdges.clear()
        when (viewer.value.load) {
            is MediaLoad.Failed -> loadAround(viewer.value.currentId)
            is MediaLoad.Loaded -> loadMoreIfNear()
            MediaLoad.Loading, MediaLoad.Gone -> Unit
        }
    }

    /**
     * Loads the page around message [messageId], replacing whatever was loaded. Not found means the message was
     * deleted or holds no media; other failures can be tried again.
     */
    private fun loadAround(messageId: Long) {
        firstPage?.cancel()
        viewer.update { it.copy(load = MediaLoad.Loading) }
        firstPage =
            viewModelScope.launch {
                when (val page = mediaRepository.around(chatId, messageId)) {
                    is Outcome.Success -> onFirstPage(page.value, messageId)
                    is Outcome.Failure -> viewer.update { it.copy(load = page.error.toFailedLoad()) }
                }
            }
    }

    /**
     * Shows the first [page], then loads around it and asks for the position of [messageId]. The repository never
     * leaves the message out; the window does if the viewer cannot show it (a photo without sizes): it is gone.
     */
    private fun onFirstPage(
        page: ChatMediaPage,
        messageId: Long,
    ) {
        val window = page.takeIf { it.messages.isNotEmpty() }?.let(MediaWindow::of)
        if (window == null || window.indexOf(messageId) < 0) {
            viewer.update { it.copy(load = MediaLoad.Gone) }
            return
        }
        viewer.update { it.copy(load = MediaLoad.Loaded(window)) }
        loadMoreIfNear()
        if (!window.reachedNewest) {
            viewModelScope.launch {
                mediaRepository.position(chatId, window.filter, messageId).onSuccess { position ->
                    updateWindow { it.withPosition(messageId, position) }
                }
            }
        }
    }

    /** Loads a page on each side the current item is near, unless that side is loading, failed or at its end. */
    private fun loadMoreIfNear() {
        val state = viewer.value
        val window = state.window ?: return
        val index = window.indexOf(state.currentId).takeIf { it >= 0 } ?: return
        if (index < PREFETCH_DISTANCE) loadEdge(Edge.Older, window)
        if (window.messages.lastIndex - index < PREFETCH_DISTANCE) loadEdge(Edge.Newer, window)
    }

    private fun loadEdge(
        edge: Edge,
        window: MediaWindow,
    ) {
        val reached = if (edge == Edge.Older) window.reachedOldest else window.reachedNewest
        if (reached || edge in loadingEdges || edge in failedEdges) return
        loadingEdges += edge
        viewModelScope.launch {
            val page =
                when (edge) {
                    Edge.Older -> mediaRepository.older(chatId, window.filter, window.olderFrom)
                    Edge.Newer -> mediaRepository.newer(chatId, window.filter, window.newerFrom)
                }
            loadingEdges -= edge
            when (page) {
                is Outcome.Success -> {
                    updateWindow { if (edge == Edge.Older) it.withOlder(page.value) else it.withNewer(page.value) }
                    loadMoreIfNear()
                }

                is Outcome.Failure -> {
                    failedEdges += edge
                    effectChannel.showError(page.error)
                }
            }
        }
    }

    private fun onDeleted(ids: Set<Long>) {
        val state = viewer.value
        val next = state.without(ids)
        if (next != state) {
            viewer.value = next
            savedStateHandle[KEY_CURRENT] = next.currentId
        }
    }

    /**
     * Downloads around the current item, puts its video or GIF in the player once its file is on the device, and
     * tells [sharing] which item is current.
     */
    private fun focus(
        viewer: Viewer,
        files: ImmutableMap<Int, FileState>,
    ) {
        val window = viewer.window
        val index = window?.indexOf(viewer.currentId) ?: -1
        if (window == null || index < 0) {
            playback.show(null)
            sharing.focus(null, files)
            return
        }
        downloads.focus(window.messages.map { it.content.toViewerMedia()?.fileId ?: 0 }, index, files)
        val message = window.messages[index]
        val media = message.content.toViewerMedia()
        playback.show(media?.playTarget(message.id, files))
        sharing.focus(media?.let { MediaSharing.Target(message.id, it) }, files)
    }

    private fun updateWindow(transform: (MediaWindow) -> MediaWindow) =
        viewer.update { viewer ->
            val load = viewer.load as? MediaLoad.Loaded ?: return@update viewer
            viewer.copy(load = MediaLoad.Loaded(transform(load.window)))
        }

    internal companion object {
        /** The current item's message id, which survives process death. */
        const val KEY_CURRENT = "media.currentMessageId"

        /** How close to either end of what is loaded the current item comes before the next page there loads. */
        const val PREFETCH_DISTANCE = 5

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What is loaded: the first page, its failure, nothing to show, or the media loaded so far. */
private sealed interface MediaLoad {
    data object Loading : MediaLoad

    data class Failed(
        val error: DomainError,
    ) : MediaLoad

    data object Gone : MediaLoad

    data class Loaded(
        val window: MediaWindow,
    ) : MediaLoad
}

/** Gone for a message that was not found; else a failure that can be tried again. */
private fun DomainError.toFailedLoad(): MediaLoad =
    if (this == DomainError.NotFound) MediaLoad.Gone else MediaLoad.Failed(this)

/** What [load] holds, and the current item's message id. */
private data class Viewer(
    val load: MediaLoad,
    val currentId: Long,
) {
    val window: MediaWindow? get() = (load as? MediaLoad.Loaded)?.window

    /** The files of the current item and its neighbours, which the viewer follows. */
    fun nearbyFileIds(): Set<Int> {
        val window = window
        val index = window?.indexOf(currentId) ?: -1
        if (window == null || index < 0) return emptySet()
        return (index - 1..index + 1)
            .mapNotNull {
                window.messages
                    .getOrNull(it)
                    ?.content
                    ?.toViewerMedia()
            }.flatMapTo(LinkedHashSet()) { it.followedFileIds }
    }

    /** The chats that sent loaded media. */
    fun senderChatIds(): Set<Long> =
        window?.messages?.mapNotNullTo(LinkedHashSet()) { (it.sender as? MessageSender.Chat)?.chatId }.orEmpty()

    /**
     * Without the deleted messages [ids]. A deleted current item gives way to the item that takes its index (the next
     * newer one, or the newest if it was the newest); with nothing left, the media is gone.
     */
    fun without(ids: Set<Long>): Viewer {
        val window = window ?: return this
        val remaining = window.without(ids)
        return when {
            remaining === window -> {
                this
            }

            remaining.messages.isEmpty() -> {
                copy(load = MediaLoad.Gone)
            }

            currentId in ids -> {
                val index = window.indexOf(currentId).coerceAtMost(remaining.messages.lastIndex)
                Viewer(MediaLoad.Loaded(remaining), remaining.messages[index].id)
            }

            else -> {
                copy(load = MediaLoad.Loaded(remaining))
            }
        }
    }
}

/** The items of [load], mapped, and the current item's message id. */
private class Listing(
    val load: MediaLoad,
    val items: ImmutableList<MediaItem>,
    val currentId: Long,
)

private enum class Edge {
    Older,
    Newer,
}

/** The state of [listing], with the [files], the [playback] and the [actions] of the moment. */
private fun uiStateOf(
    listing: Listing,
    files: ImmutableMap<Int, FileState>,
    playback: VideoPlayback?,
    actions: MediaActions,
): MediaViewerUiState =
    when (val load = listing.load) {
        MediaLoad.Loading -> {
            MediaViewerUiState.Loading
        }

        is MediaLoad.Failed -> {
            MediaViewerUiState.Error(load.error)
        }

        MediaLoad.Gone -> {
            MediaViewerUiState.Unavailable
        }

        is MediaLoad.Loaded -> {
            val items = listing.items
            if (items.isEmpty()) {
                MediaViewerUiState.Unavailable
            } else {
                MediaViewerUiState.Content(
                    items = items,
                    currentIndex = items.indexOfFirst { it.messageId == listing.currentId }.coerceAtLeast(0),
                    count = load.window.count(),
                    files = files,
                    playback = playback,
                    actions = actions,
                )
            }
        }
    }

/** A snackbar for [error]; none for `DomainError.Silent`. */
private fun SendChannel<MediaViewerEffect>.showError(error: DomainError) {
    if (error != DomainError.Silent) trySend(MediaViewerEffect.ShowError(error))
}

/** What the player plays for this media: a video's or GIF's file once it is on the device; nothing for a photo. */
private fun ViewerMedia.playTarget(
    messageId: Long,
    files: Map<Int, FileState>,
): PlayTarget? {
    val path = (files[fileId] as? FileState.Downloaded)?.path
    return when {
        path == null -> null
        this is ViewerMedia.Clip -> PlayTarget(messageId, path, VideoKind.Video, video.duration * MILLIS_PER_SECOND)
        this is ViewerMedia.Gif -> PlayTarget(messageId, path, VideoKind.Gif, animation.duration * MILLIS_PER_SECOND)
        else -> null
    }
}

/**
 * The states of [fileIds], by id, as TDLib reports them, starting from those [known] already, so a page turned to
 * does not lose its states for a moment; a file not reported yet is left out until it is.
 */
private fun FileRepository.statesOf(
    fileIds: Set<Int>,
    known: Map<Int, FileState>,
): Flow<ImmutableMap<Int, FileState>> =
    if (fileIds.isEmpty()) {
        flowOf(persistentMapOf())
    } else {
        combine(
            fileIds.map { id ->
                observe(id).map<FileState, Pair<Int, FileState?>> { id to it }.onStart { emit(id to known[id]) }
            },
        ) { states -> states.mapNotNull { (id, state) -> state?.let { id to it } }.toMap().toImmutableMap() }
            .distinctUntilChanged()
    }

private const val MILLIS_PER_SECOND = 1000L

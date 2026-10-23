package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.TimeSource

/**
 * The stories viewer, read only (`docs/ROADMAP.md` M6; `docs/DESIGN_SYSTEM.md` §6, Story viewer): the chats of the
 * main stories list to swipe between, starting at the route's chat (alone, if it is in no list), and the stories of
 * the current one, from the route's story, else where the user left that chat in this viewer, else its first unseen
 * story ([ActiveStories.firstUnreadIndex]).
 *
 * - **Playing**: the story's file downloads at visible priority; once it is on the device, a photo stays for
 *   [StoryContent.PHOTO_DURATION_MILLIS] and a video plays once ([StoryTimeline]), then the next story shows, then the
 *   next chat's, and after the last chat's last story the viewer closes ([StoryViewerEffect.Close]). Taps move to the
 *   next or previous story; before the first of the first chat, the story starts over. What it cannot show (a live
 *   stream, unsupported content, a deleted story) says so for a photo's time and moves on. The next story's file
 *   downloads ahead at prefetch priority, and nothing further.
 * - **Viewed**: every story that shows while the screen is started is opened for TDLib (`openStory`, which marks it
 *   viewed, as Telegram's terms ask: there is no hidden viewing and no stealth mode), once per showing, and closed
 *   (`closeStory`) when it leaves, the screen stops or the viewer closes.
 * - **Pausing**: while a finger holds the story, the pager moves, the reactions are open, the reply field has the
 *   keyboard, or the screen is stopped ([Pause]).
 * - **Reactions**: the emoji TDLib offers for stories without Telegram Premium; a tap sets one or takes it back,
 *   refusals are snackbars. **Replies**: only to a user's story that may be replied to, privately, in their chat.
 * - **Saved state**: the chats, the one shown and its story, so after process death the viewer opens where it was.
 *   The reply's text is not saved.
 *
 * It takes the repositories it combines, the clock, the time source, the application scope (to close the story
 * after the view model is cleared) and its player, hence `LongParameterList` is suppressed; it is large because it
 * plays, navigates and reacts in one place, hence `TooManyFunctions`.
 */
@Suppress("LongParameterList", "TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoryViewerViewModel internal constructor(
    private val route: StoryViewerRoute,
    private val savedStateHandle: SavedStateHandle,
    private val storyRepository: StoryRepository,
    private val chatRepository: ChatRepository,
    private val fileRepository: FileRepository,
    private val userRepository: UserRepository,
    private val clock: WallClock,
    timeSource: TimeSource,
    private val applicationScope: CoroutineScope,
    private val videoPlayer: VideoPlayer,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        storyRepository: StoryRepository,
        chatRepository: ChatRepository,
        fileRepository: FileRepository,
        userRepository: UserRepository,
        clock: WallClock,
        @ApplicationScope applicationScope: CoroutineScope,
        videoPlayer: VideoPlayer,
    ) : this(
        savedStateHandle.toRoute<StoryViewerRoute>(),
        savedStateHandle,
        storyRepository,
        chatRepository,
        fileRepository,
        userRepository,
        clock,
        TimeSource.Monotonic,
        applicationScope,
        videoPlayer,
    )

    /** The reply field's text, never saved. */
    val replyText = TextFieldState()

    /** The player a video story's surface shows; null before the first video. */
    val surfacePlayer get() = videoPlayer.surfacePlayer

    private val posters =
        MutableStateFlow(savedStateHandle.get<LongArray>(KEY_POSTERS)?.toList()?.toImmutableList())

    private val position =
        MutableStateFlow(Position(savedStateHandle[KEY_POSTER] ?: 0, savedStateHandle[KEY_STORY] ?: route.storyId))

    /** Where the user left each chat in this viewer, by story id, so swiping back returns there. */
    private val leftAt = MutableStateFlow<PersistentMap<Long, Int>>(persistentMapOf())

    private val pauses = MutableStateFlow(setOf(Pause.Stopped))
    private val muted = MutableStateFlow(false)
    private val retries = MutableStateFlow(0)
    private val reactions = MutableStateFlow<ImmutableList<ReactionType.Emoji>?>(null)
    private val interaction = MutableStateFlow(Interaction())

    private val effectChannel = Channel<StoryViewerEffect>(Channel.BUFFERED)
    val effects: Flow<StoryViewerEffect> = effectChannel.receiveAsFlow()

    private val timeline =
        StoryTimeline(
            player = videoPlayer,
            scope = viewModelScope,
            timeSource = timeSource,
            onEnd = ::next,
            onFailed = ::onPlaybackFailed,
        )

    /** The current chat's active stories as last read, for moving between its stories. */
    private var currentActive: ActiveStories? = null

    /** The main stories list as last read, for the story the next chat opens at. */
    private var listedActive: Map<Long, ActiveStories> = emptyMap()

    /** The story open for TDLib (`openStory`), until it is closed. */
    private var opened: StoryKey? = null

    /** Files downloaded at visible or prefetch priority in this viewer, each asked for once. */
    private val requestedFiles = mutableSetOf<Int>()
    private var failedPlayback: StoryKey? = null

    private val currentChatId: Flow<Long?> =
        combine(posters, position) { posters, position -> posters?.getOrNull(position.posterIndex) }
            .distinctUntilChanged()

    // Shared: the pages and the story choice read it, and it asks TDLib for a chat it has not announced.
    private val currentStories: Flow<Outcome<ActiveStories>?> =
        combine(currentChatId, retries) { chatId, _ -> chatId }
            .flatMapLatest { chatId -> chatId?.let(storyRepository::activeStoriesOf) ?: flowOf(null) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val currentKey: Flow<StoryKey?> =
        combine(currentChatId, position) { chatId, position ->
            if (chatId != null && position.storyId != null) StoryKey(chatId, position.storyId) else null
        }.distinctUntilChanged()

    /**
     * The current story with its file's state; null while no story is chosen. Shared: the state and the timeline read
     * it, and it asks TDLib for the story.
     */
    private val shown: Flow<Shown?> =
        combine(currentKey, retries) { key, _ -> key }
            .flatMapLatest { key ->
                if (key == null) {
                    flowOf(null)
                } else {
                    storyRepository.story(key.chatId, key.storyId).flatMapLatest { outcome -> shownOf(key, outcome) }
                }
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val pages: Flow<ImmutableList<StoryPosterUi>> =
        posters.flatMapLatest { ids ->
            if (ids.isNullOrEmpty()) {
                flowOf(persistentListOf())
            } else {
                val listed =
                    storyRepository.activeStories(StoryList.Main).map { list -> list.associateBy { it.chatId } }
                val chats = combine(ids.map { id -> chatRepository.chat(id) }) { it.toList() }
                combine(chats, listed, currentStories, position, leftAt) { chats, listed, current, position, leftAt ->
                    val currentStories = (current as? Outcome.Success)?.value
                    ids
                        .mapIndexed { index, id ->
                            val active = currentStories?.takeIf { it.chatId == id } ?: listed[id]
                            val shownId = if (index == position.posterIndex) position.storyId else leftAt[id]
                            pageOf(id, chats[index], active, shownId)
                        }.toImmutableList()
                }
            }
        }

    private val chrome: Flow<Chrome> =
        combine(pauses, muted, reactions, interaction) { pauses, muted, reactions, interaction ->
            Chrome(pauses.isNotEmpty(), muted, reactions, interaction)
        }

    private val me: Flow<Long?> = userRepository.myUserId

    val uiState: StateFlow<StoryViewerUiState> =
        combine(
            pages,
            position,
            shown,
            timeline.state,
            combine(chrome, me, currentChat()) { chrome, me, chat -> Triple(chrome, me, chat) },
        ) {
            pages,
            position,
            shown,
            timeline,
            (chrome, me, chat),
            ->
            stateOf(pages, position, shown, timeline, chrome, me, chat)
        }.distinctUntilChanged()
            .onStart { start() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryViewerUiState.Loading)

    fun onEvent(event: StoryViewerEvent) {
        when (event) {
            is StoryViewerEvent.Started -> {
                setPaused(Pause.Stopped, !event.started)
            }

            StoryViewerEvent.Next -> {
                next()
            }

            StoryViewerEvent.Previous -> {
                previous()
            }

            is StoryViewerEvent.Hold -> {
                setPaused(Pause.Held, event.held)
            }

            is StoryViewerEvent.Paging -> {
                setPaused(Pause.Paging, event.moving)
            }

            is StoryViewerEvent.PosterSettled -> {
                if (event.index != position.value.posterIndex) showPoster(event.index)
            }

            StoryViewerEvent.ToggleMute -> {
                muted.update { !it }
                videoPlayer.setMuted(muted.value)
            }

            is StoryViewerEvent.ReplyFocused -> {
                setPaused(Pause.Typing, event.focused)
            }

            StoryViewerEvent.SendReply -> {
                sendReply()
            }

            is StoryViewerEvent.ReactionsOpen -> {
                interaction.update { it.copy(reactionsOpen = event.open) }
                setPaused(Pause.Reactions, event.open)
            }

            is StoryViewerEvent.React -> {
                react(event.reaction)
            }

            StoryViewerEvent.Retry -> {
                failedPlayback = null
                requestedFiles.clear()
                retries.update { it + 1 }
            }
        }
    }

    override fun onCleared() {
        timeline.release()
        opened?.let { story -> applicationScope.launch { storyRepository.closeStory(story.chatId, story.storyId) } }
        opened = null
    }

    /** Starts the viewer's work once its screen first collects the state, after the first frame. */
    private var hasStarted = false

    private fun start() {
        if (hasStarted) return
        hasStarted = true
        viewModelScope.launch { choosePosters() }
        viewModelScope.launch { followCurrentStories() }
        viewModelScope.launch { followShown() }
        viewModelScope.launch { followOpened() }
        viewModelScope.launch { pauses.collect { timeline.setPaused(it.isNotEmpty()) } }
        viewModelScope.launch { position.collect(::save) }
        viewModelScope.launch {
            storyRepository.activeStories(StoryList.Main).collect { list ->
                listedActive = list.associateBy { it.chatId }
            }
        }
        viewModelScope.launch {
            val available = storyRepository.availableReactions()
            reactions.value =
                (available as? Outcome.Success)?.value?.filterIsInstance<ReactionType.Emoji>()?.toImmutableList()
        }
    }

    /** The chats to swipe between: the main stories list as it is now, or the route's chat alone if it is not in it. */
    private suspend fun choosePosters() {
        if (posters.value != null) return
        val listed = storyRepository.activeStories(StoryList.Main).first().map { it.chatId }
        val chosen = if (route.chatId in listed) listed else listOf(route.chatId)
        position.value = Position(chosen.indexOf(route.chatId), route.storyId)
        posters.value = chosen.toImmutableList()
        savedStateHandle[KEY_POSTERS] = chosen.toLongArray()
    }

    /**
     * Follows the current chat's active stories: picks the story to show when none is chosen yet, and moves on when the
     * chat has none left.
     */
    private suspend fun followCurrentStories() {
        currentStories.collect { outcome ->
            val active = (outcome as? Outcome.Success)?.value ?: return@collect
            currentActive = active
            val current = position.value
            if (current.storyId != null) return@collect
            val ids = active.stories.map { it.id }
            if (ids.isEmpty()) {
                nextPoster()
                return@collect
            }
            val storyId = leftAt.value[active.chatId]?.takeIf { it in ids } ?: ids[active.firstUnreadIndex]
            position.value = current.copy(storyId = storyId)
        }
    }

    /** Puts the current story into the timeline once it can play, downloads its file, and the next one's ahead. */
    private suspend fun followShown() {
        shown.collect { shown ->
            // A video the player could not play shows its picture for a photo's time instead.
            val failed = shown?.key?.takeIf { it == failedPlayback }
            val still = failed?.let { TimelineTarget.Still(it.storyId, StoryContent.PHOTO_DURATION_MILLIS) }
            timeline.show(still ?: shown?.target)
            if (shown == null) return@collect
            shown.download?.let { download(it, DownloadPriority.Visible) }
            if (shown.target != null) prefetchAfter(shown.key)
        }
    }

    /**
     * Keeps the current story open for TDLib while the screen is started: `openStory` when a story shows, `closeStory`
     * for the one it replaces and when the screen stops.
     */
    private suspend fun followOpened() {
        combine(currentKey, pauses.map { Pause.Stopped !in it }.distinctUntilChanged()) { key, started ->
            key.takeIf { started }
        }.distinctUntilChanged()
            .collect { key ->
                opened?.let { storyRepository.closeStory(it.chatId, it.storyId) }
                opened = key
                key?.let { storyRepository.openStory(it.chatId, it.storyId) }
            }
    }

    /** What [outcome], story [key] as TDLib gives it, shows: an error, a deleted story, or the story. */
    private fun shownOf(
        key: StoryKey,
        outcome: Outcome<Story?>,
    ): Flow<Shown> =
        when (outcome) {
            is Outcome.Failure -> {
                flowOf(Shown(key, null, StoryMedia.Failed(outcome.error), target = null))
            }

            is Outcome.Success -> {
                val story = outcome.value
                if (story == null) flowOf(Shown.still(key, null, StoryMedia.Unavailable)) else shownOf(key, story)
            }
        }

    /** What [story] shows, with its file's state for a photo or a video. */
    private fun shownOf(
        key: StoryKey,
        story: Story,
    ): Flow<Shown> =
        when (val content = story.content) {
            is StoryContent.Photo -> {
                val size = content.photo.sizes.maxByOrNull { it.width.toLong() * it.height }
                if (size == null) {
                    flowOf(Shown.still(key, story, StoryMedia.Unsupported))
                } else {
                    fileStates(size.file.id).map { file ->
                        val ready = file is FileState.Downloaded
                        val minithumbnail = content.photo.minithumbnail
                        Shown(
                            key = key,
                            story = story,
                            media = StoryMedia.Photo(size.file.id, size.width, size.height, minithumbnail, ready),
                            target =
                                TimelineTarget.Still(key.storyId, StoryContent.PHOTO_DURATION_MILLIS).takeIf { ready },
                            download = size.file.id.takeUnless { ready },
                        )
                    }
                }
            }

            is StoryContent.Video -> {
                fileStates(content.file.id).map { file ->
                    val path = (file as? FileState.Downloaded)?.path
                    Shown(
                        key = key,
                        story = story,
                        media =
                            StoryMedia.Video(
                                width = content.width,
                                height = content.height,
                                minithumbnail = content.minithumbnail,
                                thumbnailFileId = content.thumbnail?.file?.id,
                                isReady = path != null,
                                showsFrame = false,
                                hasSound = !content.isAnimation,
                            ),
                        target = path?.let { TimelineTarget.Video(key.storyId, it, content.durationMillis) },
                        download = content.file.id.takeIf { path == null },
                    )
                }
            }

            StoryContent.Live -> {
                flowOf(Shown.still(key, story, StoryMedia.Live))
            }

            StoryContent.Unsupported -> {
                flowOf(Shown.still(key, story, StoryMedia.Unsupported))
            }
        }

    /** File [fileId]'s state, null until TDLib reported it. */
    private fun fileStates(fileId: Int): Flow<FileState?> =
        fileRepository
            .observe(fileId)
            .map<FileState, FileState?> { it }
            .onStart { emit(null) }
            .distinctUntilChanged()

    private fun download(
        fileId: Int,
        priority: DownloadPriority,
    ) {
        if (!requestedFiles.add(fileId)) return
        viewModelScope.launch { fileRepository.download(fileId, priority) }
    }

    /**
     * Downloads the file of the story after [key] ahead, at prefetch priority: the next of its chat, or the next
     * chat's.
     */
    private fun prefetchAfter(key: StoryKey) {
        val next = nextKey(key) ?: return
        viewModelScope.launch {
            val found = storyRepository.story(next.chatId, next.storyId).first()
            val story = (found as? Outcome.Success)?.value ?: return@launch
            val fileId =
                when (val content = story.content) {
                    is StoryContent.Photo -> {
                        content.photo.sizes
                            .maxByOrNull { it.width.toLong() * it.height }
                            ?.file
                            ?.id
                    }

                    is StoryContent.Video -> {
                        content.file.id
                    }

                    else -> {
                        null
                    }
                }
            fileId?.let { download(it, DownloadPriority.Prefetch) }
        }
    }

    /** The story after [key]: the next of its chat, else the first one the next chat would show. */
    private fun nextKey(key: StoryKey): StoryKey? {
        val stories = currentActive?.takeIf { it.chatId == key.chatId }?.stories
        val following = stories?.firstOrNull { it.id > key.storyId }
        return if (following != null) StoryKey(key.chatId, following.id) else nextChatKey()
    }

    /** The story the next chat would open at, as the stories list knows it; null without one. */
    private fun nextChatKey(): StoryKey? {
        val nextChat = posters.value?.getOrNull(position.value.posterIndex + 1)
        val active = nextChat?.let { listedActive[it] }?.takeIf { it.stories.isNotEmpty() } ?: return null
        val storyId = leftAt.value[active.chatId]?.takeIf { id -> active.stories.any { it.id == id } }
        return StoryKey(active.chatId, storyId ?: active.stories[active.firstUnreadIndex].id)
    }

    /** The next story of the chat, else the next chat; nothing while the chat's story is still being chosen. */
    private fun next() {
        val posters = posters.value ?: return
        val current = position.value
        val storyId = current.storyId ?: return
        val following = currentActive?.stories?.firstOrNull { it.id > storyId }
        if (following != null && currentActive?.chatId == posters.getOrNull(current.posterIndex)) {
            position.value = current.copy(storyId = following.id)
        } else {
            nextPoster()
        }
    }

    /** The next chat, or after the last one the viewer closes. */
    private fun nextPoster() {
        val posters = posters.value ?: return
        val current = position.value
        if (current.posterIndex < posters.lastIndex) {
            showPoster(current.posterIndex + 1)
        } else {
            effectChannel.trySend(StoryViewerEffect.Close)
        }
    }

    private fun previous() {
        val posters = posters.value ?: return
        val current = position.value
        val storyId = current.storyId ?: return
        val earlier = currentActive?.stories?.lastOrNull { it.id < storyId }
        when {
            earlier != null && currentActive?.chatId == posters.getOrNull(current.posterIndex) -> {
                position.value = current.copy(storyId = earlier.id)
            }

            current.posterIndex > 0 -> {
                showPoster(current.posterIndex - 1)
            }

            else -> {
                timeline.restart()
            }
        }
    }

    /** Shows chat [index] of the posters, where the user left it or at its first unseen story. */
    private fun showPoster(index: Int) {
        val posters = posters.value ?: return
        if (index !in posters.indices) return
        val current = position.value
        val leaving = posters.getOrNull(current.posterIndex)
        if (leaving != null && current.storyId != null) leftAt.update { it.putting(leaving, current.storyId) }
        currentActive = null
        interaction.update { it.copy(reactionsOpen = false) }
        setPaused(Pause.Reactions, false)
        position.value = Position(index, storyId = null)
    }

    private fun setPaused(
        pause: Pause,
        paused: Boolean,
    ) {
        pauses.update { if (paused) it + pause else it - pause }
    }

    private fun onPlaybackFailed() {
        val key = currentKeyNow() ?: return
        failedPlayback = key
        effectChannel.trySend(StoryViewerEffect.PlaybackFailed)
        timeline.show(TimelineTarget.Still(key.storyId, StoryContent.PHOTO_DURATION_MILLIS))
    }

    private fun currentKeyNow(): StoryKey? {
        val current = position.value
        val chatId = posters.value?.getOrNull(current.posterIndex) ?: return null
        return current.storyId?.let { StoryKey(chatId, it) }
    }

    private fun sendReply() {
        val key = currentKeyNow() ?: return
        val text = replyText.text.toString().trim()
        if (text.isEmpty() || interaction.value.isSendingReply) return
        interaction.update { it.copy(isSendingReply = true) }
        viewModelScope.launch {
            when (val sent = storyRepository.reply(key.chatId, key.storyId, FormattedText(text, persistentListOf()))) {
                is Outcome.Success -> {
                    replyText.clearText()
                    effectChannel.trySend(StoryViewerEffect.ReplySent)
                }

                is Outcome.Failure -> {
                    effectChannel.trySend(StoryViewerEffect.ShowError(sent.error))
                }
            }
            interaction.update { it.copy(isSendingReply = false) }
        }
    }

    private fun react(reaction: ReactionType.Emoji) {
        val key = currentKeyNow() ?: return
        val chosen = (uiState.value as? StoryViewerUiState.Content)?.reactions?.chosen
        val target = if (chosen == reaction) null else reaction
        interaction.update { it.copy(reactionsOpen = false, pendingReaction = PendingReaction(target)) }
        setPaused(Pause.Reactions, false)
        viewModelScope.launch {
            val set = storyRepository.setReaction(key.chatId, key.storyId, target)
            if (set is Outcome.Failure) effectChannel.trySend(StoryViewerEffect.ShowError(set.error))
            interaction.update { it.copy(pendingReaction = null) }
        }
    }

    private fun currentChat(): Flow<Chat?> =
        currentChatId.flatMapLatest { id -> id?.let(chatRepository::chat) ?: flowOf(null) }

    private fun stateOf(
        pages: ImmutableList<StoryPosterUi>,
        position: Position,
        shown: Shown?,
        timeline: TimelineState?,
        chrome: Chrome,
        me: Long?,
        chat: Chat?,
    ): StoryViewerUiState {
        if (pages.isEmpty()) return StoryViewerUiState.Loading
        val index = position.posterIndex.coerceIn(pages.indices)
        val page = pages[index]
        // A story still shown for the position before is no longer the current one.
        val current = shown?.takeIf { it.key == StoryKey(page.chatId, position.storyId ?: NO_STORY) }
        val story = current?.story
        val played = timeline?.takeIf { current != null && it.target.storyId == current.key.storyId }
        val isOwn = me != null && (chat?.type as? ChatType.Private)?.userId == me
        return StoryViewerUiState.Content(
            posters = pages,
            current = index,
            story =
                StoryUi(
                    storyId = position.storyId ?: NO_STORY,
                    index = page.shownIndex,
                    count = page.storyCount,
                    date = story?.date ?: 0,
                    ageSeconds = story?.let { (clock.now().epochSecond - it.date).coerceAtLeast(0) } ?: 0,
                    media = mediaOf(current, played),
                    caption = story?.caption ?: FormattedText.Empty,
                    progress = played?.progress ?: NO_PROGRESS,
                ),
            isPaused = chrome.isPaused,
            isMuted = chrome.isMuted,
            reply = replyOf(story, chat, isOwn, chrome.interaction),
            reactions = story?.takeUnless { isOwn }?.let { reactionsOf(it, chrome) },
        )
    }

    /** What the story shows; a video's first frame from the timeline. */
    private fun mediaOf(
        shown: Shown?,
        played: TimelineState?,
    ): StoryMedia =
        when (val media = shown?.media ?: StoryMedia.Loading) {
            is StoryMedia.Video -> media.copy(showsFrame = played?.showsFrame == true)
            else -> media
        }

    /** The reply field: only for a user's story that may be replied to, never the user's own. */
    private fun replyOf(
        story: Story?,
        chat: Chat?,
        isOwn: Boolean,
        interaction: Interaction,
    ): StoryReplyUi? {
        val canReply = story != null && story.canBeReplied && chat?.type is ChatType.Private && !isOwn
        return if (canReply) StoryReplyUi(isSending = interaction.isSendingReply) else null
    }

    /** The reactions on [story], the one on its way to TDLib shown as chosen; null while none are known. */
    private fun reactionsOf(
        story: Story,
        chrome: Chrome,
    ): StoryReactionsUi? {
        val available = chrome.reactions?.takeIf { it.isNotEmpty() } ?: return null
        val pending = chrome.interaction.pendingReaction
        return StoryReactionsUi(
            available = available,
            chosen = if (pending != null) pending.reaction else story.chosenReaction,
            isOpen = chrome.interaction.reactionsOpen,
            isSending = pending != null,
        )
    }

    private fun save(position: Position) {
        savedStateHandle[KEY_POSTER] = position.posterIndex
        savedStateHandle[KEY_STORY] = position.storyId
    }

    /** Why the story does not play; it plays while there is none. */
    private enum class Pause {
        Stopped,
        Held,
        Paging,
        Typing,
        Reactions,
    }

    /** The chat shown, by its index among the posters, and its story; null until chosen. */
    private data class Position(
        val posterIndex: Int,
        val storyId: Int?,
    )

    /** What the user does to the story: the reactions' row, a reaction on its way, a reply on its way. */
    private data class Interaction(
        val reactionsOpen: Boolean = false,
        val pendingReaction: PendingReaction? = null,
        val isSendingReply: Boolean = false,
    )

    /** A reaction, or none ([reaction] null), set but not yet confirmed by TDLib. */
    private data class PendingReaction(
        val reaction: ReactionType?,
    )

    private data class Chrome(
        val isPaused: Boolean,
        val isMuted: Boolean,
        val reactions: ImmutableList<ReactionType.Emoji>?,
        val interaction: Interaction,
    )

    private companion object {
        const val KEY_POSTERS = "story_viewer_posters"
        const val KEY_POSTER = "story_viewer_poster"
        const val KEY_STORY = "story_viewer_story"

        /** Keeps the state through a rotation, as every screen's view model does. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        val NO_PROGRESS: () -> Float = { 0f }

        /** Stands for no story chosen yet; TDLib's story ids are positive. */
        const val NO_STORY = 0
    }
}

/** Story [storyId] of chat [chatId]. */
internal data class StoryKey(
    val chatId: Long,
    val storyId: Int,
)

/**
 * The story of [key] as it shows: the [story] (null when it failed or was deleted), its [media], what the timeline
 * plays once it can ([target]) and the file to download first ([download]).
 */
internal data class Shown(
    val key: StoryKey,
    val story: Story?,
    val media: StoryMedia,
    val target: TimelineTarget?,
    val download: Int? = null,
) {
    companion object {
        /** What shows for a photo's time without a file: a live stream, unsupported content, a deleted story. */
        fun still(
            key: StoryKey,
            story: Story?,
            media: StoryMedia,
        ) = Shown(key, story, media, TimelineTarget.Still(key.storyId, StoryContent.PHOTO_DURATION_MILLIS))
    }
}

/**
 * The page of chat [chatId]: its name and photo, its story count and the story it shows or opens at: [shownId], else
 * its first unseen one.
 */
private fun pageOf(
    chatId: Long,
    chat: Chat?,
    active: ActiveStories?,
    shownId: Int?,
): StoryPosterUi {
    val stories = active?.stories.orEmpty()
    val shownIndex =
        shownId?.let { id -> stories.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: active?.firstUnreadIndex ?: 0
    return StoryPosterUi(
        chatId = chatId,
        name = chat?.title.orEmpty(),
        photo = chat?.photo,
        storyCount = stories.size,
        shownIndex = shownIndex,
    )
}

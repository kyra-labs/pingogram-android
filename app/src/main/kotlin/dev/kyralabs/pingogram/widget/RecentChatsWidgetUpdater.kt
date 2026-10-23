package dev.kyralabs.pingogram.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.notifications.AvatarIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the home screen widget of recent chats current (`docs/adr/0017-home-screen-widget.md`), without polling: it
 * follows the login, the passcode lock and the main chat list while the app's process runs, and only while a widget
 * is placed ([RecentChatsWidgets.hasWidgets], then the receiver's [onWidgetsChanged]). Each change of what the widget
 * shows becomes [content] and one update of the placed widgets ([RecentChatsWidgets.updateAll], Glance's own work),
 * [UPDATE_DELAY] after the chats last changed, so a busy chat list redraws it rarely. With a passcode set the widget
 * names no chat ([RecentChatsContent.Locked]).
 *
 * A process started for the widget (after a reboot, or by a push in the gms flavor) starts TDLib as any other; the
 * updater then loads the first chats itself if TDLib has fewer, as the chat list would on screen.
 *
 * `PingogramApplication` calls [start] in every process; it returns at once, all of it running on the application
 * scope.
 */
@Singleton
@Suppress("LongParameterList") // Injected: the repositories it combines, the avatars, the widgets and the app's scope.
class RecentChatsWidgetUpdater
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val authRepository: AuthRepository,
        private val passcodeRepository: PasscodeRepository,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val avatars: AvatarIcons,
        private val widgets: RecentChatsWidgets,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        /** Whether a widget is placed; null until asked. */
        private val placed = MutableStateFlow<Boolean?>(null)

        private val mutableContent = MutableStateFlow<RecentChatsContent>(RecentChatsContent.Loading)

        /** What the widgets show now; [RecentChatsContent.Loading] while no widget is placed. */
        val content: StateFlow<RecentChatsContent> = mutableContent.asStateFlow()

        /** Starts following what the widgets show; call once per process. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun start() {
            // The first redraw of a process waits for the start to settle: Glance composes the widget in a worker,
            // which starts WorkManager, and that would compete with the chat list's first page (docs/PERFORMANCE.md
            // §2). The system shows the widget's last content meanwhile.
            val started = scope.async { delay(START_UPDATE_DELAY) }
            scope.launch {
                if (placed.value == null) placed.compareAndSet(null, widgets.hasWidgets())
                placed
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { if (it) contents() else emptyFlow() }
                    .collectLatest { content ->
                        mutableContent.value = content
                        started.await()
                        widgets.updateAll()
                    }
            }
        }

        /** The first widget was placed ([present]), the system asked for its content, or the last one was removed. */
        fun onWidgetsChanged(present: Boolean) {
            placed.value = present
            if (!present) mutableContent.value = RecentChatsContent.Loading
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun contents(): Flow<RecentChatsContent> =
            combine(
                authRepository.authStep,
                passcodeRepository.settings.map { it.isSet }.distinctUntilChanged(),
            ) { step, locked ->
                when {
                    step == AuthStep.Initializing -> State.Loading
                    step != AuthStep.Ready -> State.SignedOut
                    locked -> State.Locked
                    else -> State.Chats
                }
            }.distinctUntilChanged()
                .flatMapLatest { state ->
                    when (state) {
                        State.Loading -> flowOf(RecentChatsContent.Loading)
                        State.SignedOut -> flowOf(RecentChatsContent.SignedOut)
                        State.Locked -> flowOf(RecentChatsContent.Locked)
                        State.Chats -> chats()
                    }
                }

        /** The rows of the most recent chats with their avatars, as they change; loading the first chats if needed. */
        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private fun chats(): Flow<RecentChatsContent> =
            channelFlow {
                val loaded = MutableStateFlow(false)
                launch {
                    if (chatRepository.chatList(ChatListType.Main).first().size < MAX_CHATS) {
                        chatRepository.loadChats(ChatListType.Main, MAX_CHATS)
                    }
                    loaded.value = true
                }
                combine(
                    chatRepository.chatList(ChatListType.Main),
                    userRepository.users,
                    userRepository.myUserId,
                    loaded,
                ) { chats, users, me, done ->
                    val rows = widgetChats(chats, users, me, context.resources, MAX_CHATS)
                    if (rows.isEmpty() && !done) null else Rows(rows, chats.take(MAX_CHATS).map { it.photo })
                }.distinctUntilChanged()
                    .debounce(UPDATE_DELAY)
                    .mapLatest { rows -> rows?.withAvatars() ?: RecentChatsContent.Loading }
                    .collect { send(it) }
            }

        private suspend fun Rows.withAvatars(): RecentChatsContent {
            val size = (AVATAR_SIZE_DP * context.resources.displayMetrics.density).roundToInt()
            return RecentChatsContent.Chats(
                rows.mapIndexed { index, row ->
                    row.copy(avatar = avatars.bitmap(photos[index], row.id, row.title, size))
                },
            )
        }

        /** The widget's rows and the chats' photos, compared as a whole before avatars are drawn. */
        private data class Rows(
            val rows: List<WidgetChat>,
            val photos: List<ProfilePhoto?>,
        )

        private enum class State { Loading, SignedOut, Locked, Chats }

        internal companion object {
            /** How many chats the widget shows at most: a tall widget's worth; each avatar travels as a bitmap. */
            const val MAX_CHATS = 10

            /** How long the chats must stay as they are before the widgets are drawn again. */
            val UPDATE_DELAY = 1.seconds

            /** How long after the process started the widgets are first drawn again. */
            val START_UPDATE_DELAY = 5.seconds

            /** The avatars' size in the widget's rows ([WidgetTokens.avatar]), in dp. */
            const val AVATAR_SIZE_DP = 40f
        }
    }

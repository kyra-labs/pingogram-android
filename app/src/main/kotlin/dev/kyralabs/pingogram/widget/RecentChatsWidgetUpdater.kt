package dev.kyralabs.pingogram.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.notifications.AvatarIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the home screen widgets of recent chats current (`docs/adr/0017-home-screen-widget.md`), without polling: it
 * follows the login, the passcode lock and the main chat list while the app's process runs, and only while a widget
 * is placed ([RecentChatsWidgets.slots], then [onWidgetShown] and [onWidgetsChanged]). Each widget keeps the account
 * it was placed in (`docs/adr/0031-multiple-accounts.md`), so the updater follows the accounts of the placed widgets,
 * each one's [content] apart; a widget whose account left the device moves to the shown account. Each change of what
 * a widget shows becomes one update of the placed widgets ([RecentChatsWidgets.updateAll], Glance's own work),
 * [UPDATE_DELAY] after the chats last changed, so a busy chat list redraws them rarely. With a passcode set a widget
 * names no chat ([RecentChatsContent.Locked]); locked chats (`docs/adr/0029-locked-chats.md`), which need a passcode,
 * are left out besides, should the widget ever show chats behind one.
 *
 * A process started for the widget (after a reboot, or by a push in the gms flavor) starts TDLib as any other; the
 * updater then loads the first chats itself if TDLib has fewer, as the chat list would on screen.
 *
 * `PingogramApplication` calls [start] in every process; it returns at once, all of it running on the application
 * scope.
 */
@Singleton
class RecentChatsWidgetUpdater
    internal constructor(
        private val context: Context,
        private val passcodeRepository: PasscodeRepository,
        private val widgets: RecentChatsWidgets,
        private val accounts: Flow<Map<Int, WidgetAccount>>,
        private val activeSlot: StateFlow<Int>,
        private val scope: CoroutineScope,
    ) {
        @Inject
        internal constructor(
            @ApplicationContext context: Context,
            passcodeRepository: PasscodeRepository,
            widgets: RecentChatsWidgets,
            graphs: AccountGraphs,
            appearance: AppearanceRepository,
            @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
            @ApplicationScope scope: CoroutineScope,
        ) : this(
            context,
            passcodeRepository,
            widgets,
            widgetAccounts(context, graphs, appearance, ioDispatcher),
            graphs.activeSlot,
            scope,
        )

        /** The accounts of the placed widgets, by slot; null until asked. */
        private val placed = MutableStateFlow<Set<Int>?>(null)

        private val contents = MutableStateFlow<Map<Int, RecentChatsContent>>(emptyMap())

        /** The shown account's slot, which a widget placed now keeps. */
        val shownSlot: Int get() = activeSlot.value

        /** What the widgets of the account in [slot] show now; [RecentChatsContent.Loading] while none is placed. */
        fun content(slot: Int): Flow<RecentChatsContent> = contents.map { it.contentOf(slot) }.distinctUntilChanged()

        /** What the widgets of the account in [slot] show at this moment, for the first frame. */
        fun currentContent(slot: Int): RecentChatsContent = contents.value.contentOf(slot)

        /** Starts following what the widgets show; call once per process. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun start() {
            // The first redraw of a process waits for the start to settle: Glance composes the widget in a worker,
            // which starts WorkManager, and that would compete with the chat list's first page (docs/PERFORMANCE.md
            // §2). The system shows the widget's last content meanwhile.
            val started = scope.async { delay(START_UPDATE_DELAY) }
            scope.launch {
                if (placed.value == null) placed.compareAndSet(null, widgets.slots())
                placed
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { slots -> if (slots.isEmpty()) flowOf(emptyMap()) else contentsOf(slots) }
                    .collectLatest { shown ->
                        contents.value = shown
                        if (shown.isEmpty()) return@collectLatest
                        started.await()
                        widgets.updateAll()
                    }
            }
            scope.launch { accounts.collect { known -> moveWidgetsOfGone(known.keys) } }
        }

        /** A widget of the account in [slot] asked for its content. */
        fun onWidgetShown(slot: Int) {
            placed.update { (it ?: emptySet()) + slot }
        }

        /** Widgets were placed or removed; [present] false: the last one was. */
        fun onWidgetsChanged(present: Boolean) {
            if (!present) {
                placed.value = emptySet()
                return
            }
            scope.launch { placed.value = widgets.slots() }
        }

        /** Each placed account's content, as they change; an account not on the device (yet) signed out. */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun contentsOf(slots: Set<Int>): Flow<Map<Int, RecentChatsContent>> =
            accounts
                .map { known -> slots.associateWith { known[it] } }
                .distinctUntilChanged()
                .flatMapLatest { bySlot ->
                    combine(
                        bySlot.map { (slot, account) ->
                            (account?.let { contents(slot, it) } ?: flowOf(RecentChatsContent.SignedOut))
                                .map { slot to it }
                        },
                    ) { it.toMap() }
                }

        /** Widgets of accounts that left the device keep the shown account from now on. */
        private suspend fun moveWidgetsOfGone(known: Set<Int>) {
            if (known.isEmpty()) return
            val gone = placed.value.orEmpty() - known
            if (gone.isEmpty()) return
            val to = activeSlot.value.takeIf { it in known } ?: known.min()
            gone.forEach { widgets.move(from = it, to = to) }
            placed.value = widgets.slots()
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun contents(
            slot: Int,
            account: WidgetAccount,
        ): Flow<RecentChatsContent> =
            combine(
                account.auth.authStep,
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
                        State.Chats -> chats(slot, account)
                    }
                }

        /** The rows of the most recent chats with their avatars, as they change; loading the first chats if needed. */
        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private fun chats(
            slot: Int,
            account: WidgetAccount,
        ): Flow<RecentChatsContent> =
            channelFlow {
                val chatRepository = account.chats
                val loaded = MutableStateFlow(false)
                launch {
                    if (chatRepository.chatList(ChatListType.Main).first().size < MAX_CHATS) {
                        chatRepository.loadChats(ChatListType.Main, MAX_CHATS)
                    }
                    loaded.value = true
                }
                val chats =
                    combine(chatRepository.chatList(ChatListType.Main), account.chatLock.lockedChatIds) { all, locked ->
                        if (locked.isEmpty()) all else all.filterNot { it.id in locked }
                    }
                combine(chats, account.users.users, account.users.myUserId, loaded) { list, users, me, done ->
                    val rows = widgetChats(list, users, me, context.resources, MAX_CHATS, slot)
                    if (rows.isEmpty() && !done) null else Rows(rows, list.take(MAX_CHATS).map { it.photo })
                }.distinctUntilChanged()
                    .debounce(UPDATE_DELAY)
                    .mapLatest { rows -> rows?.withAvatars(account.avatars) ?: RecentChatsContent.Loading }
                    .collect { send(it) }
            }

        private suspend fun Rows.withAvatars(avatars: AvatarIcons): RecentChatsContent {
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

        private fun Map<Int, RecentChatsContent>.contentOf(slot: Int) = this[slot] ?: RecentChatsContent.Loading

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

/** What the widget reads of one account: its login, chats, users, locked chats, and avatars from its files. */
internal data class WidgetAccount(
    val auth: AuthRepository,
    val chats: ChatRepository,
    val users: UserRepository,
    val chatLock: ChatLockRepository,
    val avatars: AvatarIcons,
)

/** Every account on the device as the widget reads it, by slot; each account's avatars made once. */
private fun widgetAccounts(
    context: Context,
    graphs: AccountGraphs,
    appearance: AppearanceRepository,
    ioDispatcher: CoroutineDispatcher,
): Flow<Map<Int, WidgetAccount>> {
    val made = ConcurrentHashMap<AccountRepositories, WidgetAccount>()
    return graphs.accounts.map { list ->
        list.associate { graph ->
            val repositories = graph.repositories
            graph.slot to
                made.getOrPut(repositories) {
                    WidgetAccount(
                        auth = repositories.authRepository(),
                        chats = repositories.chatRepository(),
                        users = repositories.userRepository(),
                        chatLock = repositories.chatLockRepository(),
                        avatars = AvatarIcons(context, repositories.fileRepository(), appearance, ioDispatcher),
                    )
                }
        }
    }
}

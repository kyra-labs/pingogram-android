package dev.kyralabs.pingogram.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** The chat a Conversation widget keeps, and the account it is in (`docs/adr/0031-multiple-accounts.md`). */
data class ConversationKey(
    val slot: Int,
    val chatId: Long,
)

/** The placed Conversation widgets, behind a seam so the updater's tests need no launcher. */
interface ConversationWidgets {
    /** The chats the placed widgets keep; none for a widget that keeps no chat yet. */
    suspend fun keys(): Set<ConversationKey>

    /** Draws every placed Conversation widget again. */
    suspend fun updateAll()
}

/**
 * Keeps the Conversation widgets (board 33) current, as [RecentChatsWidgetUpdater] keeps the recent chats: without
 * polling, only while the app's process runs and a widget is placed ([ConversationWidgets.keys], [onWidgetShown],
 * [onWidgetsChanged]), each change becoming one redraw of the placed widgets [UPDATE_DELAY] after the chat last
 * changed. For each widget's chat it follows the login, the passcode lock and the locked chats (both keep the chat off
 * the home screen), the chat, and its history, whose newest page it loads (from TDLib's local database first; loading
 * reads nothing). A load that fails shows [ConversationContent.Failed] until [retry].
 *
 * `PingogramApplication` calls [start] in every process; it returns at once, all of it running on the application
 * scope.
 */
@Singleton
class ConversationWidgetUpdater
    internal constructor(
        private val context: Context,
        private val passcodeRepository: PasscodeRepository,
        private val widgets: ConversationWidgets,
        private val accounts: Flow<Map<Int, WidgetAccount>>,
        private val scope: CoroutineScope,
    ) {
        @Inject
        internal constructor(
            @ApplicationContext context: Context,
            passcodeRepository: PasscodeRepository,
            widgets: ConversationWidgets,
            accounts: WidgetAccounts,
            @ApplicationScope scope: CoroutineScope,
        ) : this(context, passcodeRepository, widgets, accounts.accounts, scope)

        /** The chats of the placed widgets; null until asked. */
        private val placed = MutableStateFlow<Set<ConversationKey>?>(null)

        private val contents = MutableStateFlow<Map<ConversationKey, ConversationContent>>(emptyMap())

        /** Counts the taps on "Tap to retry": each loads the failed histories again. */
        private val retries = MutableStateFlow(0)

        /** What the widgets that keep [key] show now. */
        fun content(key: ConversationKey): Flow<ConversationContent> =
            contents.map { it[key] ?: ConversationContent.Loading }.distinctUntilChanged()

        /** What the widgets that keep [key] show at this moment, for the first frame. */
        fun currentContent(key: ConversationKey): ConversationContent =
            contents.value[key] ?: ConversationContent.Loading

        /** Starts following what the widgets show; call once per process. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun start() {
            // As the recent chats' first redraw: Glance's worker would start WorkManager during the app's start.
            val started = scope.async { delay(RecentChatsWidgetUpdater.START_UPDATE_DELAY) }
            scope.launch {
                if (placed.value == null) placed.compareAndSet(null, widgets.keys())
                placed
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { keys -> if (keys.isEmpty()) flowOf(emptyMap()) else contentsOf(keys) }
                    .collectLatest { shown ->
                        contents.value = shown
                        if (shown.isEmpty()) return@collectLatest
                        started.await()
                        widgets.updateAll()
                    }
            }
        }

        /**
         * What a widget that keeps no chat yet shows (board 33): "Choose a chat" once an account on the device is
         * signed in and Pingogram is not locked with a passcode; asking to sign in first, or that it is locked,
         * otherwise.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val setUpContent: Flow<ConversationContent> =
            accounts
                .flatMapLatest { known ->
                    if (known.isEmpty()) {
                        flowOf(listOf(AuthStep.Initializing))
                    } else {
                        combine(known.values.map { it.auth.authStep }) { it.toList() }
                    }
                }.combine(passcodeRepository.settings.map { it.isSet }.distinctUntilChanged()) { steps, passcode ->
                    when {
                        AuthStep.Ready !in steps && AuthStep.Initializing in steps -> ConversationContent.Loading
                        AuthStep.Ready !in steps -> ConversationContent.SignedOut
                        passcode -> ConversationContent.Locked
                        else -> ConversationContent.NoChat
                    }
                }.distinctUntilChanged()

        /** A widget that keeps [key] asked for its content. */
        fun onWidgetShown(key: ConversationKey) {
            placed.update { (it ?: emptySet()) + key }
        }

        /** Widgets were placed, removed or given a chat: asks the launcher again which chats they keep. */
        fun onWidgetsChanged() {
            scope.launch { placed.value = widgets.keys() }
        }

        /** "Tap to retry": loads the histories that failed again. */
        fun retry() {
            retries.update { it + 1 }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun contentsOf(keys: Set<ConversationKey>): Flow<Map<ConversationKey, ConversationContent>> =
            accounts
                .map { known -> keys.associateWith { known[it.slot] } }
                .distinctUntilChanged()
                .flatMapLatest { byKey ->
                    combine(
                        byKey.map { (key, account) ->
                            (account?.let { contents(key, it) } ?: flowOf(ConversationContent.NoChat))
                                .map { key to it }
                        },
                    ) { it.toMap() }
                }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun contents(
            key: ConversationKey,
            account: WidgetAccount,
        ): Flow<ConversationContent> =
            combine(
                account.auth.authStep,
                passcodeRepository.settings.map { it.isSet }.distinctUntilChanged(),
                account.chatLock.lockedChatIds
                    .map { key.chatId in it }
                    .distinctUntilChanged(),
            ) { step, passcode, lockedChat ->
                when {
                    step == AuthStep.Initializing -> ConversationContent.Loading
                    step != AuthStep.Ready -> ConversationContent.SignedOut
                    passcode || lockedChat -> ConversationContent.Locked
                    else -> null
                }
            }.distinctUntilChanged()
                .flatMapLatest { state -> state?.let { flowOf(it) } ?: chat(key, account) }

        /** The chat's header and newest messages as they change, its history loaded first (again on [retry]). */
        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private fun chat(
            key: ConversationKey,
            account: WidgetAccount,
        ): Flow<ConversationContent> =
            channelFlow {
                val load = MutableStateFlow<Outcome<Unit>?>(null)
                launch {
                    retries.collectLatest {
                        load.value = null
                        // A process started for the widget may not know the chat yet: asking announces it.
                        val known = account.chats.getChat(key.chatId)
                        load.value = if (known is Outcome.Failure) known else account.messages.loadNewest(key.chatId)
                    }
                }
                combine(
                    combine(account.chats.chat(key.chatId), account.messages.history(key.chatId), load, ::Triple),
                    account.users.users,
                    account.users.myUserId,
                    account.groups.basicGroups,
                    account.groups.supergroups,
                ) { (chat, history, loaded), users, me, basicGroups, supergroups ->
                    val failure = (loaded as? Outcome.Failure)?.error
                    when {
                        failure == DomainError.NotFound -> {
                            Frame(ConversationContent.NoChat)
                        }

                        chat == null || history.isEmptyBecause(loaded) -> {
                            Frame(if (failure != null) ConversationContent.Failed else ConversationContent.Loading)
                        }

                        else -> {
                            val access =
                                chat.writeAccess(
                                    chat.userIn(users),
                                    (chat.type as? ChatType.BasicGroup)?.let { basicGroups[it.basicGroupId] },
                                    (chat.type as? ChatType.Supergroup)?.let { supergroups[it.supergroupId] },
                                )
                            val resources = context.resources
                            val title = widgetTitle(chat, users, me, resources)
                            Frame(
                                ConversationContent.Chat(
                                    chatId = key.chatId,
                                    slot = key.slot,
                                    title = title,
                                    unreadCount = chat.unreadCount,
                                    isMuted = chat.isMuted,
                                    messages = widgetMessages(chat, history.messages, users, me, resources),
                                    canReply = access == WriteAccess.Allowed,
                                    isSecret = chat.type is ChatType.Secret,
                                ),
                                chat.photo,
                                isForum =
                                    (chat.type as? ChatType.Supergroup)
                                        ?.let {
                                            supergroups[it.supergroupId]
                                        }?.isForum ==
                                        true,
                            )
                        }
                    }
                }.distinctUntilChanged()
                    .debounce(RecentChatsWidgetUpdater.UPDATE_DELAY)
                    .mapLatest { frame -> frame.withAvatar(account) }
                    .collect { send(it) }
            }

        /** The frame's content, a chat with its avatar drawn once the chat stayed as it is. */
        private suspend fun Frame.withAvatar(account: WidgetAccount): ConversationContent {
            val chat = content as? ConversationContent.Chat ?: return content
            val density = context.resources.displayMetrics.density
            val size = (ConversationTokens.AVATAR_SIZE_DP * density).roundToInt()
            // A forum's avatar is a rounded square, as the recent chats widget draws it (docs/DESIGN_SYSTEM.md §13.13).
            val corner = (RecentChatsWidgetUpdater.AVATAR_FORUM_CORNER_DP * density).takeIf { isForum }
            return chat.copy(avatar = account.avatars.bitmap(photo, chat.chatId, chat.title, size, corner))
        }

        /** What a widget shows, before its avatar is drawn, with the chat's [photo]: compared as a whole. */
        private data class Frame(
            val content: ConversationContent,
            val photo: ProfilePhoto? = null,
            val isForum: Boolean = false,
        )
    }

/** No messages yet, while the newest page is still on its way or failed: nothing to show but loading or the error. */
private fun ChatHistory.isEmptyBecause(loaded: Outcome<Unit>?): Boolean =
    messages.isEmpty() && !reachedStart && (loaded == null || loaded is Outcome.Failure)

private fun Chat.userIn(users: Map<Long, User>) =
    when (val type = type) {
        is ChatType.Private -> users[type.userId]
        is ChatType.Secret -> users[type.userId]
        else -> null
    }

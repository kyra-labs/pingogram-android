package dev.kyralabs.pingogram.widget

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.WidgetChatPickerRoute
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A chat the picker offers: its title (a string resource in its place for Saved Messages and a deleted account,
 * [titleRes]), photo, avatar shape and placeholder icon (Saved Messages' bookmark).
 */
@Immutable
data class WidgetPickerChat(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val shape: AvatarShape,
    val placeholderIcon: Int? = null,
    val titleRes: Int? = null,
)

/** The picker: the chats that match [query] (null while they load). */
@Immutable
data class WidgetChatPickerState(
    val chats: ImmutableList<WidgetPickerChat>? = null,
    val query: String = "",
)

/** Keeps a chat in a Conversation widget, behind a seam so the view model's tests need no launcher. */
fun interface WidgetChatChooser {
    /** Keeps chat [chatId] of the account in [slot] in widget [appWidgetId]; false when it is no such widget. */
    suspend fun choose(
        appWidgetId: Int,
        slot: Int,
        chatId: Long,
    ): Boolean
}

/** Keeps the chat in the widget's Glance state ([ConversationWidgetSetup.choose]). */
internal class GlanceWidgetChatChooser
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WidgetChatChooser {
        override suspend fun choose(
            appWidgetId: Int,
            slot: Int,
            chatId: Long,
        ): Boolean = ConversationWidgetSetup.choose(context, appWidgetId, slot, chatId)
    }

/**
 * What the picker reads of the shown account: its main chat list, users, who it is, its locked chats, and which
 * supergroups are forums (their avatars are rounded squares).
 */
internal class WidgetPickerSources(
    val chats: Flow<List<Chat>>,
    val users: Flow<Map<Long, User>>,
    val myUserId: Flow<Long?>,
    val lockedChatIds: Flow<Set<Long>>,
    val forums: Flow<Set<Long>>,
) {
    @Inject
    constructor(
        chatRepository: ChatRepository,
        userRepository: UserRepository,
        groupRepository: GroupRepository,
        chatLock: ChatLockRepository,
    ) : this(
        chatRepository.chatList(ChatListType.Main),
        userRepository.users,
        userRepository.myUserId,
        chatLock.lockedChatIds,
        groupRepository.supergroups.map { groups ->
            groups.values
                .filter { it.isForum }
                .map { it.id }
                .toSet()
        },
    )
}

/**
 * The Conversation widget's chat picker (board 33): the shown account's main chat list as TDLib has loaded it, without
 * secret chats (whose messages stay off the home screen) and locked chats (`docs/adr/0029-locked-chats.md`), filtered
 * on the device by every word of the query at the start of a word of the title, in any letter case and with or without
 * accents. Picking a chat keeps it in the widget ([WidgetChatChooser]) and closes the picker ([closed]). The list is
 * mapped and filtered on [dispatcher], off the main thread: it runs again for every typed letter and chat update.
 */
@HiltViewModel
class WidgetChatPickerViewModel
    internal constructor(
        savedStateHandle: SavedStateHandle,
        sources: WidgetPickerSources,
        private val activeSlot: StateFlow<Int>,
        private val chooser: WidgetChatChooser,
        dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        @Inject
        internal constructor(
            savedStateHandle: SavedStateHandle,
            sources: WidgetPickerSources,
            graphs: AccountGraphs,
            chooser: GlanceWidgetChatChooser,
            @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        ) : this(savedStateHandle, sources, graphs.activeSlot, chooser, dispatcher)

        private val appWidgetId = savedStateHandle.toRoute<WidgetChatPickerRoute>().appWidgetId

        private val query = MutableStateFlow("")

        private val closedChannel = Channel<Unit>(Channel.CONFLATED)

        /** One event once a chat is kept in the widget, or the widget turned out to be none. */
        val closed: Flow<Unit> = closedChannel.receiveAsFlow()

        val state: StateFlow<WidgetChatPickerState> =
            combine(
                combine(sources.chats, sources.lockedChatIds, sources.forums, ::Triple),
                sources.users,
                sources.myUserId,
                query,
            ) { (list, locked, forumIds), userMap, me, typed ->
                val words = wordsOf(typed)
                WidgetChatPickerState(
                    chats =
                        list
                            .filter { it.type !is ChatType.Secret && it.id !in locked }
                            .map { it.toPickerChat(userMap, me, forumIds) }
                            .filter { chat -> words.isEmpty() || words.all { word -> chat.matches(word) } }
                            .toImmutableList(),
                    query = typed,
                )
            }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WidgetChatPickerState())

        fun onQueryChange(text: String) {
            query.value = text
        }

        /** Keeps [chatId] in the widget, then closes; nothing more on a second tap while the first is kept. */
        fun onPick(chatId: Long) {
            viewModelScope.launch {
                chooser.choose(appWidgetId, activeSlot.value, chatId)
                closedChannel.send(Unit)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun Chat.toPickerChat(
    users: Map<Long, User>,
    myUserId: Long?,
    forums: Set<Long>,
): WidgetPickerChat {
    val type = type
    val userId = (type as? ChatType.Private)?.userId
    val isSavedMessages = userId != null && userId == myUserId
    val isDeleted = userId != null && users[userId]?.type == UserType.Deleted
    val supergroup = type as? ChatType.Supergroup
    return WidgetPickerChat(
        id = id,
        title = title,
        photo = if (isSavedMessages) null else photo,
        shape =
            AvatarShape.ofChat(
                isForum = supergroup != null && supergroup.supergroupId in forums,
                isGroup = type is ChatType.BasicGroup || (supergroup != null && !supergroup.isChannel),
                isChannel = supergroup?.isChannel == true,
                isSavedMessages = isSavedMessages,
            ),
        placeholderIcon =
            when {
                isSavedMessages -> UiR.drawable.ic_bookmark_filled
                isDeleted -> UiR.drawable.ic_person_filled
                else -> null
            },
        titleRes =
            when {
                isSavedMessages -> UiR.string.chat_saved_messages
                isDeleted -> UiR.string.user_deleted_account
                else -> null
            },
    )
}

/** The words of [text] as [matches] compares them: lower case, accents removed. */
private fun wordsOf(text: String): List<String> = fold(text).split(' ').filter { it.isNotBlank() }

private fun WidgetPickerChat.matches(word: String): Boolean = fold(title).split(' ').any { it.startsWith(word) }

private fun fold(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")

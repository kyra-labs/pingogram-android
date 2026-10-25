package dev.kyralabs.pingogram.core.notifications

import androidx.core.app.Person
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.WriteAccess
import dev.kyralabs.pingogram.core.domain.model.writeAccess
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * The Direct Share targets (`REQUIREMENTS.md` FR-N2): the [MAX_TARGETS] most recent chats of the main chat list, in
 * its order (pinned chats first), where the user can send messages, published as conversation shortcuts
 * ([ConversationShortcuts.publishShareTargets]), so the system's share sheet offers them, with their avatars
 * ([AvatarIcons]). Saved Messages is left out: it has no avatar of its own there, and the share sheet lists it first.
 * Locked chats are left out too, unlocked or not (`docs/adr/0029-locked-chats.md`).
 *
 * It follows the chat list while someone is logged in, publishing [PUBLISH_DELAY] after the first [MAX_TARGETS]
 * chats last changed, so a busy chat list rewrites the shortcuts rarely; logging out removes them with every other
 * conversation shortcut (`NotificationRenderer`). Android 11+ only, like the conversation shortcuts.
 *
 * `PingogramApplication` calls [start] in every process; it returns at once, all of it running on the application
 * scope.
 */
@Singleton
@Suppress("LongParameterList") // Injected: the repositories it combines, the parts that publish, the app's scope.
class ShareTargetShortcuts
    @Inject
    internal constructor(
        private val authRepository: AuthRepository,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val groupRepository: GroupRepository,
        private val chatLock: ChatLockRepository,
        private val avatars: AvatarIcons,
        private val shortcuts: ConversationShortcuts,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        /** Starts following the chat list; call once per process. */
        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        fun start() {
            scope.launch {
                authRepository.authStep
                    .map { it == AuthStep.Ready }
                    .distinctUntilChanged()
                    .flatMapLatest { ready -> if (ready) recentChats() else flowOf(emptyList()) }
                    .distinctUntilChanged()
                    .debounce(PUBLISH_DELAY)
                    .collectLatest { targets -> if (targets.isNotEmpty()) publish(targets) }
            }
        }

        /** The [MAX_TARGETS] most recent chats the user can write in, as they change. */
        private fun recentChats(): Flow<List<Target>> =
            combine(
                combine(chatRepository.chatList(ChatListType.Main), chatLock.lockedChatIds) { chats, locked ->
                    if (locked.isEmpty()) chats else chats.filterNot { it.id in locked }
                },
                userRepository.users,
                userRepository.myUserId,
                groupRepository.basicGroups,
                groupRepository.supergroups,
            ) { chats, users, me, basicGroups, supergroups ->
                targetsOf(chats, People(users, me, basicGroups, supergroups))
            }

        private suspend fun publish(targets: List<Target>) {
            val chats =
                targets.map { target ->
                    val icon = avatars.icon(target.photo, target.chatId, target.title)
                    val person =
                        target.partner?.let { partner ->
                            Person
                                .Builder()
                                .setKey(USER_KEY + partner.userId)
                                .setName(target.title)
                                .setIcon(icon)
                                .setBot(partner.isBot)
                                .build()
                        }
                    ChatShortcut(target.chatId, target.title, icon, person)
                }
            shortcuts.publishShareTargets(chats)
        }

        /** What a chat's shortcut needs, compared to publish only when it changed. */
        private data class Target(
            val chatId: Long,
            val title: String,
            val photo: ProfilePhoto?,
            val partner: Partner?,
        ) {
            override fun toString() = "Target(chatId=$chatId, …)"
        }

        /** The other user of a private chat. */
        private data class Partner(
            val userId: Long,
            val isBot: Boolean,
        )

        /** Who the chats' write access depends on. */
        private class People(
            val users: ImmutableMap<Long, User>,
            val myUserId: Long?,
            val basicGroups: ImmutableMap<Long, BasicGroup>,
            val supergroups: ImmutableMap<Long, Supergroup>,
        )

        private fun targetsOf(
            chats: List<Chat>,
            people: People,
        ): List<Target> =
            chats
                .asSequence()
                .filter { chat -> chat.title.isNotBlank() && !chat.isSavedMessages(people.myUserId) }
                .filter { chat -> chat.writeAccessOf(people) == WriteAccess.Allowed }
                .take(MAX_TARGETS)
                .map { chat ->
                    val partnerId = (chat.type as? ChatType.Private)?.userId
                    val partner =
                        partnerId?.let { Partner(it, isBot = people.users[it]?.type == UserType.Bot) }
                    Target(chat.id, chat.title, chat.photo, partner)
                }.toList()

        private fun Chat.writeAccessOf(people: People): WriteAccess {
            val type = type
            val user =
                when (type) {
                    is ChatType.Private -> people.users[type.userId]
                    is ChatType.Secret -> people.users[type.userId]
                    else -> null
                }
            return writeAccess(
                user = user,
                basicGroup = (type as? ChatType.BasicGroup)?.let { people.basicGroups[it.basicGroupId] },
                supergroup = (type as? ChatType.Supergroup)?.let { people.supergroups[it.supergroupId] },
            )
        }

        private fun Chat.isSavedMessages(myUserId: Long?): Boolean =
            myUserId != null && (type as? ChatType.Private)?.userId == myUserId

        internal companion object {
            /** How many chats the system's share sheet is offered: two rows of its targets. */
            const val MAX_TARGETS = 8

            /** How long the most recent chats must stay as they are before their shortcuts are published. */
            val PUBLISH_DELAY = 5.seconds

            private const val USER_KEY = "user:"
        }
    }

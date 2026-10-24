package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMemberRole
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.domain.model.ChatReactions
import dev.kyralabs.pingogram.core.domain.model.ChatSettings
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.repository.ChatSettingsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatSettingsRepository] on TDLib: the chat (`getChat`: reactions, protected content), a supergroup or channel
 * (`getSupergroup`: signatures, topics, the user's role; `getSupergroupFullInfo`: the linked chat) or a basic group
 * (`getBasicGroup`: the user's role), each followed by its updates while collected, which are subscribed to before the
 * requests go out. The emoji Telegram offers as reactions come once, with `updateActiveEmojiReactions` at TDLib's
 * start, so this is a [TdlibUpdateCache] for them.
 */
@Singleton
class TdlibChatSettingsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : ChatSettingsRepository,
        TdlibUpdateCache {
        private val reactionStore =
            UpdateStore<ImmutableList<String>>(client, scope, persistentListOf()) { state, update ->
                if (update is TdApi.UpdateActiveEmojiReactions) {
                    update.emojis
                        .orEmpty()
                        .toList()
                        .toImmutableList()
                } else {
                    state
                }
            }

        override val activeReactions: Flow<ImmutableList<String>> = reactionStore.state

        override fun settings(chatId: Long): Flow<Outcome<ChatSettings>> =
            channelFlow {
                val state = MutableStateFlow<SettingsState?>(null)
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.collect { update -> state.update { it?.reduce(chatId, update) } }
                }
                val chat =
                    when (val found = client.request(TdApi.GetChat(chatId))) {
                        is Outcome.Success -> found.value
                        is Outcome.Failure -> return@channelFlow send(found)
                    }
                val initial =
                    when (val loaded = load(chat)) {
                        is Outcome.Success -> loaded.value
                        is Outcome.Failure -> return@channelFlow send(loaded)
                    }
                state.update { it ?: initial }
                state.collectLatest { current -> current?.let { send(Outcome.Success(it.settings)) } }
            }

        private suspend fun load(chat: TdApi.Chat): Outcome<SettingsState> {
            val reactions = chat.availableReactions.toChatReactions()
            return when (val type = chat.type) {
                is TdApi.ChatTypeSupergroup -> {
                    val supergroup =
                        when (val found = client.request(TdApi.GetSupergroup(type.supergroupId))) {
                            is Outcome.Success -> found.value
                            is Outcome.Failure -> return found
                        }
                    // The linked chat only: the page works without it.
                    val full =
                        (
                            client.request(
                                TdApi.GetSupergroupFullInfo(type.supergroupId),
                            ) as? Outcome.Success
                        )?.value
                    Outcome.Success(
                        SettingsState(
                            supergroupId = type.supergroupId,
                            basicGroupId = 0,
                            settings =
                                ChatSettings(
                                    kind = if (supergroup.isChannel) GroupKind.Channel else GroupKind.Supergroup,
                                    myRole = supergroup.status.toMemberRole(),
                                    reactions = reactions,
                                    hasProtectedContent = chat.hasProtectedContent,
                                ).withSupergroup(supergroup).copy(linkedChatId = full?.linkedChatId ?: 0),
                        ),
                    )
                }

                is TdApi.ChatTypeBasicGroup -> {
                    client.request(TdApi.GetBasicGroup(type.basicGroupId)).map { group ->
                        SettingsState(
                            supergroupId = 0,
                            basicGroupId = type.basicGroupId,
                            settings =
                                ChatSettings(
                                    kind = GroupKind.BasicGroup,
                                    myRole = group.status.toMemberRole(),
                                    reactions = reactions,
                                    hasProtectedContent = chat.hasProtectedContent,
                                ),
                        )
                    }
                }

                else -> {
                    Outcome.Failure(DomainError.NotFound)
                }
            }
        }

        override suspend fun setReactions(
            chatId: Long,
            reactions: ChatReactions,
        ): Outcome<Unit> = client.request(TdApi.SetChatAvailableReactions(chatId, reactions.toTdlib())).map {}

        override suspend fun setProtectedContent(
            chatId: Long,
            protect: Boolean,
        ): Outcome<Unit> = client.request(TdApi.ToggleChatHasProtectedContent(chatId, protect)).map {}

        override suspend fun setSignMessages(
            chatId: Long,
            sign: Boolean,
            showSender: Boolean,
        ): Outcome<Unit> =
            withSupergroup(chatId) { client.request(TdApi.ToggleSupergroupSignMessages(it, sign, sign && showSender)) }

        override suspend fun setForum(
            chatId: Long,
            isForum: Boolean,
            hasTabs: Boolean,
        ): Outcome<Unit> =
            withSupergroup(chatId) { client.request(TdApi.ToggleSupergroupIsForum(it, isForum, isForum && hasTabs)) }

        override suspend fun discussionGroupCandidates(): Outcome<ImmutableList<Long>> =
            client.request(TdApi.GetSuitableDiscussionChats()).map {
                (it.chatIds ?: LongArray(0)).toList().toImmutableList()
            }

        override suspend fun setDiscussionGroup(
            channelChatId: Long,
            groupChatId: Long,
        ): Outcome<Unit> = client.request(TdApi.SetChatDiscussionGroup(channelChatId, groupChatId)).map {}

        override suspend fun upgradeToSupergroup(chatId: Long): Outcome<Long> =
            client.request(TdApi.UpgradeBasicGroupChatToSupergroupChat(chatId)).map { it.id }

        /** Runs [request] with chat [chatId]'s supergroup id; a chat of another kind is not found. */
        private suspend fun withSupergroup(
            chatId: Long,
            request: suspend (supergroupId: Long) -> Outcome<TdApi.Ok>,
        ): Outcome<Unit> =
            when (val found = client.request(TdApi.GetChat(chatId))) {
                is Outcome.Success -> {
                    val type = found.value.type as? TdApi.ChatTypeSupergroup
                    if (type == null) Outcome.Failure(DomainError.NotFound) else request(type.supergroupId).map {}
                }

                is Outcome.Failure -> {
                    found
                }
            }
    }

/** The settings of one chat while collected, with the ids its updates name. */
private data class SettingsState(
    val supergroupId: Long,
    val basicGroupId: Long,
    val settings: ChatSettings,
) {
    fun reduce(
        chatId: Long,
        update: TdApi.Update,
    ): SettingsState =
        when {
            update is TdApi.UpdateChatAvailableReactions && update.chatId == chatId -> {
                copy(settings = settings.copy(reactions = update.availableReactions.toChatReactions()))
            }

            update is TdApi.UpdateChatHasProtectedContent && update.chatId == chatId -> {
                copy(settings = settings.copy(hasProtectedContent = update.hasProtectedContent))
            }

            update is TdApi.UpdateSupergroup && supergroupId != 0L && update.supergroup.id == supergroupId -> {
                copy(settings = settings.withSupergroup(update.supergroup))
            }

            update is TdApi.UpdateSupergroupFullInfo && supergroupId != 0L && update.supergroupId == supergroupId -> {
                copy(settings = settings.copy(linkedChatId = update.supergroupFullInfo.linkedChatId))
            }

            update is TdApi.UpdateBasicGroup && basicGroupId != 0L && update.basicGroup.id == basicGroupId -> {
                copy(settings = settings.copy(myRole = update.basicGroup.status.toMemberRole()))
            }

            else -> {
                this
            }
        }
}

private fun ChatSettings.withSupergroup(supergroup: TdApi.Supergroup) =
    copy(
        myRole = supergroup.status.toMemberRole(),
        signMessages = supergroup.signMessages,
        showMessageSender = supergroup.showMessageSender,
        isForum = supergroup.isForum,
        hasForumTabs = supergroup.hasForumTabs,
    )

/** TDLib's reactions; none when it gives none. */
internal fun TdApi.ChatAvailableReactions?.toChatReactions(): ChatReactions =
    when (this) {
        is TdApi.ChatAvailableReactionsAll -> {
            ChatReactions.All(maxReactionCount)
        }

        is TdApi.ChatAvailableReactionsSome -> {
            ChatReactions.Some(
                reactions.orEmpty().mapNotNull { it?.toReactionType() }.toImmutableList(),
                maxReactionCount,
            )
        }

        else -> {
            ChatReactions.Some(persistentListOf(), 0)
        }
    }

private fun ChatReactions.toTdlib(): TdApi.ChatAvailableReactions =
    when (this) {
        is ChatReactions.All -> {
            TdApi.ChatAvailableReactionsAll(maxCount)
        }

        is ChatReactions.Some -> {
            // The paid reaction, which Pingogram does not offer, stays as the chat has it.
            val types = reactions.map { it.toTdReactionType() ?: TdApi.ReactionTypePaid() }
            TdApi.ChatAvailableReactionsSome(types.toTypedArray(), maxCount)
        }
    }

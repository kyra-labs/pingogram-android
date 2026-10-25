package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toActiveStories
import dev.kyralabs.pingogram.core.data.mapper.toReactionType
import dev.kyralabs.pingogram.core.data.mapper.toStory
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTdReactionType
import dev.kyralabs.pingogram.core.data.mapper.toTdStoryList
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [StoryRepository] on TDLib's stories. TDLib announces each chat's active stories only with `updateChatActiveStories`
 * (after `loadActiveStories`, and whenever they change: a new story, one seen, one expired), so every chat's active
 * stories are kept for the session in an [UpdateStore], which makes this a [TdlibUpdateCache]: as many entries as
 * chats with stories the user follows, a few ids each. The stories themselves are not cached: TDLib keeps them and
 * announces each change with `updateStory` and `updateStoryDeleted`, which [story] follows while collected.
 *
 * - **Lists**: TDLib sorts chats by `order`, then by chat id, both descending; an `order` of 0 or no list means the
 *   chat is in no list. Chats without stories left are kept (TDLib announces them so) but listed nowhere.
 * - **Loading**: [loadActiveStories] asks once per session and list ([StoryState.requested], reset with the state at
 *   logout); `loadActiveStories` answers 404 once every chat is loaded, which is no error here.
 * - **Viewing**: [openStory] and [closeStory] are always sent around a story on screen; TDLib then marks it viewed and
 *   announces the chat's new `max_read_story_id`. `activateStoryStealthMode` (Telegram Premium) is never sent.
 *
 * Nothing about a story (caption, content) is logged.
 */
@AccountScoped
class TdlibStoryRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : StoryRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, StoryState(), ::reduce)

        override fun activeStories(list: StoryList): Flow<ImmutableList<ActiveStories>> =
            store.state
                .map { it.chats }
                .distinctUntilChanged { old, new -> old === new }
                .map { chats -> chats.listed(list) }
                .distinctUntilChanged()

        override suspend fun loadActiveStories(list: StoryList): Outcome<Unit> {
            var first = false
            store.update { state ->
                first = list !in state.requested
                if (first) state.copy(requested = state.requested.adding(list)) else state
            }
            if (!first) return Outcome.Success(Unit)
            return when (val loaded = loadMoreActiveStories(list)) {
                is Outcome.Success -> {
                    Outcome.Success(Unit)
                }

                is Outcome.Failure -> {
                    // Asked again the next time, as nothing was loaded.
                    store.update { it.copy(requested = it.requested.removing(list)) }
                    loaded
                }
            }
        }

        override suspend fun loadMoreActiveStories(list: StoryList): Outcome<Boolean> =
            when (val answer = client.request(TdApi.LoadActiveStories(list.toTdStoryList()))) {
                is Outcome.Success -> Outcome.Success(true)

                // 404: every chat with active stories is loaded.
                is Outcome.Failure -> if (answer.error == DomainError.NotFound) Outcome.Success(false) else answer
            }

        override fun activeStoriesOf(chatId: Long): Flow<Outcome<ActiveStories>> =
            channelFlow {
                if (store.state.value.chats[chatId] == null) {
                    when (val answer = client.request(TdApi.GetChatActiveStories(chatId))) {
                        is Outcome.Success -> {
                            val stories = answer.value.toActiveStories()
                            // An update that came in the meantime is newer than the answer.
                            store.update { state ->
                                if (chatId in
                                    state.chats
                                ) {
                                    state
                                } else {
                                    state.copy(chats = state.chats.putting(chatId, stories))
                                }
                            }
                        }

                        is Outcome.Failure -> {
                            send(answer)
                        }
                    }
                }
                store.state
                    .mapNotNull { it.chats[chatId] }
                    .distinctUntilChanged()
                    .collect { send(Outcome.Success(it)) }
            }

        override fun story(
            chatId: Long,
            storyId: Int,
        ): Flow<Outcome<Story?>> =
            channelFlow {
                // Subscribed before the request goes out, so a change in between is not missed.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    client.updates
                        .filter { it.isAbout(chatId, storyId) }
                        .collect { update ->
                            when (update) {
                                is TdApi.UpdateStory -> send(Outcome.Success(update.story.toStory()))
                                is TdApi.UpdateStoryDeleted -> send(Outcome.Success(null))
                            }
                        }
                }
                send(client.request(TdApi.GetStory(chatId, storyId, false)).map { it.toStory() })
            }

        override suspend fun openStory(
            chatId: Long,
            storyId: Int,
        ): Outcome<Unit> = client.request(TdApi.OpenStory(chatId, storyId)).map {}

        override suspend fun closeStory(
            chatId: Long,
            storyId: Int,
        ): Outcome<Unit> = client.request(TdApi.CloseStory(chatId, storyId)).map {}

        override suspend fun availableReactions(): Outcome<ImmutableList<ReactionType>> =
            client.request(TdApi.GetStoryAvailableReactions(REACTION_ROW_SIZE)).map { available ->
                sequenceOf(available.topReactions, available.recentReactions, available.popularReactions)
                    .flatMap { it.orEmpty().asSequence() }
                    .filterNot { it.needsPremium }
                    .mapNotNull { it.type.toReactionType() as? ReactionType.Emoji }
                    .distinct()
                    .toImmutableList()
            }

        override suspend fun setReaction(
            chatId: Long,
            storyId: Int,
            reaction: ReactionType?,
        ): Outcome<Unit> =
            client.request(TdApi.SetStoryReaction(chatId, storyId, reaction?.toTdReactionType(), true)).map {}

        override suspend fun reply(
            chatId: Long,
            storyId: Int,
            text: FormattedText,
        ): Outcome<Unit> {
            val content = TdApi.InputMessageText(text.toTdFormattedText(), null, true)
            val replyTo = TdApi.InputMessageReplyToStory(chatId, storyId)
            return client.request(TdApi.SendMessage(chatId, null, replyTo, null, null, content)).map {}
        }

        private companion object {
            /** The quick reactions TDLib puts first: one row of the reactions picker. */
            const val REACTION_ROW_SIZE = 8
        }
    }

/**
 * What [TdlibStoryRepository] keeps: every chat's active stories as last announced, and the lists whose first chats
 * were asked for in this session.
 */
internal data class StoryState(
    val chats: PersistentMap<Long, ActiveStories> = persistentMapOf(),
    val requested: PersistentSet<StoryList> = persistentSetOf(),
)

/** The chats of [list] with stories, by `order` then chat id, both descending, as TDLib sorts them. */
private fun Map<Long, ActiveStories>.listed(list: StoryList): ImmutableList<ActiveStories> =
    values
        .filter { it.list == list && it.order != 0L && it.stories.isNotEmpty() }
        .sortedWith(compareByDescending<ActiveStories> { it.order }.thenByDescending { it.chatId })
        .toImmutableList()

/** The state with [update] applied; the same state for other updates and for active stories that are unchanged. */
private fun reduce(
    state: StoryState,
    update: TdApi.Update,
): StoryState {
    if (update !is TdApi.UpdateChatActiveStories) return state
    val stories = update.activeStories.toActiveStories()
    return if (state.chats[stories.chatId] == stories) {
        state
    } else {
        state.copy(chats = state.chats.putting(stories.chatId, stories))
    }
}

private fun TdApi.Update.isAbout(
    chatId: Long,
    storyId: Int,
): Boolean =
    when (this) {
        is TdApi.UpdateStory -> story.posterChatId == chatId && story.id == storyId
        is TdApi.UpdateStoryDeleted -> storyPosterChatId == chatId && this.storyId == storyId
        else -> false
    }

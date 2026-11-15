package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryViewersPage
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * [StoryRepository] for view model tests: the chats with active stories are [active] (listed in [StoryList.Main] by
 * their order, as TDLib sorts them), the stories [stories] by chat and id (a missing one is [onMissingStory]'s answer,
 * null for a deleted one), and every request is recorded in [calls] (`open:<chat>:<story>`, `close:…`,
 * `react:<chat>:<story>:<emoji or none>`, `reply:<chat>:<story>:<text>`, `load:<list>`, `loadMore:<list>`), answered
 * by the matching `on…` handler, which succeeds unless a test says otherwise.
 */
class FakeStoryRepository : StoryRepository {
    val active = MutableStateFlow<Map<Long, ActiveStories>>(emptyMap())
    val stories = MutableStateFlow<Map<Pair<Long, Int>, Outcome<Story?>>>(emptyMap())
    val calls = mutableListOf<String>()

    var onMissingStory: Outcome<Story?> = Outcome.Failure(DomainError.NotFound)
    var onLoad: suspend (StoryList) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onLoadMore: suspend (StoryList) -> Outcome<Boolean> = { Outcome.Success(false) }
    var onActiveStoriesOf: (Long) -> Outcome<ActiveStories>? = { null }
    var onReactions: suspend () -> Outcome<ImmutableList<ReactionType>> = {
        Outcome.Success(persistentListOf(ReactionType.Emoji("❤"), ReactionType.Emoji("👍")))
    }
    var onReact: suspend (ReactionType?) -> Outcome<Unit> = { Outcome.Success(Unit) }
    var onReply: suspend (String) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** Who saw the user's own story, by page: none by default. */
    var onViewers: suspend (query: String, onlyContacts: Boolean, reactionsFirst: Boolean, offset: String) ->
    Outcome<StoryViewersPage> = { _, _, _, _ -> Outcome.Success(StoryViewersPage(0, 0, persistentListOf(), null)) }

    /** Deleting a story: success by default. */
    var onDelete: suspend (chatId: Long, storyId: Int) -> Outcome<Unit> = { _, _ -> Outcome.Success(Unit) }

    /** Puts [activeStories] in, replacing the chat's earlier ones. */
    fun setActive(activeStories: ActiveStories) {
        active.value += activeStories.chatId to activeStories
    }

    fun setStory(story: Story) {
        stories.value += (story.posterChatId to story.id) to Outcome.Success(story)
    }

    /** Story [storyId] of chat [chatId] was deleted or expired, as TDLib announces it. */
    fun removeStory(
        chatId: Long,
        storyId: Int,
    ) {
        stories.value += (chatId to storyId) to Outcome.Success(null)
    }

    override fun activeStories(list: StoryList): Flow<ImmutableList<ActiveStories>> =
        active
            .map { chats ->
                chats.values
                    .filter { it.list == list && it.stories.isNotEmpty() }
                    .sortedWith(compareByDescending<ActiveStories> { it.order }.thenByDescending { it.chatId })
                    .toImmutableList()
            }.distinctUntilChanged()

    override suspend fun loadActiveStories(list: StoryList): Outcome<Unit> {
        calls += "load:$list"
        return onLoad(list)
    }

    override suspend fun loadMoreActiveStories(list: StoryList): Outcome<Boolean> {
        calls += "loadMore:$list"
        return onLoadMore(list)
    }

    override fun activeStoriesOf(chatId: Long): Flow<Outcome<ActiveStories>> =
        active
            .map { it[chatId]?.let { stories -> Outcome.Success(stories) } ?: onActiveStoriesOf(chatId) }
            .mapNotNull { it }
            .distinctUntilChanged()

    override fun story(
        chatId: Long,
        storyId: Int,
    ): Flow<Outcome<Story?>> = stories.map { it[chatId to storyId] ?: onMissingStory }.distinctUntilChanged()

    override suspend fun openStory(
        chatId: Long,
        storyId: Int,
    ): Outcome<Unit> {
        calls += "open:$chatId:$storyId"
        return Outcome.Success(Unit)
    }

    override suspend fun closeStory(
        chatId: Long,
        storyId: Int,
    ): Outcome<Unit> {
        calls += "close:$chatId:$storyId"
        return Outcome.Success(Unit)
    }

    override suspend fun availableReactions(): Outcome<ImmutableList<ReactionType>> = onReactions()

    override suspend fun setReaction(
        chatId: Long,
        storyId: Int,
        reaction: ReactionType?,
    ): Outcome<Unit> {
        calls += "react:$chatId:$storyId:${(reaction as? ReactionType.Emoji)?.emoji ?: "none"}"
        return onReact(reaction)
    }

    override suspend fun reply(
        chatId: Long,
        storyId: Int,
        text: FormattedText,
    ): Outcome<Unit> {
        calls += "reply:$chatId:$storyId:${text.text}"
        return onReply(text.text)
    }

    override suspend fun deleteStory(
        chatId: Long,
        storyId: Int,
    ): Outcome<Unit> {
        calls += "delete:$chatId:$storyId"
        return onDelete(chatId, storyId)
    }

    override suspend fun viewers(
        storyId: Int,
        query: String,
        onlyContacts: Boolean,
        reactionsFirst: Boolean,
        offset: String,
    ): Outcome<StoryViewersPage> {
        calls += "viewers:$storyId:$query:$onlyContacts:$reactionsFirst:$offset"
        return onViewers(query, onlyContacts, reactionsFirst, offset)
    }
}

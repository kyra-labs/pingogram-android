package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Stories, read only (`docs/ROADMAP.md` M6): the chats with active stories, which TDLib announces chat by chat with
 * `updateChatActiveStories` and which this repository keeps for the session, and what the viewer does with a story.
 * Nothing is ever posted, and no story is watched unseen: [openStory] marks it viewed, as Telegram's terms ask
 * (https://core.telegram.org/api/terms), and there is no stealth mode (a Telegram Premium feature).
 *
 * Requests that get no answer in time fail with `DomainError.Network`; a story or chat TDLib does not know fails with
 * `DomainError.NotFound`, a flood wait with `DomainError.FloodWait`.
 */
interface StoryRepository {
    /**
     * The chats with active stories in [list], in TDLib's order (by `order`, then chat id, both descending: the user's
     * own first, then unseen ones, as Telegram sorts them), then the list after every change. Holds only the chats
     * TDLib announced: [loadActiveStories] asks for them.
     */
    fun activeStories(list: StoryList): Flow<ImmutableList<ActiveStories>>

    /**
     * Asks TDLib for the first chats with active stories in [list], once per session: later calls succeed at once
     * without asking again, as TDLib keeps announcing changes by itself. They reach [activeStories].
     */
    suspend fun loadActiveStories(list: StoryList): Outcome<Unit>

    /**
     * Asks TDLib for more chats with active stories in [list] (td_api.tl `loadActiveStories`); they reach
     * [activeStories]. True if more may follow, false once all are loaded.
     */
    suspend fun loadMoreActiveStories(list: StoryList): Outcome<Boolean>

    /**
     * The active stories of chat [chatId] as announced, else as `getChatActiveStories` answers, then every change while
     * collected. A chat without active stories has an empty list. A failed request is the flow's first value; changes
     * still follow it.
     */
    fun activeStoriesOf(chatId: Long): Flow<Outcome<ActiveStories>>

    /**
     * Story [storyId] of chat [chatId] (`getStory`, which TDLib answers from memory when it can), then the story again
     * after every `updateStory`, and null once it was deleted or expired (`updateStoryDeleted`), while collected. A
     * failed request is the flow's first value; changes still follow it.
     */
    fun story(
        chatId: Long,
        storyId: Int,
    ): Flow<Outcome<Story?>>

    /**
     * Tells TDLib that story [storyId] of chat [chatId] is on screen, until [closeStory]: TDLib marks it viewed (the
     * poster sees the view, and the chat's `maxReadStoryId` follows). Sent every time a story shows; never skipped.
     */
    suspend fun openStory(
        chatId: Long,
        storyId: Int,
    ): Outcome<Unit>

    /** Tells TDLib that story [storyId] of chat [chatId] left the screen; the counterpart of [openStory]. */
    suspend fun closeStory(
        chatId: Long,
        storyId: Int,
    ): Outcome<Unit>

    /**
     * The reactions the user can put on a story, the quick ones first: only emoji (Pingogram draws no custom emoji in
     * the viewer), without those that need Telegram Premium the user does not have, and without the paid one.
     */
    suspend fun availableReactions(): Outcome<ImmutableList<ReactionType>>

    /** Puts [reaction] on story [storyId] of chat [chatId], replacing the user's earlier one, or removes it (null). */
    suspend fun setReaction(
        chatId: Long,
        storyId: Int,
        reaction: ReactionType?,
    ): Outcome<Unit>

    /**
     * Sends [text] to chat [chatId], the private chat of the user who posted story [storyId], as a reply to the story
     * (td_api.tl `inputMessageReplyToStory`): only the poster sees it. Only where `Story.canBeReplied`; channel stories
     * cannot be replied to.
     */
    suspend fun reply(
        chatId: Long,
        storyId: Int,
        text: FormattedText,
    ): Outcome<Unit>
}

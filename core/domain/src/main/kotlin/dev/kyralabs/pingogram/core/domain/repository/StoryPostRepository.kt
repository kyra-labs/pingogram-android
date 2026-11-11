package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * Posting the user's own stories (`docs/adr/0036-posting-stories.md`): whether they may post ([ability]), how many
 * close friends a story for them reaches ([closeFriendCount]), posting photos and videos ([post]), and the stories
 * TDLib is still posting ([uploads]), which [cancel] stops.
 *
 * Every request fails with `DomainError.Network` when TDLib gives no answer in time, `DomainError.FloodWait`, or
 * another `DomainError` when TDLib refuses it.
 */
interface StoryPostRepository {
    /** Whether the user may post a story now, and why not. */
    suspend fun ability(): Outcome<StoryPostAbility>

    /** How many users are on the user's close friends list. */
    suspend fun closeFriendCount(): Outcome<Int>

    /**
     * Posts [items], photos and videos prepared by `AttachmentRepository`, as stories of the user's, one after another
     * in their order, seen by [privacy] for 24 hours. [caption] goes with the first. A video longer than a story takes
     * ([MAX_VIDEO_SECONDS]) is cut on the device into parts that each become a story, in order. Succeeds once TDLib
     * took every story, which it then uploads; the copies then belong to the repository, which deletes them once
     * posted. Fails with `DomainError.StoryVideoNotCut` for a video that cannot be cut, before anything is posted.
     * When it fails, the copies of the items TDLib did not take stay the caller's.
     */
    suspend fun post(
        items: List<Attachment>,
        caption: FormattedText,
        privacy: StoryPrivacy,
    ): Outcome<Unit>

    /**
     * The stories posted through [post] that TDLib is still uploading, or gave up on, in the order they were posted;
     * for this session only.
     */
    val uploads: StateFlow<ImmutableList<StoryUpload>>

    /**
     * Stops posting the stories [storyIds] of [uploads] (TDLib deletes a story it has not posted yet), or removes those
     * that failed. Their copies are deleted.
     */
    suspend fun cancel(storyIds: List<Int>): Outcome<Unit>

    companion object {
        /** The longest video a story takes (Telegram's limit). */
        const val MAX_VIDEO_SECONDS = 60
    }
}

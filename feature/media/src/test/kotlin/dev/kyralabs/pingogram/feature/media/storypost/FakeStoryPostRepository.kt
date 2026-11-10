package dev.kyralabs.pingogram.feature.media.storypost

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

/** Posting stories: answers with what tests set, and records every post. */
internal class FakeStoryPostRepository : StoryPostRepository {
    var ability: Outcome<StoryPostAbility> = Outcome.Success(StoryPostAbility.Allowed(3))
    var closeFriends: Outcome<Int> = Outcome.Success(14)
    var onPost: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
    val posts = mutableListOf<Post>()

    override val uploads = MutableStateFlow<ImmutableList<StoryUpload>>(persistentListOf())

    override suspend fun cancel(storyIds: List<Int>): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun ability(): Outcome<StoryPostAbility> = ability

    override suspend fun closeFriendCount(): Outcome<Int> = closeFriends

    override suspend fun post(
        items: List<Attachment>,
        caption: FormattedText,
        privacy: StoryPrivacy,
    ): Outcome<Unit> {
        posts += Post(items, caption, privacy)
        return onPost()
    }

    data class Post(
        val items: List<Attachment>,
        val caption: FormattedText,
        val privacy: StoryPrivacy,
    )
}

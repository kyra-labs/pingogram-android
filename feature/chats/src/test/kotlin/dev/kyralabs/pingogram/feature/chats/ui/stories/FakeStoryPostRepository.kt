package dev.kyralabs.pingogram.feature.chats.ui.stories

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** The stories being posted as tests set them in [uploads]; [cancel] records what it stopped and answers [onCancel]. */
internal class FakeStoryPostRepository : StoryPostRepository {
    override val uploads = MutableStateFlow<ImmutableList<StoryUpload>>(persistentListOf())
    var onCancel: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) }
    val cancelled = mutableListOf<List<Int>>()

    fun setUploads(vararg upload: StoryUpload) {
        uploads.value = upload.toList().toImmutableList()
    }

    override suspend fun cancel(storyIds: List<Int>): Outcome<Unit> {
        cancelled += storyIds
        return onCancel().also { if (it is Outcome.Success) uploads.value = persistentListOf() }
    }

    override suspend fun ability(): Outcome<StoryPostAbility> = Outcome.Failure(DomainError.NotFound)

    override suspend fun closeFriendCount(): Outcome<Int> = Outcome.Failure(DomainError.NotFound)

    override suspend fun post(
        items: List<Attachment>,
        caption: FormattedText,
        privacy: StoryPrivacy,
    ): Outcome<Unit> = Outcome.Failure(DomainError.NotFound)
}

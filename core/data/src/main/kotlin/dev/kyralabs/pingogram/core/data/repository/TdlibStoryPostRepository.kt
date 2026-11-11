package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.error.toDomainError
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.story.MediaStoryVideoCutter
import dev.kyralabs.pingogram.core.data.story.StoryPostings
import dev.kyralabs.pingogram.core.data.story.StoryVideoCutter
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository.Companion.MAX_VIDEO_SECONDS
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [StoryPostRepository] on TDLib's `postStory` (`docs/adr/0036-posting-stories.md`). Stories go out as the user's own,
 * from their Saved Messages chat as td_api.tl asks, for 24 hours (other periods need Telegram Premium), on the profile,
 * without areas, albums or protection.
 *
 * - **Videos** longer than [MAX_VIDEO_SECONDS] are cut first by [cutter], all of them before anything is posted, so
 *   one that cannot be cut posts nothing; a cut video's copy is deleted once its parts were all posted.
 * - **Copies** of what TDLib took are deleted once it posted them (`updateStoryPostSucceeded`) or the user cancelled
 *   them ([cancel]). A story that failed keeps its copy until removed; the upload cache's cleaning deletes it
 *   otherwise. When posting fails, the items' copies TDLib did not take stay the caller's, to post again or discard;
 *   the parts cut for them are deleted.
 * - **Uploads** (board 10c): each story TDLib took is followed until it is posted, its file's `updateFile` giving how
 *   much is sent and `updateStoryPostFailed` why it failed, in [uploads].
 *
 * Nothing about a story (caption, content) is logged.
 */
@AccountScoped
class TdlibStoryPostRepository internal constructor(
    private val client: TdlibClient,
    private val files: UploadFiles,
    private val cutter: StoryVideoCutter,
    private val io: CoroutineDispatcher,
    scope: CoroutineScope,
) : StoryPostRepository {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, files, MediaStoryVideoCutter(files), io, scope)

    /** The stories TDLib is posting. */
    private val posting = StoryPostings(scope)

    override val uploads: StateFlow<ImmutableList<StoryUpload>> = posting.uploads

    init {
        // Subscribed before this returns, so no update about a story posted through it is missed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateStoryPostSucceeded -> {
                        posted(update.oldStoryId)
                    }

                    is TdApi.UpdateStoryPostFailed -> {
                        val error = update.error
                        posting.fail(update.story.id, TdError(error.code, error.message).toDomainError())
                    }

                    is TdApi.UpdateFile -> {
                        val file = update.file
                        posting.sent(file.id, file.remote.uploadedSize, file.sizeOrExpected())
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    override suspend fun ability(): Outcome<StoryPostAbility> =
        selfChat().let { chat ->
            when (chat) {
                is Outcome.Failure -> chat
                is Outcome.Success -> client.request(TdApi.CanPostStory(chat.value)).map { it.toAbility() }
            }
        }

    override suspend fun closeFriendCount(): Outcome<Int> =
        client.request(TdApi.GetCloseFriends()).map { it.totalCount }

    override suspend fun post(
        items: List<Attachment>,
        caption: FormattedText,
        privacy: StoryPrivacy,
    ): Outcome<Unit> {
        val chat = selfChat()
        val contents = (chat as? Outcome.Success)?.let { withContext(io) { contents(items) } }
        val result =
            when {
                chat is Outcome.Failure -> chat
                contents == null -> Outcome.Failure(DomainError.StoryVideoNotCut)
                else -> postAll((chat as Outcome.Success).value, contents, caption, privacy.toTd())
            }
        // The videos that were cut gave way to their parts once these were all posted.
        if (result is Outcome.Success) delete(contents.orEmpty().mapNotNull { it.cutFrom }.distinct())
        return result
    }

    /**
     * Posts [contents] in order, [caption] with the first. A refusal stops the rest: the parts of cut videos not
     * posted are deleted, the items' own copies left to the caller.
     */
    private suspend fun postAll(
        chat: Long,
        contents: List<StoryFile>,
        caption: FormattedText,
        privacy: TdApi.StoryPrivacySettings,
    ): Outcome<Unit> {
        contents.forEachIndexed { index, file ->
            val text = if (index == 0) caption.toTdFormattedText() else TdApi.FormattedText("", emptyArray())
            val story = TdApi.PostStory(chat, file.content, null, text, null, privacy, IntArray(0), DAY, null, true, false)
            when (val posted = client.request(story)) {
                is Outcome.Success -> {
                    val story = posted.value
                    val upload = story.content.file()
                    posting.add(story.id, file.path, upload?.id ?: StoryPostings.NO_FILE, uploadOf(story.id, upload))
                }

                is Outcome.Failure -> {
                    delete(contents.drop(index).filter { it.cutFrom != null }.map { it.path })
                    return posted
                }
            }
        }
        return Outcome.Success(Unit)
    }

    /**
     * The items as TDLib's contents, videos too long for a story cut into parts; null when one cannot be cut, with
     * the parts made so far deleted.
     */
    private suspend fun contents(items: List<Attachment>): List<StoryFile>? {
        val result = mutableListOf<StoryFile>()
        for (item in items) {
            when {
                item is Attachment.Photo -> {
                    result +=
                        StoryFile(item.path, TdApi.InputStoryContentPhoto(TdApi.InputFileLocal(item.path), IntArray(0)))
                }

                item is Attachment.Video && item.duration <= MAX_VIDEO_SECONDS -> {
                    result += StoryFile(item.path, video(item.path, item.duration.toDouble()))
                }

                item is Attachment.Video -> {
                    val parts = cutter.cut(item.path, MAX_VIDEO_SECONDS)
                    if (parts == null) {
                        delete(result.filter { it.cutFrom != null }.map { it.path })
                        return null
                    }
                    parts.forEach { part ->
                        result +=
                            StoryFile(part.path, video(part.path, part.durationSeconds), item.path)
                    }
                }
            }
        }
        return result
    }

    override suspend fun cancel(storyIds: List<Int>): Outcome<Unit> {
        val chat = selfChat()
        if (chat !is Outcome.Success) return chat as Outcome.Failure
        var result: Outcome<Unit> = Outcome.Success(Unit)
        storyIds.forEach { id ->
            when (val deleted = client.request(TdApi.DeleteStory(chat.value, id))) {
                is Outcome.Success -> posted(id)
                is Outcome.Failure -> result = deleted
            }
        }
        return result
    }

    /** TDLib posted the story it first knew as [storyId], or it was cancelled: its copy is no longer needed. */
    private fun posted(storyId: Int) {
        val path = posting.remove(storyId) ?: return
        delete(listOf(path))
    }

    private fun delete(paths: List<String>) {
        if (paths.isNotEmpty()) files.deleteUploads(paths)
    }

    /** The user's Saved Messages chat, which posts the user's own stories. */
    private suspend fun selfChat(): Outcome<Long> =
        when (val me = client.request(TdApi.GetMe())) {
            is Outcome.Success -> client.request(TdApi.CreatePrivateChat(me.value.id, false)).map { it.id }
            is Outcome.Failure -> me
        }

    /** A copy to post at [path] and what TDLib posts of it; [cutFrom] is the video it was cut from, if any. */
    private class StoryFile(
        val path: String,
        val content: TdApi.InputStoryContent,
        val cutFrom: String? = null,
    )

    private companion object {
        /** How long the user's stories stay: a day, the period Telegram gives everyone. */
        const val DAY = 86_400
    }
}

private fun video(
    path: String,
    durationSeconds: Double,
): TdApi.InputStoryContent =
    TdApi.InputStoryContentVideo(TdApi.InputFileLocal(path), IntArray(0), durationSeconds, 0.0, false)

/** The file TDLib uploads for a story's content: a photo's largest size, or the video. */
private fun TdApi.StoryContent.file(): TdApi.File? =
    when (this) {
        is TdApi.StoryContentPhoto -> photo.sizes.maxByOrNull { it.width.toLong() * it.height }?.photo
        is TdApi.StoryContentVideo -> video.video
        else -> null
    }

/** How much of [file] is sent for story [storyId]; nothing known without a file. */
private fun uploadOf(
    storyId: Int,
    file: TdApi.File?,
): StoryUpload =
    StoryUpload(
        storyId = storyId,
        uploadedBytes = file?.remote?.uploadedSize ?: 0,
        sizeBytes = file?.sizeOrExpected() ?: 0,
    )

/** The file's size, or what TDLib expects it to be while it does not know it. */
private fun TdApi.File.sizeOrExpected(): Long = if (size > 0) size else expectedSize

private fun StoryPrivacy.toTd(): TdApi.StoryPrivacySettings =
    when (this) {
        StoryPrivacy.Everyone -> TdApi.StoryPrivacySettingsEveryone(LongArray(0))
        StoryPrivacy.Contacts -> TdApi.StoryPrivacySettingsContacts(LongArray(0))
        StoryPrivacy.CloseFriends -> TdApi.StoryPrivacySettingsCloseFriends()
        is StoryPrivacy.Selected -> TdApi.StoryPrivacySettingsSelectedUsers(userIds.toLongArray())
    }

private fun TdApi.CanPostStoryResult.toAbility(): StoryPostAbility =
    when (this) {
        is TdApi.CanPostStoryResultOk -> StoryPostAbility.Allowed(storyCount)
        is TdApi.CanPostStoryResultActiveStoryLimitExceeded -> StoryPostAbility.TooManyActive
        is TdApi.CanPostStoryResultWeeklyLimitExceeded -> StoryPostAbility.WeeklyLimit(retryAfter)
        is TdApi.CanPostStoryResultMonthlyLimitExceeded -> StoryPostAbility.MonthlyLimit(retryAfter)
        is TdApi.CanPostStoryResultPremiumNeeded -> StoryPostAbility.PremiumNeeded
        is TdApi.CanPostStoryResultBoostNeeded -> StoryPostAbility.BoostNeeded
        is TdApi.CanPostStoryResultLiveStoryIsActive -> StoryPostAbility.LiveStoryActive
        else -> StoryPostAbility.NotAllowed
    }

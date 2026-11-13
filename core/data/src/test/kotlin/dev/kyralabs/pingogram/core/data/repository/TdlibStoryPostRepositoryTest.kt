package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.story.StoryVideoCutter
import dev.kyralabs.pingogram.core.data.story.VideoPart
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.StoryUpload
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.StoryFixtures.story
import dev.kyralabs.pingogram.core.testing.StoryFixtures.videoContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Posting the user's stories: what `postStory` gets for photos, videos and privacy, the caption on the first, long
 * videos cut first, copies deleted once posted or never taken, whether the user may post, and the uploads followed
 * until posted, failed or cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibStoryPostRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val files by lazy { UploadFiles { cacheDir } }
    private var cuts: (String) -> List<VideoPart>? = { null }
    private val cutter = StoryVideoCutter { path, _ -> cuts(path) }

    private fun TestScope.repository() =
        TdlibStoryPostRepository(client, files, cutter, StandardTestDispatcher(testScheduler), backgroundScope)

    @BeforeEach
    fun setUp() {
        client.on(TdApi.GetMe::class, TdResult.Success(TdApiFixtures.user(id = SELF)))
        client.on(TdApi.CreatePrivateChat::class) { TdResult.Success(TdApiFixtures.chat(id = it.userId)) }
        var next = -1
        client.on(TdApi.PostStory::class) { TdResult.Success(story(id = next--, posterChatId = SELF)) }
    }

    /** A copy in a directory of its own, as the preparer leaves it. */
    private fun copy(name: String): String = File(files.newUploadDirectory(), name).apply { writeText("data") }.path

    private fun photo(name: String) = Attachment.Photo(copy(name), 4, 1080, 1920)

    private fun video(
        name: String,
        seconds: Int,
    ) = Attachment.Video(copy(name), 4, 1080, 1920, seconds, null)

    @Test
    fun `posts each item as the user's story for a day, the caption on the first, in order`() =
        runTest {
            val first = photo("a.jpg")
            val second = video("b.mp4", 14)

            val result =
                repository().post(
                    listOf(first, second),
                    FormattedText("Beach", persistentListOf()),
                    StoryPrivacy.Contacts,
                )

            assertEquals(Outcome.Success(Unit), result)
            val posts = client.sentOf<TdApi.PostStory>()
            assertEquals(listOf(SELF, SELF), posts.map { it.chatId })
            assertEquals(listOf("Beach", ""), posts.map { it.caption.text })
            assertEquals(listOf(86_400, 86_400), posts.map { it.activePeriod })
            assertTrue(posts.all { it.isPostedToChatPage && !it.protectContent && it.areas == null })
            assertEquals(
                first.path,
                ((posts[0].content as TdApi.InputStoryContentPhoto).photo as TdApi.InputFileLocal).path,
            )
            val video = posts[1].content as TdApi.InputStoryContentVideo
            assertEquals(second.path, (video.video as TdApi.InputFileLocal).path)
            assertEquals(14.0, video.duration)
            assertInstanceOf(TdApi.StoryPrivacySettingsContacts::class.java, posts[0].privacySettings)
        }

    @Test
    fun `maps every privacy choice`() =
        runTest {
            val repository = repository()
            listOf(
                StoryPrivacy.Everyone,
                StoryPrivacy.CloseFriends,
                StoryPrivacy.Selected(listOf(7, 8)),
            ).forEach { repository.post(listOf(photo("p.jpg")), FormattedText.Empty, it) }

            val settings = client.sentOf<TdApi.PostStory>().map { it.privacySettings }
            assertInstanceOf(TdApi.StoryPrivacySettingsEveryone::class.java, settings[0])
            assertInstanceOf(TdApi.StoryPrivacySettingsCloseFriends::class.java, settings[1])
            assertArrayEquals(longArrayOf(7, 8), (settings[2] as TdApi.StoryPrivacySettingsSelectedUsers).userIds)
        }

    @Test
    fun `cuts a long video into stories, deleting the whole copy once they are posted`() =
        runTest {
            val long = video("long.mp4", 150)
            val parts =
                listOf(
                    VideoPart(copy("p1.mp4"), 56.0),
                    VideoPart(copy("p2.mp4"), 56.0),
                    VideoPart(copy("p3.mp4"), 38.0),
                )
            cuts = { parts }

            repository().post(listOf(long), FormattedText.Empty, StoryPrivacy.Everyone)

            val videos = client.sentOf<TdApi.PostStory>().map { it.content as TdApi.InputStoryContentVideo }
            assertEquals(parts.map { it.path }, videos.map { (it.video as TdApi.InputFileLocal).path })
            assertEquals(listOf(56.0, 56.0, 38.0), videos.map { it.duration })
            assertFalse(File(long.path).exists())
        }

    @Test
    fun `a video that cannot be cut posts nothing, leaving the copies to the caller`() =
        runTest {
            val first = photo("a.jpg")
            val long = video("long.mp4", 150)

            val result = repository().post(listOf(first, long), FormattedText.Empty, StoryPrivacy.Everyone)

            assertEquals(Outcome.Failure(DomainError.StoryVideoNotCut), result)
            assertTrue(client.sentOf<TdApi.PostStory>().isEmpty())
            assertTrue(File(first.path).exists())
            assertTrue(File(long.path).exists())
        }

    @Test
    fun `deletes a copy once TDLib posted its story, and keeps it until then`() =
        runTest {
            val item = photo("a.jpg")
            repository().post(listOf(item), FormattedText.Empty, StoryPrivacy.Everyone)
            assertTrue(File(item.path).exists())

            client.emit(TdApi.UpdateStoryPostSucceeded(story(id = 5, posterChatId = SELF), -1))
            runCurrent()

            assertFalse(File(item.path).exists())
        }

    @Test
    fun `a refusal stops the rest, leaving their copies to the caller, and deletes the parts cut for them`() =
        runTest {
            val first = photo("a.jpg")
            val second = photo("b.jpg")
            val long = video("long.mp4", 150)
            val part = VideoPart(copy("p1.mp4"), 56.0)
            cuts = { listOf(part) }
            var calls = 0
            client.on(TdApi.PostStory::class) {
                if (calls++ ==
                    0
                ) {
                    TdResult.Success(story(id = -1))
                } else {
                    TdResult.Failure(TdError(400, "STORIES_TOO_MUCH"))
                }
            }

            val result = repository().post(listOf(first, second, long), FormattedText.Empty, StoryPrivacy.Everyone)

            assertInstanceOf(Outcome.Failure::class.java, result)
            assertTrue(File(first.path).exists())
            assertTrue(File(second.path).exists())
            assertTrue(File(long.path).exists())
            assertFalse(File(part.path).exists())
        }

    @Test
    fun `asks whether the user may post from their own chat and says why not`() =
        runTest {
            val repository = repository()
            val answers =
                listOf(
                    TdApi.CanPostStoryResultOk(3) to StoryPostAbility.Allowed(3),
                    TdApi.CanPostStoryResultWeeklyLimitExceeded(600) to StoryPostAbility.WeeklyLimit(600),
                    TdApi.CanPostStoryResultMonthlyLimitExceeded(900) to StoryPostAbility.MonthlyLimit(900),
                    TdApi.CanPostStoryResultActiveStoryLimitExceeded() to StoryPostAbility.TooManyActive,
                    TdApi.CanPostStoryResultPremiumNeeded() to StoryPostAbility.PremiumNeeded,
                    TdApi.CanPostStoryResultLiveStoryIsActive(4) to StoryPostAbility.LiveStoryActive,
                )
            answers.forEach { (answer, expected) ->
                client.on(TdApi.CanPostStory::class, TdResult.Success(answer))
                assertEquals(Outcome.Success(expected), repository.ability())
            }
            assertEquals(SELF, client.sentOf<TdApi.CanPostStory>().first().chatId)
        }

    @Test
    fun `counts the close friends`() =
        runTest {
            client.on(TdApi.GetCloseFriends::class, TdResult.Success(TdApi.Users(14, LongArray(14) { it.toLong() })))

            assertEquals(Outcome.Success(14), repository().closeFriendCount())
        }

    @Test
    fun `follows each story's upload until TDLib posted it`() =
        runTest {
            client.on(TdApi.PostStory::class) {
                TdResult.Success(story(id = -1, posterChatId = SELF, content = videoContent(file = uploading(0))))
            }
            val repository = repository()
            repository.post(listOf(video("b.mp4", 14)), FormattedText.Empty, StoryPrivacy.Everyone)
            runCurrent()
            assertEquals(listOf(StoryUpload(-1, 0, SIZE)), repository.uploads.value)

            client.emit(TdApi.UpdateFile(uploading(SIZE / 2)))
            runCurrent()
            assertEquals(listOf(StoryUpload(-1, SIZE / 2, SIZE)), repository.uploads.value)

            client.emit(TdApi.UpdateStoryPostSucceeded(story(id = 5, posterChatId = SELF), -1))
            runCurrent()
            assertTrue(repository.uploads.value.isEmpty())
        }

    @Test
    fun `a story TDLib gave up on says why, and cancelling deletes it and its copy`() =
        runTest {
            client.on(TdApi.DeleteStory::class, TdResult.Success(TdApi.Ok()))
            val item = photo("a.jpg")
            val repository = repository()
            repository.post(listOf(item), FormattedText.Empty, StoryPrivacy.Everyone)

            client.emit(
                TdApi.UpdateStoryPostFailed(
                    story(id = -1, posterChatId = SELF),
                    TdApi.Error(400, "STORY_SEND_FLOOD"),
                    TdApi.CanPostStoryResultOk(0),
                ),
            )
            runCurrent()
            assertTrue(
                repository.uploads.value
                    .single()
                    .failure != null,
            )

            assertEquals(Outcome.Success(Unit), repository.cancel(listOf(-1)))
            runCurrent()

            val deleted = client.sentOf<TdApi.DeleteStory>().single()
            assertEquals(SELF to -1, deleted.storyPosterChatId to deleted.storyId)
            assertTrue(repository.uploads.value.isEmpty())
            assertFalse(File(item.path).exists())
        }

    /** The story video's file, [uploaded] bytes of [SIZE] sent. */
    private fun uploading(uploaded: Long) =
        TdApi.File(
            FILE,
            SIZE,
            SIZE,
            TdApi.LocalFile("", false, false, false, true, 0, SIZE, SIZE),
            TdApi.RemoteFile("", "", true, false, uploaded),
        )

    private companion object {
        const val SELF = 99L
        const val FILE = 31
        const val SIZE = 2_000_000L
    }
}

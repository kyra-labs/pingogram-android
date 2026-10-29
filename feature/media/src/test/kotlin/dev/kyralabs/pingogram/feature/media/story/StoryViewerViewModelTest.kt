package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.core.testing.FakeStoryRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeFileRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.media.ui.FakeVideoPlayer
import dev.kyralabs.pingogram.feature.media.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * The stories viewer: where it opens, playing and moving on, taps, pausing, changing chats, the end, marking viewed,
 * reactions, replies, errors and process death, on fakes of the repositories and the player and the test clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewerViewModelTest {
    private val stories = FakeStoryRepository()
    private val chats = FakeChatRepository()
    private val files = FakeFileRepository()
    private val users = FakeUserRepository()
    private val player = FakeVideoPlayer()
    private val created = mutableListOf<StoryViewerViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        // Ada, a user: stories 10, 11 and 12, seen up to 10. A channel: stories 20 and 21, none seen.
        chats.chats.value =
            mapOf(
                ADA to MediaFixtures.chat(ADA, "Ada").copy(type = ChatType.Private(ADA)),
                CHANNEL to MediaFixtures.chat(CHANNEL, "News"),
                ME to MediaFixtures.chat(ME, "Me").copy(type = ChatType.Private(ME)),
            )
        stories.setActive(active(ADA, order = 9, maxRead = 10, ids = listOf(10, 11, 12)))
        stories.setActive(active(CHANNEL, order = 5, maxRead = 0, ids = listOf(20, 21)))
        listOf(10, 11, 12).forEach { stories.setStory(photoStory(it, ADA)) }
        listOf(20, 21).forEach { stories.setStory(photoStory(it, CHANNEL, canBeReplied = false)) }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * [runTest] that clears every view model the test made once [testBody] ends, which stops what still runs in their
     * scopes, such as the timeline's ticker.
     */
    private fun runViewModelTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            try {
                testBody()
            } finally {
                created.toList().forEach(::clear)
            }
        }

    /** Clears [viewModel] as leaving the screen does: through a [ViewModelStore], which closes what it holds. */
    private fun clear(viewModel: StoryViewerViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[StoryViewerViewModel::class]
        store.clear()
        created -= viewModel
    }

    private fun active(
        chatId: Long,
        order: Long,
        maxRead: Int,
        ids: List<Int>,
    ) = ActiveStories(chatId, StoryList.Main, order, maxRead, ids.map { StoryInfo(it, 0) }.toImmutableList())

    private fun photoStory(
        id: Int,
        chatId: Long,
        canBeReplied: Boolean = true,
        chosen: ReactionType? = null,
    ) = Story(
        id = id,
        posterChatId = chatId,
        date = NOW.epochSecond - THREE_HOURS,
        content = StoryContent.Photo(Photo(persistentListOf(PhotoSize("x", 1080, 1920, FileRef(fileOf(id), 1))), null)),
        caption = FormattedText("Story $id", persistentListOf()),
        canBeReplied = canBeReplied,
        chosenReaction = chosen,
    )

    private fun fileOf(storyId: Int) = FILE_BASE + storyId

    private fun downloaded(vararg storyIds: Int) =
        storyIds.forEach { files.setFile(fileOf(it), FileState.Downloaded("/files/$it.jpg", 1)) }

    /** A view model opened on [route], whose state is collected and whose screen is started, as by the screen. */
    private fun TestScope.viewModel(
        route: StoryViewerRoute = StoryViewerRoute(ADA),
        handle: SavedStateHandle = SavedStateHandle(),
        started: Boolean = true,
    ): StoryViewerViewModel {
        val viewModel =
            StoryViewerViewModel(
                route = route,
                savedStateHandle = handle,
                storyRepository = stories,
                chatRepository = chats,
                fileRepository = files,
                userRepository = users,
                clock = FakeWallClock(NOW),
                timeSource = testScheduler.timeSource,
                applicationScope = backgroundScope,
                videoPlayer = player,
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        if (started) viewModel.onEvent(StoryViewerEvent.Started(true))
        runCurrent()
        return viewModel
    }

    private val StoryViewerViewModel.content get() = uiState.value as StoryViewerUiState.Content

    private val StoryViewerViewModel.shownStory get() = content.posters[content.current].chatId to content.story.storyId

    @Test
    fun `opens at the route's chat on its first unseen story, among the stories list's chats`() =
        runViewModelTest {
            val viewModel = viewModel()

            val content = viewModel.content
            assertEquals(listOf(ADA, CHANNEL), content.posters.map { it.chatId })
            assertEquals(ADA to 11, viewModel.shownStory)
            assertEquals(1, content.story.index)
            assertEquals(3, content.story.count)
            assertEquals(THREE_HOURS, content.story.ageSeconds)
            assertEquals("Ada", content.posters.first().name)
        }

    @Test
    fun `a chat in no list opens alone, and at the route's story`() =
        runViewModelTest {
            stories.setActive(ActiveStories(7, null, 0, 0, persistentListOf(StoryInfo(70, 0), StoryInfo(71, 0))))
            stories.setStory(photoStory(71, 7))

            val viewModel = viewModel(StoryViewerRoute(7, storyId = 71))

            assertEquals(listOf(7L), viewModel.content.posters.map { it.chatId })
            assertEquals(7L to 71, viewModel.shownStory)
        }

    @Test
    fun `a chat whose stories are not known yet shows loading`() =
        runViewModelTest {
            // The stories list is empty, and TDLib has not answered for the chat's stories yet.
            stories.active.value = emptyMap()
            val viewModel = viewModel()

            assertEquals(StoryViewerUiState.Loading, viewModel.uiState.value)

            stories.setActive(active(ADA, order = 9, maxRead = 10, ids = listOf(10, 11, 12)))
            runCurrent()
            assertEquals(ADA to 11, viewModel.shownStory)
        }

    @Test
    fun `downloads the photo, then shows it for five seconds and moves on`() =
        runViewModelTest {
            val viewModel = viewModel()
            assertTrue("download:${fileOf(11)}:${DownloadPriority.Visible}" in files.calls)
            assertFalse((viewModel.content.story.media as StoryMedia.Photo).isReady)

            // Nothing runs while the photo is not on the device.
            advanceTimeBy(PHOTO_MILLIS * 2)
            assertEquals(ADA to 11, viewModel.shownStory)

            downloaded(11)
            runCurrent()
            assertTrue((viewModel.content.story.media as StoryMedia.Photo).isReady)
            advanceTimeBy(PHOTO_MILLIS / 2)
            assertEquals(0.5f, viewModel.content.story.progress(), PROGRESS_TOLERANCE)
            advanceTimeBy(PHOTO_MILLIS / 2 + TICK_MILLIS)
            runCurrent()

            assertEquals(ADA to 12, viewModel.shownStory)
        }

    @Test
    fun `downloads the next story's file ahead, at prefetch priority`() =
        runViewModelTest {
            downloaded(11)
            viewModel()

            assertTrue("download:${fileOf(12)}:${DownloadPriority.Prefetch}" in files.calls)
            assertFalse(files.calls.any { it.startsWith("download:${fileOf(20)}") })
        }

    @Test
    fun `taps move to the next and previous story, and back before the first starts it over`() =
        runViewModelTest {
            downloaded(10, 11, 12)
            val viewModel = viewModel()

            viewModel.onEvent(StoryViewerEvent.Next)
            runCurrent()
            assertEquals(ADA to 12, viewModel.shownStory)
            viewModel.onEvent(StoryViewerEvent.Previous)
            viewModel.onEvent(StoryViewerEvent.Previous)
            runCurrent()
            assertEquals(ADA to 10, viewModel.shownStory)

            advanceTimeBy(PHOTO_MILLIS / 2)
            viewModel.onEvent(StoryViewerEvent.Previous)
            runCurrent()
            assertEquals(ADA to 10, viewModel.shownStory)
            assertEquals(0f, viewModel.content.story.progress(), PROGRESS_TOLERANCE)
        }

    @Test
    fun `after a chat's last story the next chat's first unseen one shows, and the last chat's end closes`() =
        runViewModelTest {
            downloaded(11, 12, 20, 21)
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StoryViewerEvent.Next)
                viewModel.onEvent(StoryViewerEvent.Next)
                runCurrent()
                assertEquals(CHANNEL to 20, viewModel.shownStory)
                assertEquals(1, viewModel.content.current)

                viewModel.onEvent(StoryViewerEvent.Next)
                runCurrent()
                advanceTimeBy(PHOTO_MILLIS + TICK_MILLIS)
                runCurrent()
                assertEquals(StoryViewerEffect.Close, awaitItem())
            }
        }

    @Test
    fun `holding pauses the story, and letting go plays on from there`() =
        runViewModelTest {
            downloaded(11)
            val viewModel = viewModel()
            advanceTimeBy(PHOTO_MILLIS / 2)

            viewModel.onEvent(StoryViewerEvent.Hold(true))
            runCurrent()
            assertTrue(viewModel.content.isPaused)
            advanceTimeBy(PHOTO_MILLIS * 2)
            assertEquals(ADA to 11, viewModel.shownStory)

            viewModel.onEvent(StoryViewerEvent.Hold(false))
            runCurrent()
            assertFalse(viewModel.content.isPaused)
            advanceTimeBy(PHOTO_MILLIS / 2 + TICK_MILLIS)
            runCurrent()
            assertEquals(ADA to 12, viewModel.shownStory)
        }

    @Test
    fun `the reply field, the reactions and the pager pause the story too`() =
        runViewModelTest {
            downloaded(11)
            val viewModel = viewModel()

            viewModel.onEvent(StoryViewerEvent.ReplyFocused(true))
            viewModel.onEvent(StoryViewerEvent.ReactionsOpen(true))
            viewModel.onEvent(StoryViewerEvent.Paging(true))
            runCurrent()
            assertTrue(viewModel.content.isPaused)
            viewModel.onEvent(StoryViewerEvent.ReplyFocused(false))
            viewModel.onEvent(StoryViewerEvent.ReactionsOpen(false))
            runCurrent()
            assertTrue(viewModel.content.isPaused)
            viewModel.onEvent(StoryViewerEvent.Paging(false))
            runCurrent()
            assertFalse(viewModel.content.isPaused)
        }

    @Test
    fun `swiping to another chat shows its first unseen story, and swiping back where the user left`() =
        runViewModelTest {
            val viewModel = viewModel()
            viewModel.onEvent(StoryViewerEvent.Next)
            runCurrent()
            assertEquals(ADA to 12, viewModel.shownStory)

            viewModel.onEvent(StoryViewerEvent.PosterSettled(1))
            runCurrent()
            assertEquals(CHANNEL to 20, viewModel.shownStory)
            assertEquals(1, viewModel.content.current)
            viewModel.onEvent(StoryViewerEvent.PosterSettled(0))
            runCurrent()

            assertEquals(ADA to 12, viewModel.shownStory)
            assertEquals(
                2,
                viewModel.content.posters
                    .first()
                    .shownIndex,
            )
        }

    @Test
    fun `marks each story viewed once while it shows, and closes it when it leaves or the screen stops`() =
        runViewModelTest {
            downloaded(11, 12)
            val viewModel = viewModel()
            viewModel.onEvent(StoryViewerEvent.Hold(true))
            viewModel.onEvent(StoryViewerEvent.Hold(false))
            runCurrent()
            val viewing = { stories.calls.filter { it.startsWith("open") || it.startsWith("close") } }
            assertEquals(listOf("open:$ADA:11"), viewing())

            viewModel.onEvent(StoryViewerEvent.Next)
            runCurrent()
            viewModel.onEvent(StoryViewerEvent.Started(false))
            runCurrent()

            assertEquals(
                listOf("open:$ADA:11", "close:$ADA:11", "open:$ADA:12", "close:$ADA:12"),
                viewing(),
            )
        }

    @Test
    fun `nothing is opened before the screen is started, and nothing plays`() =
        runViewModelTest {
            downloaded(11)
            val viewModel = viewModel(started = false)
            advanceTimeBy(PHOTO_MILLIS * 2)

            assertTrue(stories.calls.none { it.startsWith("open") })
            assertEquals(ADA to 11, viewModel.shownStory)
        }

    @Test
    fun `clearing the view model closes the story it showed`() =
        runViewModelTest {
            val viewModel = viewModel()
            clear(viewModel)
            runCurrent()

            assertEquals("close:$ADA:11", stories.calls.last())
        }

    @Test
    fun `a video plays once its file is on the device, muted on request, and its end moves on`() =
        runViewModelTest {
            val video =
                StoryContent.Video(
                    durationMillis = 12_000,
                    width = 720,
                    height = 1280,
                    isAnimation = false,
                    minithumbnail = null,
                    thumbnail = null,
                    file = FileRef(fileOf(11), 1),
                )
            stories.setStory(photoStory(11, ADA).copy(content = video))
            val viewModel = viewModel()
            assertTrue(player.calls.none { it.startsWith("play") })

            files.setFile(fileOf(11), FileState.Downloaded("/files/11.mp4", 1))
            runCurrent()
            assertEquals("play:/files/11.mp4:${VideoKind.Video}", player.calls.last())
            assertTrue((viewModel.content.story.media as StoryMedia.Video).hasSound)
            player.renderFirstFrame()
            runCurrent()
            assertTrue((viewModel.content.story.media as StoryMedia.Video).showsFrame)

            viewModel.onEvent(StoryViewerEvent.ToggleMute)
            runCurrent()
            assertTrue(viewModel.content.isMuted)
            assertTrue("muted:true" in player.calls)

            // A volume key: the sound comes back, once.
            viewModel.onEvent(StoryViewerEvent.Unmute)
            viewModel.onEvent(StoryViewerEvent.Unmute)
            runCurrent()
            assertFalse(viewModel.content.isMuted)
            assertEquals(1, player.calls.count { it == "muted:false" })

            player.end()
            runCurrent()
            assertEquals(ADA to 12, viewModel.shownStory)
        }

    @Test
    fun `a video the player cannot play says so and moves on after a photo's time`() =
        runViewModelTest {
            val video = StoryContent.Video(12_000, 720, 1280, false, null, null, FileRef(fileOf(11), 1))
            stories.setStory(photoStory(11, ADA).copy(content = video))
            files.setFile(fileOf(11), FileState.Downloaded("/files/11.mp4", 1))
            val viewModel = viewModel()

            viewModel.effects.test {
                player.fail()
                assertEquals(StoryViewerEffect.PlaybackFailed, awaitItem())
                advanceTimeBy(PHOTO_MILLIS + TICK_MILLIS)
                runCurrent()
                assertEquals(ADA to 12, viewModel.shownStory)
            }
        }

    @Test
    fun `what the viewer cannot show says so and moves on`() =
        runViewModelTest {
            stories.setStory(photoStory(11, ADA).copy(content = StoryContent.Unsupported))
            stories.deleteStory(ADA, 12)
            val viewModel = viewModel()
            assertEquals(StoryMedia.Unsupported, viewModel.content.story.media)

            advanceTimeBy(PHOTO_MILLIS + TICK_MILLIS)
            runCurrent()
            assertEquals(ADA to 12, viewModel.shownStory)
            assertEquals(StoryMedia.Unavailable, viewModel.content.story.media)
        }

    @Test
    fun `a story that cannot be loaded shows the error, and Try again asks again`() =
        runViewModelTest {
            stories.stories.value -= ADA to 11
            stories.onMissingStory = Outcome.Failure(DomainError.Unauthorized)
            val viewModel = viewModel()
            assertEquals(StoryMedia.Failed(DomainError.Unauthorized), viewModel.content.story.media)

            stories.setStory(photoStory(11, ADA))
            viewModel.onEvent(StoryViewerEvent.Retry)
            runCurrent()

            assertInstanceOf(StoryMedia.Photo::class.java, viewModel.content.story.media)
        }

    @Test
    fun `replies only to a user's story that may be replied to, and never to the user's own`() =
        runViewModelTest {
            val viewModel = viewModel()
            assertEquals(StoryReplyUi(), viewModel.content.reply)

            viewModel.onEvent(StoryViewerEvent.PosterSettled(1))
            runCurrent()
            assertNull(viewModel.content.reply)

            users.myUserId.value = ME
            stories.setActive(active(ME, order = 20, maxRead = 0, ids = listOf(30)))
            stories.setStory(photoStory(30, ME))
            val own = viewModel(StoryViewerRoute(ME))
            assertNull(own.content.reply)
            assertNull(own.content.reactions)
        }

    @Test
    fun `sends a reply to the story, clears the field and says so`() =
        runViewModelTest {
            val viewModel = viewModel()
            viewModel.replyText.setTextAndPlaceCursorAtEnd("  Lovely!  ")

            viewModel.effects.test {
                viewModel.onEvent(StoryViewerEvent.SendReply)
                runCurrent()
                assertEquals(StoryViewerEffect.ReplySent, awaitItem())
            }
            assertEquals("reply:$ADA:11:Lovely!", stories.calls.last())
            assertEquals("", viewModel.replyText.text.toString())
        }

    @Test
    fun `a reply that fails keeps the text and shows the error`() =
        runViewModelTest {
            stories.onReply = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            viewModel.replyText.setTextAndPlaceCursorAtEnd("Lovely!")

            viewModel.effects.test {
                viewModel.onEvent(StoryViewerEvent.SendReply)
                runCurrent()
                assertEquals(StoryViewerEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals("Lovely!", viewModel.replyText.text.toString())
            assertFalse(viewModel.content.reply!!.isSending)
        }

    @Test
    fun `reacts with an emoji, shown at once, and a second tap takes it back`() =
        runViewModelTest {
            val viewModel = viewModel()
            val heart = ReactionType.Emoji("❤")
            assertEquals(listOf(heart, ReactionType.Emoji("👍")), viewModel.content.reactions?.available)

            viewModel.onEvent(StoryViewerEvent.ReactionsOpen(true))
            viewModel.onEvent(StoryViewerEvent.React(heart))
            runCurrent()
            assertEquals("react:$ADA:11:❤", stories.calls.last())
            assertEquals(false, viewModel.content.reactions?.isOpen)

            // TDLib announces the reaction with the story.
            stories.setStory(photoStory(11, ADA, chosen = heart))
            runCurrent()
            assertEquals(heart, viewModel.content.reactions?.chosen)
            viewModel.onEvent(StoryViewerEvent.React(heart))
            runCurrent()
            assertEquals("react:$ADA:11:none", stories.calls.last())
        }

    @Test
    fun `a refused reaction shows the error`() =
        runViewModelTest {
            stories.onReact = { Outcome.Failure(DomainError.FloodWait(30)) }
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(StoryViewerEvent.React(ReactionType.Emoji("❤")))
                runCurrent()
                assertEquals(StoryViewerEffect.ShowError(DomainError.FloodWait(30)), awaitItem())
            }
            assertNull(viewModel.content.reactions?.chosen)
        }

    @Test
    fun `after process death it opens on the chat and story it showed`() =
        runViewModelTest {
            val handle = SavedStateHandle()
            val first = viewModel(handle = handle)
            first.onEvent(StoryViewerEvent.PosterSettled(1))
            runCurrent()
            first.onEvent(StoryViewerEvent.Next)
            runCurrent()
            assertEquals(CHANNEL to 21, first.shownStory)

            // The list changed meanwhile; the restored viewer keeps its own chats.
            stories.setActive(active(ADA, order = 1, maxRead = 12, ids = listOf(10, 11, 12)))
            val restored = viewModel(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

            assertEquals(listOf(ADA, CHANNEL), restored.content.posters.map { it.chatId })
            assertEquals(CHANNEL to 21, restored.shownStory)
        }

    private companion object {
        const val ADA = 2L
        const val CHANNEL = 3L
        const val ME = 1L
        const val FILE_BASE = 100
        const val THREE_HOURS = 10_800L
        const val PHOTO_MILLIS = StoryContent.PHOTO_DURATION_MILLIS
        const val TICK_MILLIS = 40L
        const val PROGRESS_TOLERANCE = 0.02f
        val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")
    }
}

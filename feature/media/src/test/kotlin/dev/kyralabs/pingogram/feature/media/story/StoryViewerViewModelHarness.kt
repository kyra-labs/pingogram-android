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
import dev.kyralabs.pingogram.core.domain.repository.StoryAlbumRepository
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.core.testing.FakeStoryRepository
import dev.kyralabs.pingogram.feature.media.storyalbum.FakeStoryAlbumRepository
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

/** The fakes, the view model's making and clearing, and the sample chats and stories the viewer's tests share. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class StoryViewerViewModelHarness {
    protected val stories = FakeStoryRepository()
    protected val chats = FakeChatRepository()
    protected val files = FakeFileRepository()
    protected val users = FakeUserRepository()
    protected val player = FakeVideoPlayer()
    protected val created = mutableListOf<StoryViewerViewModel>()
    protected val albums = FakeStoryAlbumRepository()

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
    protected fun runViewModelTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            try {
                testBody()
            } finally {
                created.toList().forEach(::clear)
            }
        }

    /** Clears [viewModel] as leaving the screen does: through a [ViewModelStore], which closes what it holds. */
    protected fun clear(viewModel: StoryViewerViewModel) {
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

    protected fun active(
        chatId: Long,
        order: Long,
        maxRead: Int,
        ids: List<Int>,
    ) = ActiveStories(chatId, StoryList.Main, order, maxRead, ids.map { StoryInfo(it, 0) }.toImmutableList())

    protected fun photoStory(
        id: Int,
        chatId: Long,
        canBeReplied: Boolean = true,
        chosen: ReactionType? = null,
        canBeDeleted: Boolean = false,
        canBeForwarded: Boolean = false,
    ) = Story(
        id = id,
        posterChatId = chatId,
        date = NOW.epochSecond - THREE_HOURS,
        content = StoryContent.Photo(Photo(persistentListOf(PhotoSize("x", 1080, 1920, FileRef(fileOf(id), 1))), null)),
        caption = FormattedText("Story $id", persistentListOf()),
        canBeReplied = canBeReplied,
        chosenReaction = chosen,
        canBeDeleted = canBeDeleted,
        canBeForwarded = canBeForwarded,
    )

    protected fun fileOf(storyId: Int) = FILE_BASE + storyId

    protected fun downloaded(vararg storyIds: Int) =
        storyIds.forEach { files.setFile(fileOf(it), FileState.Downloaded("/files/$it.jpg", 1)) }

    /** A view model opened on [route], whose state is collected and whose screen is started, as by the screen. */
    protected fun TestScope.viewModel(
        route: StoryViewerRoute = StoryViewerRoute(ADA),
        handle: SavedStateHandle = SavedStateHandle(),
        started: Boolean = true,
        albums: StoryAlbumRepository? = null,
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
                albumRepository = albums,
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        if (started) viewModel.onEvent(StoryViewerEvent.Started(true))
        runCurrent()
        return viewModel
    }

    protected val StoryViewerViewModel.content get() = uiState.value as StoryViewerUiState.Content

    protected val StoryViewerViewModel.shownStory get() =
        content.posters[content.current].chatId to
            content.story.storyId

    protected companion object {
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

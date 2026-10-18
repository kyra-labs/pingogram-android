package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.NOW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * The fakes and helpers the tests of [MediaViewerViewModel] share: the view model on fakes of the domain repositories,
 * of the player and of the gallery, a clock stopped at [NOW] in UTC and fixed English time patterns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MediaViewerViewModelHarness {
    protected val media = FakeChatMediaRepository()
    protected val files = FakeFileRepository()
    protected val users = FakeUserRepository()
    protected val chats = FakeChatRepository()
    internal val player = FakeVideoPlayer()
    protected val fileActions = FakeMediaFileActions()

    /** The view models the test made, cleared when it ends. */
    private val created = mutableListOf<MediaViewerViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model opened on message [messageId], whose state is collected, as by the screen. */
    protected fun TestScope.viewModel(
        messageId: Long,
        handle: SavedStateHandle = SavedStateHandle(),
    ): MediaViewerViewModel {
        val viewModel =
            MediaViewerViewModel(
                route = MediaViewerRoute(CHAT, messageId),
                savedStateHandle = handle,
                mediaRepository = media,
                fileRepository = files,
                userRepository = users,
                chatRepository = chats,
                clock = FakeWallClock(NOW),
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                applicationScope = backgroundScope,
                locale = { Locale.US },
                videoPlayer = player,
                fileActions = fileActions,
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /**
     * [runTest] that clears every view model the test made once [testBody] ends, which stops what still runs in their
     * scopes, such as the progress ticker of a playing video.
     */
    protected fun runViewModelTest(testBody: suspend TestScope.() -> Unit) =
        runTest {
            try {
                testBody()
            } finally {
                created.toList().forEach(::clear)
            }
        }

    protected fun TestScope.send(
        viewModel: MediaViewerViewModel,
        vararg events: MediaViewerEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** Clears [viewModel] as leaving the screen does: through a [ViewModelStore], which closes what it holds. */
    protected fun clear(viewModel: MediaViewerViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[MediaViewerViewModel::class]
        store.clear()
        created -= viewModel
    }

    protected val MediaViewerViewModel.state get() = uiState.value

    protected val MediaViewerViewModel.content get() = state as MediaViewerUiState.Content

    protected val MediaViewerViewModel.ids get() = content.items.map { it.messageId }

    protected val MediaViewerViewModel.current get() = content.current.messageId

    /** The current item's "N of total", null while unknown. */
    protected val MediaViewerViewModel.position get() = content.count?.positionOf(content.currentIndex)
}

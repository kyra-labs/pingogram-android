package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.KeywordHit
import dev.kyralabs.pingogram.core.testing.FakeKeywordHitRepository
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The conversation's keyword match bar ([KeywordMatchViewModel], board 30). */
@OptIn(ExperimentalCoroutinesApi::class)
class KeywordMatchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hits =
        FakeKeywordHitRepository(
            listOf(
                KeywordHit(CHAT, 10, "launch", date = 100),
                KeywordHit(CHAT, 30, "launch", date = 300),
                KeywordHit(CHAT, 20, "Pingogram", date = 200),
                KeywordHit(OTHER_CHAT, 40, "launch", date = 400),
            ),
        )
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(openedAt: Long? = 30): KeywordMatchViewModel {
        val viewModel = KeywordMatchViewModel(CHAT, openedAt, savedState, hits)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.bar.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.shownBy(viewModel: KeywordMatchViewModel): List<Long> =
        mutableListOf<Long>().also { list ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.showMessage.collect(list::add) }
        }

    @Test
    fun `an alert's chat shows its match among the chat's, the newest first`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            assertEquals(KeywordMatchBar("launch", messageId = 30, position = 1, count = 3), viewModel.bar.value)
        }

    @Test
    fun `the arrows step through the matches and show each message, no further than the ends`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val shown = shownBy(viewModel)

            viewModel.onEvent(KeywordMatchEvent.Newer)
            viewModel.onEvent(KeywordMatchEvent.Older)
            runCurrent()
            assertEquals(KeywordMatchBar("Pingogram", messageId = 20, position = 2, count = 3), viewModel.bar.value)
            viewModel.onEvent(KeywordMatchEvent.Older)
            viewModel.onEvent(KeywordMatchEvent.Older)
            runCurrent()

            assertEquals(listOf(20L, 10L), shown)
            assertEquals(3, viewModel.bar.value?.position)
        }

    @Test
    fun `a chat opened without an alert's message has no bar`() =
        runTest(dispatcher) {
            assertNull(viewModel(openedAt = null).bar.value)
            assertNull(viewModel(openedAt = 99).bar.value)
        }

    @Test
    fun `closing hides the bar, also after process death`() =
        runTest(dispatcher) {
            viewModel().onEvent(KeywordMatchEvent.Close)
            runCurrent()

            assertNull(viewModel().bar.value)
        }

    @Test
    fun `the bar leaves with the chat's matches, once read`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            hits.clearChat(CHAT)
            runCurrent()

            assertNull(viewModel.bar.value)
        }

    private companion object {
        const val CHAT = 1L
        const val OTHER_CHAT = 2L
    }
}

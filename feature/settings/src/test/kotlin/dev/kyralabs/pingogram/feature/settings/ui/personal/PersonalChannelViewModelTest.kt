package dev.kyralabs.pingogram.feature.settings.ui.personal

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPersonalChannelRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Personal channel page through the real TDLib repositories on [FakeTdlibClient], as the app wires them. */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalChannelViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(
        shown: Long = NOTES,
        failCandidates: Boolean = false,
    ): PersonalChannelViewModel {
        val profile = TdlibProfileRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApi.UpdateNewChat(TdApiFixtures.chat(id = NOTES, title = "Ada's notes")))
        client.emit(TdApi.UpdateNewChat(TdApiFixtures.chat(id = NEWS, title = "Engine news")))
        runCurrent()
        client.on(TdApi.GetMe::class, TdResult.Success(TdApiFixtures.user(id = 42)))
        client.on(
            TdApi.GetUserFullInfo::class,
            TdResult.Success(ProfileFixtures.userFullInfo(bio = null).also { it.personalChatId = shown }),
        )
        if (failCandidates) {
            client.on(TdApi.GetSuitablePersonalChats::class, TdResult.Failure(TdError(500, "Internal")))
        } else {
            client.on(TdApi.GetSuitablePersonalChats::class, TdResult.Success(TdApi.Chats(2, longArrayOf(NOTES, NEWS))))
        }
        return PersonalChannelViewModel(profile, TdlibPersonalChannelRepository(client), chats).also { runCurrent() }
    }

    private fun TestScope.effectsOf(viewModel: PersonalChannelViewModel): List<PersonalChannelEffect> {
        val effects = mutableListOf<PersonalChannelEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val PersonalChannelViewModel.content get() = uiState.value as PersonalChannelUiState.Content

    @Test
    fun `lists the user's channels with the one shown chosen`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(
                listOf(PersonalChannelItem(NOTES, "Ada's notes"), PersonalChannelItem(NEWS, "Engine news")),
                viewModel.content.channels,
            )
            assertEquals(NOTES, viewModel.content.chosen)
        }

    @Test
    fun `choosing a channel shows it at once and sets it`() =
        runTest {
            client.on(TdApi.SetPersonalChat::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            viewModel.onEvent(PersonalChannelEvent.Choose(NEWS))
            assertTrue(viewModel.content.isSaving)
            assertEquals(NEWS, viewModel.content.chosen)
            runCurrent()

            assertFalse(viewModel.content.isSaving)
            assertEquals(NEWS, client.sentOf<TdApi.SetPersonalChat>().single().chatId)
        }

    @Test
    fun `none takes the channel off the profile`() =
        runTest {
            client.on(TdApi.SetPersonalChat::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            viewModel.onEvent(PersonalChannelEvent.Choose(0))
            runCurrent()

            assertEquals(0L, viewModel.content.chosen)
            assertEquals(0L, client.sentOf<TdApi.SetPersonalChat>().single().chatId)
        }

    @Test
    fun `a refused choice is taken back with a snackbar`() =
        runTest {
            client.on(TdApi.SetPersonalChat::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 10")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PersonalChannelEvent.Choose(NEWS))
            runCurrent()

            assertEquals(NOTES, viewModel.content.chosen)
            assertTrue((effects.single() as PersonalChannelEffect.ShowError).error is DomainError.FloodWait)
        }

    @Test
    fun `a failed load shows why, and trying again loads`() =
        runTest {
            val viewModel = viewModel(failCandidates = true)
            assertTrue(viewModel.uiState.value is PersonalChannelUiState.Failed)

            client.on(TdApi.GetSuitablePersonalChats::class, TdResult.Success(TdApi.Chats(1, longArrayOf(NOTES))))
            viewModel.onEvent(PersonalChannelEvent.Retry)
            runCurrent()

            assertEquals(listOf(NOTES), viewModel.content.channels.map { it.id })
        }

    private companion object {
        const val NOTES = -1_001L
        const val NEWS = -1_002L
    }
}

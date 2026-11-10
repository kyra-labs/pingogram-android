package dev.kyralabs.pingogram.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSecretChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSharedMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeIdVisibilityRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Suggesting a birthday to a contact on [ProfileViewModel] (`suggestUserBirthdate`), through the real TDLib
 * repositories on a [FakeTdlibClient]: the offer, sending, the snackbar and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBirthdaySuggestionViewModelTest {
    private val client = FakeTdlibClient()
    private val created = mutableListOf<ProfileViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private val ada = TdApiFixtures.user(id = ADA, firstName = "Ada", lastName = "Lovelace", isContact = true)

    private fun fullInfo(birthdate: TdApi.Birthdate? = null) = TdApi.UserFullInfo().also { it.birthdate = birthdate }

    /** Answers what opening a profile asks TDLib for: the user, the full info, the private chat, photos and media. */
    private fun answerProfile(
        user: TdApi.User = ada,
        info: TdResult<TdApi.UserFullInfo> = TdResult.Success(fullInfo()),
    ) {
        client.on(TdApi.GetUser::class, TdResult.Success(user))
        client.on(TdApi.GetUserFullInfo::class, info)
        val chat = TdApiFixtures.chat(id = user.id, type = TdApi.ChatTypePrivate(user.id))
        client.on(TdApi.CreatePrivateChat::class) {
            client.emit(TdApiFixtures.updateNewChat(chat))
            TdResult.Success(chat)
        }
        client.on(TdApi.GetUserProfilePhotos::class, TdResult.Success(TdApi.ChatPhotos(0, emptyArray())))
        client.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)))
    }

    private val clock =
        object : WallClock {
            override val minutes = MutableStateFlow(NOW)

            override fun now(): Instant = NOW

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val patterns =
        object : TimePatterns {
            override fun is24HourClock() = false

            override fun bestPattern(
                locale: Locale,
                skeleton: String,
            ) = "MMMM d"
        }

    private suspend fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        myId: Long = ME,
    ): ProfileViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(myId))
        runCurrent()
        val viewModel =
            ProfileViewModel(
                userId = ADA,
                savedStateHandle = handle,
                userRepository = users,
                profileRepository = TdlibUserProfileRepository(client),
                secretChatRepository = TdlibSecretChatRepository(client, backgroundScope),
                chatRepository = chats,
                blockListRepository = TdlibBlockListRepository(client),
                messageActionRepository = TdlibMessageActionRepository(client),
                sharedMediaRepository = TdlibSharedMediaRepository(client),
                searchRepository = TdlibSearchRepository(client, chats),
                idVisibility = FakeIdVisibilityRepository(),
                clock = clock,
                timePatterns = patterns,
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: ProfileViewModel,
        vararg events: ProfileEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val ProfileViewModel.content get() = uiState.value as ProfileUiState.Content

    @Test
    fun `a contact without a birthday gets the offer to suggest one`() =
        runTest {
            answerProfile()
            assertEquals(BirthdaySuggestionOffer("Ada"), viewModel().content.info.birthdaySuggestion)
        }

    @Test
    fun `no offer once the contact has a birthday`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            client.emit(TdApi.UpdateUserFullInfo(ADA, fullInfo(TdApi.Birthdate(10, 12, 0))))
            runCurrent()

            assertNull(viewModel.content.info.birthdaySuggestion)
        }

    @Test
    fun `no offer for someone who is no contact, oneself, or while the full info is unknown`() =
        runTest {
            answerProfile(user = TdApiFixtures.user(id = ADA, firstName = "Ada", isContact = false))
            assertNull(viewModel().content.info.birthdaySuggestion)

            answerProfile()
            assertNull(viewModel(myId = ADA).content.info.birthdaySuggestion)

            answerProfile(info = TdResult.Failure(TdError(500, "Internal")))
            assertNull(viewModel().content.info.birthdaySuggestion)
        }

    @Test
    fun `a picked birthday goes to Telegram once, then a snackbar names the contact`() =
        runTest {
            answerProfile()
            client.on(TdApi.SuggestUserBirthdate::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, ProfileEvent.SuggestBirthdate(Birthdate(10, 12, 1815)))

            val request = client.sentOf<TdApi.SuggestUserBirthdate>().single()
            assertEquals(ADA, request.userId)
            assertEquals(listOf(10, 12, 1815), with(request.birthdate) { listOf(day, month, year) })
            assertEquals(listOf<ProfileEffect>(ProfileEffect.BirthdaySuggested("Ada")), effects)
            assertEquals(BirthdaySuggestionOffer("Ada"), viewModel.content.info.birthdaySuggestion)
        }

    @Test
    fun `a birthday without its year goes with year 0`() =
        runTest {
            answerProfile()
            client.on(TdApi.SuggestUserBirthdate::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.SuggestBirthdate(Birthdate(29, 2, null)))

            assertEquals(
                0,
                client
                    .sentOf<TdApi.SuggestUserBirthdate>()
                    .single()
                    .birthdate.year,
            )
        }

    @Test
    fun `the offer waits while Telegram answers, and a second pick meanwhile sends nothing`() =
        runTest {
            answerProfile()
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SuggestUserBirthdate::class) { answer.await() }
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.SuggestBirthdate(Birthdate(10, 12, null)))
            assertTrue(
                viewModel.content.info.birthdaySuggestion!!
                    .isSending,
            )

            send(viewModel, ProfileEvent.SuggestBirthdate(Birthdate(11, 12, null)))
            assertEquals(1, client.sentOf<TdApi.SuggestUserBirthdate>().size)

            answer.complete(TdResult.Success(TdApi.Ok()))
            runCurrent()
            assertEquals(BirthdaySuggestionOffer("Ada"), viewModel.content.info.birthdaySuggestion)
        }

    @Test
    fun `a refusal is a snackbar and the offer stays`() =
        runTest {
            answerProfile()
            val flood = TdResult.Failure(TdError(429, "Too Many Requests: retry after 3"))
            client.on(TdApi.SuggestUserBirthdate::class, flood)
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            send(viewModel, ProfileEvent.SuggestBirthdate(Birthdate(10, 12, null)))

            assertEquals(listOf<ProfileEffect>(ProfileEffect.ShowError(DomainError.FloodWait(3))), effects)
            assertEquals(BirthdaySuggestionOffer("Ada"), viewModel.content.info.birthdaySuggestion)
        }

    private companion object {
        const val ADA = 42L
        const val ME = 1L

        val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")
    }
}

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
 * Someone not in the contacts on [ProfileViewModel] (the owner's board 24c), through the real TDLib repositories on a
 * [FakeTdlibClient]: the card when they wrote first, and a phone number their privacy settings hide.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStrangerViewModelTest {
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

    private fun fullInfo(groupsInCommon: Int = 0) = TdApi.UserFullInfo().also { it.groupInCommonCount = groupsInCommon }

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

    private val ProfileViewModel.content get() = uiState.value as ProfileUiState.Content

    @Test
    fun `someone not in the contacts who wrote first gets the card, with the groups in common`() =
        runTest {
            val stranger = TdApiFixtures.user(id = ADA, firstName = "Dev", lastName = "Malhotra", isContact = false)
            val info = fullInfo(groupsInCommon = 2)
            answerProfile(user = stranger, info = TdResult.Success(info))
            val viewModel = viewModel()
            assertNull(viewModel.content.stranger)

            client.emit(TdApi.UpdateChatActionBar(ADA, TdApi.ChatActionBarReportAddBlock(false, null)))
            runCurrent()
            assertEquals(ProfileStranger(firstName = "Dev", groupsInCommon = 2), viewModel.content.stranger)

            // Dismissed in the chat, or added as a contact elsewhere: the card goes with the bar.
            client.emit(TdApi.UpdateChatActionBar(ADA, null))
            runCurrent()
            assertNull(viewModel.content.stranger)
        }

    @Test
    fun `a contact gets no card, whatever the chat's bar says`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            client.emit(TdApi.UpdateChatActionBar(ADA, TdApi.ChatActionBarReportAddBlock(false, null)))
            runCurrent()
            assertNull(viewModel.content.stranger)
        }

    @Test
    fun `a phone number hidden by privacy settings says so, once the full info is known`() =
        runTest {
            val hidden = TdApiFixtures.user(id = ADA, firstName = "Dev", lastName = "Malhotra", phoneNumber = "")
            answerProfile(user = hidden)

            with(viewModel().content.info) {
                assertNull(phoneNumber)
                assertEquals("Dev", phoneHiddenBy)
            }
        }

    @Test
    fun `a bot's missing phone number is not hidden, there is none`() =
        runTest {
            val bot = TdApiFixtures.user(id = ADA, firstName = "Helper", phoneNumber = "", type = TdApi.UserTypeBot())
            answerProfile(user = bot)

            assertNull(viewModel().content.info.phoneHiddenBy)
        }

    private companion object {
        const val ADA = 42L
        const val ME = 1L
        val NOW: Instant = Instant.parse("2026-11-15T12:00:00Z")
    }
}

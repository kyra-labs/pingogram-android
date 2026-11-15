package dev.kyralabs.pingogram.feature.profile.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeIdVisibilityRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * A deleted account on [ProfileViewModel] (the owner's board 24g), through the real TDLib repositories on a
 * [FakeTdlibClient]: its one action, deleting the chat, which asks first and leaves the profile once done.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDeletedViewModelTest {
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
        // Deleting reads the chat first, for what it may delete.
        client.on(TdApi.GetChat::class, TdResult.Success(chat))
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

    private val deleted =
        TdApiFixtures.user(id = ADA, firstName = "", phoneNumber = "", type = TdApi.UserTypeDeleted())

    @Test
    fun `a deleted account's one action is deleting the chat, with the card about what is kept`() =
        runTest {
            answerProfile(user = deleted)
            val viewModel = viewModel()

            with(viewModel.content) {
                assertTrue(isDeletedAccount)
                assertTrue(actions.canDeleteChat)
                assertFalse(actions.canMessage)
                assertNull(actions.isMuted)
                assertNull(actions.exportChatId)
                assertNull(actions.callUserId)
            }
        }

    @Test
    fun `someone else's profile cannot delete the chat`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            with(viewModel.content) {
                assertFalse(isDeletedAccount)
                assertFalse(actions.canDeleteChat)
            }
        }

    @Test
    fun `deleting asks first, then deletes the history for the user and leaves the profile`() =
        runTest {
            answerProfile(user = deleted)
            client.on(TdApi.DeleteChatHistory::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(ProfileEvent.DeleteChat)
            runCurrent()
            assertEquals(ProfileDialog.DeleteChat, viewModel.content.dialog)
            assertTrue(client.sent.none { it is TdApi.DeleteChatHistory })

            viewModel.onEvent(ProfileEvent.ConfirmDeleteChat)
            runCurrent()
            assertNull(viewModel.content.dialog)
            val sent = client.sent.filterIsInstance<TdApi.DeleteChatHistory>().single()
            assertEquals(ADA, sent.chatId)
            assertTrue(sent.removeFromChatList)
            assertFalse(sent.revoke)
            assertEquals(listOf<ProfileEffect>(ProfileEffect.ChatDeleted), effects)
        }

    @Test
    fun `a refused deletion stays on the profile with the error`() =
        runTest {
            answerProfile(user = deleted)
            client.on(TdApi.DeleteChatHistory::class, TdResult.Failure(TdError(400, "CHAT_ID_INVALID")))
            val viewModel = viewModel()
            val effects = mutableListOf<ProfileEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(ProfileEvent.ConfirmDeleteChat)
            runCurrent()
            assertTrue(effects.single() is ProfileEffect.ShowError)
        }

    private companion object {
        const val ADA = 42L
        const val ME = 1L
        val NOW: Instant = Instant.parse("2026-11-15T12:00:00Z")
    }
}

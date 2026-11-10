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
 * The note about a contact on [ProfileViewModel] (`setUserNote`, [ProfileNote]), through the real TDLib repositories on
 * a [FakeTdlibClient]: the row, the dialog, saving, failures and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileNoteViewModelTest {
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

    private fun fullInfo(note: String? = null) =
        TdApi.UserFullInfo().also { it.note = note?.let(TdApiFixtures::formattedText) }

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
    fun `a contact's note shows last, and an empty one offers to add one`() =
        runTest {
            answerProfile(info = TdResult.Success(fullInfo(note = "Owes me a chai")))
            val viewModel = viewModel()
            assertEquals(ContactNote("Owes me a chai", "Ada"), viewModel.content.info.note)

            client.emit(TdApi.UpdateUserFullInfo(ADA, fullInfo()))
            runCurrent()
            assertEquals(ContactNote("", "Ada"), viewModel.content.info.note)
        }

    @Test
    fun `no note row for someone who is no contact`() =
        runTest {
            answerProfile(user = TdApiFixtures.user(id = ADA, firstName = "Ada", isContact = false))
            assertNull(viewModel().content.info.note)
        }

    @Test
    fun `no note row on one's own profile`() =
        runTest {
            answerProfile()
            assertNull(viewModel(myId = ADA).content.info.note)
        }

    @Test
    fun `no note row while the full info is unknown`() =
        runTest {
            answerProfile(info = TdResult.Failure(TdError(500, "Internal")))
            assertNull(viewModel().content.info.note)
        }

    @Test
    fun `the note dialog opens on the note, saves only a change, and closes once Telegram took it`() =
        runTest {
            answerProfile(info = TdResult.Success(fullInfo(note = "Owes me a chai")))
            client.on(TdApi.SetUserNote::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.EditNote)
            val opened = viewModel.noteEditor.value
            assertEquals(ProfileNoteEditor("Ada", "Owes me a chai", "Owes me a chai"), opened)
            assertFalse(opened!!.canSave)

            send(viewModel, ProfileEvent.SaveNote)
            assertTrue(client.sentOf<TdApi.SetUserNote>().isEmpty())

            send(viewModel, ProfileEvent.NoteChanged("Owes me two chais"), ProfileEvent.SaveNote)
            val request = client.sentOf<TdApi.SetUserNote>().single()
            assertEquals(ADA, request.userId)
            assertEquals("Owes me two chais", request.note.text)
            assertNull(viewModel.noteEditor.value)
        }

    @Test
    fun `the note keeps at most 140 characters`() =
        runTest {
            answerProfile()
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.EditNote, ProfileEvent.NoteChanged("a".repeat(200)))

            assertEquals(
                140,
                viewModel.noteEditor.value
                    ?.text
                    ?.length,
            )
        }

    @Test
    fun `a failed save stays in the dialog with its error and the text`() =
        runTest {
            answerProfile()
            client.on(TdApi.SetUserNote::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")))
            val viewModel = viewModel()

            send(viewModel, ProfileEvent.EditNote, ProfileEvent.NoteChanged("Met at the Society"))
            send(viewModel, ProfileEvent.SaveNote)

            val editor = viewModel.noteEditor.value
            assertEquals("Met at the Society", editor?.text)
            assertEquals(DomainError.FloodWait(3), editor?.error)
            assertFalse(editor!!.isSaving)

            send(viewModel, ProfileEvent.NoteChanged("Met at the Royal Society"))
            assertNull(viewModel.noteEditor.value?.error)
        }

    @Test
    fun `cancelling closes the dialog and sends nothing`() =
        runTest {
            answerProfile()
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle = handle)

            send(viewModel, ProfileEvent.EditNote, ProfileEvent.NoteChanged("Draft"), ProfileEvent.DismissNote)

            assertNull(viewModel.noteEditor.value)
            assertTrue(client.sentOf<TdApi.SetUserNote>().isEmpty())
            assertFalse(handle.contains("profile.note"))
        }

    @Test
    fun `the note dialog and what was typed survive process death`() =
        runTest {
            answerProfile(info = TdResult.Success(fullInfo(note = "Owes me a chai")))
            val handle = SavedStateHandle()
            send(viewModel(handle = handle), ProfileEvent.EditNote, ProfileEvent.NoteChanged("Owes me nothing"))

            val restored = viewModel(handle = handle)
            runCurrent()

            assertEquals(ProfileNoteEditor("Ada", "Owes me nothing", "Owes me a chai"), restored.noteEditor.value)
        }

    private companion object {
        const val ADA = 42L
        const val ME = 1L

        val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")
    }
}

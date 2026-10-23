package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chats.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chats.ui.create.FakeChatCreationRepository.Companion.GROUP_ID
import dev.kyralabs.pingogram.feature.chats.ui.create.FakeChatCreationRepository.Companion.LINK
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [NewGroupViewModel] (`REQUIREMENTS.md` FR-P4) on fakes: picking people from the contacts (search, picks, the limit,
 * errors), the group's details and photo, creating it with what was picked, the people Telegram did not add, each
 * error path, and restoring after process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewGroupViewModelTest {
    private val contacts = FakeContactRepository()
    private val creation = FakeChatCreationRepository()
    private val photos = FakePickedPhotoRepository()
    private val users = FakeUserRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun contact(
        id: Long,
        firstName: String,
        lastName: String = "",
        username: String? = null,
    ): User =
        user(id, firstName = firstName, lastName = lastName, status = UserStatus.Empty).copy(
            isContact = true,
            usernames = username?.let { persistentListOf(it) } ?: persistentListOf(),
        )

    private val ada = contact(2, "Ada", "Lovelace", username = "ada")
    private val alan = contact(3, "Alan", "Turing", username = "turing")
    private val grace = contact(4, "Grace", "Hopper")

    /** A view model whose steps are collected, as by the screens. */
    private fun TestScope.viewModel(savedState: SavedStateHandle = SavedStateHandle()): NewGroupViewModel {
        val viewModel =
            NewGroupViewModel(
                savedStateHandle = savedState,
                repository = creation,
                photoRepository = photos,
                userRepository = users,
                contactRepository = contacts,
                clock = FakeWallClock(NOW),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.members.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.details.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: NewGroupViewModel): List<CreateChatEffect> {
        val effects = mutableListOf<CreateChatEffect>()
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { viewModel.memberEffects.collect(effects::add) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.detailsEffects.collect(effects::add)
        }
        return effects
    }

    private fun TestScope.members(
        viewModel: NewGroupViewModel,
        event: MemberPickerEvent,
    ) {
        viewModel.onMembersEvent(event)
        runCurrent()
    }

    private fun TestScope.details(
        viewModel: NewGroupViewModel,
        event: ChatDetailsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val NewGroupViewModel.shownIds: List<Long>
        get() = (members.value.contacts as MemberList.Content).items.map { it.userId }

    // Picking

    @Test
    fun `the contacts load once the step shows, then list every contact by name`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(MemberList.Loading, viewModel.members.value.contacts)

            contacts.setContacts(ada, alan, grace)
            runCurrent()

            assertEquals(listOf(2L, 3L, 4L), viewModel.shownIds)
            assertEquals("Ada Lovelace", (viewModel.members.value.contacts as MemberList.Content).items[0].name)
            assertEquals("ada", (viewModel.members.value.contacts as MemberList.Content).items[0].username)
        }

    @Test
    fun `no contacts and a failure say so, and try again lists them again`() =
        runTest {
            val viewModel = viewModel()
            contacts.setContacts()
            runCurrent()
            assertEquals(MemberList.Empty, viewModel.members.value.contacts)

            contacts.list.value = Outcome.Failure(DomainError.Network)
            val failing = viewModel()
            assertEquals(MemberList.Error(DomainError.Network), failing.members.value.contacts)
            val listings = contacts.listings
            contacts.setContacts(ada)
            members(failing, MemberPickerEvent.Retry)

            assertEquals(listings + 1, contacts.listings)
            assertEquals(listOf(2L), failing.shownIds)
        }

    @Test
    fun `nothing is listed again without a failure`() =
        runTest {
            contacts.setContacts(ada)
            val viewModel = viewModel()
            val listings = contacts.listings

            members(viewModel, MemberPickerEvent.Retry)

            assertEquals(listings, contacts.listings)
        }

    @Test
    fun `the query filters by the words of names and by usernames, and TDLib's search adds its matches`() =
        runTest {
            contacts.setContacts(ada, alan, grace)
            contacts.searches["gr"] = emptyList()
            contacts.searches["хоппер"] = listOf(4)
            val viewModel = viewModel()

            members(viewModel, MemberPickerEvent.QueryChanged("gr"))
            assertEquals(listOf(4L), viewModel.shownIds)
            members(viewModel, MemberPickerEvent.QueryChanged("@tur"))
            assertEquals(listOf(3L), viewModel.shownIds)

            members(viewModel, MemberPickerEvent.QueryChanged("хоппер"))
            assertEquals(MemberList.NoResults, viewModel.members.value.contacts)
            advanceTimeBy(MemberPicker.SEARCH_DELAY_MILLIS + 1)
            runCurrent()
            assertEquals(listOf(4L), viewModel.shownIds)
            assertEquals(listOf("хоппер"), contacts.searched.filter { it == "хоппер" })
        }

    @Test
    fun `tapping picks and unpicks, the chips keep the order, and a chip removes its pick`() =
        runTest {
            contacts.setContacts(ada, alan, grace)
            val viewModel = viewModel()

            members(viewModel, MemberPickerEvent.Toggle(4))
            members(viewModel, MemberPickerEvent.Toggle(2))
            assertEquals(
                listOf(4L, 2L),
                viewModel.members.value.picked
                    .map { it.userId },
            )
            val rows = (viewModel.members.value.contacts as MemberList.Content).items
            assertEquals(listOf(true, false, true), rows.map { it.isPicked })

            members(viewModel, MemberPickerEvent.Toggle(4))
            members(viewModel, MemberPickerEvent.Remove(2))
            assertTrue(
                viewModel.members.value.picked
                    .isEmpty(),
            )
        }

    @Test
    fun `no more people than Telegram adds can be picked`() =
        runTest {
            val people = (1L..ChatCreationLimits.MEMBERS_TO_ADD_MAX + 1L).map { contact(it, "Person $it") }
            contacts.setContacts(*people.toTypedArray())
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            people.forEach { members(viewModel, MemberPickerEvent.Toggle(it.id)) }

            assertEquals(ChatCreationLimits.MEMBERS_TO_ADD_MAX, viewModel.members.value.picked.size)
            assertEquals(listOf(CreateChatEffect.TooManyMembers), effects)
        }

    // Details and creating

    @Test
    fun `the details start empty and count the people picked`() =
        runTest {
            contacts.setContacts(ada, alan)
            val viewModel = viewModel()
            members(viewModel, MemberPickerEvent.Toggle(2))

            val state = viewModel.details.value
            assertEquals("", state.title)
            assertEquals(MessageAutoDelete.Off, state.autoDelete)
            assertEquals(ChatPhoto.None, state.photo)
            assertEquals(1, state.memberCount)
            assertTrue(state.canCreate)
        }

    @Test
    fun `creating without a name shows the field's error and asks nothing`() =
        runTest {
            val viewModel = viewModel()

            details(viewModel, ChatDetailsEvent.TitleChanged("   "))
            details(viewModel, ChatDetailsEvent.Create)

            assertTrue(viewModel.details.value.titleError)
            assertTrue(creation.groups.isEmpty())
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))
            assertFalse(viewModel.details.value.titleError)
        }

    @Test
    fun `a name is cut at Telegram's limit`() =
        runTest {
            val viewModel = viewModel()

            details(viewModel, ChatDetailsEvent.TitleChanged("x".repeat(ChatCreationLimits.TITLE_LENGTH_MAX + 5)))

            assertEquals(ChatCreationLimits.TITLE_LENGTH_MAX, viewModel.details.value.title.length)
        }

    @Test
    fun `create sends the name, the picks, the auto-delete time and the photo once, then opens the group`() =
        runTest {
            contacts.setContacts(ada, alan, grace)
            val answer = CompletableDeferred<Outcome<CreatedGroup>>()
            creation.onCreateGroup = { answer.await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            members(viewModel, MemberPickerEvent.Toggle(3))
            members(viewModel, MemberPickerEvent.Toggle(2))
            details(viewModel, ChatDetailsEvent.TitleChanged("Book club"))
            details(viewModel, ChatDetailsEvent.AutoDeleteChanged(MessageAutoDelete.Week))
            details(viewModel, ChatDetailsEvent.PhotoPicked("content://photo"))

            details(viewModel, ChatDetailsEvent.Create)
            assertTrue(viewModel.details.value.isCreating)
            details(viewModel, ChatDetailsEvent.Create)
            answer.complete(Outcome.Success(CreatedGroup(GROUP_ID, persistentListOf())))
            runCurrent()

            assertEquals(
                listOf(
                    GroupRequest(
                        "Book club",
                        listOf(3, 2),
                        MessageAutoDelete.Week,
                        "/cache/uploads/content://photo.jpg",
                    ),
                ),
                creation.groups,
            )
            assertEquals(listOf(CreateChatEffect.OpenChat(GROUP_ID)), effects)
            clear(viewModel)
            assertTrue(photos.discarded.isEmpty())
        }

    @Test
    fun `the people Telegram did not add are named in a dialog, which then opens the group`() =
        runTest {
            users.setUsers(alan, grace)
            creation.onCreateGroup = { Outcome.Success(CreatedGroup(GROUP_ID, persistentListOf(3, 4, 9))) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))

            details(viewModel, ChatDetailsEvent.Create)

            val dialog = viewModel.details.value.notAdded
            assertEquals(NotAddedDialog(GROUP_ID, 3, persistentListOf("Alan Turing", "Grace Hopper")), dialog)
            assertTrue(effects.isEmpty())
            viewModel.onNotAddedEvent(NotAddedEvent.Dismiss)
            runCurrent()
            assertEquals(listOf(CreateChatEffect.OpenChat(GROUP_ID)), effects)
            assertNull(viewModel.details.value.notAdded)
        }

    @Test
    fun `sharing the invite link with them shares it, then opens the group`() =
        runTest {
            creation.onCreateGroup = { Outcome.Success(CreatedGroup(GROUP_ID, persistentListOf(3))) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))
            details(viewModel, ChatDetailsEvent.Create)

            viewModel.onNotAddedEvent(NotAddedEvent.ShareLink)
            runCurrent()

            assertEquals(listOf(GROUP_ID), creation.inviteLinks)
            assertEquals(listOf(CreateChatEffect.ShareLink(LINK), CreateChatEffect.OpenChat(GROUP_ID)), effects)
        }

    @Test
    fun `a link that cannot be read is a snackbar, and the dialog stays`() =
        runTest {
            creation.onCreateGroup = { Outcome.Success(CreatedGroup(GROUP_ID, persistentListOf(3))) }
            creation.onInviteLink = { Outcome.Failure(DomainError.Network) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))
            details(viewModel, ChatDetailsEvent.Create)

            viewModel.onNotAddedEvent(NotAddedEvent.ShareLink)
            runCurrent()

            assertEquals(listOf(CreateChatEffect.ShowError(DomainError.Network)), effects)
            assertFalse(
                viewModel.details.value.notAdded!!
                    .isSharing,
            )
        }

    // Errors

    @Test
    fun `a flood wait counts down before create can be tapped again`() =
        runTest {
            creation.onCreateGroup = { Outcome.Failure(DomainError.FloodWait(3)) }
            val viewModel = viewModel()
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))

            details(viewModel, ChatDetailsEvent.Create)

            assertEquals(FloodWait(3, 3), viewModel.details.value.floodWait)
            assertFalse(viewModel.details.value.canCreate)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(FloodWait(2, 3), viewModel.details.value.floodWait)
            advanceUntilIdle()
            assertNull(viewModel.details.value.floodWait)
            assertTrue(viewModel.details.value.canCreate)
        }

    @Test
    fun `a name Telegram refuses shows at its field`() =
        runTest {
            creation.onCreateGroup = { Outcome.Failure(DomainError.TitleInvalid) }
            val viewModel = viewModel()
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))

            details(viewModel, ChatDetailsEvent.Create)

            assertTrue(viewModel.details.value.titleError)
        }

    @Test
    fun `other failures are snackbars, and the form can be sent again`() =
        runTest {
            val errors = COMMON_ERRORS + DomainError.TooManyChats + DomainError.AccountRestricted
            for (error in errors) {
                creation.onCreateGroup = { Outcome.Failure(error) }
                val viewModel = viewModel()
                val effects = effectsOf(viewModel)
                details(viewModel, ChatDetailsEvent.TitleChanged("Club"))

                details(viewModel, ChatDetailsEvent.Create)

                assertEquals(listOf(CreateChatEffect.ShowError(error)), effects)
                assertTrue(viewModel.details.value.canCreate)
            }
        }

    @Test
    fun `a silent failure shows nothing`() =
        runTest {
            creation.onCreateGroup = { Outcome.Failure(DomainError.Silent) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))

            details(viewModel, ChatDetailsEvent.Create)

            assertTrue(effects.isEmpty())
        }

    // The photo

    @Test
    fun `a picked photo is copied, and a picture that cannot be one is a snackbar`() =
        runTest {
            val copy = CompletableDeferred<Outcome<String>>()
            photos.onPreparePhoto = { copy.await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            details(viewModel, ChatDetailsEvent.PhotoPicked("content://photo"))
            assertEquals(ChatPhoto.Preparing, viewModel.details.value.photo)
            assertFalse(viewModel.details.value.canCreate)
            copy.complete(Outcome.Success("/copy.jpg"))
            runCurrent()
            assertEquals(ChatPhoto.Ready("/copy.jpg"), viewModel.details.value.photo)

            photos.onPreparePhoto = { Outcome.Failure(DomainError.PhotoInvalid) }
            details(viewModel, ChatDetailsEvent.PhotoPicked("content://clip"))
            assertEquals(ChatPhoto.None, viewModel.details.value.photo)
            assertEquals(listOf(CreateChatEffect.ShowError(DomainError.PhotoInvalid)), effects)
            assertEquals(listOf("/copy.jpg"), photos.discarded)
        }

    @Test
    fun `removing the photo, or leaving without a group, deletes its copy`() =
        runTest {
            val viewModel = viewModel()
            details(viewModel, ChatDetailsEvent.PhotoPicked("a"))
            details(viewModel, ChatDetailsEvent.RemovePhoto)
            assertEquals(ChatPhoto.None, viewModel.details.value.photo)
            details(viewModel, ChatDetailsEvent.PhotoPicked("b"))

            clear(viewModel)

            assertEquals(listOf("/cache/uploads/a.jpg", "/cache/uploads/b.jpg"), photos.discarded)
        }

    // Process death

    @Test
    fun `what was typed and picked survives process death`() =
        runTest {
            contacts.setContacts(ada, alan, grace)
            val savedState = SavedStateHandle()
            val viewModel = viewModel(savedState)
            members(viewModel, MemberPickerEvent.QueryChanged("a"))
            members(viewModel, MemberPickerEvent.Toggle(4))
            details(viewModel, ChatDetailsEvent.TitleChanged("Club"))
            details(viewModel, ChatDetailsEvent.AutoDeleteChanged(MessageAutoDelete.Day))
            details(viewModel, ChatDetailsEvent.PhotoPicked("p"))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals("a", restored.members.value.query)
            assertEquals(
                listOf(4L),
                restored.members.value.picked
                    .map { it.userId },
            )
            val state = restored.details.value
            assertEquals("Club", state.title)
            assertEquals(MessageAutoDelete.Day, state.autoDelete)
            assertEquals(ChatPhoto.Ready("/cache/uploads/p.jpg"), state.photo)
        }

    @Test
    fun `a group created before process death shows its dialog again`() =
        runTest {
            users.setUsers(alan)
            val savedState =
                SavedStateHandle(
                    mapOf(
                        NewGroupViewModel.KEY_CREATED_CHAT to GROUP_ID,
                        NewGroupViewModel.KEY_NOT_ADDED to longArrayOf(3),
                    ),
                )

            val restored = viewModel(savedState)

            assertEquals(NotAddedDialog(GROUP_ID, 1, persistentListOf("Alan Turing")), restored.details.value.notAdded)
            details(restored, ChatDetailsEvent.Create)
            assertTrue(creation.groups.isEmpty())
        }
}

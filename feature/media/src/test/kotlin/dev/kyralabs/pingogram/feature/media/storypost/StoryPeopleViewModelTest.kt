package dev.kyralabs.pingogram.feature.media.storypost

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The people picker of a story's "Selected people": who it offers, the picks it starts with, the query, the picks
 * and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryPeopleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val contacts = FakeContacts()

    init {
        Dispatchers.setMain(dispatcher)
        contacts.users.value =
            Outcome.Success(
                persistentListOf(
                    user(7, "Grace", "Hopper"),
                    user(8, "Alan", "Turing"),
                    user(9, "Ada", "Lovelace"),
                    user(10, "Helper", "").copy(type = UserType.Bot),
                ),
            )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `offers the contacts, people only, and picks them`() =
        runTest(dispatcher) {
            val viewModel = StoryPeopleViewModel(SavedStateHandle(), contacts)

            viewModel.uiState.test {
                assertEquals(StoryPeopleUiState.Loading, awaitItem())
                assertEquals(listOf(7L, 8L, 9L), (awaitItem() as StoryPeopleUiState.Content).ids())

                viewModel.toggle(8)
                val picked = awaitItem() as StoryPeopleUiState.Content
                assertEquals(1, picked.pickedCount)
                assertEquals(listOf(false, true, false), picked.candidates.map { it.isPicked })

                viewModel.toggle(8)
                assertEquals(0, (awaitItem() as StoryPeopleUiState.Content).pickedCount)
            }
        }

    @Test
    fun `starts with who the story is for already, once per opening`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val viewModel = StoryPeopleViewModel(handle, contacts)
            viewModel.start(listOf(9L, 7L))
            assertEquals(listOf(9L, 7L), viewModel.picked)

            viewModel.toggle(7)
            StoryPeopleViewModel(handle, contacts).start(listOf(9L, 7L))
            assertEquals(listOf(9L), viewModel.picked)

            viewModel.reset()
            viewModel.start(listOf(8L))
            assertEquals(listOf(8L), viewModel.picked)
        }

    @Test
    fun `the query filters by the start of a name, and says when nobody matches`() =
        runTest(dispatcher) {
            val viewModel = StoryPeopleViewModel(SavedStateHandle(), contacts)

            viewModel.uiState.test {
                skipItems(2)
                viewModel.setQuery("hop")
                assertEquals(listOf(7L), (awaitItem() as StoryPeopleUiState.Content).ids())

                viewModel.setQuery("zzz")
                val none = awaitItem() as StoryPeopleUiState.Content
                assertEquals(emptyList<Long>(), none.ids())
                assertEquals(true, none.noMatches)
            }
        }

    @Test
    fun `the picks and the query survive process death, and reset drops them`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            StoryPeopleViewModel(handle, contacts).apply {
                toggle(7)
                toggle(8)
                setQuery("a")
            }

            val restored = StoryPeopleViewModel(handle, contacts)
            assertEquals(listOf(7L, 8L), restored.picked)
            assertEquals("a", restored.query.value)

            restored.reset()
            assertEquals(emptyList<Long>(), restored.picked)
            assertEquals("", restored.query.value)
        }

    @Test
    fun `a failure to read the contacts says why`() =
        runTest(dispatcher) {
            contacts.users.value = Outcome.Failure(DomainError.Network)
            val viewModel = StoryPeopleViewModel(SavedStateHandle(), contacts)

            viewModel.uiState.test {
                skipItems(1)
                assertEquals(StoryPeopleUiState.Error(DomainError.Network), awaitItem())
            }
        }
}

private fun user(
    id: Long,
    firstName: String,
    lastName: String,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    usernames = persistentListOf(),
    phoneNumber = "",
    status = UserStatus.Empty,
    profilePhoto = null,
    isVerified = false,
    isScam = false,
    isFake = false,
    isPremium = false,
    isContact = true,
    isMutualContact = true,
    isSupport = false,
    type = UserType.Regular,
    haveAccess = true,
)

private fun StoryPeopleUiState.Content.ids() = candidates.map { it.userId }

/** The contacts as [users] holds them; nothing else is asked of it. */
internal class FakeContacts : ContactRepository {
    val users = MutableStateFlow<Outcome<ImmutableList<User>>>(Outcome.Success(persistentListOf()))

    override fun contacts(): Flow<Outcome<ImmutableList<User>>> = users

    override suspend fun search(query: String): Outcome<ImmutableList<Long>> =
        Outcome.Success(emptyList<Long>().toImmutableList())

    override suspend fun addByPhoneNumber(
        phoneNumber: String,
        firstName: String,
        lastName: String,
    ): Outcome<AddContactResult> = Outcome.Failure(DomainError.NotFound)

    override suspend fun addByUsername(
        username: String,
        firstName: String,
        lastName: String,
    ): Outcome<Long> = Outcome.Failure(DomainError.NotFound)

    override suspend fun editableContact(userId: Long): Outcome<EditableContact> = Outcome.Failure(DomainError.NotFound)

    override suspend fun save(
        userId: Long,
        firstName: String,
        lastName: String,
        sharePhoneNumber: Boolean,
        note: String?,
    ): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun delete(userId: Long): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun privateChatId(userId: Long): Outcome<Long> = Outcome.Success(userId)

    override suspend fun inviteLink(): String = ""
}

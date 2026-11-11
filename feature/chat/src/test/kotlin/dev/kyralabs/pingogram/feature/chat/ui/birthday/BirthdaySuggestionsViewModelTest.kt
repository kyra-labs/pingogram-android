package dev.kyralabs.pingogram.feature.chat.ui.birthday

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionLists
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacyRulesChange
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.repository.DeclinedBirthdaysRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.ui.component.BirthdaySuggestionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The answers to a conversation's birthday suggestions (the owner's board 16b, [BirthdaySuggestionsViewModel]). */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaySuggestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeProfiles()
    private val privacy = FakePrivacy()
    private val declined = FakeDeclined()

    private val suggested = Birthdate(day = 10, month = 12, year = null)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): BirthdaySuggestionsViewModel {
        val viewModel = BirthdaySuggestionsViewModel(CHAT, profiles, privacy, declined)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.answers.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a suggestion waits for an answer, and names who may see the birthday`() =
        runTest(dispatcher) {
            val answers = viewModel().answers.value

            assertEquals(BirthdaySuggestionStatus.Pending, answers.statusOf(MESSAGE, suggested))
            assertEquals(PrivacyChoice.Contacts, answers.privacy)
        }

    @Test
    fun `a suggestion of the birthday the user has is accepted`() =
        runTest(dispatcher) {
            profiles.birthdate = suggested

            assertEquals(BirthdaySuggestionStatus.Accepted, viewModel().answers.value.statusOf(MESSAGE, suggested))
        }

    @Test
    fun `accepting sets the birthday only on the tap, waiting for Telegram`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            assertTrue(profiles.set.isEmpty())
            val answer = CompletableDeferred<Outcome<Unit>>()
            profiles.answer = answer

            viewModel.accept(MESSAGE, suggested)
            runCurrent()
            assertEquals(BirthdaySuggestionStatus.Accepting, viewModel.answers.value.statusOf(MESSAGE, suggested))

            answer.complete(Outcome.Success(Unit))
            runCurrent()
            assertEquals(listOf(suggested), profiles.set)
            assertEquals(BirthdaySuggestionStatus.Accepted, viewModel.answers.value.statusOf(MESSAGE, suggested))
        }

    @Test
    fun `a refused birthday stays to answer, and says why`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            profiles.answer = CompletableDeferred(Outcome.Failure(DomainError.Network))
            val error = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.errors.first() }

            viewModel.accept(MESSAGE, suggested)
            runCurrent()

            assertEquals(BirthdaySuggestionStatus.Pending, viewModel.answers.value.statusOf(MESSAGE, suggested))
            assertTrue(error.isCompleted)
        }

    @Test
    fun `declining is kept on the device`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.decline(MESSAGE)
            runCurrent()

            assertEquals(BirthdaySuggestionStatus.Declined, viewModel.answers.value.statusOf(MESSAGE, suggested))
            assertEquals(setOf(MESSAGE), declined.ids.value)
            assertTrue(profiles.set.isEmpty())
        }

    @Test
    fun `a change of the privacy setting shows at once`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            privacy.changed.emit(PrivacyRulesChange(PrivacySetting.Birthdate, PrivacyRules(PrivacyChoice.Nobody)))
            runCurrent()

            assertEquals(PrivacyChoice.Nobody, viewModel.answers.value.privacy)
        }

    private class FakeProfiles : ProfileRepository {
        var birthdate: Birthdate? = null
        var answer = CompletableDeferred<Outcome<Unit>>(Outcome.Success(Unit))
        val set = mutableListOf<Birthdate?>()

        override val bioLengthMax: StateFlow<Int> = MutableStateFlow(70)

        override suspend fun editableProfile() =
            Outcome.Success(EditableProfile("Ada", "", "", "", birthdate = birthdate))

        override suspend fun setBirthdate(birthdate: Birthdate?): Outcome<Unit> =
            answer.await().also { if (it is Outcome.Success) set += birthdate }

        override suspend fun checkUsername(username: String): Outcome<UsernameCheck> = error("Not used")

        override suspend fun setName(
            firstName: String,
            lastName: String,
        ): Outcome<Unit> = error("Not used")

        override suspend fun setUsername(username: String): Outcome<Unit> = error("Not used")

        override suspend fun setBio(bio: String): Outcome<Unit> = error("Not used")

        override suspend fun setProfilePhoto(
            path: String,
            onProgress: (progress: Float) -> Unit,
        ): Outcome<Unit> = error("Not used")

        override suspend fun deleteProfilePhoto(): Outcome<Unit> = error("Not used")

        override suspend fun profileLink(): Outcome<ProfileLink> = error("Not used")
    }

    private class FakePrivacy : PrivacyRepository {
        val changed = MutableSharedFlow<PrivacyRulesChange>()

        override val changes: Flow<PrivacyRulesChange> = changed

        override suspend fun rules(setting: PrivacySetting) = Outcome.Success(PrivacyRules(PrivacyChoice.Contacts))

        override suspend fun setChoice(
            setting: PrivacySetting,
            choice: PrivacyChoice,
        ): Outcome<PrivacyRules> = error("Not used")

        override suspend fun exceptions(setting: PrivacySetting): Outcome<PrivacyExceptionLists> = error("Not used")

        override suspend fun setExceptions(
            setting: PrivacySetting,
            lists: PrivacyExceptionLists,
        ): Outcome<PrivacyRules> = error("Not used")

        override suspend fun showsReadDate(): Outcome<Boolean> = error("Not used")

        override suspend fun setShowsReadDate(shows: Boolean): Outcome<Unit> = error("Not used")

        override suspend fun newChatPrivacy(): Outcome<NewChatPrivacy> = error("Not used")

        override suspend fun setAllowsNonContacts(allows: Boolean): Outcome<Unit> = error("Not used")
    }

    private class FakeDeclined : DeclinedBirthdaysRepository {
        val ids = MutableStateFlow(emptySet<Long>())

        override fun declined(chatId: Long): Flow<Set<Long>> = ids.map { it }

        override suspend fun decline(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> {
            ids.value += messageId
            return Outcome.Success(Unit)
        }
    }

    private companion object {
        const val CHAT = 42L
        const val MESSAGE = 7L
    }
}

package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()
    private val photoFiles = FakePickedPhotoRepository()
    private val copy = "/data/cache/uploads/1/photo.jpg"
    private val ok = TdResult.Success(TdApi.Ok())

    private val me =
        TdApiFixtures.user(id = 42, firstName = "Ada", lastName = "Lovelace", usernames = listOf("ada"))

    private val saved = ProfileFields(firstName = "Ada", lastName = "Lovelace", username = "ada", bio = "Analyst")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        photoFiles.onPreparePhoto = { Outcome.Success(copy) }
        client.on(TdApi.GetMe::class) { TdResult.Success(me) }
        client.on(TdApi.GetUserFullInfo::class, TdResult.Success(ProfileFixtures.userFullInfo(bio = "Analyst")))
        client.on(TdApi.CheckChatUsername::class, TdResult.Success(TdApi.CheckChatUsernameResultOk()))
        client.on(TdApi.SetName::class, ok)
        client.on(TdApi.SetUsername::class, ok)
        client.on(TdApi.SetBio::class, ok)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them. The caching repositories subscribe
    // to TDLib's updates before the test emits any, as they do at app start, and TDLib has announced the user.
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): EditProfileViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val profiles = TdlibProfileRepository(client, backgroundScope)
        runCurrent()
        backgroundScope.launch {
            client.emit(TdApiFixtures.updateUser(me))
            client.emit(TdApiFixtures.myIdOption(42))
        }
        runCurrent()
        return EditProfileViewModel(handle, profiles, users, photoFiles).also { runCurrent() }
    }

    private fun TestScope.effectsOf(viewModel: EditProfileViewModel): List<EditProfileEffect> {
        val effects = mutableListOf<EditProfileEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: EditProfileViewModel,
        vararg events: EditProfileEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** Types [username] and waits out the pause before Telegram is asked. */
    private fun TestScope.typeUsername(
        viewModel: EditProfileViewModel,
        username: String,
    ) {
        send(viewModel, EditProfileEvent.UsernameChanged(username))
        advanceTimeBy(EditProfileViewModel.USERNAME_CHECK_DELAY.inWholeMilliseconds + 1)
        runCurrent()
    }

    private val EditProfileViewModel.content get() = uiState.value as EditProfileUiState.Content

    // Loading

    @Test
    fun `is loading until TDLib reports the profile`() =
        runTest {
            client.on(TdApi.GetMe::class) { CompletableDeferred<TdResult<TdApi.User>>().await() }

            assertEquals(EditProfileUiState.Loading, viewModel().uiState.value)
        }

    @Test
    fun `shows the profile with the avatar, nothing changed`() =
        runTest {
            val content = viewModel().content

            assertEquals(saved, content.saved)
            assertEquals(saved, content.fields)
            assertEquals(ProfileAccount(userId = 42, name = "Ada Lovelace", photo = null), content.account)
            assertEquals(70, content.bioLengthMax)
            assertEquals(UsernameStatus.Unchanged, content.usernameStatus)
            assertFalse(content.hasChanges)
            assertFalse(content.canSave)
        }

    @Test
    fun `a failure shows why, and retry loads again`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")))
            val viewModel = viewModel()

            assertEquals(EditProfileUiState.Error(DomainError.FloodWait(8)), viewModel.uiState.value)

            client.on(TdApi.GetMe::class) { TdResult.Success(me) }
            send(viewModel, EditProfileEvent.Retry)

            assertEquals(saved, viewModel.content.saved)
        }

    @Test
    fun `the bio limit follows TDLib's option`() =
        runTest {
            val viewModel = viewModel()

            client.emit(ProfileFixtures.integerOption("bio_length_max", 140))
            runCurrent()

            assertEquals(140, viewModel.content.bioLengthMax)
        }

    @Test
    fun `the avatar follows the user TDLib announces`() =
        runTest {
            val viewModel = viewModel()

            client.emit(TdApiFixtures.updateUser(me.apply { profilePhoto = TdApiFixtures.profilePhoto() }))
            runCurrent()

            assertEquals(
                10,
                viewModel.content.account
                    ?.photo
                    ?.small
                    ?.id,
            )
        }

    // Fields

    @Test
    fun `typed changes can be saved`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.LastNameChanged("King"), EditProfileEvent.BioChanged("Countess"))

            assertEquals(saved.copy(lastName = "King", bio = "Countess"), viewModel.content.fields)
            assertTrue(viewModel.content.canSave)
        }

    @Test
    fun `typing back what TDLib has, or only spaces around it, is no change`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                EditProfileEvent.FirstNameChanged("Adah"),
                EditProfileEvent.FirstNameChanged(" Ada "),
                EditProfileEvent.BioChanged("Analyst "),
            )

            assertFalse(viewModel.content.hasChanges)
            assertFalse(viewModel.content.canSave)
        }

    @Test
    fun `the first name is required, and names have at most 64 characters`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.FirstNameChanged("  "))
            assertEquals(FieldProblem.Required, viewModel.content.firstNameProblem)
            assertFalse(viewModel.content.canSave)

            send(viewModel, EditProfileEvent.FirstNameChanged("A".repeat(65)))
            assertEquals(FieldProblem.TooLong, viewModel.content.firstNameProblem)

            send(viewModel, EditProfileEvent.FirstNameChanged("A".repeat(64)), EditProfileEvent.LastNameChanged(""))
            assertNull(viewModel.content.firstNameProblem)
            assertNull(viewModel.content.lastNameProblem)
            assertTrue(viewModel.content.canSave)

            send(viewModel, EditProfileEvent.LastNameChanged("😀".repeat(65)))
            assertEquals(FieldProblem.TooLong, viewModel.content.lastNameProblem)
            assertFalse(viewModel.content.canSave)
        }

    @Test
    fun `the bio has at most bio_length_max characters, and no line breaks`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.BioChanged("Poetical\nscientist"))
            assertEquals("Poetical scientist", viewModel.content.fields.bio)

            send(viewModel, EditProfileEvent.BioChanged("a".repeat(71)))
            assertEquals(FieldProblem.TooLong, viewModel.content.bioProblem)
            assertFalse(viewModel.content.canSave)

            client.emit(ProfileFixtures.integerOption("bio_length_max", 140))
            runCurrent()
            assertNull(viewModel.content.bioProblem)
            assertTrue(viewModel.content.canSave)
        }

    // Username

    @Test
    fun `a new username is checked with Telegram after a pause in typing`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.UsernameChanged("ada_l"))
            assertEquals(UsernameStatus.Checking, viewModel.content.usernameStatus)
            advanceTimeBy(EditProfileViewModel.USERNAME_CHECK_DELAY.inWholeMilliseconds - 1)
            runCurrent()
            assertTrue(client.sentOf<TdApi.CheckChatUsername>().isEmpty())

            advanceTimeBy(2)
            runCurrent()

            val check = client.sentOf<TdApi.CheckChatUsername>().single()
            assertEquals(42L to "ada_l", check.chatId to check.username)
            assertEquals(UsernameStatus.Checked(UsernameCheck.Available), viewModel.content.usernameStatus)
            assertTrue(viewModel.content.canSave)
        }

    @Test
    fun `typing on cancels the pending check, and the one running`() =
        runTest {
            val running = CompletableDeferred<TdResult<TdApi.CheckChatUsernameResult>>()
            client.on(TdApi.CheckChatUsername::class) { running.await() }
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.UsernameChanged("ada_l"))
            advanceTimeBy(300)
            send(viewModel, EditProfileEvent.UsernameChanged("ada_lo"))
            typeUsername(viewModel, "ada_lov")
            client.on(TdApi.CheckChatUsername::class, TdResult.Success(TdApi.CheckChatUsernameResultUsernameOccupied()))
            typeUsername(viewModel, "ada_love")
            running.complete(TdResult.Success(TdApi.CheckChatUsernameResultOk()))
            runCurrent()

            assertEquals(listOf("ada_lov", "ada_love"), client.sentOf<TdApi.CheckChatUsername>().map { it.username })
            assertEquals(UsernameStatus.Checked(UsernameCheck.Taken), viewModel.content.usernameStatus)
            assertFalse(viewModel.content.canSave)
        }

    @Test
    fun `a username Telegram would refuse anyway is not sent`() =
        runTest {
            val viewModel = viewModel()

            typeUsername(viewModel, "ad")
            assertEquals(UsernameStatus.Problem(UsernameProblem.TooShort), viewModel.content.usernameStatus)
            typeUsername(viewModel, "1ada_l")
            assertEquals(UsernameStatus.Problem(UsernameProblem.StartsWithNonLetter), viewModel.content.usernameStatus)

            assertTrue(client.sentOf<TdApi.CheckChatUsername>().isEmpty())
            assertFalse(viewModel.content.canSave)
        }

    @Test
    fun `an @ typed or pasted before the username is dropped`() =
        runTest {
            val viewModel = viewModel()

            typeUsername(viewModel, "@ada_l")

            assertEquals("ada_l", viewModel.content.fields.username)
        }

    @Test
    fun `the username TDLib has needs no check, and an emptied one is removed`() =
        runTest {
            val viewModel = viewModel()

            typeUsername(viewModel, "")
            assertEquals(UsernameStatus.Removed, viewModel.content.usernameStatus)
            assertTrue(viewModel.content.canSave)
            typeUsername(viewModel, "ada")
            assertEquals(UsernameStatus.Unchanged, viewModel.content.usernameStatus)

            assertTrue(client.sentOf<TdApi.CheckChatUsername>().isEmpty())
        }

    @Test
    fun `a username for sale cannot be saved, one that could not be checked can`() =
        runTest {
            client.on(
                TdApi.CheckChatUsername::class,
                TdResult.Success(TdApi.CheckChatUsernameResultUsernamePurchasable()),
            )
            val viewModel = viewModel()

            typeUsername(viewModel, "pricey")
            assertEquals(UsernameStatus.Checked(UsernameCheck.ForSale), viewModel.content.usernameStatus)
            assertFalse(viewModel.content.canSave)

            client.on(TdApi.CheckChatUsername::class, TdResult.Failure(TdError(500, "Network error")))
            typeUsername(viewModel, "offline")
            assertEquals(UsernameStatus.CheckFailed, viewModel.content.usernameStatus)
            assertTrue(viewModel.content.canSave)
        }

    // Saving

    @Test
    fun `save sends only what changed, then leaves`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.BioChanged(" Countess of Lovelace "), EditProfileEvent.Save)

            assertEquals(listOf("Countess of Lovelace"), client.sentOf<TdApi.SetBio>().map { it.bio })
            assertTrue(client.sentOf<TdApi.SetName>().isEmpty())
            assertTrue(client.sentOf<TdApi.SetUsername>().isEmpty())
            assertEquals(listOf(EditProfileEffect.Saved), effects)
            assertFalse(viewModel.content.hasChanges)
        }

    @Test
    fun `everything changed is saved username first, names trimmed`() =
        runTest {
            val viewModel = viewModel()

            send(
                viewModel,
                EditProfileEvent.FirstNameChanged(" Augusta "),
                EditProfileEvent.LastNameChanged("King "),
                EditProfileEvent.BioChanged("Countess"),
            )
            typeUsername(viewModel, "augusta")
            send(viewModel, EditProfileEvent.Save)

            val saves = client.sent.filter { it is TdApi.SetUsername || it is TdApi.SetName || it is TdApi.SetBio }
            assertEquals(
                listOf(TdApi.SetUsername::class, TdApi.SetName::class, TdApi.SetBio::class),
                saves.map { it::class },
            )
            val name = client.sentOf<TdApi.SetName>().single()
            assertEquals("Augusta" to "King", name.firstName to name.lastName)
            assertEquals("augusta", client.sentOf<TdApi.SetUsername>().single().username)
        }

    @Test
    fun `the save action shows progress, and the fields wait`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetBio::class) { answer.await() }
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.BioChanged("Countess"), EditProfileEvent.Save)
            assertTrue(viewModel.content.isSaving)
            assertFalse(viewModel.content.canSave)

            send(viewModel, EditProfileEvent.BioChanged("Typed meanwhile"), EditProfileEvent.Save)
            assertEquals("Countess", viewModel.content.fields.bio)

            answer.complete(ok)
            runCurrent()
            assertFalse(viewModel.content.isSaving)
            assertEquals(1, client.sentOf<TdApi.SetBio>().size)
        }

    @Test
    fun `removing the username asks first`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            typeUsername(viewModel, "")
            send(viewModel, EditProfileEvent.Save)
            assertEquals(EditProfileDialog.RemoveUsername, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.SetUsername>().isEmpty())

            send(viewModel, EditProfileEvent.ConfirmDialog)

            assertEquals(listOf(""), client.sentOf<TdApi.SetUsername>().map { it.username })
            assertNull(viewModel.content.dialog)
            assertEquals(listOf(EditProfileEffect.Saved), effects)
        }

    @Test
    fun `a failure stops saving, keeps what was saved, and shows why`() =
        runTest {
            client.on(TdApi.SetName::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 300")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.LastNameChanged("King"), EditProfileEvent.BioChanged("Countess"))
            typeUsername(viewModel, "augusta")
            send(viewModel, EditProfileEvent.Save)

            assertEquals(listOf(EditProfileEffect.ShowError(DomainError.FloodWait(300))), effects)
            assertTrue(client.sentOf<TdApi.SetBio>().isEmpty())
            val content = viewModel.content
            assertFalse(content.isSaving)
            assertEquals("augusta", content.saved.username)
            assertEquals(UsernameStatus.Unchanged, content.usernameStatus)
            assertTrue(content.nameChanged && content.bioChanged)
            assertTrue(content.canSave)
        }

    @Test
    fun `a username refused on saving shows below its field`() =
        runTest {
            client.on(TdApi.SetUsername::class, TdResult.Failure(TdError(400, "USERNAME_OCCUPIED")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            typeUsername(viewModel, "augusta")
            send(viewModel, EditProfileEvent.Save)

            assertEquals(UsernameStatus.Checked(UsernameCheck.Taken), viewModel.content.usernameStatus)
            assertEquals(listOf(EditProfileEffect.ShowError(DomainError.UsernameTaken)), effects)
        }

    @Test
    fun `a silent failure stops saving without a message`() =
        runTest {
            client.on(TdApi.SetBio::class, TdResult.Failure(TdError(406, "SOMETHING")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.BioChanged("Countess"), EditProfileEvent.Save)

            assertTrue(effects.isEmpty())
            assertFalse(viewModel.content.isSaving)
        }

    // Back

    @Test
    fun `back without changes leaves at once`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.Back)

            assertEquals(listOf(EditProfileEffect.Leave), effects)
        }

    @Test
    fun `back with changes asks first, and discarding leaves`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.BioChanged("Countess"), EditProfileEvent.Back)
            assertEquals(EditProfileDialog.Discard, viewModel.content.dialog)
            assertTrue(effects.isEmpty())

            send(viewModel, EditProfileEvent.DismissDialog)
            assertNull(viewModel.content.dialog)
            assertEquals("Countess", viewModel.content.fields.bio)

            send(viewModel, EditProfileEvent.Back, EditProfileEvent.ConfirmDialog)
            assertEquals(listOf(EditProfileEffect.Leave), effects)
        }

    @Test
    fun `back waits while saving`() =
        runTest {
            client.on(TdApi.SetBio::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.BioChanged("Countess"), EditProfileEvent.Save, EditProfileEvent.Back)

            assertTrue(effects.isEmpty())
            assertNull(viewModel.content.dialog)
        }

    // Process death

    @Test
    fun `typed text and an open confirmation survive process death`() =
        runTest {
            val first = viewModel()
            send(first, EditProfileEvent.FirstNameChanged("Augusta"), EditProfileEvent.BioChanged("Countess"))
            typeUsername(first, "augusta")
            send(first, EditProfileEvent.Back)

            // A new process: the saved state comes back, the repositories and TDLib start over.
            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
            val second = viewModel(restored)

            val content = second.content
            assertEquals(ProfileFields("Augusta", "Lovelace", "augusta", "Countess"), content.fields)
            assertEquals(saved, content.saved)
            assertEquals(EditProfileDialog.Discard, content.dialog)
            assertEquals(UsernameStatus.Checked(UsernameCheck.Available), content.usernameStatus)
        }

    // Photo

    @Test
    fun `a picked photo is prepared, uploaded with its progress, and its copy deleted`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetProfilePhoto::class) {
                client.emit(
                    TdApiFixtures.updateFile(
                        ProfileFixtures.uploadingFile(localPath = copy, size = 1_000, uploadedSize = 500),
                    ),
                )
                answer.await()
            }
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.PhotoPicked("content://media/picker/1"))

            assertEquals(listOf("content://media/picker/1"), photoFiles.prepared)
            assertEquals(PhotoTask.Uploading(progress = 0.5f), viewModel.content.photoTask)
            val photo = client.sentOf<TdApi.SetProfilePhoto>().single().photo as TdApi.InputChatPhotoStatic
            assertEquals(copy, (photo.photo as TdApi.InputFileLocal).path)
            assertTrue(photoFiles.discarded.isEmpty())

            answer.complete(ok)
            runCurrent()

            assertEquals(PhotoTask.None, viewModel.content.photoTask)
            assertEquals(listOf(copy), photoFiles.discarded)
        }

    @Test
    fun `one photo at a time`() =
        runTest {
            client.on(TdApi.SetProfilePhoto::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            send(
                viewModel,
                EditProfileEvent.PhotoPicked("content://media/picker/1"),
                EditProfileEvent.PhotoPicked("content://media/picker/2"),
            )

            assertEquals(listOf("content://media/picker/1"), photoFiles.prepared)
        }

    @Test
    fun `an image that cannot be read says so`() =
        runTest {
            photoFiles.onPreparePhoto = { Outcome.Failure(DomainError.PhotoInvalid) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.PhotoPicked("content://media/picker/1"))

            assertEquals(listOf(EditProfileEffect.ShowError(DomainError.PhotoInvalid)), effects)
            assertEquals(PhotoTask.None, viewModel.content.photoTask)
            assertTrue(client.sentOf<TdApi.SetProfilePhoto>().isEmpty())
        }

    @Test
    fun `a refused photo shows why, and its copy is deleted`() =
        runTest {
            client.on(TdApi.SetProfilePhoto::class, TdResult.Failure(TdError(400, "PHOTO_CROP_SIZE_SMALL")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, EditProfileEvent.PhotoPicked("content://media/picker/1"))

            assertEquals(listOf(EditProfileEffect.ShowError(DomainError.PhotoInvalid)), effects)
            assertEquals(listOf(copy), photoFiles.discarded)
            assertEquals(PhotoTask.None, viewModel.content.photoTask)
        }

    @Test
    fun `removing the photo asks first, then deletes the current one`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.DeleteProfilePhoto::class) { answer.await() }
            me.profilePhoto = TdApiFixtures.profilePhoto(id = 777)
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.RemovePhoto)
            assertEquals(EditProfileDialog.RemovePhoto, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.DeleteProfilePhoto>().isEmpty())

            send(viewModel, EditProfileEvent.ConfirmDialog)
            assertEquals(PhotoTask.Removing, viewModel.content.photoTask)
            assertNull(viewModel.content.dialog)
            assertEquals(777L, client.sentOf<TdApi.DeleteProfilePhoto>().single().profilePhotoId)

            answer.complete(ok)
            runCurrent()
            assertEquals(PhotoTask.None, viewModel.content.photoTask)
        }

    @Test
    fun `without a photo there is nothing to remove`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, EditProfileEvent.RemovePhoto)

            assertNull(viewModel.content.dialog)
        }
}

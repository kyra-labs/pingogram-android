package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.EditableProfile
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibProfileRepositoryTest {
    private val client = FakeTdlibClient()

    // Subscribed to TDLib's updates before the test emits any, as at app start.
    private fun TestScope.repository() = TdlibProfileRepository(client, backgroundScope).also { runCurrent() }

    private fun answerMe(user: TdApi.User) = client.on(TdApi.GetMe::class, TdResult.Success(user))

    private val ok = TdResult.Success(TdApi.Ok())

    @Test
    fun `the profile is the user's names, editable username and bio`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, firstName = "Ada", lastName = "Lovelace").withUsernames())
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(ProfileFixtures.userFullInfo(bio = "Analyst")))

            val profile = repository().editableProfile()

            assertEquals(Outcome.Success(EditableProfile("Ada", "Lovelace", "ada", "Analyst")), profile)
            assertEquals(42L, client.sentOf<TdApi.GetUserFullInfo>().single().userId)
        }

    @Test
    fun `the profile carries the channel shown on it`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, firstName = "Ada"))
            client.on(
                TdApi.GetUserFullInfo::class,
                TdResult.Success(ProfileFixtures.userFullInfo(bio = null).also { it.personalChatId = CHANNEL }),
            )

            val profile = (repository().editableProfile() as Outcome.Success).value

            assertEquals(CHANNEL, profile.personalChatId)
        }

    @Test
    fun `no username and no bio are empty`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, firstName = "Ada"))
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(ProfileFixtures.userFullInfo(bio = null)))

            assertEquals(Outcome.Success(EditableProfile("Ada", "", "", "")), repository().editableProfile())
        }

    @Test
    fun `a failed request fails the profile`() =
        runTest {
            client.on(TdApi.GetMe::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 5")))

            assertEquals(Outcome.Failure(DomainError.FloodWait(5)), repository().editableProfile())
            assertTrue(client.sentOf<TdApi.GetUserFullInfo>().isEmpty())
        }

    @Test
    fun `the bio limit follows TDLib's option, 70 until then and after logout`() =
        runTest {
            val repository = repository()
            assertEquals(70, repository.bioLengthMax.value)

            client.emit(ProfileFixtures.integerOption("bio_length_max", 140))
            runCurrent()
            assertEquals(140, repository.bioLengthMax.value)

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))
            runCurrent()
            assertEquals(70, repository.bioLengthMax.value)
        }

    @Test
    fun `usernames are checked for the user's own chat`() =
        runTest {
            val repository = repository()
            client.emit(TdApiFixtures.myIdOption(42))
            runCurrent()
            client.on(TdApi.CheckChatUsername::class, TdResult.Success(TdApi.CheckChatUsernameResultOk()))

            assertEquals(Outcome.Success(UsernameCheck.Available), repository.checkUsername("ada_l"))
            val sent = client.sentOf<TdApi.CheckChatUsername>().single()
            assertEquals(42L, sent.chatId)
            assertEquals("ada_l", sent.username)
        }

    @ParameterizedTest(name = "{1}")
    @MethodSource("checkResults")
    fun `maps every check result`(
        result: TdApi.CheckChatUsernameResult,
        expected: UsernameCheck,
    ) = runTest {
        val repository = repository()
        client.emit(TdApiFixtures.myIdOption(42))
        runCurrent()
        client.on(TdApi.CheckChatUsername::class, TdResult.Success(result))

        assertEquals(Outcome.Success(expected), repository.checkUsername("ada_l"))
    }

    @Test
    fun `no username is checked before TDLib named the user`() =
        runTest {
            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository().checkUsername("ada_l"))
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `sets the names, the username and the bio`() =
        runTest {
            client.on(TdApi.SetName::class, ok)
            client.on(TdApi.SetUsername::class, ok)
            client.on(TdApi.SetBio::class, ok)
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setName("Ada", ""))
            assertEquals(Outcome.Success(Unit), repository.setUsername(""))
            assertEquals(Outcome.Success(Unit), repository.setBio("Analyst"))

            val name = client.sentOf<TdApi.SetName>().single()
            assertEquals("Ada" to "", name.firstName to name.lastName)
            assertEquals("", client.sentOf<TdApi.SetUsername>().single().username)
            assertEquals("Analyst", client.sentOf<TdApi.SetBio>().single().bio)
        }

    @Test
    fun `the birthday comes with the profile, its year left out where TDLib has 0`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, firstName = "Ada"))
            client.on(
                TdApi.GetUserFullInfo::class,
                TdResult.Success(ProfileFixtures.userFullInfo(birthdate = TdApi.Birthdate(10, 12, 0))),
            )

            val profile = (repository().editableProfile() as Outcome.Success).value

            assertEquals(Birthdate(day = 10, month = 12, year = null), profile.birthdate)
        }

    @Test
    fun `sets and removes the birthday`() =
        runTest {
            client.on(TdApi.SetBirthdate::class, ok)
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setBirthdate(Birthdate(10, 12, 1815)))
            assertEquals(Outcome.Success(Unit), repository.setBirthdate(null))

            val (set, removed) = client.sentOf<TdApi.SetBirthdate>()
            assertEquals(Triple(10, 12, 1815), Triple(set.birthdate.day, set.birthdate.month, set.birthdate.year))
            assertEquals(null, removed.birthdate)
        }

    @Test
    fun `a taken username fails with its error`() =
        runTest {
            client.on(TdApi.SetUsername::class, TdResult.Failure(TdError(400, "USERNAME_OCCUPIED")))

            assertEquals(Outcome.Failure(DomainError.UsernameTaken), repository().setUsername("taken"))
        }

    @Test
    fun `a photo is uploaded from its file as the main photo, reporting its progress`() =
        runTest {
            val path = "/data/cache/profile_photos/new.jpg"
            client.on(TdApi.SetProfilePhoto::class) {
                // Another file's progress does not count.
                client.emit(TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = "/elsewhere.jpg")))
                client.emit(
                    TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = path, uploadedSize = 250)),
                )
                client.emit(
                    TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = path, uploadedSize = 750)),
                )
                client.emit(
                    TdApiFixtures.updateFile(
                        ProfileFixtures.uploadingFile(
                            localPath = path,
                            uploadedSize = 1_000,
                            isUploadingCompleted = true,
                        ),
                    ),
                )
                TdResult.Success(TdApi.Ok())
            }
            val progress = mutableListOf<Float>()

            val outcome = repository().setProfilePhoto(path) { progress += it }

            assertEquals(Outcome.Success(Unit), outcome)
            assertEquals(listOf(0.25f, 0.75f, 1f), progress)
            val sent = client.sentOf<TdApi.SetProfilePhoto>().single()
            assertFalse(sent.isPublic)
            val photo = sent.photo as TdApi.InputChatPhotoStatic
            assertEquals(path, (photo.photo as TdApi.InputFileLocal).path)
        }

    @Test
    fun `a refused photo fails with its error`() =
        runTest {
            client.on(TdApi.SetProfilePhoto::class, TdResult.Failure(TdError(400, "PHOTO_CROP_SIZE_SMALL")))

            assertEquals(Outcome.Failure(DomainError.PhotoInvalid), repository().setProfilePhoto("/a.jpg") {})
        }

    @Test
    fun `an upload waits as long as it makes progress, and gives up after a timeout without any`() =
        runTest {
            val path = "/data/cache/profile_photos/new.jpg"
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetProfilePhoto::class) { answer.await() }
            val repository = repository()
            val upload = async { repository.setProfilePhoto(path) {} }
            runCurrent()

            advanceTimeBy(25_000)
            client.emit(TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = path, uploadedSize = 100)))
            advanceTimeBy(25_000)
            assertFalse(upload.isCompleted)

            advanceTimeBy(6_000)
            assertEquals(Outcome.Failure(DomainError.Network), upload.await())
        }

    @Test
    fun `deletes the current photo`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, profilePhoto = TdApiFixtures.profilePhoto(id = 777)))
            client.on(TdApi.DeleteProfilePhoto::class, ok)

            assertEquals(Outcome.Success(Unit), repository().deleteProfilePhoto())
            assertEquals(777L, client.sentOf<TdApi.DeleteProfilePhoto>().single().profilePhotoId)
        }

    @Test
    fun `without a photo there is nothing to delete`() =
        runTest {
            answerMe(TdApiFixtures.user(id = 42, profilePhoto = null))

            assertEquals(Outcome.Success(Unit), repository().deleteProfilePhoto())
            assertTrue(client.sentOf<TdApi.DeleteProfilePhoto>().isEmpty())
        }

    private fun TdApi.User.withUsernames() =
        apply {
            usernames = TdApi.Usernames(arrayOf("ada", "ada_collectible"), emptyArray(), "ada", emptyArray(), "")
        }

    companion object {
        const val CHANNEL = -1_001_234L

        @JvmStatic
        fun checkResults(): List<Arguments> =
            listOf(
                Arguments.of(TdApi.CheckChatUsernameResultOk(), UsernameCheck.Available),
                Arguments.of(TdApi.CheckChatUsernameResultUsernameInvalid(), UsernameCheck.Invalid),
                Arguments.of(TdApi.CheckChatUsernameResultUsernameOccupied(), UsernameCheck.Taken),
                Arguments.of(TdApi.CheckChatUsernameResultUsernamePurchasable(), UsernameCheck.ForSale),
                Arguments.of(TdApi.CheckChatUsernameResultPublicChatsTooMany(), UsernameCheck.Unavailable),
                Arguments.of(TdApi.CheckChatUsernameResultPublicGroupsUnavailable(), UsernameCheck.Unavailable),
            )
    }

    @Test
    fun `the profile link is TDLib's, permanent with a username and temporary without`() =
        runTest {
            client.on(TdApi.GetUserLink::class, TdResult.Success(TdApi.UserLink("https://t.me/ada", 0)))
            val permanent = repository().profileLink()
            client.on(TdApi.GetUserLink::class, TdResult.Success(TdApi.UserLink("https://t.me/contact/abc", 3600)))
            val temporary = repository().profileLink()

            assertEquals(Outcome.Success(ProfileLink("https://t.me/ada", 0)), permanent)
            assertEquals(false, (permanent as Outcome.Success).value.isTemporary)
            assertEquals(true, (temporary as Outcome.Success).value.isTemporary)
            assertEquals(false, "contact/abc" in temporary.value.toString())
        }
}

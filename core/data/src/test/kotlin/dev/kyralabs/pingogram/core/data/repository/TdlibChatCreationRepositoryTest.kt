package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.ImageInfo
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * [TdlibChatCreationRepository] on a [FakeTdlibClient]: groups as basic groups or supergroups, the people Telegram
 * did not add, channels, their username and invite link, adding members, the photo after creation, and errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibChatCreationRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        TdlibChatCreationRepository(
            client = client,
            files = files,
            content = content,
            media = media,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private fun failure(
        code: Int,
        message: String,
    ) = TdResult.Failure(TdError(code, message))

    private fun failed(vararg userIds: Long) =
        TdApi.FailedToAddMembers(userIds.map { TdApi.FailedToAddMember(it, false, false) }.toTypedArray())

    private fun supergroupChat(
        id: Long,
        isChannel: Boolean,
    ) = chat(id = id, type = TdApi.ChatTypeSupergroup(SUPERGROUP_ID, isChannel))

    /** A prepared photo copy, as [TdlibChatCreationRepository.preparePhoto] leaves it. */
    private fun photoCopy(): String = File(files.newUploadDirectory(), "photo.jpg").apply { writeText("jpeg") }.path

    // Groups

    @Test
    fun `a group with people to add is a basic group with its title, members and auto-delete time`() =
        runTest {
            client.on(TdApi.CreateNewBasicGroupChat::class, TdResult.Success(TdApi.CreatedBasicGroupChat(-5, null)))

            val created = repository().createGroup("  Book club ", listOf(7, 8, 7), MessageAutoDelete.Week, null)

            assertEquals(Outcome.Success(CreatedGroup(-5, persistentListOf())), created)
            val sent = client.sentOf<TdApi.CreateNewBasicGroupChat>().single()
            assertArrayEquals(longArrayOf(7, 8), sent.userIds)
            assertEquals("Book club", sent.title)
            assertEquals(MessageAutoDelete.Week.seconds, sent.messageAutoDeleteTime)
            assertTrue(client.sentOf<TdApi.CreateNewSupergroupChat>().isEmpty())
        }

    @Test
    fun `the people Telegram did not add come back with the group`() =
        runTest {
            client.on(
                TdApi.CreateNewBasicGroupChat::class,
                TdResult.Success(TdApi.CreatedBasicGroupChat(-5, failed(8, 9))),
            )

            val created = repository().createGroup("Book club", listOf(7, 8, 9), MessageAutoDelete.Off, null)

            assertEquals(listOf(8L, 9L), (created as Outcome.Success).value.notAddedUserIds)
        }

    @Test
    fun `a group with no one to add is a supergroup`() =
        runTest {
            client.on(TdApi.CreateNewSupergroupChat::class, TdResult.Success(supergroupChat(-100, isChannel = false)))

            val created = repository().createGroup("Notes", emptyList(), MessageAutoDelete.Day, null)

            assertEquals(Outcome.Success(CreatedGroup(-100, persistentListOf())), created)
            val sent = client.sentOf<TdApi.CreateNewSupergroupChat>().single()
            assertFalse(sent.isChannel)
            assertFalse(sent.isForum)
            assertFalse(sent.forImport)
            assertNull(sent.location)
            assertEquals(MessageAutoDelete.Day.seconds, sent.messageAutoDeleteTime)
            assertTrue(client.sentOf<TdApi.AddChatMembers>().isEmpty())
            assertTrue(client.sentOf<TdApi.CreateNewBasicGroupChat>().isEmpty())
        }

    @Test
    fun `a group for more people than a basic group holds is a supergroup they are added to`() =
        runTest {
            client.on(TdApi.CreateNewSupergroupChat::class, TdResult.Success(supergroupChat(-100, isChannel = false)))
            client.on(TdApi.AddChatMembers::class, TdResult.Success(failed(3)))
            val people = (1L..200L).toList()

            val created = repository().createGroup("Everyone", people, MessageAutoDelete.Off, null)

            assertEquals(Outcome.Success(CreatedGroup(-100, persistentListOf(3L))), created)
            val added = client.sentOf<TdApi.AddChatMembers>().single()
            assertEquals(-100L, added.chatId)
            assertArrayEquals(people.toLongArray(), added.userIds)
        }

    @Test
    fun `a supergroup whose members cannot be added at all still counts, with no one added`() =
        runTest {
            client.on(TdApi.CreateNewSupergroupChat::class, TdResult.Success(supergroupChat(-100, isChannel = false)))
            client.on(TdApi.AddChatMembers::class, failure(400, "USERS_TOO_MUCH"))
            val people = (1L..200L).toList()

            val created = repository().createGroup("Everyone", people, MessageAutoDelete.Off, null)

            assertEquals(people, (created as Outcome.Success).value.notAddedUserIds)
        }

    @Test
    fun `creating fails with Telegram's reason`() =
        runTest {
            client.on(TdApi.CreateNewBasicGroupChat::class, failure(400, "CHANNELS_TOO_MUCH"))
            val repository = repository()

            assertEquals(
                Outcome.Failure(DomainError.TooManyChats),
                repository.createGroup("Club", listOf(7), MessageAutoDelete.Off, null),
            )
            client.on(TdApi.CreateNewBasicGroupChat::class, failure(429, "Too Many Requests: retry after 30"))
            assertEquals(
                Outcome.Failure(DomainError.FloodWait(30)),
                repository.createGroup("Club", listOf(7), MessageAutoDelete.Off, null),
            )
        }

    // Photos

    @Test
    fun `the photo is set once the group exists, and its copy deleted once TDLib answered`() =
        runTest {
            client.on(TdApi.CreateNewBasicGroupChat::class, TdResult.Success(TdApi.CreatedBasicGroupChat(-5, null)))
            val answer = CompletableDeferred<TdResult<TdApi.Ok>>()
            client.on(TdApi.SetChatPhoto::class) { answer.await() }
            val path = photoCopy()

            repository().createGroup("Club", listOf(7), MessageAutoDelete.Off, path)
            runCurrent()

            val sent = client.sentOf<TdApi.SetChatPhoto>().single()
            assertEquals(-5L, sent.chatId)
            assertEquals(path, ((sent.photo as TdApi.InputChatPhotoStatic).photo as TdApi.InputFileLocal).path)
            assertTrue(File(path).exists())
            answer.complete(TdResult.Success(TdApi.Ok()))
            runCurrent()
            assertFalse(File(path).exists())
        }

    @Test
    fun `a chat that could not be created keeps its photo's copy for another attempt`() =
        runTest {
            client.on(TdApi.CreateNewSupergroupChat::class, failure(403, "USER_RESTRICTED"))
            val path = photoCopy()

            val created = repository().createChannel("News", "", path)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.AccountRestricted), created)
            assertTrue(client.sentOf<TdApi.SetChatPhoto>().isEmpty())
            assertTrue(File(path).exists())
        }

    @Test
    fun `a picked image becomes a photo copy, anything else is refused and leaves nothing`() =
        runTest {
            val repository = repository()
            content.add("content://photo", "photo.jpg", "image/jpeg", bytes = byteArrayOf(1))
            media.images[1] = ImageInfo(800, 600, Orientation.Normal)
            content.add("content://clip", "clip.mp4", "video/mp4", bytes = byteArrayOf(2))

            val photo = repository.preparePhoto("content://photo")
            val video = repository.preparePhoto("content://clip")

            assertTrue(File((photo as Outcome.Success).value).exists())
            assertEquals(Outcome.Failure(DomainError.PhotoInvalid), video)
            assertEquals(1, File(cacheDir, "uploads").listFiles()?.size)
        }

    // Channels

    @Test
    fun `a channel is a supergroup that is a channel, with its description`() =
        runTest {
            client.on(TdApi.CreateNewSupergroupChat::class, TdResult.Success(supergroupChat(-100, isChannel = true)))

            val created = repository().createChannel(" News ", " What happens ", null)

            assertEquals(Outcome.Success(-100L), created)
            val sent = client.sentOf<TdApi.CreateNewSupergroupChat>().single()
            assertTrue(sent.isChannel)
            assertFalse(sent.isForum)
            assertEquals("News", sent.title)
            assertEquals("What happens", sent.description)
            assertEquals(0, sent.messageAutoDeleteTime)
        }

    @Test
    fun `a username is checked for the channel and set on its supergroup`() =
        runTest {
            client.on(TdApi.CheckChatUsername::class, TdResult.Success(TdApi.CheckChatUsernameResultUsernameOccupied()))
            client.on(TdApi.GetChat::class, TdResult.Success(supergroupChat(-100, isChannel = true)))
            client.on(TdApi.SetSupergroupUsername::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()

            assertEquals(Outcome.Success(UsernameCheck.Taken), repository.checkUsername(-100, "news"))
            assertEquals(-100L, client.sentOf<TdApi.CheckChatUsername>().single().chatId)
            assertEquals(Outcome.Success(Unit), repository.setUsername(-100, "news"))
            val set = client.sentOf<TdApi.SetSupergroupUsername>().single()
            assertEquals(SUPERGROUP_ID, set.supergroupId)
            assertEquals("news", set.username)
        }

    @Test
    fun `a taken username fails as taken`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(supergroupChat(-100, isChannel = true)))
            client.on(TdApi.SetSupergroupUsername::class, failure(400, "USERNAME_OCCUPIED"))

            assertEquals(Outcome.Failure(DomainError.UsernameTaken), repository().setUsername(-100, "news"))
        }

    @Test
    fun `the invite link is the primary one, created when there is none`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(supergroupChat(-100, isChannel = true)))
            val info = TdApi.SupergroupFullInfo()
            client.on(TdApi.GetSupergroupFullInfo::class) { TdResult.Success(info) }
            client.on(
                TdApi.ReplacePrimaryChatInviteLink::class,
                TdResult.Success(TdApi.ChatInviteLink().also { it.inviteLink = "https://t.me/+new" }),
            )
            val repository = repository()

            assertEquals(Outcome.Success("https://t.me/+new"), repository.inviteLink(-100))
            info.inviteLink = TdApi.ChatInviteLink().also { it.inviteLink = "https://t.me/+primary" }
            assertEquals(Outcome.Success("https://t.me/+primary"), repository.inviteLink(-100))
            assertEquals(1, client.sentOf<TdApi.ReplacePrimaryChatInviteLink>().size)
        }

    @Test
    fun `a basic group's invite link comes from its full info`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = -5, type = TdApi.ChatTypeBasicGroup(9))))
            val link = TdApi.ChatInviteLink().also { it.inviteLink = "https://t.me/+group" }
            val info = TdApi.BasicGroupFullInfo().also { it.inviteLink = link }
            client.on(TdApi.GetBasicGroupFullInfo::class, TdResult.Success(info))

            assertEquals(Outcome.Success("https://t.me/+group"), repository().inviteLink(-5))
            assertEquals(9L, client.sentOf<TdApi.GetBasicGroupFullInfo>().single().basicGroupId)
        }

    @Test
    fun `adding members gives whom Telegram did not add, and nothing is asked for no one`() =
        runTest {
            client.on(TdApi.AddChatMembers::class, TdResult.Success(failed(8)))
            val repository = repository()

            assertEquals(Outcome.Success(persistentListOf(8L)), repository.addMembers(-100, listOf(7, 8)))
            assertEquals(Outcome.Success(persistentListOf<Long>()), repository.addMembers(-100, emptyList()))
            assertEquals(1, client.sentOf<TdApi.AddChatMembers>().size)
        }

    private companion object {
        const val SUPERGROUP_ID = 77L
    }
}

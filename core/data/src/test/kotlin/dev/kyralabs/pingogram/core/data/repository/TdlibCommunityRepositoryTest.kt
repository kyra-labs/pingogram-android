package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CommunityAddChatRequest
import dev.kyralabs.pingogram.core.domain.model.CommunityAddResult
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.CommunityRole
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Communities: the cache of `updateCommunity`, their full info and its updates, renaming and deleting. */
class TdlibCommunityRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibCommunityRepository(client, backgroundScope)

    private fun community(
        status: TdApi.CommunityMemberStatus = TdApi.CommunityMemberStatusCreator(),
        id: Long = ID,
    ) = TdApi.Community(id, true, "Kyra Labs", null, 1_700_000_000, status, TdApi.CommunityPermissions(true), false)

    @Test
    fun `keeps the communities TDLib announces, with the user's role`() =
        runTest {
            val repository = repository()
            assertNull(repository.community(ID).first())

            client.emit(TdApi.UpdateCommunity(community()))
            assertEquals(CommunityRole.Owner, repository.community(ID).first()?.role)

            val admin =
                TdApi.CommunityMemberStatusAdministrator(
                    true,
                    TdApi.CommunityAdministratorRights(false, true, false, false, false),
                )
            client.emit(TdApi.UpdateCommunity(community(admin)))
            with(repository.community(ID).first()!!) {
                assertEquals(CommunityRole.Administrator(canChangeInfo = true, canEditChatList = false), role)
                assertEquals("Kyra Labs", name)
                assertTrue(canEditChatList)
            }
        }

    @Test
    fun `reads what a community holds, then follows its updates`() =
        runTest {
            client.on(
                TdApi.GetCommunityFullInfo::class,
                TdResult.Success(fullInfo(TdApi.CommunityChat(10, true, false))),
            )
            val repository = repository()
            val seen = mutableListOf<Outcome<*>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.details(ID).take(2).toList(seen)
            }
            runCurrent()

            client.emit(TdApi.UpdateCommunityFullInfo(ID, fullInfo(TdApi.CommunityChat(11, false, true))))
            runCurrent()

            val (first, second) = seen.map { (it as Outcome.Success<*>).value }
            assertEquals(listOf(CommunityChat(10, true, false)), (first as CommunityDetails).chats)
            assertEquals(
                listOf(CommunityChat(11, false, true)),
                (second as CommunityDetails).chats,
            )
        }

    @Test
    fun `renames with the name trimmed, refuses a blank one, and deletes`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.TitleInvalid), repository.rename(ID, "  "))
            repository.rename(ID, " Kyra ")
            repository.delete(ID)

            assertEquals("Kyra", client.sentOf<TdApi.SetCommunityName>().single().name)
            assertEquals(ID, client.sentOf<TdApi.DeleteCommunity>().single().communityId)
        }

    @Test
    fun `lists the joined communities in Telegram's order, then the ones joined since`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateCommunity(community(id = 2)))
            client.emit(TdApi.UpdateCommunity(community(id = 1)))
            client.emit(TdApi.UpdateCommunity(community(id = 3, status = TdApi.CommunityMemberStatusLeft())))
            client.on(TdApi.GetJoinedCommunities::class, TdResult.Success(TdApi.Communities(longArrayOf(1, 2, 3))))

            val lists = mutableListOf<List<Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.joined().collect { lists += (it as Outcome.Success).value.map { c -> c.id } }
            }
            runCurrent()
            client.emit(TdApi.UpdateCommunity(community(id = 5)))
            client.emit(TdApi.UpdateCommunity(community(id = 2, status = TdApi.CommunityMemberStatusLeft())))
            runCurrent()

            assertEquals(listOf(listOf(1L, 2L), listOf(1L, 2L, 5L), listOf(1L, 5L)), lists)
        }

    @Test
    fun `gives the communities it knows before TDLib answers which the user joined`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateCommunity(community(id = 1)))
            val answer = CompletableDeferred<TdResult<TdApi.Communities>>()
            client.on(TdApi.GetJoinedCommunities::class) { answer.await() }

            val lists = mutableListOf<List<Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.joined().collect { lists += (it as Outcome.Success).value.map { c -> c.id } }
            }
            runCurrent()
            assertEquals(listOf(listOf(1L)), lists)

            client.emit(TdApi.UpdateCommunity(community(id = 2)))
            answer.complete(TdResult.Success(TdApi.Communities(longArrayOf(2, 1))))
            runCurrent()

            assertEquals(listOf(listOf(1L), listOf(2L, 1L)), lists)
        }

    @Test
    fun `gives a community's full info read before at once, then TDLib's answer`() =
        runTest {
            client.on(
                TdApi.GetCommunityFullInfo::class,
                TdResult.Success(fullInfo(TdApi.CommunityChat(10, true, false))),
            )
            val repository = repository()
            repository.details(ID).first()
            val answer = CompletableDeferred<TdResult<TdApi.CommunityFullInfo>>()
            client.on(TdApi.GetCommunityFullInfo::class) { answer.await() }

            val seen = mutableListOf<List<Long>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.details(ID).collect { seen += (it as Outcome.Success).value.chats.map { c -> c.chatId } }
            }
            runCurrent()
            assertEquals(listOf(listOf(10L)), seen)

            answer.complete(TdResult.Success(fullInfo(TdApi.CommunityChat(11, true, false))))
            runCurrent()

            assertEquals(listOf(listOf(10L), listOf(11L)), seen)
        }

    @Test
    fun `adds a chat, or sends its administrators a request, and passes other errors on`() =
        runTest {
            val repository = repository()
            client.on(TdApi.AddCommunityChat::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(CommunityAddResult.Added), repository.addChat(ID, 10, isHidden = true))
            with(client.sentOf<TdApi.AddCommunityChat>().single()) {
                assertEquals(Triple(ID, 10L, true), Triple(communityId, chatId, isHidden))
            }

            client.on(TdApi.AddCommunityChat::class, TdResult.Failure(TdError(400, "COMMUNITY_REQUEST_CREATED")))
            assertEquals(Outcome.Success(CommunityAddResult.RequestSent), repository.addChat(ID, 11, isHidden = false))

            client.on(TdApi.AddCommunityChat::class, TdResult.Failure(TdError(400, "COMMUNITY_PEERS_TOO_MUCH")))
            assertEquals(Outcome.Failure(DomainError.CommunityFull), repository.addChat(ID, 12, isHidden = false))
        }

    @Test
    fun `reads the suitable chats and the requests, and answers and removes`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetSuitableCommunityChats::class, TdResult.Success(TdApi.Chats(2, longArrayOf(4, 5))))
            client.on(
                TdApi.GetCommunityAddChatRequests::class,
                TdResult.Success(
                    TdApi.CommunityAddChatRequests(
                        1,
                        arrayOf(TdApi.CommunityAddChatRequest(9, true, 42, 1_700_000)),
                        "",
                    ),
                ),
            )
            client.on(TdApi.ProcessCommunityAddChatRequest::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.RemoveCommunityChat::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(listOf(4L, 5L)), repository.suitableChats())
            assertEquals(
                Outcome.Success(
                    listOf(CommunityAddChatRequest(9, isHidden = true, senderUserId = 42, date = 1_700_000)),
                ),
                repository.addChatRequests(ID),
            )
            repository.processAddChatRequest(ID, 9, approve = false)
            repository.removeChat(ID, 10)

            with(client.sentOf<TdApi.ProcessCommunityAddChatRequest>().single()) {
                assertEquals(Triple(ID, 9L, false), Triple(communityId, chatId, approve))
            }
            assertEquals(10L, client.sentOf<TdApi.RemoveCommunityChat>().single().chatId)
        }

    @Test
    fun `mutes keeping the other settings, unmutes to the defaults, and collapses`() =
        runTest {
            val repository = repository()
            val settings =
                TdApi.ChatNotificationSettings().also {
                    it.useDefaultMuteFor = true
                    it.useDefaultShowPreview = false
                    it.showPreview = false
                }
            client.on(
                TdApi.GetCommunityFullInfo::class,
                TdResult.Success(TdApi.CommunityFullInfo(null, emptyArray(), 2, 0, 3, settings)),
            )
            client.on(TdApi.SetCommunityNotificationSettings::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleCommunityAreChatsCollapsed::class, TdResult.Success(TdApi.Ok()))

            repository.setMuted(ID, muted = true)
            with(client.sentOf<TdApi.SetCommunityNotificationSettings>().single().notificationSettings) {
                assertEquals(false, useDefaultMuteFor)
                assertEquals(Int.MAX_VALUE, muteFor)
                assertEquals(false, useDefaultShowPreview)
            }
            repository.setMuted(ID, muted = false)
            with(client.sentOf<TdApi.SetCommunityNotificationSettings>().last().notificationSettings) {
                assertEquals(true, useDefaultMuteFor)
                assertEquals(0, muteFor)
            }
            repository.setCollapsed(ID, collapsed = true)
            assertEquals(true, client.sentOf<TdApi.ToggleCommunityAreChatsCollapsed>().single().areChatsCollapsed)
        }

    @Test
    fun `full info carries the requests waiting and whether the community is muted`() =
        runTest {
            val muted = TdApi.ChatNotificationSettings().also { it.muteFor = 60 }
            client.on(
                TdApi.GetCommunityFullInfo::class,
                TdResult.Success(TdApi.CommunityFullInfo(null, emptyArray(), 2, 0, 3, muted)),
            )

            val details = (repository().details(ID).first() as Outcome.Success).value

            assertEquals(3, details.addChatRequestCount)
            assertTrue(details.isMuted)
        }

    private fun fullInfo(vararg chats: TdApi.CommunityChat) =
        TdApi.CommunityFullInfo(null, arrayOf(*chats), 2, 0, 0, null)

    private companion object {
        const val ID = 77L
    }
}

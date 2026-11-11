package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CommunityChat
import dev.kyralabs.pingogram.core.domain.model.CommunityDetails
import dev.kyralabs.pingogram.core.domain.model.CommunityRole
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
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

    private fun community(status: TdApi.CommunityMemberStatus = TdApi.CommunityMemberStatusCreator()) =
        TdApi.Community(ID, true, "Kyra Labs", null, 1_700_000_000, status, TdApi.CommunityPermissions(true), false)

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

    private fun fullInfo(vararg chats: TdApi.CommunityChat) = TdApi.CommunityFullInfo(null, arrayOf(*chats), 2, 0, 0, null)

    private companion object {
        const val ID = 77L
    }
}

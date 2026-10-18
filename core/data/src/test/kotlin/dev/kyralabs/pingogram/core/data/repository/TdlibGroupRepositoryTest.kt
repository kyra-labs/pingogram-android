package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.data.mapper.toBasicGroup
import dev.kyralabs.pingogram.core.data.mapper.toSupergroup
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.basicGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.supergroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateBasicGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateSupergroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TdlibGroupRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibGroupRepository(client, backgroundScope)

    @Test
    fun `knows no group before TDLib announces one`() =
        runTest {
            val repository = repository()

            assertNull(repository.basicGroup(5).first())
            assertNull(repository.supergroup(6).first())
            assertEquals(emptyMap<Long, Any>(), repository.basicGroups.first())
            assertEquals(emptyMap<Long, Any>(), repository.supergroups.first())
        }

    @Test
    fun `caches a basic group announced right after it was created, before TDLib could start`() =
        runTest {
            val repository = repository()
            val group = basicGroup(id = 5, memberCount = 12)

            // No suspension in between: an update without a subscriber is lost, as it would be from TDLib.
            client.emit(updateBasicGroup(group))

            assertEquals(group.toBasicGroup(), repository.basicGroup(5).first())
        }

    @Test
    fun `caches a supergroup announced right after it was created`() =
        runTest {
            val repository = repository()
            val channel = supergroup(id = 6, isChannel = true)

            client.emit(updateSupergroup(channel))

            assertEquals(channel.toSupergroup(), repository.supergroup(6).first())
        }

    @Test
    fun `a new update replaces the cached group`() =
        runTest {
            val repository = repository()
            client.emit(updateBasicGroup(basicGroup(id = 5, status = TdApiFixtures.memberStatus())))
            client.emit(updateSupergroup(supergroup(id = 6, status = TdApiFixtures.memberStatus())))

            client.emit(updateBasicGroup(basicGroup(id = 5, status = TdApiFixtures.leftStatus())))
            client.emit(updateSupergroup(supergroup(id = 6, status = TdApiFixtures.bannedStatus())))

            assertEquals(ChatMemberStatus.Left, repository.basicGroup(5).first()?.status)
            assertEquals(ChatMemberStatus.Banned, repository.supergroup(6).first()?.status)
        }

    @Test
    fun `the whole-map flows hold every group by id`() =
        runTest {
            val repository = repository()

            client.emit(updateBasicGroup(basicGroup(id = 5)))
            client.emit(updateBasicGroup(basicGroup(id = 7)))
            client.emit(updateSupergroup(supergroup(id = 6)))

            assertEquals(setOf(5L, 7L), repository.basicGroups.first().keys)
            assertEquals(setOf(6L), repository.supergroups.first().keys)
        }

    @Test
    fun `a group emits only its own changes`() =
        runTest {
            val repository = repository()
            client.emit(updateSupergroup(supergroup(id = 6)))

            repository.supergroup(6).test {
                awaitItem()

                client.emit(updateSupergroup(supergroup(id = 8)))
                client.emit(updateBasicGroup(basicGroup(id = 6)))

                expectNoEvents()
            }
        }

    @Test
    fun `the supergroups emit nothing when only basic groups change, and the other way round`() =
        runTest {
            val repository = repository()

            repository.supergroups.test {
                awaitItem()

                client.emit(updateBasicGroup(basicGroup(id = 5)))

                expectNoEvents()
            }
            repository.basicGroups.test {
                awaitItem()

                client.emit(updateSupergroup(supergroup(id = 6)))

                expectNoEvents()
            }
        }

    @Test
    fun `an update that changes nothing Pingogram keeps emits nothing`() =
        runTest {
            val repository = repository()
            client.emit(updateSupergroup(supergroup(id = 6)))

            repository.supergroups.test {
                awaitItem()

                client.emit(updateSupergroup(supergroup(id = 6).apply { boostLevel = 3 }))

                expectNoEvents()
            }
        }

    @Test
    fun `other updates leave the groups alone`() =
        runTest {
            val repository = repository()
            client.emit(updateBasicGroup(basicGroup(id = 5)))

            repository.basicGroups.test {
                awaitItem()

                client.emit(TdApi.UpdateOption("version", TdApi.OptionValueString("1.8.68")))

                expectNoEvents()
            }
        }

    @Test
    fun `forgets every group when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(updateBasicGroup(basicGroup(id = 5)))
            client.emit(updateSupergroup(supergroup(id = 6)))

            client.emitAuthorizationState(TdApiFixtures.closed())

            assertNull(repository.basicGroup(5).first())
            assertNull(repository.supergroup(6).first())
        }
}

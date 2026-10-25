package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibDataOptionsRepository]: TDLib's option `prefer_ipv6` and deleting the cloud drafts. */
class TdlibDataOptionsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibDataOptionsRepository(client)

    @Test
    fun `prefer IPv6 is read, then follows its option's updates and no other`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueBoolean(true)))

            repository.prefersIpv6.test {
                assertTrue(awaitItem())
                assertEquals("prefer_ipv6", client.sentOf<TdApi.GetOption>().single().name)

                client.emit(TdApi.UpdateOption("use_quick_ack", TdApi.OptionValueBoolean(false)))
                client.emit(TdApi.UpdateOption("prefer_ipv6", TdApi.OptionValueBoolean(false)))
                assertFalse(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `an option TDLib cannot read counts as IPv4`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Failure(TdError(400, "OPTION_NOT_FOUND")))

            repository.prefersIpv6.test {
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `deleting the cloud drafts leaves secret chats' drafts`() =
        runTest {
            client.on(TdApi.ClearAllDraftMessages::class, TdResult.Success(TdApi.Ok()))

            repository.deleteCloudDrafts()

            assertTrue(client.sentOf<TdApi.ClearAllDraftMessages>().single().excludeSecretChats)
        }
}

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Passkey
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibPasskeyRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibPasskeyRepository(client)

    @Test
    fun `lists the passkeys in Telegram's order`() =
        runTest {
            client.on(
                TdApi.GetLoginPasskeys::class,
                TdResult.Success(
                    TdApi.Passkeys(
                        arrayOf(
                            TdApi.Passkey("a", "Google Password Manager", 100, 200, 0),
                            TdApi.Passkey("b", "Bitwarden", 300, 0, 0),
                        ),
                    ),
                ),
            )

            val passkeys = (repository.passkeys() as Outcome.Success).value

            assertEquals(Passkey("a", "Google Password Manager", 100, 200), passkeys.first())
            assertEquals(listOf("a", "b"), passkeys.map { it.id })
        }

    @Test
    fun `hands Telegram's creation options on as they come`() =
        runTest {
            client.on(TdApi.GetPasskeyParameters::class, TdResult.Success(TdApi.Text("{\"rp\":{}}")))

            assertEquals(Outcome.Success("{\"rp\":{}}"), repository.creationOptions())
        }

    @Test
    fun `adds the password manager's passkey and removes one`() =
        runTest {
            client.on(TdApi.AddLoginPasskey::class, TdResult.Success(TdApi.Passkey("c", "1Password", 500, 0, 0)))
            client.on(TdApi.RemoveLoginPasskey::class, TdResult.Success(TdApi.Ok()))
            val attestation = byteArrayOf(1, 2, 3)

            val added = repository.add(PasskeyRegistration("{\"type\":\"webauthn.create\"}", attestation))
            val removed = repository.remove("a")

            assertEquals(Outcome.Success(Passkey("c", "1Password", 500, 0)), added)
            assertEquals(Outcome.Success(Unit), removed)
            val sent = client.sentOf<TdApi.AddLoginPasskey>().single()
            assertEquals("{\"type\":\"webauthn.create\"}", sent.clientData)
            assertArrayEquals(attestation, sent.attestationObject)
            assertEquals("a", client.sentOf<TdApi.RemoveLoginPasskey>().single().passkeyId)
        }

    @Test
    fun `a failure reaches the caller as a domain error`() =
        runTest {
            client.on(
                TdApi.GetLoginPasskeys::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            val failure = repository.passkeys() as Outcome.Failure

            assertTrue(failure.error is DomainError.FloodWait)
        }
}

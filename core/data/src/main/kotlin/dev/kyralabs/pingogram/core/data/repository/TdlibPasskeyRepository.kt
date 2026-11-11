package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.Passkey
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import dev.kyralabs.pingogram.core.domain.repository.PasskeyRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [PasskeyRepository] on TDLib's `getLoginPasskeys`, `getPasskeyParameters`, `addLoginPasskey` and
 * `removeLoginPasskey`, each a request to Telegram.
 */
@AccountScoped
class TdlibPasskeyRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : PasskeyRepository {
        override suspend fun passkeys(): Outcome<ImmutableList<Passkey>> =
            client.request(TdApi.GetLoginPasskeys()).map { passkeys ->
                passkeys.passkeys
                    .orEmpty()
                    .map { it.toPasskey() }
                    .toImmutableList()
            }

        override suspend fun creationOptions(): Outcome<String> =
            client.request(TdApi.GetPasskeyParameters()).map { it.text.orEmpty() }

        override suspend fun add(registration: PasskeyRegistration): Outcome<Passkey> =
            client
                .request(TdApi.AddLoginPasskey(registration.clientData, registration.attestationObject))
                .map { it.toPasskey() }

        override suspend fun remove(passkeyId: String): Outcome<Unit> =
            client.request(TdApi.RemoveLoginPasskey(passkeyId)).map {}
    }

private fun TdApi.Passkey.toPasskey() =
    Passkey(
        id = id.orEmpty(),
        name = name.orEmpty(),
        addedAt = additionDate.toLong(),
        lastUsedAt = lastUsageDate.toLong(),
    )

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toSavedProxy
import dev.kyralabs.pingogram.core.data.mapper.toTdlib
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ProxyRepository] on TDLib's `getProxies`, `addProxy`, `editProxy`, `enableProxy`, `disableProxy`, `removeProxy` and
 * `pingProxy`. TDLib has no update for its proxies, so [proxies] reads them again after each change made here
 * ([changes] counts them); every screen that changes proxies goes through this singleton.
 */
@AccountScoped
class TdlibProxyRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ProxyRepository {
        private val changes = MutableStateFlow(0)

        override val proxies: Flow<Outcome<ImmutableList<SavedProxy>>> =
            changes.mapLatest {
                client.request(TdApi.GetProxies()).map { added ->
                    added.proxies.mapNotNull { it.toSavedProxy() }.toImmutableList()
                }
            }

        override suspend fun add(
            proxy: Proxy,
            name: String,
            enable: Boolean,
        ): Outcome<SavedProxy> = saved(client.request(TdApi.AddProxy(proxy.toTdlib(), enable, name)))

        override suspend fun edit(
            id: Int,
            proxy: Proxy,
            name: String,
            enable: Boolean,
        ): Outcome<SavedProxy> = saved(client.request(TdApi.EditProxy(id, proxy.toTdlib(), enable, name)))

        override suspend fun enable(id: Int): Outcome<Unit> = changed(client.request(TdApi.EnableProxy(id)))

        override suspend fun disable(): Outcome<Unit> = changed(client.request(TdApi.DisableProxy()))

        override suspend fun remove(id: Int): Outcome<Unit> = changed(client.request(TdApi.RemoveProxy(id)))

        override suspend fun ping(proxy: Proxy): Outcome<Duration> =
            when (val outcome = client.request(TdApi.PingProxy(proxy.toTdlib()), PING_TIMEOUT)) {
                is Outcome.Success -> {
                    Outcome.Success(outcome.value.seconds.seconds)
                }

                // TDLib answers 400 with why it did not get through ("Connection refused", a timeout).
                is Outcome.Failure -> {
                    val error = outcome.error
                    Outcome.Failure(if (error.asInputError() != null) DomainError.Network else error)
                }
            }

        /** A proxy TDLib took, or its refusal as [DomainError.InputRejected]; [proxies] reads again on success. */
        private fun saved(outcome: Outcome<TdApi.AddedProxy>): Outcome<SavedProxy> =
            when (outcome) {
                is Outcome.Success -> {
                    changes.update { it + 1 }
                    outcome.value.toSavedProxy()?.let { Outcome.Success(it) }
                        ?: Outcome.Failure(DomainError.InputRejected)
                }

                is Outcome.Failure -> {
                    Outcome.Failure(outcome.error.asInputError() ?: outcome.error)
                }
            }

        private fun changed(outcome: Outcome<TdApi.Ok>): Outcome<Unit> = outcome.map { changes.update { it + 1 } }

        /** TDLib refuses a proxy it cannot use with 400 ("Wrong port number", "Wrong server name"). */
        private fun DomainError.asInputError(): DomainError? =
            if (this is DomainError.Unknown && code == BAD_REQUEST) DomainError.InputRejected else null

        private companion object {
            const val BAD_REQUEST = 400

            /** TDLib's own limit for a ping is 10 s; a little more lets its answer come first. */
            val PING_TIMEOUT = 15.seconds
        }
    }

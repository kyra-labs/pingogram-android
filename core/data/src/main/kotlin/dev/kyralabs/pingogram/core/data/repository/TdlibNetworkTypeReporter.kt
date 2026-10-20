package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells TDLib the network the device uses (`setNetworkType`, `docs/PERFORMANCE.md` §3): at once for every new default
 * network, even one of the same kind, as TDLib asks, so it reopens its connections on the new network instead of
 * waiting for the old ones to time out, and counts its traffic per kind of network. Each TDLib instance hears it once
 * it has its parameters (any state from `WaitPhoneNumber` on), so a new instance after a logout hears it too.
 * A failure is dropped: the next network change sends it again.
 */
@Singleton
class TdlibNetworkTypeReporter internal constructor(
    private val client: TdlibClient,
    private val defaultNetwork: Flow<DefaultNetwork>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        client: TdlibClient,
        networks: AndroidNetworkRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, networks.defaultNetwork, scope)

    private val started = AtomicBoolean(false)

    /** Starts following the network, once per process; returns at once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val networks = defaultNetwork.map { it.handle to it.status.type }.distinctUntilChanged()
            val acceptsRequests = client.authState.map { it.acceptsRequests }.distinctUntilChanged()
            combine(acceptsRequests, networks) { accepts, network -> network.takeIf { accepts } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { (_, type) -> client.request(TdApi.SetNetworkType(type.toTdlib())) }
        }
    }
}

/** TDLib takes requests other than its parameters once it has them, until it starts closing. */
private val AuthState.acceptsRequests: Boolean
    get() =
        when (this) {
            AuthState.Initializing,
            AuthState.WaitTdlibParameters,
            AuthState.LoggingOut,
            AuthState.Closing,
            AuthState.Closed,
            -> false

            else -> true
        }

private fun NetworkType.toTdlib(): TdApi.NetworkType =
    when (this) {
        NetworkType.None -> TdApi.NetworkTypeNone()
        NetworkType.WiFi -> TdApi.NetworkTypeWiFi()
        NetworkType.Mobile -> TdApi.NetworkTypeMobile()
        NetworkType.Roaming -> TdApi.NetworkTypeMobileRoaming()
    }

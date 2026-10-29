package dev.kyralabs.pingogram.core.data.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Tells TDLib the network the device uses and whether the app is in use (`docs/PERFORMANCE.md` §3):
 *
 * - `setNetworkType` at once for every new default network, even one of the same kind, as TDLib asks, so it reopens
 *   its connections on the new network instead of waiting for the old ones to time out, and counts its traffic per
 *   kind of network.
 * - The `online` option: true while an activity of the app is visible ([foreground]), false otherwise. TDLib reconnects
 *   at once while online and backs off for longer and longer while not, so without it a process Android froze in the
 *   background came back "Connecting…" for minutes.
 * - Coming back to the app while TDLib is not connected sends the network again, which makes TDLib drop the
 *   connections it is waiting on and open new ones at once.
 *
 * Each TDLib instance hears them once it has its parameters (any state from `WaitPhoneNumber` on), so a new instance
 * after a logout hears them too. A failure is dropped: the next change sends it again.
 */
@AccountScoped
class TdlibNetworkTypeReporter internal constructor(
    private val client: TdlibClient,
    private val defaultNetwork: Flow<DefaultNetwork>,
    private val foreground: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        client: TdlibClient,
        networks: dagger.Lazy<AndroidNetworkRepository>,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        client,
        // Created where the flow is collected, on the scope's dispatcher: the repository asks Android for the network
        // and registers its callbacks as it is created, binder calls that would otherwise run in Application.onCreate.
        flow { emitAll(networks.get().defaultNetwork) },
        ProcessLifecycleOwner
            .get()
            .lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) },
        scope,
    )

    private val started = AtomicBoolean(false)

    /** Starts following the network, once per process; returns at once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val networks =
            defaultNetwork
                .map { it.handle to it.status.type }
                .distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, null)
        val acceptsRequests = client.authState.map { it.acceptsRequests }.distinctUntilChanged()
        val inForeground = foreground.distinctUntilChanged()
        scope.launch {
            combine(acceptsRequests, networks) { accepts, network -> network.takeIf { accepts } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { (_, type) -> client.request(TdApi.SetNetworkType(type.toTdlib())) }
        }
        scope.launch {
            combine(acceptsRequests, inForeground) { accepts, online -> online.takeIf { accepts } }
                .distinctUntilChanged()
                .filterNotNull()
                .collect { online ->
                    client.request(TdApi.SetOption(ONLINE, TdApi.OptionValueBoolean(online)))
                    val network = networks.value
                    if (online && network != null && client.connectionState.value != ConnectionState.Ready) {
                        client.request(TdApi.SetNetworkType(network.second.toTdlib()))
                    }
                }
        }
    }
}

/** TDLib's option for whether the user uses the app (td_api.tl, "online"). */
private const val ONLINE = "online"

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

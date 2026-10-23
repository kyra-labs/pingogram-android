package dev.kyralabs.pingogram.core.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkRepository] on Android's `ConnectivityManager` (`ACCESS_NETWORK_STATE`): the device's default network, which
 * is the one apps' data goes through, from a default network callback registered once per process, and Data Saver's
 * state for Pingogram (`getRestrictBackgroundStatus`), followed through `ACTION_RESTRICT_BACKGROUND_CHANGED`. The
 * first status is read at once, so nothing waits for the first callback.
 *
 * [defaultNetwork] also tells networks apart, for TDLib, which wants to hear of every new network even of the same
 * kind ([TdlibNetworkTypeReporter]).
 */
@Singleton
class AndroidNetworkRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NetworkRepository {
        private val connectivity = checkNotNull(context.getSystemService(ConnectivityManager::class.java))

        /** The default network's capabilities as last reported; null without a default network. */
        @Volatile
        private var current: Pair<Network, NetworkCapabilities?>? =
            connectivity.activeNetwork?.let { it to connectivity.getNetworkCapabilities(it) }

        private val mutableDefaultNetwork = MutableStateFlow(snapshot())
        private val mutableNetworkStatus = MutableStateFlow(mutableDefaultNetwork.value.status)

        /** The default network and its status, then every change. */
        internal val defaultNetwork: StateFlow<DefaultNetwork> = mutableDefaultNetwork.asStateFlow()

        override val networkStatus: StateFlow<NetworkStatus> = mutableNetworkStatus.asStateFlow()

        init {
            connectivity.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        current = network to networkCapabilities
                        publish()
                    }

                    override fun onLost(network: Network) {
                        // A switch to another network may report the old one lost after the new one arrived.
                        if (current?.first == network) {
                            current = null
                            publish()
                        }
                    }
                },
            )
            ContextCompat.registerReceiver(
                context,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) = publish()
                },
                IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        @Synchronized
        private fun publish() {
            val snapshot = snapshot()
            mutableDefaultNetwork.value = snapshot
            mutableNetworkStatus.value = snapshot.status
        }

        private fun snapshot(): DefaultNetwork {
            val (network, capabilities) = current ?: return DefaultNetwork(handle = null, NetworkStatus.Offline)
            val type =
                if (capabilities == null) {
                    NetworkType.Mobile
                } else {
                    networkType(
                        isWifi =
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                        isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                        isNotMetered = capabilities.isNotMetered,
                        isRoaming = capabilities.isRoaming,
                    )
                }
            val dataSaver =
                connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            return DefaultNetwork(
                handle = network.networkHandle,
                status =
                    NetworkStatus(
                        type = type,
                        isMetered = capabilities?.isNotMetered != true,
                        isDataSaverOn = dataSaver,
                    ),
            )
        }
    }

/**
 * The default network: [handle] tells networks apart (null without one), [status] is what [NetworkRepository]
 * reports.
 */
internal data class DefaultNetwork(
    val handle: Long?,
    val status: NetworkStatus,
)

/**
 * The kind of a network from its transports: Wi-Fi (and Ethernet) as Wi-Fi, mobile data as mobile or roaming, and
 * anything else (a VPN that does not say what it runs over, Bluetooth) by whether it is metered.
 */
internal fun networkType(
    isWifi: Boolean,
    isCellular: Boolean,
    isNotMetered: Boolean,
    isRoaming: Boolean,
): NetworkType =
    when {
        isWifi -> NetworkType.WiFi
        isCellular && isRoaming -> NetworkType.Roaming
        isCellular -> NetworkType.Mobile
        isNotMetered -> NetworkType.WiFi
        else -> NetworkType.Mobile
    }

private val NetworkCapabilities.isNotMetered: Boolean
    get() = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

/** Roaming, from Android 9, which reports it per network; before, mobile data counts as the home network. */
private val NetworkCapabilities.isRoaming: Boolean
    get() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

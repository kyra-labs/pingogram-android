package dev.kyralabs.pingogram.core.domain.model

/**
 * The kind of network the device uses for its data (its default network), as automatic download and TDLib's network
 * statistics tell networks apart (`docs/PERFORMANCE.md` §3).
 */
enum class NetworkType {
    /** No network. */
    None,

    /** Wi-Fi, Ethernet, or another network that is not metered. */
    WiFi,

    /** Mobile data in the home network, or another metered network. */
    Mobile,

    /** Mobile data abroad. */
    Roaming,
}

/** The automatic download rules [NetworkType] follows; none without a network. */
val NetworkType.autoDownloadNetwork: AutoDownloadNetwork?
    get() =
        when (this) {
            NetworkType.None -> null
            NetworkType.WiFi -> AutoDownloadNetwork.WiFi
            NetworkType.Mobile -> AutoDownloadNetwork.Mobile
            NetworkType.Roaming -> AutoDownloadNetwork.Roaming
        }

/**
 * The device's network and how Android asks apps to treat it.
 *
 * @property type the kind of network.
 * @property isMetered the network costs money or is limited (mobile data, a phone's hotspot).
 * @property isDataSaverOn Android's Data Saver is on and Pingogram is not exempt from it.
 */
data class NetworkStatus(
    val type: NetworkType,
    val isMetered: Boolean,
    val isDataSaverOn: Boolean,
) {
    /** Data Saver holds back this network's data: it is on, and the network is metered. */
    val savesData: Boolean get() = isDataSaverOn && isMetered

    companion object {
        val Offline = NetworkStatus(NetworkType.None, isMetered = false, isDataSaverOn = false)
    }
}

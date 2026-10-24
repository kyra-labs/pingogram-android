package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableMap

/**
 * How much data Pingogram sent and received through Telegram (`REQUIREMENTS.md` FR-S1, Data and storage), as TDLib
 * counts it on this device (`getNetworkStatistics`), since [sinceDate] (Unix seconds): when the statistics were last
 * reset, or when TDLib first counted.
 *
 * @property usage what each network carried, by what it carried; networks and kinds with nothing are left out.
 */
data class NetworkUsage(
    val sinceDate: Long,
    val usage: ImmutableMap<AutoDownloadNetwork, ImmutableMap<DataKind, DataAmount>>,
)

/** Bytes sent and received. */
data class DataAmount(
    val sentBytes: Long,
    val receivedBytes: Long,
) {
    operator fun plus(other: DataAmount) = DataAmount(sentBytes + other.sentBytes, receivedBytes + other.receivedBytes)

    /** Both directions. */
    val totalBytes: Long get() = sentBytes + receivedBytes
}

/** What the data was for, as Data and storage sums TDLib's file types up. */
enum class DataKind {
    /** Messages, chats, settings and everything else that is no file. */
    Messages,

    Photos,

    /** Videos, GIFs and video messages. */
    Videos,

    VoiceMessages,

    Music,

    Files,

    /** Stickers, custom emoji, profile photos, thumbnails, wallpapers and other small files. */
    Other,

    /** Voice and video calls. */
    Calls,
}

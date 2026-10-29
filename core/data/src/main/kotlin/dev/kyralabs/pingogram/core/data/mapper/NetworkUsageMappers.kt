package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.DataAmount
import dev.kyralabs.pingogram.core.domain.model.DataKind
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage
import kotlinx.collections.immutable.toImmutableMap
import org.drinkless.tdlib.TdApi

/**
 * TDLib's statistics summed up by network and by [DataKind]. TDLib's "other" networks (Ethernet) count as Wi-Fi, as
 * automatic download treats them; what it counted without a network is left out.
 */
internal fun TdApi.NetworkStatistics.toNetworkUsage(): NetworkUsage {
    val usage = mutableMapOf<AutoDownloadNetwork, MutableMap<DataKind, DataAmount>>()
    entries.orEmpty().forEach { entry ->
        val (network, kind, amount) =
            when (entry) {
                is TdApi.NetworkStatisticsEntryFile -> {
                    Triple(
                        entry.networkType,
                        entry.fileType.dataKind(),
                        DataAmount(entry.sentBytes, entry.receivedBytes),
                    )
                }

                is TdApi.NetworkStatisticsEntryCall -> {
                    Triple(entry.networkType, DataKind.Calls, DataAmount(entry.sentBytes, entry.receivedBytes))
                }

                else -> {
                    return@forEach
                }
            }
        val autoDownloadNetwork = network.autoDownloadNetwork() ?: return@forEach
        if (amount.totalBytes <= 0) return@forEach
        val kinds = usage.getOrPut(autoDownloadNetwork) { mutableMapOf() }
        kinds[kind] = (kinds[kind] ?: DataAmount(0, 0)) + amount
    }
    return NetworkUsage(
        sinceDate = sinceDate.toLong(),
        usage = usage.mapValues { it.value.toImmutableMap() }.toImmutableMap(),
    )
}

private fun TdApi.NetworkType?.autoDownloadNetwork(): AutoDownloadNetwork? =
    when (this) {
        is TdApi.NetworkTypeWiFi, is TdApi.NetworkTypeOther -> AutoDownloadNetwork.WiFi
        is TdApi.NetworkTypeMobile -> AutoDownloadNetwork.Mobile
        is TdApi.NetworkTypeMobileRoaming -> AutoDownloadNetwork.Roaming
        else -> null
    }

/** The kind of data a file of this type is; no file type is what is not a file (messages, chats, settings). */
private fun TdApi.FileType?.dataKind(): DataKind =
    when (this) {
        null, is TdApi.FileTypeNone -> DataKind.Messages

        is TdApi.FileTypePhoto, is TdApi.FileTypeSelfDestructingPhoto, is TdApi.FileTypePhotoStory -> DataKind.Photos

        is TdApi.FileTypeVideo, is TdApi.FileTypeAnimation, is TdApi.FileTypeVideoNote,
        is TdApi.FileTypeSelfDestructingVideo, is TdApi.FileTypeSelfDestructingVideoNote,
        is TdApi.FileTypeVideoStory, is TdApi.FileTypeLivePhotoVideo, is TdApi.FileTypeSelfDestructingLivePhotoVideo,
        -> DataKind.Videos

        is TdApi.FileTypeVoiceNote, is TdApi.FileTypeSelfDestructingVoiceNote -> DataKind.VoiceMessages

        is TdApi.FileTypeAudio -> DataKind.Music

        is TdApi.FileTypeDocument, is TdApi.FileTypeSecure -> DataKind.Files

        else -> DataKind.Other
    }

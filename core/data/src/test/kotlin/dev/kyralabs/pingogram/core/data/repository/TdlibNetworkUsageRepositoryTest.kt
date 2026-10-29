package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.DataAmount
import dev.kyralabs.pingogram.core.domain.model.DataKind
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TdlibNetworkUsageRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibNetworkUsageRepository(client)

    @Test
    fun `sums TDLib's statistics up by network and kind`() =
        runTest {
            client.on(
                TdApi.GetNetworkStatistics::class,
                TdResult.Success(
                    TdApi.NetworkStatistics(
                        1_700_000_000,
                        arrayOf(
                            file(TdApi.FileTypeNone(), TdApi.NetworkTypeWiFi(), sent = 10, received = 20),
                            file(TdApi.FileTypePhoto(), TdApi.NetworkTypeWiFi(), sent = 0, received = 100),
                            file(TdApi.FileTypeSelfDestructingPhoto(), TdApi.NetworkTypeWiFi(), sent = 0, received = 5),
                            file(TdApi.FileTypeAnimation(), TdApi.NetworkTypeOther(), sent = 0, received = 7),
                            file(TdApi.FileTypeSticker(), TdApi.NetworkTypeMobile(), sent = 0, received = 3),
                            file(TdApi.FileTypeVoiceNote(), TdApi.NetworkTypeMobileRoaming(), sent = 4, received = 0),
                            // Nothing went over it, or it went over no network.
                            file(TdApi.FileTypeDocument(), TdApi.NetworkTypeMobile(), sent = 0, received = 0),
                            file(TdApi.FileTypeDocument(), TdApi.NetworkTypeNone(), sent = 9, received = 9),
                            TdApi.NetworkStatisticsEntryCall(TdApi.NetworkTypeMobile(), 50, 60, 12.0),
                        ),
                    ),
                ),
            )

            assertEquals(
                Outcome.Success(
                    NetworkUsage(
                        sinceDate = 1_700_000_000,
                        usage =
                            persistentMapOf(
                                AutoDownloadNetwork.WiFi to
                                    persistentMapOf(
                                        DataKind.Messages to DataAmount(10, 20),
                                        DataKind.Photos to DataAmount(0, 105),
                                        DataKind.Videos to DataAmount(0, 7),
                                    ),
                                AutoDownloadNetwork.Mobile to
                                    persistentMapOf(
                                        DataKind.Other to DataAmount(0, 3),
                                        DataKind.Calls to DataAmount(50, 60),
                                    ),
                                AutoDownloadNetwork.Roaming to
                                    persistentMapOf(DataKind.VoiceMessages to DataAmount(4, 0)),
                            ),
                    ),
                ),
                repository.usage(),
            )
            assertFalse(client.sentOf<TdApi.GetNetworkStatistics>().single().onlyCurrent)
        }

    @Test
    fun `resets the statistics`() =
        runTest {
            client.on(TdApi.ResetNetworkStatistics::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.reset())
        }

    private fun file(
        type: TdApi.FileType,
        network: TdApi.NetworkType,
        sent: Long,
        received: Long,
    ) = TdApi.NetworkStatisticsEntryFile(type, network, sent, received)
}

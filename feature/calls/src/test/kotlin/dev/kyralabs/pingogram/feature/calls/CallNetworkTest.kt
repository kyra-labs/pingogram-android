package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.feature.calls.engine.VideoQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** "Use less data for calls" ([withDataSaving]): where it applies, a call sends video as with Data Saver, 360p. */
class CallNetworkTest {
    private val wifi = NetworkStatus(NetworkType.WiFi, isMetered = false, isDataSaverOn = false)
    private val mobile = NetworkStatus(NetworkType.Mobile, isMetered = true, isDataSaverOn = false)

    private fun quality(
        network: NetworkStatus,
        saving: CallDataSaving,
    ) = VideoQuality.forNetwork(withDataSaving(network, saving))

    @Test
    fun `never leaves the network's quality`() {
        assertEquals(VideoQuality.High, quality(wifi, CallDataSaving.Never))
        assertEquals(VideoQuality.Medium, quality(mobile, CallDataSaving.Never))
    }

    @Test
    fun `on mobile data lowers mobile calls only`() {
        assertEquals(VideoQuality.High, quality(wifi, CallDataSaving.OnMobileData))
        assertEquals(VideoQuality.Low, quality(mobile, CallDataSaving.OnMobileData))
    }

    @Test
    fun `always lowers every call`() {
        assertEquals(VideoQuality.Low, quality(wifi, CallDataSaving.Always))
        assertEquals(VideoQuality.Low, quality(mobile, CallDataSaving.Always))
    }
}

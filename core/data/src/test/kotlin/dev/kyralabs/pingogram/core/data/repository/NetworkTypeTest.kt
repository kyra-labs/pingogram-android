package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [networkType], which [AndroidNetworkRepository] reads from the default network's capabilities. */
class NetworkTypeTest {
    @Test
    fun `Wi-Fi and Ethernet are Wi-Fi, metered or not`() {
        assertEquals(
            NetworkType.WiFi,
            networkType(isWifi = true, isCellular = false, isNotMetered = false, isRoaming = false),
        )
    }

    @Test
    fun `mobile data is mobile, or roaming abroad`() {
        assertEquals(
            NetworkType.Mobile,
            networkType(isWifi = false, isCellular = true, isNotMetered = false, isRoaming = false),
        )
        assertEquals(
            NetworkType.Roaming,
            networkType(isWifi = false, isCellular = true, isNotMetered = false, isRoaming = true),
        )
    }

    @Test
    fun `another network counts by whether it is metered`() {
        assertEquals(
            NetworkType.WiFi,
            networkType(isWifi = false, isCellular = false, isNotMetered = true, isRoaming = false),
        )
        assertEquals(
            NetworkType.Mobile,
            networkType(isWifi = false, isCellular = false, isNotMetered = false, isRoaming = false),
        )
    }
}

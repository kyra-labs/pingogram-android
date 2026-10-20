package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import kotlinx.coroutines.flow.Flow

/**
 * The device's location, for sharing it in a chat (`REQUIREMENTS.md` FR-C5 "location"): only while the user is
 * sending it, in the foreground, never in the background. The permission is asked for by the screen.
 */
interface DeviceLocationRepository {
    /** How much of the location the user lets Pingogram know now. */
    fun access(): LocationAccess

    /** Whether the device's location is turned on (Android's location setting). */
    fun isLocationEnabled(): Boolean

    /**
     * Searches for the device's location while collected, as precisely as [access] allows: first the last location
     * Android knows, if it is recent, then each measurement that is better than the one emitted before (more precise,
     * or clearly newer). Collecting it keeps the device's location providers on; cancelling it stops them. It ends
     * when there is no provider to ask, or none left: without access, with the location turned off, or when it is
     * turned off meanwhile.
     */
    fun locate(): Flow<DeviceLocation>
}

package dev.kyralabs.pingogram.feature.chat.ui.location

import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

/**
 * The device's location as tests set it: the [access] and [enabled] setting, and a search that emits what tests send
 * to [send] until they [end] it, or ends at once without a provider ([hasProvider] false). [searching] counts the
 * searches being collected, so tests see that one stops.
 */
class FakeDeviceLocationRepository : DeviceLocationRepository {
    var access = LocationAccess.Precise
    var enabled = true
    var hasProvider = true

    /** How many searches are being collected now. */
    var searching = 0
        private set

    /** How many searches were started. */
    var searches = 0
        private set

    private val fixes = MutableSharedFlow<DeviceLocation?>()

    /** Emits [location] to the searches being collected. */
    suspend fun send(location: DeviceLocation) = fixes.emit(location)

    /** Ends the searches being collected, as when Android turns the providers off. */
    suspend fun end() = fixes.emit(null)

    override fun access(): LocationAccess = access

    override fun isLocationEnabled(): Boolean = enabled

    override fun locate(): Flow<DeviceLocation> {
        if (!hasProvider) return emptyFlow()
        return flow {
            searches++
            searching++
            try {
                fixes.takeWhile { it != null }.collect { emit(checkNotNull(it)) }
            } finally {
                searching--
            }
        }
    }
}

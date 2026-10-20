package dev.kyralabs.pingogram.core.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import android.location.Location as AndroidLocation

/**
 * [DeviceLocationRepository] on the platform's `LocationManager`, without Google Play services, so the foss flavor
 * stays free of them (`docs/adr/0005-platform-location-manager.md`).
 *
 * - **Providers**: with precise access GPS and the network provider, with approximate access the network provider
 *   only (GPS needs precise access), and on Android 12 and later the platform's fused provider too, which combines
 *   them and serves approximate access as well. Only providers the device has and Android has turned on.
 * - **Measurements**: [LocationManagerCompat]'s `requestLocationUpdates` (Android 12's `LocationRequest` where there is
 *   one, the older listener API before), every second while collected, on the main thread, which only compares and
 *   emits; first the last location each provider knows, if it is at most [MAX_CACHED_AGE_MILLIS] old. Continuous
 *   updates rather than `getCurrentLocation` (Android 11+, one measurement), so the accuracy shown improves while the
 *   user looks at it; they stop as soon as the collector does.
 * - Which measurement wins: [isBetterThan]. Nothing about the location is logged.
 */
class AndroidDeviceLocationRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DeviceLocationRepository {
        private val manager: LocationManager? get() = context.getSystemService(LocationManager::class.java)

        override fun access(): LocationAccess =
            when {
                granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.Precise
                granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.Approximate
                else -> LocationAccess.None
            }

        override fun isLocationEnabled(): Boolean = manager?.let(LocationManagerCompat::isLocationEnabled) == true

        // Checked right here: providersFor gives no provider without a location permission (`:app` declares both),
        // and a permission revoked meanwhile throws the SecurityException caught below.
        @SuppressLint("MissingPermission")
        override fun locate(): Flow<DeviceLocation> =
            callbackFlow {
                val manager = manager
                val providers = manager?.let { providersFor(it, access()) }.orEmpty()
                if (manager == null || providers.isEmpty()) {
                    close()
                    return@callbackFlow
                }
                val lock = Any()
                var best: LocationFix? = null
                val offer = { location: AndroidLocation, cached: Boolean ->
                    val fix = location.toFix(cached)
                    synchronized(lock) {
                        if (fix.isBetterThan(best)) {
                            best = fix
                            trySend(fix.location)
                        }
                    }
                }
                val enabled = providers.toMutableSet()
                val listeners =
                    providers.map { provider ->
                        object : LocationListenerCompat {
                            override fun onLocationChanged(location: AndroidLocation) = offer(location, false)

                            override fun onProviderEnabled(provider: String) {
                                synchronized(lock) { enabled += provider }
                            }

                            override fun onProviderDisabled(provider: String) {
                                // The user turned the location off, or this provider: end once none is left.
                                val noneLeft =
                                    synchronized(lock) {
                                        enabled -= provider
                                        enabled.isEmpty()
                                    }
                                if (noneLeft) close()
                            }
                        }
                    }
                try {
                    providers.forEach { provider ->
                        manager
                            .getLastKnownLocation(provider)
                            ?.takeIf { it.ageMillis() <= MAX_CACHED_AGE_MILLIS }
                            ?.let { offer(it, true) }
                    }
                    val executor = ContextCompat.getMainExecutor(context)
                    providers.zip(listeners).forEach { (provider, listener) ->
                        LocationManagerCompat.requestLocationUpdates(manager, provider, Request, executor, listener)
                    }
                } catch (_: SecurityException) {
                    // The permission was revoked meanwhile.
                    close()
                }
                awaitClose {
                    try {
                        listeners.forEach { LocationManagerCompat.removeUpdates(manager, it) }
                    } catch (_: SecurityException) {
                        // Revoked meanwhile: Android stopped the updates with the permission.
                    }
                }
            }

        private fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private companion object {
            /** Every second at most, as precisely as the provider can: only while the user waits for it. */
            val Request: LocationRequestCompat =
                LocationRequestCompat
                    .Builder(UPDATE_INTERVAL_MILLIS)
                    .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                    .build()
        }
    }

/** The providers that can give [access], that the device has and that are on, the most precise first. */
internal fun providersFor(
    manager: LocationManager,
    access: LocationAccess,
): List<String> {
    val platform =
        when (access) {
            LocationAccess.Precise -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            LocationAccess.Approximate -> listOf(LocationManager.NETWORK_PROVIDER)
            LocationAccess.None -> return emptyList()
        }
    val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(LocationManager.FUSED_PROVIDER) else listOf()
    val present = manager.allProviders.toSet()
    return (fused + platform).filter { it in present && manager.isProviderEnabled(it) }
}

/** A measurement: [location] for the domain, measured at [elapsedRealtimeMillis] (since boot). */
internal data class LocationFix(
    val location: DeviceLocation,
    val elapsedRealtimeMillis: Long,
)

/**
 * Whether this measurement should replace [current]: when there is none, when it is clearly newer (by more than
 * [SIGNIFICANTLY_NEWER_MILLIS]: the device may have moved since), or, measured about as recently, when it is at least
 * as precise. One without accuracy counts as the least precise. A clearly older one never does: the last location a
 * provider knows does not replace a fresh measurement.
 */
internal fun LocationFix.isBetterThan(current: LocationFix?): Boolean {
    if (current == null) return true
    val newer = elapsedRealtimeMillis - current.elapsedRealtimeMillis
    return when {
        newer > SIGNIFICANTLY_NEWER_MILLIS -> true
        newer < -SIGNIFICANTLY_NEWER_MILLIS -> false
        else -> location.accuracyOrWorst <= current.location.accuracyOrWorst
    }
}

private val DeviceLocation.accuracyOrWorst: Float get() = accuracyMeters ?: Float.MAX_VALUE

private fun AndroidLocation.toFix(cached: Boolean): LocationFix =
    LocationFix(
        location =
            DeviceLocation(
                location = Location(latitude, longitude),
                accuracyMeters = accuracy.takeIf { hasAccuracy() },
                isCached = cached,
            ),
        elapsedRealtimeMillis = elapsedRealtimeNanos / NANOS_PER_MILLI,
    )

private fun AndroidLocation.ageMillis(): Long = SystemClock.elapsedRealtime() - elapsedRealtimeNanos / NANOS_PER_MILLI

private const val UPDATE_INTERVAL_MILLIS = 1_000L

/** The oldest last-known location shown while searching: two minutes, about as far as anyone walks in that time. */
private const val MAX_CACHED_AGE_MILLIS = 120_000L

/** How much newer a measurement must be to replace a more precise one. */
private const val SIGNIFICANTLY_NEWER_MILLIS = 10_000L

private const val NANOS_PER_MILLI = 1_000_000L

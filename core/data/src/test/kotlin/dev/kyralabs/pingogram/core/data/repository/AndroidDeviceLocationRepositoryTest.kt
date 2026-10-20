package dev.kyralabs.pingogram.core.data.repository

import android.Manifest
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.location.Location as AndroidLocation

/**
 * [AndroidDeviceLocationRepository] on Robolectric's `LocationManager`: the access the permissions give, the providers
 * each access asks, a recent last-known location first, then only measurements that are better, and the search ending
 * when there is no provider (left) to ask.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidDeviceLocationRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val repository = AndroidDeviceLocationRepository(context)

    /** Robolectric turns the network provider off at first; a phone has it on. */
    @Before
    fun turnProvidersOn() {
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    private fun grant(vararg permissions: String) = shadowOf(context).grantPermissions(*permissions)

    private fun location(
        provider: String,
        accuracy: Float,
        ageMillis: Long = 0,
        latitude: Double = 48.85,
    ) = AndroidLocation(provider).apply {
        this.latitude = latitude
        longitude = 2.29
        this.accuracy = accuracy
        time = System.currentTimeMillis() - ageMillis
        elapsedRealtimeNanos = (SystemClock.elapsedRealtime() - ageMillis) * 1_000_000
    }

    /** Collects [AndroidDeviceLocationRepository.locate] into the returned list; [ended] once it ends. */
    private class Collected(
        val fixes: MutableList<DeviceLocation> = mutableListOf(),
        var ended: Boolean = false,
        var job: Job? = null,
    )

    private fun CoroutineScope.collect(): Collected {
        val collected = Collected()
        collected.job =
            launch(UnconfinedTestDispatcher()) {
                repository.locate().onCompletion { collected.ended = true }.collect { collected.fixes += it }
            }
        return collected
    }

    private fun deliver(location: AndroidLocation) {
        shadowOf(manager).simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `access follows the granted permissions`() {
        assertEquals(LocationAccess.None, repository.access())
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(LocationAccess.Approximate, repository.access())
        grant(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(LocationAccess.Precise, repository.access())
    }

    @Test
    fun `the location setting is read from Android`() {
        shadowOf(manager).setLocationEnabled(false)
        assertFalse(repository.isLocationEnabled())
        shadowOf(manager).setLocationEnabled(true)
        assertTrue(repository.isLocationEnabled())
    }

    @Test
    fun `approximate access asks the network and fused providers, never GPS`() {
        val providers = providersFor(manager, LocationAccess.Approximate)

        assertFalse(LocationManager.GPS_PROVIDER in providers)
        assertTrue(LocationManager.NETWORK_PROVIDER in providers)
        assertEquals(listOf<String>(), providersFor(manager, LocationAccess.None))
    }

    @Test
    fun `precise access asks GPS too, but only providers that are on`() {
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        val providers = providersFor(manager, LocationAccess.Precise)

        assertTrue(LocationManager.GPS_PROVIDER in providers)
        assertFalse(LocationManager.NETWORK_PROVIDER in providers)
    }

    @Test
    fun `without access the search ends at once`() =
        runTest {
            val collected = collect()

            assertTrue(collected.ended)
            assertEquals(listOf<DeviceLocation>(), collected.fixes)
        }

    @Test
    fun `a recent last-known location comes first, an old one never`() =
        runTest {
            grant(Manifest.permission.ACCESS_FINE_LOCATION)
            shadowOf(manager).setLastKnownLocation(
                LocationManager.GPS_PROVIDER,
                location(LocationManager.GPS_PROVIDER, 30f, ageMillis = 60_000),
            )
            shadowOf(manager).setLastKnownLocation(
                LocationManager.NETWORK_PROVIDER,
                location(LocationManager.NETWORK_PROVIDER, 10f, ageMillis = 600_000),
            )

            val collected = collect()

            assertEquals(listOf(DeviceLocation(Location(48.85, 2.29), 30f, isCached = true)), collected.fixes)
            collected.job?.cancel()
        }

    @Test
    fun `measurements replace the shown one only when they are better`() =
        runTest {
            grant(Manifest.permission.ACCESS_FINE_LOCATION)
            val collected = collect()

            deliver(location(LocationManager.NETWORK_PROVIDER, 40f))
            deliver(location(LocationManager.GPS_PROVIDER, 8f, latitude = 48.86))
            deliver(location(LocationManager.NETWORK_PROVIDER, 40f))

            assertEquals(listOf(40f, 8f), collected.fixes.map { it.accuracyMeters })
            assertFalse(collected.fixes.any { it.isCached })
            assertFalse(collected.ended)
            collected.job?.cancel()
        }

    @Test
    fun `the search ends when the user turns the location off`() =
        runTest {
            grant(Manifest.permission.ACCESS_FINE_LOCATION)
            val collected = collect()

            // Turning the location off turns every provider off.
            manager.allProviders.forEach { shadowOf(manager).setProviderEnabled(it, false) }
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(collected.ended)
        }

    @Test
    fun `a clearly newer measurement wins over a more precise one, a clearly older one never`() {
        val precise = LocationFix(DeviceLocation(Location(1.0, 1.0), 5f), elapsedRealtimeMillis = 100_000)
        val rough = DeviceLocation(Location(1.0, 1.0), 50f)

        assertTrue(LocationFix(rough, elapsedRealtimeMillis = 111_000).isBetterThan(precise))
        assertFalse(LocationFix(rough, elapsedRealtimeMillis = 105_000).isBetterThan(precise))
        assertFalse(LocationFix(DeviceLocation(Location(1.0, 1.0), 1f), 80_000).isBetterThan(precise))
        assertTrue(LocationFix(rough, elapsedRealtimeMillis = 0).isBetterThan(null))
    }

    @Test
    fun `a measurement without accuracy counts as the least precise`() {
        val rough = LocationFix(DeviceLocation(Location(1.0, 1.0), 900f), elapsedRealtimeMillis = 100_000)

        assertFalse(LocationFix(DeviceLocation(Location(1.0, 1.0), null), 101_000).isBetterThan(rough))
    }
}
